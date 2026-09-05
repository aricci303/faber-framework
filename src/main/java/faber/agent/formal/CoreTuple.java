package faber.agent.formal;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import faber.agent.PlanResult;
import faber.agent.PlanResult.ActionKind;

/**
 * ⟨W, G, A, J⟩ from the Core semantics note. J is represented here as
 * (relation, W') rather than a single opaque object, since both fields
 * are needed independently by the well-formedness and coherence checks.
 */
public final class CoreTuple {

    public static final String BOTTOM = "\u22A5"; // the explicit "no active goal" symbol, never left implicit

    public final Set<String> W;              // cited belief atoms
    public final String G;                    // goal id, or BOTTOM
    public final PlanResult.ActionKind A;
    public final Relation r;                  // J's relation type
    public final Set<String> WPrime;          // subset of W actually cited in J

    public CoreTuple(Set<String> W, String G, PlanResult.ActionKind A, Relation r, Set<String> WPrime) {
        this.W = new LinkedHashSet<>(W);
        this.G = G;
        this.A = A;
        this.r = r;
        this.WPrime = new LinkedHashSet<>(WPrime);
    }

    public boolean isReactive() { return BOTTOM.equals(G); }

    @Override
    public String toString() {
        return "\u27E8W=" + W + ", G=" + G + ", A=" + A + ", J=" + r + "(" + WPrime + ")\u27E9";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoreTuple)) return false;
        CoreTuple other = (CoreTuple) o;
        return W.equals(other.W) && G.equals(other.G) && A == other.A
                && r == other.r && WPrime.equals(other.WPrime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(W, G, A, r, WPrime);
    }
}
