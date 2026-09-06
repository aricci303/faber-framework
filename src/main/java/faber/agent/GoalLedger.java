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
 * Extended to track resolution explicitly. The gap this closes: a goal
 * registered without an attached trigger — the ordinary case for a
 * straightforward sequential plan, not one waiting on a future event —
 * previously had no persistent home anywhere. toContextBlock() only
 * ever rendered the triggers map, never the goals themselves, so a
 * committed goal with no trigger was invisible from the cycle after it
 * was introduced onward — visible only if the model's own narrative
 * happened to still be carrying it, the exact erosion risk this class
 * already exists to prevent for triggered goals. In BDI terms: an
 * intention is meant to remain a standing, aware commitment from the
 * moment it's adopted until it is achieved or dropped — not only while
 * it happens to also carry a future-conditional trigger. resolveGoal
 * gives the agent an explicit, agent-initiated way to close a goal out,
 * the same discipline as NotebookArtifact.retract_note — nothing here
 * infers resolution automatically, since that would be guessing at
 * something only the agent can actually know.
 */
public final class GoalLedger {

    public static final class PendingTrigger {
        public final String goalId;
        public final String condition;
        public final String plannedAction;

        PendingTrigger(String goalId, String condition, String plannedAction) {
            this.goalId = goalId;
            this.condition = condition;
            this.plannedAction = plannedAction;
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
    public void registerTrigger(String goalId, String condition, String plannedAction) {
        if (goalId != null && condition != null && plannedAction != null) {
            triggers.putIfAbsent(goalId, new PendingTrigger(goalId, condition, plannedAction));
        }
    }

    /** Called once an action addresses a fired trigger — it graduates to an ordinary in-flight operation. */
    public void resolveTrigger(String goalId) {
        triggers.remove(goalId);
    }

    /**
     * Explicit, agent-initiated closure of a goal — "achieved" or
     * "dropped", per the action's own declared status. Never inferred:
     * a goal may take several actions and cycles to complete, so no
     * automatic rule (e.g. "a reply happened") can safely stand in for
     * the agent's own judgment that it's actually done.
     */
    public void resolveGoal(String goalId, String status) {
        if (goalId != null && status != null) {
            resolutions.put(goalId, status);
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
     * one exists, is shown alongside it rather than being the
     * criterion for whether the goal appears at all.
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
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "(none)" : sb.toString().stripTrailing();
    }
}
