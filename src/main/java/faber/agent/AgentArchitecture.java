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

		// Retries the LLM call itself, not the whole cycle — percepts are captured once, above, and
		// never re-sensed, since a failed parse must not cost the agent the very percepts that
		// triggered this cycle. Only the call+parse step retries; each attempt's tokens are summed
		// honestly into the final reported total, since real cost was spent on every attempt, not
		// just the one that happened to succeed.
		final int maxAttempts = 3;
		String attemptContext = context;
		LlmClient.LlmCallResult lastCallResult = null;
		PlanResult planResult = null;
		long sumInputNonCached = 0, sumCacheCreation = 0, sumCacheRead = 0, sumOutput = 0;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			lastCallResult = llm.plan(SystemPrompt.systemPrompt, attemptContext);
			sumInputNonCached += lastCallResult.numInputTokens();
			sumCacheCreation += lastCallResult.cacheCreationInputTokens();
			sumCacheRead += lastCallResult.cacheReadInputTokens();
			sumOutput += lastCallResult.numOutputTokens();

			try {
				planResult = PlanResult.parse(lastCallResult.output());
				break;
			} catch (IllegalStateException parseError) {
				if (attempt == maxAttempts) {
					throw new IllegalStateException("Model output remained malformed after " + maxAttempts
							+ " attempts — giving up rather than retry indefinitely. Last parse error: "
							+ parseError.getMessage(), parseError);
				}
				System.out.println("[RETRY] cycle " + nextCycleToRun + ", attempt " + attempt
						+ " produced malformed output (" + parseError.getMessage() + "). Retrying with feedback.");
				attemptContext = context + "\n\n[NOTE: your previous response for this turn could not be "
						+ "parsed and was entirely discarded — nothing from it was acted on or recorded, "
						+ "so nothing is lost by trying again. The specific problem was: "
						+ parseError.getMessage() + " Please produce a fresh, complete response in the "
						+ "required three-part format — your reasoning, then your goals list, then your "
						+ "action — double-checking that every JSON object and array you open is properly "
						+ "closed before you finish.]";
			}
		}

		var llmCallResult = new LlmClient.LlmCallResult(
				lastCallResult.output(), sumInputNonCached, sumOutput, sumCacheCreation, sumCacheRead);
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

		// C1's keyword-overlap check wants whichever of objective/plan the model actually supplied —
		// an action's own justification typically cites specific plan detail (an operation, a date) at
		// least as often as the more abstract objective, so both are combined rather than picking one.
		String goalContent = tuple.isReactive() ? null : combineObjectiveAndPlan(
				goalLedger.objectiveOf(tuple.G), goalLedger.planOf(tuple.G));
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

	private final java.util.Map<String, MechanicalLog.PendingOp> thisCycleResolvedOps = new java.util.HashMap<>();

	private String assembleContext(List<Percept> percepts) {
		thisCycleResolvedOps.clear();
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
					MechanicalLog.PendingOp resolved = mechanicalLog.resolve(p.correlationId);
					if (resolved != null) {
						thisCycleResolvedOps.put(p.correlationId, resolved);
					}
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
	
	public String dumpLastCycleGoalChanges() {
		StringBuilder sb = new StringBuilder();
		var planRes = lastCycleResult.planResult();
		if (planRes.getGoals().size() > 0) {
			for (var g: planRes.getGoals()) {
				sb.append("- " + g.id);
				sb.append(" - status: " + (g.status != null ? g.status.toJsonValue() : "(no update this turn)"));
				if (g.objective != null) {
					sb.append(" - objective: " + g.objective);
				}
				if (g.plan != null) {
					sb.append(" - plan: " + g.plan);
				}
				if (g.trigger != null) {
					sb.append(" - trigger: condition=" + g.trigger.condition
							+ ", signal=" + g.trigger.signalArtifactId + "/" + g.trigger.signalName
							+ ", operation_name=" + g.trigger.operationName
							+ ", value_contains=" + g.trigger.signalValueContains
							+ ", recurring=" + g.trigger.recurring);
				}
				sb.append("\n");
			}
		} else {
			sb.append("(none)\n");
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
	 *
	 * "Addressed" also recognizes a second, independent path, found necessary
	 * from a real run: this method runs before the same cycle's <goal_changes> array is
	 * processed into the ledger (that happens later, in the extractor), so it
	 * previously had no way to see that the model had already resolved the
	 * trigger's goal via "status" in <goal_changes> this very cycle — even when the
	 * action itself (e.g. a plain send_msg_to_user reporting the outcome) never
	 * cited that goal_id. The goal got closed out correctly; only the audit was
	 * blind to it, producing a false-positive warning for a genuinely-addressed
	 * commitment. Checking result.getGoals() directly, before it's processed,
	 * closes that gap without weakening the check for a genuinely-ignored
	 * trigger — one with neither an action citation nor a status update has
	 * addressed nothing, and still warns exactly as before.
	 */
	private void checkTriggerFidelity(List<Percept> percepts, PlanResult result) {
		for (GoalLedger.PendingTrigger trigger : List.copyOf(goalLedger.pendingTriggers().values())) {
			boolean satisfied = conditionSatisfied(trigger, percepts);
			if (!satisfied)
				continue;

			boolean addressedByAction = trigger.goalId.equals(result.getActInfo().goalId());
			boolean addressedByResolution = resolvedInGoalsThisCycle(trigger.goalId, result.getGoals());
			boolean addressed = addressedByAction || addressedByResolution;
			if (addressed) {
				goalLedger.resolveTrigger(trigger.goalId);
				String via = addressedByAction ? "action cited it directly"
						: "goal marked achieved/dropped this cycle, action did not cite it";
				System.out.println("[TriggerFidelity] resolved: " + trigger.goalId + " (via: " + via + ")"
						+ (trigger.recurring ? " (recurring — stays active)" : ""));
			} else {
				System.out.println("[TriggerFidelity][WARNING] condition satisfied for goal '" + trigger.goalId
						+ "' this cycle, but action does not address it (planned: " + trigger.plannedAction + ")");
			}
		}
	}

	/** Joins whichever of objective/plan is present into one string for C1's keyword-overlap check. */
	private static String combineObjectiveAndPlan(String objective, String plan) {
		if (objective == null && plan == null) return null;
		if (objective == null) return plan;
		if (plan == null) return objective;
		return objective + " " + plan;
	}

	/** True if this cycle's <goal_changes> array includes a status change (achieved/dropped) for this goal id. */
	private static boolean resolvedInGoalsThisCycle(String goalId, List<PlanResult.GoalEntry> goals) {
		for (PlanResult.GoalEntry g : goals) {
			if (goalId.equals(g.id) && g.status != null) return true;
		}
		return false;
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
	 *
	 * A prerequisite bug found while adding signal_name_alternatives:
	 * operation_started/completed/failed percepts carry neither
	 * artifactId nor signalName/propName at all (confirmed directly
	 * against Percept's own factory methods) — meaning no trigger keyed
	 * to any of the three operation-lifecycle events could ever have
	 * been mechanically matched here, independent of alternatives. Real
	 * scenario logs showed operation-based triggers apparently
	 * "working" only because the model always happened to also cite the
	 * right goal id when addressing them — nothing was ever actually
	 * auditing those specific cases; a warning would never have fired
	 * either, regardless of whether the model got it right or wrong.
	 * effectiveArtifactId/effectiveSignalName resolve what these
	 * percepts implicitly refer to (via MechanicalLog, since
	 * correlationId is all they carry) instead of reading fields that
	 * were never populated for them in the first place.
	 *
	 * A second, related gap found afterward, in a real run: artifactId
	 * and signal_name alone are still not enough to identify which
	 * operation a trigger actually means, once an artifact can have
	 * more than one kind of operation in flight. A trigger registered
	 * for "the book_flight operation resolves" incorrectly fired when
	 * a completely different operation on the same artifact — an
	 * earlier list_available_dates call — happened to resolve first;
	 * both produce an identical operation_completed percept from this
	 * mechanism's point of view, since neither the percept nor the
	 * bare artifactId says which operation it was. operationName
	 * closes this the same way: resolved via the same MechanicalLog
	 * lookup, checked only when the trigger actually specifies one, so
	 * existing triggers with nothing to disambiguate keep working
	 * exactly as before.
	 */
	private boolean conditionSatisfied(GoalLedger.PendingTrigger trigger, List<Percept> percepts) {
		if (trigger.signalArtifactId == null || trigger.signalName == null) {
			return false; // no structural spec given — cannot be mechanically checked; never silently guess
		}
		for (Percept p : percepts) {
			if (!trigger.signalArtifactId.equals(effectiveArtifactId(p))) continue;
			if (!trigger.matchesSignalName(effectiveSignalName(p))) continue;

			if (trigger.operationName != null) {
				String opName = effectiveOperationName(p);
				if (opName == null || !opName.equals(trigger.operationName)) continue;
			}

			if (trigger.signalValueContains != null) {
				String line = p.toContextLine().toLowerCase();
				if (!line.contains(trigger.signalValueContains.toLowerCase())) continue;
			}
			return true;
		}
		return false;
	}

	/** operation_started/completed/failed carry no artifactId of their own — resolved via correlationId instead. */
	private String effectiveArtifactId(Percept p) {
		switch (p.type) {
			case OPERATION_STARTED: {
				MechanicalLog.PendingOp op = mechanicalLog.peek(p.correlationId);
				return op != null ? op.artifactId : null;
			}
			case OPERATION_COMPLETED:
			case OPERATION_FAILED: {
				MechanicalLog.PendingOp op = thisCycleResolvedOps.get(p.correlationId);
				return op != null ? op.artifactId : null;
			}
			default:
				return p.artifactId;
		}
	}

	/** Only meaningful for operation-lifecycle percepts — which specific operation this correlationId was. */
	private String effectiveOperationName(Percept p) {
		switch (p.type) {
			case OPERATION_STARTED: {
				MechanicalLog.PendingOp op = mechanicalLog.peek(p.correlationId);
				return op != null ? op.operationName : null;
			}
			case OPERATION_COMPLETED:
			case OPERATION_FAILED: {
				MechanicalLog.PendingOp op = thisCycleResolvedOps.get(p.correlationId);
				return op != null ? op.operationName : null;
			}
			default:
				return null;
		}
	}

	/** operation_started/completed/failed carry no signalName/propName — the percept's own type names it instead. */
	private String effectiveSignalName(Percept p) {
		switch (p.type) {
			case OPERATION_STARTED: return "operation_started";
			case OPERATION_COMPLETED: return "operation_completed";
			case OPERATION_FAILED: return "operation_failed";
			case ARTIFACT_SIGNAL: return p.signalName;
			case ARTIFACT_OBS_PROP_UPDATED: return p.propName;
			default: return null;
		}
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
