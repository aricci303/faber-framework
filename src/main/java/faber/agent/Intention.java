package faber.agent;

import java.util.List;

/**
 * The agent's current commitment toward one goal — the HOW: a plan,
 * a status, and whatever is currently being watched for on its
 * behalf, if anything. Bratman-aligned: a goal (GoalLedger) is a
 * state of affairs, owned by whoever assigned it; an intention pairs
 * that goal with the agent's own plan for pursuing it, and is
 * entirely the agent's own — including any interpretive assumptions
 * it had to make about an underspecified goal. Immutable: a revision
 * to any field produces a new Intention via the with* methods below,
 * the same "supply to revise" discipline every revisable field in
 * this architecture already follows.
 *
 * Deliberately holds real behavior, not just data — the rules for how
 * an intention's own execution proceeds (does this percept satisfy my
 * pending trigger; does addressing a fired trigger clear it or leave
 * it armed; what does it mean for this intention to conclude) belong
 * to the intention itself, not to whichever ledger or harness method
 * happens to be operating on it at the time.
 */
public final class Intention {

    /**
     * A trigger's structural matching fields, checked directly against
     * a percept's own resolved facts — never a heuristic guess at the
     * free-text condition's wording. See AgentArchitecture's percept
     * resolution (effectiveArtifactId/effectiveSignalName/
     * effectiveOperationName) for why operation-lifecycle percepts in
     * particular need resolving before they can be checked here at
     * all — they carry none of these fields directly.
     */
    public static final class PendingTrigger {
        public final String goalId;
        public final String condition;
        public final String plannedAction;
        public final String signalArtifactId;
        public final String signalName;
        public final List<String> signalNameAlternatives;
        public final String signalValueContains;
        public final String operationName;
        public final boolean recurring;

        PendingTrigger(String goalId, String condition, String plannedAction,
                       String signalArtifactId, String signalName, List<String> signalNameAlternatives,
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

        /**
         * The full structural match, given a percept's already-resolved facts: artifact id, effective
         * signal name (or one of its alternatives), operation name if this trigger cares which one, and
         * the percept's own context line if this trigger filters on a specific value within it. Resolving
         * those facts from a raw Percept is the caller's job (it needs MechanicalLog, which a trigger has
         * no business depending on); matching them, once resolved, is this trigger's own.
         */
        public boolean matches(String effectiveArtifactId, String effectiveSignalName,
                                String effectiveOperationName, String contextLine) {
            if (signalArtifactId == null || !signalArtifactId.equals(effectiveArtifactId)) return false;
            if (!matchesSignalName(effectiveSignalName)) return false;
            if (operationName != null && !operationName.equals(effectiveOperationName)) return false;
            if (signalValueContains != null) {
                if (contextLine == null) return false;
                if (!contextLine.toLowerCase().contains(signalValueContains.toLowerCase())) return false;
            }
            return true;
        }
    }

    public final String goalId;
    public final String plan;
    public final GoalStatus status;
    public final PendingTrigger trigger;

    Intention(String goalId, String plan, GoalStatus status, PendingTrigger trigger) {
        this.goalId = goalId;
        this.plan = plan;
        this.status = status;
        this.trigger = trigger;
    }

    /** A freshly-adopted intention: ONGOING, no trigger yet. */
    static Intention adopt(String goalId, String plan) {
        return new Intention(goalId, plan, GoalStatus.ONGOING, null);
    }

    Intention withPlan(String newPlan) {
        return new Intention(goalId, newPlan, status, trigger);
    }

    Intention withTrigger(PendingTrigger newTrigger) {
        return new Intention(goalId, plan, status, newTrigger);
    }

    public boolean isActive() {
        return status == GoalStatus.ONGOING;
    }

    /** True if this intention has a pending trigger and the given resolved percept facts satisfy it. */
    public boolean triggerSatisfiedBy(String effectiveArtifactId, String effectiveSignalName,
                                       String effectiveOperationName, String contextLine) {
        return trigger != null && trigger.matches(effectiveArtifactId, effectiveSignalName, effectiveOperationName, contextLine);
    }

    /**
     * This intention once its currently-fired trigger has been addressed: cleared if the trigger was
     * one-shot, left exactly as is (still armed) if it was recurring. The recurring-vs-clear rule lives
     * here, on the intention itself, rather than in whichever ledger method happens to call this.
     */
    Intention withTriggerAddressed() {
        if (trigger == null || trigger.recurring) return this;
        return withTrigger(null);
    }

    /**
     * This intention once its goal has been explicitly closed out: status set to the given value, and
     * any trigger cleared regardless of recurring — once the goal itself is done, nothing should keep
     * watching on its behalf, recurring or not.
     */
    Intention resolved(GoalStatus newStatus) {
        return new Intention(goalId, plan, newStatus, null);
    }
}
