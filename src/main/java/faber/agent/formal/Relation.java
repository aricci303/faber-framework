package faber.agent.formal;

/**
 * The closed enumeration for J's relation type r, from the Core
 * semantics note (Section 2). Extension layers attach primarily by
 * widening this enumeration, not by adding unconstrained new slots —
 * see Section 6 of that note. ACHIEVEMENT, MAINTENANCE, and REACTIVE
 * are active at Stage 0 (Core); the others exist here so StageProfile
 * has something concrete to gate, and so a tuple citing one of them
 * without the corresponding stage active is a detectable
 * well-formedness violation rather than a silently-accepted value.
 *
 * ACHIEVEMENT and MAINTENANCE replace what was originally a single
 * MEANS_END value — a refinement of an existing Stage 0 value rather
 * than the addition of a new stage, since the underlying claim ("no
 * intention, no action") is unchanged; only the resolution of what
 * kind of intention is new. Motivated directly by the observation
 * that, once every action reliably cites some goal (the deepest-node
 * citation discipline, GoalLedger's own doc), a flat MEANS_END stopped
 * discriminating between cases: it applied to essentially every
 * action, correctly, and had nothing left to say. Splitting it lets J
 * answer a question the tuple previously could not: is this action
 * advancing a goal toward a terminal state, or sustaining a standing
 * condition with no terminal state at all — the same achievement/
 * maintenance distinction BDI and agent-oriented programming already
 * use for goal kinds generally (see GoalKind), now read through to J.
 */
public enum Relation {
    /** Core. A advances G's own plan toward G's terminal, satisfiable state. */
    ACHIEVEMENT,
    /** Core. A sustains G, a standing condition with no terminal state — G's own kind is MAINTENANCE. */
    MAINTENANCE,
    /** Core, but no longer a tolerated possibility (see WellFormedness's WF8): A cites no goal at
     *  all (G = BOTTOM). Kept as a real enum value because the type system still needs something
     *  to represent a goal_id the model actually omitted — TupleExtractor still has to compute a
     *  concrete Relation every cycle — but its occurrence is now always a WF8 violation, given
     *  every agent is seeded with a goal from before its first cycle even runs and that goal is
     *  always available to cite. Not a real category of action any longer, just a missed citation
     *  the type system still has to be able to represent. */
    REACTIVE,
    /** Normative (Stage 1) — not active at Stage 0. A is driven by a bound Constraint. */
    CONSTRAINT_DRIVEN,
    /** Social (Stage 3) — not active at Stage 0. A is driven by trust in another agent. */
    TRUST_BASED,
    /** Social (Stage 3) — not active at Stage 0. A fulfills a delegation from another agent. */
    DELEGATED,
    /** Quantitative (Stage 4) — not active at Stage 0. A is driven by a preference/tradeoff comparison. */
    PREFERENCE_DRIVEN;

    public boolean isCore() { return this == ACHIEVEMENT || this == MAINTENANCE || this == REACTIVE; }
}
