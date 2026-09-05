package faber.agent.formal;

/**
 * The closed enumeration for J's relation type r, from the Core
 * semantics note (Section 2). Extension layers attach primarily by
 * widening this enumeration, not by adding unconstrained new slots —
 * see Section 6 of that note. Only MEANS_END and REACTIVE are active at
 * Stage 0 (Core); the others exist here so StageProfile has something
 * concrete to gate, and so a tuple citing one of them without the
 * corresponding stage active is a detectable well-formedness violation
 * rather than a silently-accepted value.
 */
public enum Relation {
    /** Core. A is proposed as a way of achieving G, given the cited beliefs. */
    MEANS_END,
    /** Core. A is a direct response to cited beliefs with no active goal (G = BOTTOM). */
    REACTIVE,
    /** Normative (Stage 1) — not active at Stage 0. A is driven by a bound Constraint. */
    CONSTRAINT_DRIVEN,
    /** Social (Stage 3) — not active at Stage 0. A is driven by trust in another agent. */
    TRUST_BASED,
    /** Social (Stage 3) — not active at Stage 0. A fulfills a delegation from another agent. */
    DELEGATED,
    /** Quantitative (Stage 4) — not active at Stage 0. A is driven by a preference/tradeoff comparison. */
    PREFERENCE_DRIVEN;

    public boolean isCore() { return this == MEANS_END || this == REACTIVE; }
}
