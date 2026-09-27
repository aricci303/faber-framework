package faber.agent;

/**
 * Whether a goal has a terminal, satisfiable state (ACHIEVEMENT) or is
 * a standing condition sustained indefinitely with no terminal state
 * at all (MAINTENANCE) — the same distinction BDI and agent-oriented
 * programming use for goal kinds generally, now a first-class
 * GoalLedger property rather than left implicit in a trigger's own
 * recurring flag.
 *
 * Set once, the cycle a goal is first registered, and never revised
 * afterward — like parentGoalId, and for the same reason: this is a
 * classification of what kind of thing is being pursued, not a claim
 * that can legitimately change without the goal itself having become a
 * different goal. An achievement goal does not turn into a maintenance
 * goal because circumstances changed; if that distinction ever seems
 * to be shifting, it is a new goal, not a reclassification of the old
 * one.
 *
 * Read through directly to J (see Relation): an action whose cited
 * goal is ACHIEVEMENT-kind is itself Relation.ACHIEVEMENT; MAINTENANCE
 * the same way. A goal with no declared kind is TO_BE_DECIDED (see
 * GoalLedger.kindOf) — never silently ACHIEVEMENT. That distinction
 * matters concretely: an earlier version of this default was
 * ACHIEVEMENT, on the reasoning that it was the safer wrong guess (an
 * actually-standing goal misclassified that way can just never reach
 * ACHIEVED, a stricter outcome, not a silently-accepted wrong one).
 * The problem that reasoning missed is representational, not
 * consequential: ACHIEVEMENT-by-default is indistinguishable, to
 * anyone reading ONGOING INTENTIONS including the model itself, from
 * ACHIEVEMENT genuinely decided — "nobody has classified this yet" and
 * "someone deliberately classified this as achievement-kind" rendered
 * identically. TO_BE_DECIDED is the same fix plan's own null already
 * gets: an honestly-absent classification instead of a plausible-
 * looking stand-in for one. Relation still needs a concrete value the
 * cycle a TO_BE_DECIDED goal is actually cited — see TupleExtractor's
 * own doc on what it falls back to and WellFormedness's WF7 on how
 * that fallback is caught and surfaced rather than left silent.
 */
public enum GoalKind {
    ACHIEVEMENT, MAINTENANCE, TO_BE_DECIDED;

    /** Parses the model's own JSON value ("achievement" / "maintenance"). Returns null for a null
     *  input, preserving "not supplied this turn" as distinct from any real, declared kind.
     *  TO_BE_DECIDED is never a value the model itself sends — it is GoalLedger's own internal
     *  default for a goal nobody has classified, not a classification the model can choose. */
    public static GoalKind fromJsonValue(String raw) {
        if (raw == null) return null;
        switch (raw) {
            case "achievement": return ACHIEVEMENT;
            case "maintenance": return MAINTENANCE;
            default: throw new IllegalArgumentException("Unknown goal kind in model output: " + raw);
        }
    }

    public String toJsonValue() {
        return name().toLowerCase();
    }
}
