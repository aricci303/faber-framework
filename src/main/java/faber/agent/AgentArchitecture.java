package faber.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

import com.anthropic.models.messages.Model;

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
	private boolean lastCycleCommittedToWait;

	private long nextCycleToRun;
	private CycleResult lastCycleResult;

	private List<Percept> lastCyclePercepts;
	private String lastCycleAct;

	
	private LlmClient llm;

	public static record CycleResult(long cycleNumber, PlanResult plan, CoreTuple tuple, WellFormedness.Result wf,
			Coherence.Result coherence) {
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
		lastCycleCommittedToWait = false;
		nextCycleToRun = 1;
		lastCycleResult = null;
		lastCyclePercepts = null;
	}

	public void notifyFailureForDisposedArtifactPendingOps(String artifactId) {
		for (String pendingId : mechanicalLog.pendingOpsFor(artifactId)) {
			mechanicalLog.resolve(pendingId);
			eventQueue.publish(Percept.operationFailed(pendingId, "artifact disposed before completion"));
		}
	}

	public void runOneCycle() throws Exception {
		lastCyclePercepts = sense();
		String context = assembleContext(lastCyclePercepts);

		String raw = llm.plan(SystemPrompt.systemPrompt, context);
		PlanResult result = PlanResult.parse(raw);
		stateOfMind.update(result.stateOfMind);

		act(result);
		checkTriggerFidelity(lastCyclePercepts, result);

		CoreTuple tuple = extractor.extract(lastCyclePercepts, result, goalLedger);
		if (!stageProfile.relationAllowed(tuple.r)) {
			throw new IllegalStateException("Relation " + tuple.r + " used but its layer isn't active "
					+ "in this StageProfile — a layer leaking in without being declared.");
		}

		Set<String> groundedSource = new LinkedHashSet<>();
		for (Percept p : lastCyclePercepts)
			groundedSource.add(p.toContextLine());
		WellFormedness.Result wf = WellFormedness.check(tuple, groundedSource, goalLedger);

		String goalContent = tuple.isReactive() ? null : goalLedger.contentOf(tuple.G);
		Coherence.Result coherence = Coherence.check(tuple, previousTuple, goalContent, false);

		previousTuple = tuple;
		tupleTrace.add(tuple);

		lastCycleResult = new CycleResult(nextCycleToRun, result, tuple, wf, coherence);
		nextCycleToRun++;

	}

	public CycleResult getLastCycleResult() {
		return lastCycleResult;
	}

	public String getLastCycleStateOfMind() {
		StringBuffer sb = new StringBuffer("");
		sb.append("[PENDING INTENTIONS]\n").append(goalLedger.toContextBlock()).append("\n");
		sb.append("[MENTAL FLOW]\n").append(stateOfMind.current()).append("\n");
		return sb.toString();
	}

	public long getNextCycleToRun() {
		return nextCycleToRun;
	}

	public List<CoreTuple> trace() {
		return tupleTrace;
	}

	private List<Percept> sense() throws InterruptedException {
		if (lastCycleCommittedToWait) {
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
		// sb.append("[PENDING INTENTIONS]\n").append(goalLedger.toContextBlock()).append("\n\n");
		// sb.append("[STATE OF MIND]\n").append(stateOfMind.current()).append("\n\n");
		sb.append("[NEW PERCEPTS]\n");
		if (lastCyclePercepts.isEmpty()) {
			sb.append("(none)\n");
		} else {
			for (Percept p : lastCyclePercepts) {
				sb.append("- ").append(p.toContextLine()).append('\n');
				if (p.type == Percept.Type.OPERATION_COMPLETED || p.type == Percept.Type.OPERATION_FAILED) {
					mechanicalLog.resolve(p.correlationId);
				}
			}
		}
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	private void act(PlanResult result) {
		switch (result.kind) {
		case WAIT:
			lastCycleCommittedToWait = true;
			lastCycleAct = "wait";
			break;

		case INVOKE: {
			lastCycleCommittedToWait = false;
			String artifactId = result.getString("artifact_id");
			String operation = result.getString("operation_name");
			JSONObject args = result.getJSONObject("parameters");
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
			lastCycleAct = "invoke " + artifactId + "." + operation + "(" + args + ")";
			break;
		}

		case FOCUS: {
			lastCycleCommittedToWait = false;
			String artifactId = result.getString("artifact_id");
			try {
				workspace.startObserving(agent, artifactId);
				lastCycleAct = "focus " + artifactId + ")";
			} catch (IllegalArgumentException e) {
				// Pre-existing gap this change would otherwise have walked straight into:
				// refusing
				// STOP_OBSERVING on an always-observed artifact needs this same handling, so
				// FOCUS
				// gets it too now rather than being allowed to crash the loop on a bad id.
				lastCycleAct = "focus " + artifactId + " refused - reason: " + e.getMessage();
			}
			break;
		}

		case STOP_OBSERVING: {
			lastCycleCommittedToWait = false;
			String artifactId = result.getString("artifact_id");
			try {
				workspace.stopObserving(agent, artifactId);
				lastCycleAct = "stop_observing " + artifactId;
			} catch (IllegalArgumentException e) {
				lastCycleAct = "stop_observing" + artifactId + " refused";
			}
			break;
		}
		}
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
			boolean satisfied = conditionSatisfied(trigger.condition, percepts);
			if (!satisfied)
				continue;

			boolean addressed = trigger.goalId.equals(result.goalId);
			if (addressed) {
				goalLedger.resolveTrigger(trigger.goalId);
				System.out.println("[TriggerFidelity] resolved: " + trigger.goalId);
			} else {
				System.out.println("[TriggerFidelity][WARNING] condition satisfied for goal '" + trigger.goalId
						+ "' this cycle, but action does not address it (planned: " + trigger.plannedAction + ")");
			}
		}
	}

	/**
	 * Requires the condition's primary term — its first word, by convention the
	 * signal/event name the condition names first, e.g. "email_received" in
	 * "email_received on email-01 with sender containing greg" — to appear verbatim
	 * in a percept line. An earlier version counted any two keyword hits, which
	 * produced false positives on operation_started/operation_failed lines that
	 * incidentally mentioned the same artifact id and a substring of the sender's
	 * address without being the signal at all. This is still an approximate
	 * heuristic — it cannot distinguish two different signals sharing a primary
	 * term but different senders — flagged here rather than left silent, in the
	 * same spirit as C1.
	 */
	private static boolean conditionSatisfied(String condition, List<Percept> percepts) {
		String primaryTerm = condition.trim().split("\\s+", 2)[0].toLowerCase();
		for (Percept p : percepts) {
			if (p.toContextLine().toLowerCase().contains(primaryTerm))
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
	
	public String getLastCycleActionDone() {
		return this.lastCycleAct;
	}

}
