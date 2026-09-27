package faber.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of intentions, keyed by the goal id each one is for — the
 * HOW half of the goal/intention split (GoalLedger holds the WHAT).
 * Deliberately independent of GoalLedger: this class has no reference
 * to it and no way to check whether a goal id it's given actually
 * corresponds to a registered goal. Keeping the two goals and
 * intentions in step (an intention should only ever exist for a real
 * goal) is the caller's responsibility — TupleExtractor updates both
 * ledgers together from the same <intention_changes> entry, and
 * AgentArchitecture combines both when rendering ONGOING INTENTIONS —
 * rather than either ledger depending on the other to enforce it
 * internally. This mirrors how real BDI systems typically keep belief,
 * goal, and intention stores as genuinely separate structures updated
 * in step by the same interpreter cycle, not a single fused structure.
 *
 * Every operation here that revises an existing intention (plan,
 * trigger, status) follows the same discipline as GoalLedger's own
 * description field: supply to introduce or revise, omit to leave
 * untouched. See Intention's own class doc for why the rules governing
 * an intention's execution (trigger matching, what addressing a
 * trigger does to it, what concluding an intention means) live on the
 * Intention object itself rather than here — this class is deliberately
 * just a keyed store over that richer object, not where its behavior lives.
 */
public final class IntentionLedger {

    private final Map<String, Intention> intentions = new LinkedHashMap<>();

    // Thread-safe mirror of "this goal's intention is currently active" — intentions itself is a
    // plain LinkedHashMap, mutated only by the agent's own thread, unsafe to poll from a separate
    // scenario-driver thread. Updated at exactly the two points that change active status: adoption
    // (registerOrUpdate, for a genuinely new intention) and explicit resolution (resolveGoal, to
    // ACHIEVED or DROPPED) — registerTrigger, resolveTrigger, and a plan revision on an existing
    // intention never change active status, so they don't touch this. Mirrors the same
    // AtomicBoolean discipline as Artifact.isCurrentlyObserved(), for the same reason: a scenario
    // driver needing to know "has this goal genuinely left ONGOING INTENTIONS yet" is a real
    // readiness condition, immune to how many cycles the agent takes to reach it — unlike a fixed
    // sleep or message count, which only ever infer that indirectly.
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> activeMirror =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Safe to poll from any thread — see the field's own doc comment above. False for a goal id
     *  that was never registered, or has since been resolved, same as isActive() itself. */
    public boolean isActiveSafe(String goalId) { return activeMirror.getOrDefault(goalId, false); }

    /** Thread-safe count of goals resolved so far (ACHIEVED or DROPPED), derived from the same
     *  mirror. Exists for a scenario driver that can't know a specific goal id in advance (ids are
     *  always the model's own choice) but can chain a sequence of waits, each for exactly one more
     *  resolution than the last, when the narrative guarantees only one thing could plausibly
     *  resolve next — see ManualRetentionMain for a concrete case of this. */
    public int resolvedGoalsCount() {
        int count = 0;
        for (Boolean active : activeMirror.values()) if (!active) count++;
        return count;
    }

    /**
     * Adopts a new intention for this goal id if none exists yet, or revises the plan of an existing
     * one if plan is non-null. A freshly-adopted intention starts ONGOING with no trigger.
     */
    public void registerOrUpdate(String goalId, String plan) {
        Intention current = intentions.get(goalId);
        if (current == null) {
            intentions.put(goalId, Intention.adopt(goalId, plan));
            activeMirror.put(goalId, true);
        } else if (plan != null) {
            intentions.put(goalId, current.withPlan(plan));
        }
    }

    public boolean isRegistered(String goalId) { return intentions.containsKey(goalId); }

    public Intention intentionOf(String goalId) { return intentions.get(goalId); }

    public String planOf(String goalId) {
        Intention i = intentions.get(goalId);
        return i != null ? i.plan : null;
    }

    public boolean isActive(String goalId) {
        Intention i = intentions.get(goalId);
        return i != null && i.isActive();
    }

    /** The intention's current, always-defined status — ONGOING, ACHIEVED, or DROPPED — or null if no such intention exists. */
    public GoalStatus statusOf(String goalId) {
        Intention i = intentions.get(goalId);
        return i != null ? i.status : null;
    }

    /**
     * Registers this goal's pending trigger if new, or genuinely revises it if a spec is resupplied for
     * a goal whose intention already has one. A no-op if no intention exists yet for this goal id —
     * registerOrUpdate is always called first in the same cycle for any newly-adopted intention.
     */
    public void registerTrigger(String goalId, PlanResult.TriggerSpec spec) {
        if (goalId == null || spec == null || spec.condition == null || spec.plannedAction == null) return;
        Intention current = intentions.get(goalId);
        if (current == null) return;
        Intention.PendingTrigger newTrigger = new Intention.PendingTrigger(goalId, spec.condition, spec.plannedAction,
                spec.signalArtifactId, spec.signalName, spec.signalNameAlternatives,
                spec.signalValueContains, spec.operationName, spec.recurring);
        intentions.put(goalId, current.withTrigger(newTrigger));
    }

    /** Called once an action addresses a fired trigger; delegates the recurring-vs-clear rule to the intention itself. */
    public void resolveTrigger(String goalId) {
        Intention current = intentions.get(goalId);
        if (current != null) intentions.put(goalId, current.withTriggerAddressed());
    }

    /**
     * Explicit, agent-initiated closure of a goal's current intention — ACHIEVED or DROPPED, per the
     * action's own declared status. A no-op if no intention exists for this goal id.
     */
    public void resolveGoal(String goalId, GoalStatus status) {
        if (goalId == null || status == null) return;
        Intention current = intentions.get(goalId);
        if (current == null) return;
        intentions.put(goalId, current.resolved(status));
        activeMirror.put(goalId, false);
    }

    /** Every currently-registered pending trigger, keyed by the goal id its intention is for. */
    public Map<String, Intention.PendingTrigger> pendingTriggers() {
        Map<String, Intention.PendingTrigger> result = new LinkedHashMap<>();
        for (Intention i : intentions.values()) {
            if (i.trigger != null) result.put(i.goalId, i.trigger);
        }
        return result;
    }

    /** Every currently active intention, in registration order — for a caller (AgentArchitecture) to combine with goal descriptions when rendering. */
    public Iterable<Intention> activeIntentions() {
        java.util.List<Intention> active = new java.util.ArrayList<>();
        for (Intention i : intentions.values()) {
            if (i.isActive()) active.add(i);
        }
        return active;
    }

    /**
     * Every intention that has been explicitly closed out (ACHIEVED or DROPPED), in registration
     * order — exists for the same reason MECHANICAL LOG exists for pending operations: without a
     * harness-guaranteed, persistent record of a resolution, the only place that fact ever existed
     * was the cycle's own STATE OF MIND narrative, which is explicitly delta-only and not guaranteed
     * to survive even one further cycle. Confirmed as a real, recurring failure this caused: a goal
     * correctly resolved, then re-registered one or two cycles later as though it had never existed,
     * because its own absence from activeIntentions() was the only signal available, and absence is
     * structurally identical whether a goal was never registered or was resolved moments ago.
     */
    public Iterable<Intention> resolvedIntentions() {
        java.util.List<Intention> resolved = new java.util.ArrayList<>();
        for (Intention i : intentions.values()) {
            if (!i.isActive()) resolved.add(i);
        }
        return resolved;
    }
}
