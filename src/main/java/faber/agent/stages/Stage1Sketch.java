package faber.agent.stages;

/**
 * SKETCH ONLY — not implemented. Recorded here so the organization of
 * all five stages is visible in one place, per the request to sketch
 * the whole curriculum while building only Stage 0 for real.
 *
 * Stage 1 adds the Normative layer: a Constraint object (safety/liveness
 * bound, obligation, or prohibition) that a tuple's W' must cite when
 * agent.formal.Relation.CONSTRAINT_DRIVEN is used. Natural test
 * environment: artifact operations refusable for policy reasons,
 * surfacing as action_failed with a constraint-shaped reason — well
 * stress-tested by Gaia2/ARE's Noise category (perturbed or restricted
 * tool access).
 *
 * TODO when this stage is built for real:
 *   - A Constraint class: kind (obligation | prohibition), scope, description.
 *   - Extend Manual so an artifact type can declare constraints on its
 *     own operations, not just their signatures and outputs.
 *   - Extend WellFormedness with a WF5: a CONSTRAINT_DRIVEN tuple must
 *     cite the specific Constraint object in W'.
 *   - Extend StageProfile.stage0() with a stage0plus1() factory once
 *     there is something real to activate.
 */
public final class Stage1Sketch {
    private Stage1Sketch() {}
}
