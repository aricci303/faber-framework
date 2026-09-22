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

	final static private long PERCEPT_TIMEOUT = 30_000;

	/** The one goal every other goal can trace back to as an ancestor — see init(). */
	public final static String STANDING_GOAL_ID = "serve-user";
	
	private Agent agent;

	private EventQueue eventQueue;
	private Workspace workspace;

	private MechanicalLog mechanicalLog;
	private final java.util.Map<String, MechanicalLog.PendingOp> thisCycleResolvedOps = new java.util.HashMap<>();

	private StateOfMind stateOfMind;
	private GoalLedger goalLedger;
	private IntentionLedger intentionLedger;

	private List<Percept> currentPercepts;	
	private PlanResult currentPlanResult;
	private ActResult currentActResult;
	

	private CoreTuple previousTuple;
	private CoreTuple currentCoreTuple;
	private WellFormedness.Result currentWF;
	private Coherence.Result currentCoherence;
	private boolean currentGoalWasAlreadySatisfiedBeforeThisCycle;
    
	/* LLM related */
	private String currentContext;
	private LlmCallResult currentLLMCallResult;

	private TupleExtractor extractor;
	private StageProfile stageProfile;
	private List<CoreTuple> tupleTrace;
	private AtomicInteger correlationCounter;

	private long nextCycleToRun;	
	
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
		intentionLedger = new IntentionLedger();

		// Registered directly by the harness, not via a model-authored <intention_changes> entry —
		// this goal exists from cycle one, before any model output has happened at all, which is
		// exactly the case the wire protocol has no way to represent on its own. Every goal the model
		// itself introduces can cite this as parent_goal_id, giving even a purely reactive moment
		// (observing user-console-01 by default, with nothing else pending) a real, registered goal
		// to be MEANS_END relative to, rather than defaulting to REACTIVE for lack of anywhere to
		// attach. See the "Goal hierarchy" section of the system prompt for what this changes for
		// the model, and why REACTIVE still exists as a genuine possibility, not a removed one.
		//
		// Registering only the goal (GoalLedger) and not also an intention (IntentionLedger) would
		// leave it invisible in ONGOING INTENTIONS — that rendering reads activeIntentions(), which
		// has no entry for a goal that was never given a plan. A registered-but-invisible standing
		// goal is worse than not having one at all: the model would have to take the system prompt's
		// word for its existence, cycle after cycle, with nothing in its own context ever confirming
		// it. Both ledgers are seeded together here for exactly that reason.
		goalLedger.registerOrUpdate(STANDING_GOAL_ID,
				"Serve the user's requests as they arise, remaining available and responsive by default.");
		intentionLedger.registerOrUpdate(STANDING_GOAL_ID,
				"Observe user-console-01 by default and respond to whatever the user asks as it arrives. "
				+ "Every specific request becomes its own subgoal, in service of this one.");

		tupleTrace = new ArrayList<>();
		correlationCounter = new AtomicInteger(0);

		previousTuple = null;
		nextCycleToRun = 1;
	}

	public void notifyFailureForDisposedArtifactPendingOps(String artifactId) {
		for (String pendingId : mechanicalLog.pendingOpsFor(artifactId)) {
			mechanicalLog.resolve(pendingId);
			eventQueue.publish(Percept.operationFailed(pendingId, "artifact disposed before completion"));
		}
	}

	
	public void runOneCycle() throws Exception {
		sense();		
		plan();		
		act();
		doChecks();		
		nextCycleToRun++;
	}

	private void sense() throws InterruptedException {		
		if (currentActResult != null && currentActResult.committedToWait()) {
			currentPercepts = eventQueue.awaitAtLeastOne(PERCEPT_TIMEOUT);
		} else {
			currentPercepts = eventQueue.drainAll();
		}
		currentContext = assembleContext(currentPercepts);
	}

	private void plan() {
		// Retries the LLM call itself, not the whole cycle — percepts are captured once, above, and
		// never re-sensed, since a failed parse must not cost the agent the very percepts that
		// triggered this cycle. Only the call+parse step retries; each attempt's tokens are summed
		// honestly into the final reported total, since real cost was spent on every attempt, not
		// just the one that happened to succeed.
		final int maxAttempts = 3;
		String attemptContext = currentContext;
		LlmClient.LlmCallResult lastCallResult = null;
		currentPlanResult = null;
		long sumInputNonCached = 0, sumCacheCreation = 0, sumCacheRead = 0, sumOutput = 0;

		try {
			for (int attempt = 1; attempt <= maxAttempts; attempt++) {
				
				lastCallResult = llm.plan(SystemPrompt.systemPrompt, attemptContext);
				
				sumInputNonCached += lastCallResult.numInputTokens();
				sumCacheCreation += lastCallResult.cacheCreationInputTokens();
				sumCacheRead += lastCallResult.cacheReadInputTokens();
				sumOutput += lastCallResult.numOutputTokens();
	
				try {
					currentPlanResult = PlanResult.parse(lastCallResult.output());
					break;
				} catch (IllegalStateException parseError) {
					if (attempt == maxAttempts) {
						throw new IllegalStateException("Model output remained malformed after " + maxAttempts
								+ " attempts — giving up rather than retry indefinitely. Last parse error: "
								+ parseError.getMessage(), parseError);
					}
					System.out.println("[RETRY] cycle " + nextCycleToRun + ", attempt " + attempt
							+ " produced malformed output (" + parseError.getMessage() + "). Retrying with feedback.");
					attemptContext = currentContext + "\n\n[NOTE: your previous response for this turn could not be "
							+ "parsed and was entirely discarded — nothing from it was acted on or recorded, "
							+ "so nothing is lost by trying again. The specific problem was: "
							+ parseError.getMessage() + " Please produce a fresh, complete response in the "
							+ "required three-part format — your reasoning, then your goals list, then your "
							+ "action — double-checking that every JSON object and array you open is properly "
							+ "closed before you finish.]";
				}
			}
		} catch (Exception ex) {
			throw new IllegalStateException("Model cannot be accessed " + ex.getMessage());
		}

		currentLLMCallResult = new LlmClient.LlmCallResult(
				lastCallResult.output(), sumInputNonCached, sumOutput, sumCacheCreation, sumCacheRead);
		stateOfMind.update(currentPlanResult.getStateOfMind());
		
	}
		
	private void act() {
		boolean committedToWait = false;
		String act = ""; 
		var actTodo = currentPlanResult.getActInfo();
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
		currentActResult = new ActResult(act, committedToWait);	
	}
	
	
	private void doChecks() {
		// checkTriggerFidelity must run BEFORE extract() — extract() is what applies this cycle's own
		// <intention_changes> to the ledgers (registering a new trigger, clearing one via resolveGoal),
		// and checkTriggerFidelity needs to see each trigger's PRE-this-cycle state to check whether
		// THIS cycle's percepts satisfied it. Reordering these (extract() first) silently loses every
		// resolution where the same cycle that satisfies a trigger also replaces or clears it — which,
		// in practice, is nearly every real resolution, since acting on a satisfied trigger almost always
		// means moving on to a new plan/trigger or closing the goal out in that same cycle. Confirmed via
		// a real run (Scenario05) that showed zero TriggerFidelity output at any resolution point despite
		// WF and Coherence both reporting normally — the audit wasn't failing, it was running against a
		// ledger this cycle's own update had already overwritten.
		checkTriggerFidelity();

		// Same reasoning applies to C3 (Coherence): "already satisfied" has to mean satisfied as of
		// BEFORE this cycle's own action, not after — the common, correct pattern of a single cycle
		// both delivering a goal's result and marking it achieved in the same <intention_changes> entry
		// must not be judged as targeting an already-satisfied goal. The action's own cited goal id is
		// available directly from currentPlanResult, with no dependency on extract() having run yet.
		String actionGoalId = currentPlanResult.getActInfo().goalId();
		GoalStatus priorStatus = actionGoalId != null ? intentionLedger.statusOf(actionGoalId) : null;
		currentGoalWasAlreadySatisfiedBeforeThisCycle =
				priorStatus == GoalStatus.ACHIEVED || priorStatus == GoalStatus.DROPPED;

		currentCoreTuple = extractor.extract(currentPercepts, currentPlanResult, goalLedger, intentionLedger);
		checkWellformedness();
		checkCoherence();		
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
	 * from a real run: this method runs before the same cycle's <intention_changes> array is
	 * processed into the ledger (that happens later, in the extractor), so it
	 * previously had no way to see that the model had already resolved the
	 * trigger's goal via "status" in <intention_changes> this very cycle — even when the
	 * action itself (e.g. a plain send_msg_to_user reporting the outcome) never
	 * cited that goal_id. The goal got closed out correctly; only the audit was
	 * blind to it, producing a false-positive warning for a genuinely-addressed
	 * commitment. Checking result.getIntentionChanges() directly, before it's processed,
	 * closes that gap without weakening the check for a genuinely-ignored
	 * trigger — one with neither an action citation nor a status update has
	 * addressed nothing, and still warns exactly as before.
	 */
	private void checkTriggerFidelity() {
		for (Intention.PendingTrigger trigger : List.copyOf(intentionLedger.pendingTriggers().values())) {
			boolean satisfied = conditionSatisfied(trigger, currentPercepts);
			if (!satisfied)
				continue;

			boolean addressedByAction = trigger.goalId.equals(currentPlanResult.getActInfo().goalId());
			boolean addressedByResolution = resolvedInIntentionChangesThisCycle(trigger.goalId, currentPlanResult.getIntentionChanges());
			boolean addressed = addressedByAction || addressedByResolution;
			if (addressed) {
				intentionLedger.resolveTrigger(trigger.goalId);
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
	
	
	private void checkWellformedness() {
		if (!stageProfile.relationAllowed(currentCoreTuple.r)) {
			throw new IllegalStateException("Relation " + currentCoreTuple.r + " used but its layer isn't active "
					+ "in this StageProfile — a layer leaking in without being declared.");
		}
		Set<String> groundedSource = new LinkedHashSet<>();
		for (Percept p : currentPercepts)
			groundedSource.add(p.toContextLine());
		currentWF = WellFormedness.check(currentCoreTuple, groundedSource, goalLedger);
	}
	
	private void checkCoherence() {
		// C1's keyword-overlap check wants whichever of goal description/plan the model actually
		// supplied — an action's own justification typically cites specific plan detail (an
		// operation, a date) at least as often as the more abstract goal, so both are combined
		// rather than picking one.
		String goalContent = currentCoreTuple.isReactive() ? null : combineGoalDescriptionAndPlan(
				goalLedger.descriptionOf(currentCoreTuple.G), intentionLedger.planOf(currentCoreTuple.G));
		currentCoherence = Coherence.check(currentCoreTuple, previousTuple, goalContent, currentGoalWasAlreadySatisfiedBeforeThisCycle);
		previousTuple = currentCoreTuple;
		tupleTrace.add(currentCoreTuple);		
	}
	

	
	public CycleResult getLastCycleResult() {
		return new CycleResult(nextCycleToRun, currentPercepts, this.getFullStateOfMind(), currentPlanResult, currentActResult,  currentCoreTuple, currentWF, currentCoherence, currentLLMCallResult);
	}

	/**
	 * Renders every currently active intention — not only ones with an
	 * attached trigger — harness-authored context, never compressed.
	 * Deliberately lives here rather than on either ledger: it needs
	 * both a goal's description (GoalLedger) and its intention's plan
	 * and trigger (IntentionLedger) together, and neither ledger has a
	 * reference to the other by design (see IntentionLedger's own class
	 * doc). A goal's description and its intention's plan are each
	 * shown unconditionally when present; a trigger, if one exists, is
	 * shown alongside via its free-text condition and planned action —
	 * the structural matching fields are for the harness's own
	 * mechanical check, not something the agent needs read back to it.
	 */
	private String ongoingIntentionsBlock() {
		StringBuilder sb = new StringBuilder();
		for (Intention i : intentionLedger.activeIntentions()) {
			sb.append("- goal: ").append(i.goalId);
			String description = goalLedger.descriptionOf(i.goalId);
			if (description != null) sb.append("\n - goal_description: ").append(description);
			String parentGoalId = goalLedger.parentOf(i.goalId);
			if (parentGoalId != null) sb.append("\n - in service of: ").append(parentGoalId);
			if (i.plan != null) sb.append("\n - plan: ").append(i.plan);

			if (i.trigger != null) {
				sb.append("\n - condition: ").append(i.trigger.condition)
				  .append("\n - planned_action: ").append(i.trigger.plannedAction);
				if (i.trigger.recurring) {
					sb.append("\n (recurring — stays active after firing)");
				}
			}
			sb.append('\n');
		}
		return sb.length() == 0 ? "(none)" : sb.toString().stripTrailing();
	}

	private String getFullStateOfMind() {
		StringBuffer sb = new StringBuffer("");
		sb.append("[ONGOING INTENTIONS]\n").append(ongoingIntentionsBlock()).append("\n");
		sb.append("[STATE OF MIND]\n").append(stateOfMind.current()).append("\n");
		return sb.toString();
	}

	public long getNextCycleToRun() {
		return nextCycleToRun;
	}

	public List<CoreTuple> trace() {
		return tupleTrace;
	}


	private String assembleContext(List<Percept> percepts) {
		thisCycleResolvedOps.clear();
		StringBuilder sb = new StringBuilder();
		sb.append("[MECHANICAL LOG]\n").append(mechanicalLog.toContextBlock()).append("\n\n");
		sb.append("[WORKSPACE]\n").append(this.getWorkspaceContextBlock()).append("\n");
		sb.append("[ONGOING INTENTIONS]\n").append(ongoingIntentionsBlock()).append("\n\n");
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
		if (currentPercepts.isEmpty()) {
			sb.append("(none)\n");
		} else {
			for (Percept p : currentPercepts) {
				sb.append("- ").append(p.toContextLine()).append('\n');
				if (p.type == Percept.Type.OPERATION_COMPLETED || p.type == Percept.Type.OPERATION_FAILED) {
					mechanicalLog.resolve(p.correlationId);
				}
			}
		}
		return sb.toString();
	}
	
	public String dumpLastCycleIntentionChanges() {
		StringBuilder sb = new StringBuilder();
		if (currentPlanResult.getIntentionChanges().size() > 0) {
			for (var g: currentPlanResult.getIntentionChanges()) {
				sb.append("- goal id: " + g.goalId);
				if (g.status != null) {
					sb.append("\n - status: " + g.status.toJsonValue());
				}
				if (g.goalDescription != null) {
					sb.append("\n - goal_description: " + g.goalDescription);
				}
				if (g.parentGoalId != null) {
					sb.append("\n - parent_goal_id: " + g.parentGoalId);
				}
				if (g.plan != null) {
					sb.append("\n - plan: " + g.plan);
				}
				if (g.trigger != null) {
					sb.append("\n - trigger: condition=" + g.trigger.condition
							+ ", signal=" + g.trigger.signalArtifactId + "/" + g.trigger.signalName
							+ ", signal_name_alternatives=" + g.trigger.signalNameAlternatives
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
	


	private String newCorrelationId() {
		return "op_" + correlationCounter.incrementAndGet();
	}


	/** Joins whichever of goal description/plan is present into one string for C1's keyword-overlap check. */
	private static String combineGoalDescriptionAndPlan(String goalDescription, String plan) {
		if (goalDescription == null && plan == null) return null;
		if (goalDescription == null) return plan;
		if (plan == null) return goalDescription;
		return goalDescription + " " + plan;
	}

	/** True if this cycle's <intention_changes> array includes a status change (achieved/dropped) for this goal id. */
	private static boolean resolvedInIntentionChangesThisCycle(String goalId, List<PlanResult.IntentionEntry> intentionChanges) {
		for (PlanResult.IntentionEntry g : intentionChanges) {
			if (goalId.equals(g.goalId) && g.status != null) return true;
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
	 * Resolving a percept's effective artifact id, signal name, and
	 * operation name (below) still lives here, since that resolution
	 * needs MechanicalLog — something a trigger has no business
	 * depending on. Once those facts are resolved, the actual
	 * structural comparison against the trigger's own fields is the
	 * trigger's own responsibility (Intention.PendingTrigger.matches),
	 * not duplicated here.
	 */
	private boolean conditionSatisfied(Intention.PendingTrigger trigger, List<Percept> percepts) {
		if (trigger.signalArtifactId == null || trigger.signalName == null) {
			return false; // no structural spec given — cannot be mechanically checked; never silently guess
		}
		for (Percept p : percepts) {
			if (trigger.matches(effectiveArtifactId(p), effectiveSignalName(p), effectiveOperationName(p), p.toContextLine())) {
				return true;
			}
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
