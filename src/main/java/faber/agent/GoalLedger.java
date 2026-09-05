package faber.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of active goals (Goals), keyed by id, plus — new — pending
 * triggers: a structured, harness-guaranteed representation of "when
 * condition X occurs, do Y", authored once by the model in its own
 * words (condition/plannedAction are free text, exactly like goal
 * content) but echoed back in full every cycle by the harness itself,
 * exempt from the delta-only compression applied to STATE OF MIND.
 *
 * This exists because a real run against a live model showed standing
 * conditional commitments eroding across idle cycles: the free
 * narrative correctly treated "nothing changed" as license to stop
 * repeating the plan, and by the time the trigger fired, the plan was
 * gone. The fix is not to instruct the model to try harder to remember
 * — it is to move the one property this content actually needs
 * (guaranteed survival) to the channel already responsible for that
 * job, the same way WORKSPACE and MECHANICAL LOG already are.
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

    public Map<String, PendingTrigger> pendingTriggers() {
        return triggers;
    }

    /** Renders every unresolved trigger in full — harness-authored context, never compressed. */
    public String toContextBlock() {
        if (triggers.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (PendingTrigger t : triggers.values()) {
            sb.append("  - goal: ").append(t.goalId)
              .append(", condition: ").append(t.condition)
              .append(", planned_action: ").append(t.plannedAction).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
