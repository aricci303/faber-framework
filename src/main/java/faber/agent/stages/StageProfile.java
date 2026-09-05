package faber.agent.stages;

import faber.agent.formal.Relation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Declares which cognitive layers are active for a deployment. A tuple
 * whose relation type falls outside the active profile is itself a
 * well-formedness violation — a layer leaking in without being
 * declared, per the design discussion on how extension layers attach.
 * Only stage0() is real; the others are placeholders until Stages 1-4
 * are actually built (see the Stage*Sketch classes).
 */
public final class StageProfile {

    private final Set<Stage> active;

    private StageProfile(Set<Stage> active) { this.active = active; }

    public static StageProfile stage0() {
        return new StageProfile(EnumSet.of(Stage.CORE));
    }

    public boolean isActive(Stage stage) { return active.contains(stage); }

    public boolean relationAllowed(Relation r) {
        switch (r) {
            case MEANS_END:
            case REACTIVE:
                return active.contains(Stage.CORE);
            case CONSTRAINT_DRIVEN:
                return active.contains(Stage.NORMATIVE);
            case TRUST_BASED:
            case DELEGATED:
                return active.contains(Stage.SOCIAL);
            case PREFERENCE_DRIVEN:
                return active.contains(Stage.QUANTITATIVE);
            default:
                return false;
        }
    }

    public Set<Stage> active() { return Collections.unmodifiableSet(active); }
}
