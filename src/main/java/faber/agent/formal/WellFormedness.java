package faber.agent.formal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import faber.agent.GoalLedger;

/**
 * WF1-WF4 from the Core semantics note — mechanically checkable,
 * independent of content quality. All four are real, complete checks
 * here (unlike some of the Coherence conditions, which need simplifying
 * assumptions); these ones only require set membership and reference
 * validity, not semantic judgment.
 */
public final class WellFormedness {

    public static final class Result {
        public final boolean pass;
        public final List<String> violations;
        Result(boolean pass, List<String> violations) { this.pass = pass; this.violations = violations; }
    }

    /**
     * @param tuple the tuple to check
     * @param groundedSource every string a cited belief is allowed to trace to —
     *                       in practice, the rendered percept lines and workspace
     *                       facts actually available this cycle
     * @param ledger the goal ledger, to validate G
     */
    public static Result check(CoreTuple tuple, Set<String> groundedSource, GoalLedger ledger) {
        List<String> violations = new ArrayList<>();

        // WF1 — groundedness
        for (String w : tuple.W) {
            if (!groundedSource.contains(w)) {
                violations.add("WF1 violated: cited belief not traceable to any available source: " + w);
            }
        }

        // WF2 — goal validity
        if (!tuple.isReactive() && !ledger.isRegistered(tuple.G)) {
            violations.add("WF2 violated: goal id not registered and no inline content was given: " + tuple.G);
        }

        // WF3 — action closure (trivially satisfied by the Java type system here: A is a real enum
        // value or the tuple could not have been constructed — kept as an explicit check anyway,
        // since a text-based harness parsing a weaker action format would need this checked for real).
        if (tuple.A == null) {
            violations.add("WF3 violated: no action recorded");
        }

        // WF4 — citation validity
        if (!tuple.W.containsAll(tuple.WPrime)) {
            violations.add("WF4 violated: J cites beliefs absent from W: " + tuple.WPrime);
        }

        return new Result(violations.isEmpty(), violations);
    }
}
