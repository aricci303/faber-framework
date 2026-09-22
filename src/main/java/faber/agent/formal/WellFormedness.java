package faber.agent.formal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import faber.agent.GoalLedger;

/**
 * WF1-WF5 from the Core semantics note plus one addition (WF5, goal
 * hierarchy validity, added when parent_goal_id was introduced) —
 * mechanically checkable, independent of content quality, and
 * correctly implemented as specified. Worth being precise about which
 * are *currently active* versus *currently vacuous*, since that's a
 * different claim than "correctly implemented":
 *
 *   - WF2 (goal validity) and WF5 (goal hierarchy validity) are
 *     genuinely active: WF2 can and does fail, confirmed directly,
 *     whenever an action cites a goal id no intention_changes entry
 *     has ever registered; WF5 the same way, whenever a registered
 *     goal's own declared parent is unregistered or the parent chain
 *     cycles back on itself.
 *
 *   - WF3 (action closure) is trivially satisfied by the Java type
 *     system — A is a real enum value or the tuple could not have
 *     been constructed. Kept as an explicit check anyway, since a
 *     text-based harness parsing a weaker action format would need
 *     this checked for real; honestly documented as vacuous here.
 *
 *   - WF1 (groundedness) and WF4 (citation validity) are, as
 *     currently wired, structurally guaranteed to hold and cannot
 *     fail — not through any bug, but because HeuristicTupleExtractor
 *     builds W, W', and the groundedSource passed in here all
 *     mechanically from the same percepts list, via the same
 *     transformation, with zero influence from anything the model
 *     itself outputs (deliberately — see that class's own doc comment
 *     on staying "non-generative"). W' is constructed as a filtered
 *     subset of W, and groundedSource is built the identical way W
 *     is; there is no path, with this extractor, for either
 *     containment to be violated. Both checks are real safeguards
 *     against a future, less conservative extractor implementation
 *     that might derive either side from something genuinely capable
 *     of diverging — not currently-active checks against this one.
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
     * @param ledger the goal ledger, to validate G and the goal hierarchy
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
            violations.add("WF2 violated: action cites goal id '" + tuple.G + "' as a means-end target, "
                    + "but no intention_changes entry has ever registered it");
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

        // WF5 — goal hierarchy validity: every declared parent must itself be a registered goal,
        // and the parent chain from any goal must never lead back to itself. Checked over the
        // ledger's full current state each cycle rather than only this cycle's own registrations —
        // a parent link, once valid, stays valid forever (GoalLedger never removes a goal), so this
        // is simpler than threading intention_changes into this check and no less correct.
        for (String goalId : ledger.allGoalIds()) {
            String parentGoalId = ledger.parentOf(goalId);
            if (parentGoalId == null) continue;
            if (!ledger.isRegistered(parentGoalId)) {
                violations.add("WF5 violated: goal '" + goalId + "' declares parent '" + parentGoalId
                        + "', but no intention_changes entry has ever registered that parent");
                continue;
            }
            Set<String> visited = new HashSet<>();
            String current = goalId;
            while (current != null) {
                if (!visited.add(current)) {
                    violations.add("WF5 violated: goal '" + goalId + "'’s parent chain cycles back on itself "
                            + "at '" + current + "'");
                    break;
                }
                current = ledger.parentOf(current);
            }
        }

        return new Result(violations.isEmpty(), violations);
    }
}

