package faber.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

import com.anthropic.models.messages.Model;

import faber.agent.LlmClient.LlmCallResult;
import faber.agent.PlanResult.ActionInfo;
import faber.agent.formal.Coherence;
import faber.agent.formal.CoreTuple;
import faber.agent.formal.TupleExtractor;
import faber.agent.formal.WellFormedness;
import faber.agent.stages.StageProfile;
import faber.environment.Artifact;
import faber.environment.Workspace;

public class AgentArchitecture {

	private Agent agent;

	private EventQueue eventQueue;
	private Workspace workspace;

	private MechanicalLog mechanicalLog;
	private StateOfMind stateOfMind;
	private GoalLedger goalLedger;

	private TupleExtractor extractor;
	private StageProfile stageProfile;
	private List<CoreTuple> tupleTrace;
	private AtomicInteger correlationCounter;
	private CoreTuple previousTuple;

	private long nextCycleToRun;	
	private CycleResult lastCycleResult;
	
	private LlmClient llm;

	public static record ActResult(
			String act,
			boolean committedToWait) {}

	public static record CycleResult(
			long cycleNumber, 
			List<Percept> senseResult,
			String stateOfMind,
			PlanResult planResult, 
			ActResult actResult,
			CoreTuple coreTuple, 
			WellFormedness.Result wf,
			Coherence.Result coherence,
			LlmCallResult llmCallResult) {
	}

	
	public AgentArchitecture(Agent agent, EventQueue eventQueue, LlmClient llmClient) {
		this.eventQueue = eventQueue;
		this.agent = agent;
		llm = llmClient;
	}

	public AgentArchitecture(Agent agent, EventQueue eventQueue) {
		this(agent, eventQueue, new LlmClient.AnthropicLlmClient(Model.CLAUDE_SONNET_5));
	}

	void init(Workspace workspace) {
		this.workspace = workspace;
		mechanicalLog = new MechanicalLog();

		stageProfile = StageProfile.stage0();
		extractor = new TupleExtractor.HeuristicTupleExtractor();

		stateOfMind = new StateOfMind();
		goalLedger = new GoalLedger();

		tupleTrace = new ArrayList<>();
		correlationCounter = new AtomicInteger(0);

		previousTuple = null;
		nextCycleToRun = 1;

		lastCycleResult = new CycleResult(0, new ArrayList<Percept>(), this.getFullStateOfMind(), null, new ActResult("",false), null, null, null, null );	
	}

	public void notifyFailureForDisposedArtifactPendingOps(String artifactId) {
		for (String pendingId : mechanicalLog.pendingOpsFor(artifactId)) {
			mechanicalLog.resolve(pendingId);
			eventQueue.publish(Percept.operationFailed(pendingId, "artifact disposed before completion"));
		}
	}

	public CycleResult runOneCycle() throws Exception {
		var percepts = sense();
		String context = assembleContext(percepts);

		var llmCallResult = llm.plan(SystemPrompt.systemPrompt, context);
		var planResult = PlanResult.parse(llmCallResult.output());
		stateOfMind.update(planResult.getStateOfMind());

		var actResult = act(planResult.getActInfo());
		checkTriggerFidelity(percepts, planResult);

		CoreTuple tuple = extractor.extract(percepts, planResult, goalLedger);
		if (!stageProfile.relationAllowed(tuple.r)) {
			throw new IllegalStateException("Relation " + tuple.r + " used but its layer isn't active "
					+ "in this StageProfile — a layer leaking in without being declared.");
		}

		Set<String> groundedSource = new LinkedHashSet<>();
		for (Percept p : percepts)
			groundedSource.add(p.toContextLine());
		WellFormedness.Result wf = WellFormedness.check(tuple, groundedSource, goalLedger);

		String goalContent = tuple.isReactive() ? null : goalLedger.contentOf(tuple.G);
		Coherence.Result coherence = Coherence.check(tuple, previousTuple, goalContent, false);

		previousTuple = tuple;
		tupleTrace.add(tuple);
		
		lastCycleResult = new CycleResult(nextCycleToRun, percepts, this.getFullStateOfMind(), planResult, actResult,  tuple, wf, coherence, llmCallResult);	
		nextCycleToRun++;
		return lastCycleResult;
	}

	public CycleResult getLastCycleResult() {
		return lastCycleResult;
	}

	private String getFullStateOfMind() {
		StringBuffer sb = new StringBuffer("");
		sb.append("[PENDING INTENTIONS]\n").append(goalLedger.toContextBlock()).append("\n");
		sb.append("[STATE OF MIND]\n").append(stateOfMind.current()).append("\n");
		return sb.toString();
	}

	public long getNextCycleToRun() {
		return nextCycleToRun;
	}

	public List<CoreTuple> trace() {
		return tupleTrace;
	}

	private List<Percept> sense() throws InterruptedException {
		if (lastCycleResult.actResult().committedToWait()) {
			return eventQueue.awaitAtLeastOne(30_000);
		}
		return eventQueue.drainAll();
	}

