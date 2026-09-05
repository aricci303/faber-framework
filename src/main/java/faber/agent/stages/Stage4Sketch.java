package faber.agent.stages;

/**
 * SKETCH ONLY — not implemented.
 *
 * Stage 4 adds the Quantitative layer: Preference (an ordering or weight
 * among competing Goals) and Tradeoff (an explicit comparison between
 * candidate Hows), using agent.formal.Relation.PREFERENCE_DRIVEN. This
 * is the layer answering Russell's utility-based critique directly: it
 * lets J host a genuinely numeric justification without needing the
 * qualitative vocabulary itself to perform the tradeoff.
 *
 * Natural test environment: not Gaia2/ARE, which has no explicit utility
 * axis — a different benchmark family, closer to multi-objective
 * decision-making or negotiation-style tasks, not yet identified
 * concretely.
 *
 * TODO when this stage is built for real:
 *   - A Preference type: a partial order or numeric weight over wish ids.
 *   - Extend J so a PREFERENCE_DRIVEN tuple can name at least one
 *     rejected alternative action alongside the chosen one.
 *   - Decide whether preference elicitation is harness-provided (a
 *     stated utility function per scenario) or agent-inferred — a real,
 *     open design question, not yet settled even in sketch form.
 */
public final class Stage4Sketch {
    private Stage4Sketch() {}
}
