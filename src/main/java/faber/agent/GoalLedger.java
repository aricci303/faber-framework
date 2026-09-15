package faber.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of goals, keyed by id, plus the intentions adopted for
 * them: a structured, harness-guaranteed representation of both what
 * is being pursued and how, echoed back in full every cycle by the
 * harness itself, exempt from the delta-only compression applied to
 * STATE OF MIND.
 *
 * Aligned with Bratman's practical-reasoning vocabulary, and BDI more
 * generally. A goal is the state of affairs being pursued — the WHAT
 * — owned by whoever assigned it (a user, another agent, or the agent
 * itself decomposing a complex goal into subgoals); goalDescription is
 * only ever revised when the assigner has genuinely changed what
 * they're asking for. An intention pairs a goal with the plan devised
 * to achieve it — the HOW — entirely the agent's own, including any
 * interpretive assumptions it had to make about an underspecified
 * goal. Motivated directly by real logs: across every Scenario05 run,
 * the goal itself never actually changed — book a flight and hotel,
 * report the itinerary — while the plan changed on nearly every cycle:
 * which date, what operation is pending, what to ask the user next. A
 * single undifferentiated field gave the model no way to signal which
 * kind of change it was making, and in practice always ended up
 * carrying plan detail from the very first cycle it was introduced,
 * not just after later revisions. Both goalDescription and plan follow
 * the identical revision discipline: supply to introduce or revise,
 * omit to leave untouched.
 *
 * Extended several times since the version that first fixed the
 * flat-goal visibility gap:
 *
 *   - PendingTrigger now carries structural matching fields
 *     (signalArtifactId, signalName, an optional signalValueContains,
 *     and a recurring flag) alongside the free-text condition/planned
 *     action — see AgentArchitecture.conditionSatisfied for why free-
 *     text heuristic matching alone proved unreliable twice over in
 *     real runs.
 *
 *   - resolveGoal now also resolves any trigger registered for that
 *     goal id. Without this, a goal marked achieved could still leave
 *     its trigger lingering in the triggers map — invisible in
 *     PENDING INTENTIONS (which is gated on the goal being active) but
 *     still live in checkTriggerFidelity's own iteration, which walks
 *     the triggers map directly, producing confusing warnings for a
 *     commitment the agent had already, correctly, closed out.
 *
 *   - Neither goalDescription, plan, nor a pending trigger is write-
 *     once — each genuinely revises the stored value when resupplied,
 *     rather than the earlier write-once behavior silently discarding
 *     later updates. A goal's persisted state is presented as fixed,
 *     authoritative ground truth, which is exactly right when nothing
 *     has changed, but wrong once the situation has genuinely moved
 *     on: a real run rebooked a flight after a booking failure,
 *     correctly adjusted a dependent hotel booking to match — then, a
 *     cycle later, reverted that correct adjustment because the
 *     goal's own stored description still described the original,
 *     now-stale plan, and there was no way to bring it up to date.
 *
 *   - A goal's status is now a real, always-present value (GoalStatus,
 *     defaulting to ONGOING the instant a goal is registered) rather
 *     than an implicit fact inferred from a goal id's absence from a
 *     separate resolutions map. The wire protocol is unchanged — the
 *     model still only ever writes "achieved" or "dropped" when
 *     closing a goal out, and still omits status entirely on an
 *     ordinary turn; there is no well-formed "still ongoing" for it to
 *     say each cycle, and requiring one would trade real, recurring
 *     output cost for information PENDING INTENTIONS already reliably
 *     carries. This only changes how that fact is represented
 *     internally: a real, defined value at all times, not something
 *     only ever visible by checking where a key is missing.
 *
 *   - Terminology aligned throughout with the goal/intention split:
 *     what was "objective" is now goalDescription, matching the wire
 *     format's own "goal_description" field.
 */
public final class GoalLedger {

    public static final class PendingTrigger {
        public final String goalId;
        public final String condition;
        public final String plannedAction;
        public final String signalArtifactId;
        public final String signalName;
        public final java.util.List<String> signalNameAlternatives;
        public final String signalValueContains;
        public final String operationName;
        public final boolean recurring;

        PendingTrigger(String goalId, String condition, String plannedAction,
                       String signalArtifactId, String signalName, java.util.List<String> signalNameAlternatives,
                       String signalValueContains, String operationName, boolean recurring) {
            this.goalId = goalId;
            this.condition = condition;
            this.plannedAction = plannedAction;
            this.signalArtifactId = signalArtifactId;
            this.signalName = signalName;
            this.signalNameAlternatives = signalNameAlternatives;
            this.signalValueContains = signalValueContains;
            this.operationName = operationName;
            this.recurring = recurring;
        }

        /** True if the given name matches this trigger's primary signal_name or any of its alternatives. */
        public boolean matchesSignalName(String name) {
            if (name == null) return false;
            if (name.equals(signalName)) return true;
            return signalNameAlternatives != null && signalNameAlternatives.contains(name);
        }
    }