	private String assembleContext(List<Percept> percepts) {
		StringBuilder sb = new StringBuilder();
		sb.append("[MECHANICAL LOG]\n").append(mechanicalLog.toContextBlock()).append("\n\n");
		sb.append("[WORKSPACE]\n").append(this.getWorkspaceContextBlock()).append("\n");
		sb.append("[PENDING INTENTIONS]\n").append(goalLedger.toContextBlock()).append("\n\n");
		sb.append("[STATE OF MIND]\n").append(stateOfMind.current()).append("\n\n");
		sb.append("[NEW PERCEPTS]\n");
		if (percepts.isEmpty()) {
			sb.append("(none)\n");
		} else {
			for (Percept p : percepts) {
				sb.append("- ").append(p.toContextLine()).append('\n');
				if (p.type == Percept.Type.OPERATION_COMPLETED || p.type == Percept.Type.OPERATION_FAILED) {
					mechanicalLog.resolve(p.correlationId);
				}
			}
		}
		return sb.toString();
	}

	public String dumpLightContext() {
		StringBuilder sb = new StringBuilder();
		sb.append("[MECHANICAL LOG]\n").append(mechanicalLog.toContextBlock()).append("\n");
		sb.append("[WORKSPACE]\n").append(this.dumpLightWorkspaceContextBlock());
		sb.append("[NEW PERCEPTS]\n");
		var percepts = lastCycleResult.senseResult();
		if (percepts.isEmpty()) {
			sb.append("(none)\n");
		} else {
			for (Percept p : percepts) {
				sb.append("- ").append(p.toContextLine()).append('\n');
				if (p.type == Percept.Type.OPERATION_COMPLETED || p.type == Percept.Type.OPERATION_FAILED) {
					mechanicalLog.resolve(p.correlationId);
				}
			}
		}
		return sb.toString();
	}
	
