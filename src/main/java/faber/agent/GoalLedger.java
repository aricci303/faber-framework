package faber.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of active goals (Goals), keyed by id, plus pending triggers:
 * a structured, harness-guaranteed representation of "when condition X
 * occurs, do Y", authored once by the model in its own words but
 * echoed back in full every cycle by the harness itself, exempt from
 * the delta-only compression applied to STATE OF MIND.
 *
 * Extended twice since the version that first fixed the flat-goal
 * visibility gap:
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
 */
public final class GoalLedger {

    public static final class PendingTrigger {
        public final String goalId;
        public final String condition;
        public final String plannedAction;
        public final String signalArtifactId;
        public final String signalName;
        public final String signalValueContains;
        public final boolean recurring;

        PendingTrigger(String goalId, String condition, String plannedAction,
                       String signalArtifactId, String signalName, String signalValueContains,
                       boolean recurring) {
            this.goalId = goalId;
            this.condition = condition;
            this.plannedAction = plannedAction;
            this.signalArtifactId = signalArtifactId;
            this.signalName = signalName;
            this.signalValueContains = signalValueContains;
            this.recurring = recurring;
        }
    }

    private final Map<String, String> goals = new LinkedHashMap<>();
    private final Map<String, PendingTrigger> triggers = new LinkedHashMap<>();
    private final Map<String, String> resolutions = new LinkedHashMap<>(); // goalId -> "achieved" | "dropped"

    public String registerOrGet(String id, String contentIfNew) {
        return goals.computeIfAbsent(id, k -> contentIfNew);
    }

    public boolean isRegistered(String id) { return goals.containsKey(id); }
    public String contentOf(String id) { return goals.get(id); }

    /** Registers a pending trigger for a goal, if one doesn't already exist for it. */
    public void registerTrigger(String goalId, PlanResult.TriggerSpec spec) {
        if (goalId == null || spec == null || spec.condition == null || spec.plannedAction == null) return;
        triggers.putIfAbsent(goalId, new PendingTrigger(goalId, spec.condition, spec.plannedAction,
                spec.signalArtifactId, spec.signalName, spec.signalValueContains, spec.recurring));
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
     * Explicit, agent-initiated closure of a goal — "achieved" or
     * "dropped", per the action's own declared status. Never inferred:
     * a goal may take several actions and cycles to complete, so no
     * automatic rule (e.g. "a reply happened") can safely stand in for
     * the agent's own judgment that it's actually done. Also removes
     * any trigger still registered for this goal, regardless of its
     * recurring flag — once the goal itself is closed out, nothing
     * should keep watching on its behalf.
     */
    public void resolveGoal(String goalId, String status) {
        if (goalId != null && status != null) {
            resolutions.put(goalId, status);
            triggers.remove(goalId);
        }
    }

    public boolean isActive(String goalId) {
        return goals.containsKey(goalId) && !resolutions.containsKey(goalId);
    }

    public Map<String, PendingTrigger> pendingTriggers() {
        return triggers;
    }

    /**
     * Renders every currently active goal — not only ones with an
     * attached trigger — harness-authored context, never compressed.
     * A goal's own content is shown unconditionally; its trigger, if
     * one exists, is shown alongside it via its free-text condition
     * and planned action — the structural matching fields are for the
     * harness's own mechanical check, not something the agent needs
     * read back to it.
     */
    public String toContextBlock() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> g : goals.entrySet()) {
            String goalId = g.getKey();
            if (resolutions.containsKey(goalId)) continue; // achieved or dropped — no longer ongoing

            sb.append("  - goal: ").append(goalId)
              .append(", content: ").append(g.getValue());

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