    private final Map<String, String> goalDescriptions = new LinkedHashMap<>();
    private final Map<String, String> plans = new LinkedHashMap<>();
    private final Map<String, PendingTrigger> triggers = new LinkedHashMap<>();
    private final Map<String, GoalStatus> statuses = new LinkedHashMap<>();

    /**
     * Registers a goal if new, or revises whichever of goalDescription/
     * plan is supplied for a goal that already exists. Either may be
     * omitted (null) independently of the other: revising just the
     * plan while the goal description stays untouched is the common
     * case (a flight rescheduled mid-episode, the goal — book a flight
     * and hotel, report the itinerary — never changing), and the
     * reverse (the assigner changing their mind about what they
     * actually want) is rarer but equally well-formed. statuses is
     * always populated the instant a goal id is first seen here,
     * regardless of whether goalDescription or plan happened to be
     * supplied that same call — statuses.containsKey is the canonical
     * "does this goal id exist at all" check, not either content map.
     */
    public void registerOrUpdate(String id, String goalDescription, String plan) {
        if (goalDescription != null) goalDescriptions.put(id, goalDescription);
        if (plan != null) plans.put(id, plan);
        statuses.putIfAbsent(id, GoalStatus.ONGOING);
    }

    public boolean isRegistered(String id) { return statuses.containsKey(id); }
    public String goalDescriptionOf(String id) { return goalDescriptions.get(id); }
    public String planOf(String id) { return plans.get(id); }

    /**
     * Registers a goal's pending trigger if new, or genuinely revises
     * it if a spec is resupplied for a goal that already has one — the
     * same discipline goalDescription/plan have. A trigger's
     * condition/planned_action are only ever presented to the agent as
     * its own past commitment, exactly like goalDescription/plan —
     * leaving them write-once would have reproduced the identical
     * failure mode: a flight rescheduled mid-episode with the
     * trigger's own planned_action still describing the original date.
     * A trigger is treated as one atomic unit here — revising it means
     * resupplying the whole spec, not patching individual fields.
     */
    public void registerTrigger(String goalId, PlanResult.TriggerSpec spec) {
        if (goalId == null || spec == null || spec.condition == null || spec.plannedAction == null) return;
        triggers.put(goalId, new PendingTrigger(goalId, spec.condition, spec.plannedAction,
                spec.signalArtifactId, spec.signalName, spec.signalNameAlternatives,
                spec.signalValueContains, spec.operationName, spec.recurring));
    }

    /**
     * Called once an action addresses a fired trigger. A recurring
     * trigger stays registered — it graduates back to standing watch,
     * ready to fire again on its next real occurrence, rather than
     * being removed after the first. A one-shot trigger (the default)
     * is removed, the same as before.
     */
    public void resolveTrigger(String goalId) {
        PendingTrigger t = triggers.get(goalId);
        if (t != null && !t.recurring) {
            triggers.remove(goalId);
        }
    }

    /**
     * Explicit, agent-initiated closure of a goal — ACHIEVED or
     * DROPPED, per the action's own declared status. Never inferred:
     * a goal may take several actions and cycles to complete, so no
     * automatic rule (e.g. "a reply happened") can safely stand in for
     * the agent's own judgment that it's actually done. Also removes
     * any trigger still registered for this goal, regardless of its
     * recurring flag — once the goal itself is closed out, nothing
     * should keep watching on its behalf.
     */
    public void resolveGoal(String goalId, GoalStatus status) {
        if (goalId != null && status != null) {
            statuses.put(goalId, status);
            triggers.remove(goalId);
        }
    }

    public boolean isActive(String goalId) {
        return statuses.containsKey(goalId) && statuses.get(goalId) == GoalStatus.ONGOING;
    }

    /** The goal's current, always-defined status — ONGOING, ACHIEVED, or DROPPED, never null for a registered goal. */
    public GoalStatus statusOf(String goalId) {
        return statuses.get(goalId);
    }

    public Map<String, PendingTrigger> pendingTriggers() {
        return triggers;
    }

    /**
     * Renders every currently active goal — not only ones with an
     * attached trigger — harness-authored context, never compressed.
     * A goal's description and plan are each shown unconditionally
     * when present; its trigger, if one exists, is shown alongside via
     * its free-text condition and planned action — the structural
     * matching fields are for the harness's own mechanical check, not
     * something the agent needs read back to it.
     */
    public String toContextBlock() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, GoalStatus> e : statuses.entrySet()) {
            String goalId = e.getKey();
            if (e.getValue() != GoalStatus.ONGOING) continue; // achieved or dropped — no longer ongoing

            sb.append("  - goal: ").append(goalId);
            String goalDescription = goalDescriptions.get(goalId);
            String plan = plans.get(goalId);
            if (goalDescription != null) sb.append(", goal_description: ").append(goalDescription);
            if (plan != null) sb.append(", plan: ").append(plan);

            PendingTrigger t = triggers.get(goalId);
            if (t != null) {
                sb.append(", condition: ").append(t.condition)
                  .append(", planned_action: ").append(t.plannedAction);
                if (t.recurring) {
                    sb.append(" (recurring — stays active after firing)");
                }
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "(none)" : sb.toString().stripTrailing();
    }
}