	public String dumpLastCyclePlanResultGoals() {
		StringBuilder sb = new StringBuilder();
		var planRes = lastCycleResult.planResult();
		// var actInfo = lastCycleResult.actResult();		
		// sb.append("action's goal_id: " + planRes.actionGoalId);
		sb.append("\nGoals identified in this cycle:\n");
		for (var g: planRes.getGoals()) {
			sb.append("- " + g.id);			
			if (g.content != null) {
				sb.append(" - content: " + g.content);
			}
			if (g.trigger != null) {
				sb.append(" - trigger: condition=" + g.trigger.condition
						+ ", signal=" + g.trigger.signalArtifactId + "/" + g.trigger.signalName
						+ ", value_contains=" + g.trigger.signalValueContains
						+ ", recurring=" + g.trigger.recurring);
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	private ActResult act(ActionInfo actTodo) {
		boolean committedToWait = false;
		String act = ""; 
		JSONObject content = actTodo.content();
		
		switch (actTodo.kind()) {
		case WAIT:
			committedToWait = true;
			act = "wait";
			break;

		case INVOKE: {
			committedToWait = false;
			String artifactId = content.getString("artifact_id");
			String operation = content.getString("operation_name");
			JSONObject args = content.getJSONObject("parameters");
			Artifact target = workspace.instanceOf(artifactId);
			if (target == null) {
				// Refused outright — no operation_started, per the system prompt's rule that a
				// request refused before ever starting fails without one.
				eventQueue.publish(Percept.operationFailed(newCorrelationId(), "no such artifact: " + artifactId));
				break;
			}
			String correlationId = newCorrelationId();
			mechanicalLog.recordInvocation(correlationId, artifactId, operation);
			target.invoke(agent, operation, args, correlationId); // publishes operation_started itself, synchronously
			act = "invoke " + artifactId + "." + operation + "(" + args + ")";
			break;
		}

		case FOCUS: {
			committedToWait = false;
			String artifactId = content.getString("artifact_id");
			try {
				workspace.startObserving(agent, artifactId);
				act = "focus " + artifactId;
			} catch (IllegalArgumentException e) {
				// Pre-existing gap this change would otherwise have walked straight into:
				// refusing
				// STOP_OBSERVING on an always-observed artifact needs this same handling, so
				// FOCUS
				// gets it too now rather than being allowed to crash the loop on a bad id.
				act = "focus " + artifactId + " refused - reason: " + e.getMessage();
			}
			break;
		}

		case STOP_OBSERVING: {
			committedToWait = false;
			String artifactId = content.getString("artifact_id");
			try {
				workspace.stopObserving(agent, artifactId);
				act = "stop_observing " + artifactId;
			} catch (IllegalArgumentException e) {
				act = "stop_observing" + artifactId + " refused";
			}
			break;
		}
		}
		return new ActResult(act, committedToWait);
		
	}

	private String newCorrelationId() {
		return "op_" + correlationCounter.incrementAndGet();
	}

	/**
	 * Audit-only, not a hard gate — mirrors C2's status. For each pending trigger,
	 * checks whether this cycle's percepts satisfy its condition (crude keyword
	 * overlap, the same honestly-simplified style as Coherence's C1) and, if so,
	 * whether this cycle's action addresses it (its goalId matches). A
	 * satisfied-but-unaddressed trigger is flagged, not blocked — the agent may
	 * have a legitimate reason to defer or reconsider (Bratman: intentions are
	 * revisable, just not silently so), and adjudicating that is left to a
	 * human/audit process, not enforced online.
	 */
	private void checkTriggerFidelity(List<Percept> percepts, PlanResult result) {
		for (GoalLedger.PendingTrigger trigger : List.copyOf(goalLedger.pendingTriggers().values())) {
			boolean satisfied = conditionSatisfied(trigger, percepts);
			if (!satisfied)
				continue;

			boolean addressed = trigger.goalId.equals(result.getActInfo().goalId());
			if (addressed) {
				goalLedger.resolveTrigger(trigger.goalId);
				System.out.println("[TriggerFidelity] resolved: " + trigger.goalId
						+ (trigger.recurring ? " (recurring — stays active)" : ""));
			} else {
				System.out.println("[TriggerFidelity][WARNING] condition satisfied for goal '" + trigger.goalId
						+ "' this cycle, but action does not address it (planned: " + trigger.plannedAction + ")");
			}
		}
	}

	/**
	 * Structural matching against the real Percept fields — no text
	 * heuristics at all. Replaces an earlier version that tried to
	 * infer what to check for by parsing the condition sentence itself
	 * (its first word, as a stand-in for the signal name), which broke
	 * twice in real runs: first when two unrelated percepts happened to
	 * share two incidental keywords, then again when a condition's own
	 * first word happened to be an ordinary word ("the") that matches
	 * almost any percept carrying natural-language content at all.
	 * signalValueContains stays as an explicit, scoped, optional text
	 * check — for the genuinely value-conditional cases (e.g. "sender
	 * is Marco specifically") — rather than being derived from the
	 * whole condition sentence the way the retired heuristic was.
	 */
	private static boolean conditionSatisfied(GoalLedger.PendingTrigger trigger, List<Percept> percepts) {
		if (trigger.signalArtifactId == null || trigger.signalName == null) {
			return false; // no structural spec given — cannot be mechanically checked; never silently guess
		}
		for (Percept p : percepts) {
			if (!trigger.signalArtifactId.equals(p.artifactId)) continue;

			boolean nameMatches = trigger.signalName.equals(p.signalName) || trigger.signalName.equals(p.propName);
			if (!nameMatches) continue;

			if (trigger.signalValueContains != null) {
				String line = p.toContextLine().toLowerCase();
				if (!line.contains(trigger.signalValueContains.toLowerCase())) continue;
			}
			return true;
		}
		return false;
	}

	/**
	 * Get the context block describing the current state of the workspace,
	 * according to the context schema adopted
	 * 
	 * @return
	 */
	private String getWorkspaceContextBlock() {
		StringBuilder sb = new StringBuilder();
		sb.append("available artifacts:\n");
		var availableArtifacts = workspace.getIAvailableArtifacts();

		if (availableArtifacts.isEmpty()) {
			sb.append("  (none)\n");
		} else {
			for (var ar : availableArtifacts) {
				sb.append("  - id: \"").append(ar.id()).append("\", type: \"").append(ar.type()).append("\"\n");
			}
		}
		sb.append("observed artifacts:\n");

		for (var instance : agent.getObservedArtifacts()) {
			Map<String, Object> props = instance == null ? Map.of() : instance.currentObsProperties();
			sb.append("  - id: \"").append(instance.id()).append("\"");
			if (props.isEmpty()) {
				sb.append("\n");
			} else {
				sb.append(", current properties: ").append(props).append("\n");
			}
		}

		sb.append("manuals:\n");
		for (var ar : availableArtifacts) {
			sb.append(workspace.manualFor(ar.type()).toJson()).append("\n");
		}

		return sb.toString();
	}
	
	public String dumpLightWorkspaceContextBlock() {
		StringBuilder sb = new StringBuilder();
		sb.append("available artifacts:\n");
		var availableArtifacts = workspace.getIAvailableArtifacts();

		if (availableArtifacts.isEmpty()) {
			sb.append("  (none)\n");
		} else {
			for (var ar : availableArtifacts) {
				sb.append("  - id: \"").append(ar.id()).append("\", type: \"").append(ar.type()).append("\"\n");
			}
		}
		sb.append("observed artifacts:\n");

		for (var instance : agent.getObservedArtifacts()) {
			Map<String, Object> props = instance == null ? Map.of() : instance.currentObsProperties();
			sb.append("  - id: \"").append(instance.id()).append("\"");
			if (props.isEmpty()) {
				sb.append("\n");
			} else {
				sb.append(", current properties: ").append(props).append("\n");
			}
		}

		sb.append("manuals: (...) \n");
		return sb.toString();
	}	
}
