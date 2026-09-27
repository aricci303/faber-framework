package faber.agent.formal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import faber.agent.GoalLedger;
import faber.agent.GoalKind;
import faber.agent.GoalStatus;
import faber.agent.IntentionLedger;

/**
 * WF1-WF8 from the Core semantics note plus four additions (WF5, goal
 * hierarchy validity, added when parent_goal_id was introduced; WF6,
 * goal-kind/status consistency, added alongside the achievement/
 * maintenance refinement of Relation; WF7, kind classification
 * currency, added alongside GoalKind.TO_BE_DECIDED replacing a silent
 * ACHIEVEMENT default; WF8, goal presence, added when REACTIVE stopped
 * being a tolerated possibility and became something that should never
 * legitimately occur at all) — mechanically checkable, independent of
 * content quality, and correctly implemented as specified. Worth being
 * precise about which are *currently active* versus *currently
 * vacuous*, since that's a different claim than "correctly
 * implemented":
 *
 *   - WF2 (goal validity), WF5 (goal hierarchy validity), WF6
 *     (goal-kind/status consistency), WF7 (kind classification
 *     currency), and WF8 (goal presence) are genuinely active: WF2 can
 *     and does fail, confirmed directly, whenever an action cites a
 *     goal id no intention_changes entry has ever registered; WF5 the
 *     same way, whenever a registered goal's own declared parent is
 *     unregistered or the parent chain cycles back on itself; WF6
 *     whenever a MAINTENANCE-kind goal's status is ever ACHIEVED — a
 *     standing condition with no terminal state reaching a terminal
 *     status is a genuine contradiction, not a style preference; WF7
 *     whenever the goal actually cited this cycle has never had its
 *     kind declared at all, riding TupleExtractor's own ACHIEVEMENT
 *     fallback instead of a real classification; WF8 whenever an
 *     action cites no goal at all (G=BOTTOM, J=REACTIVE) — confirmed
 *     directly, on a real run: a recurring trigger fired exactly as
 *     registered, the model sent precisely the right response, and
 *     still cited nothing, as though the action had no goal behind it
 *     when in fact its own watch's standing goal was what was being
 *     fulfilled. Given every agent is seeded with a goal from before
 *     its very first cycle even runs (see SeedGoal), there is no
 *     genuinely reactive moment left for REACTIVE to legitimately
 *     describe — its occurrence is now always a citation the model
 *     failed to make, not a real category of action.
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
     * @param goalLedger the goal ledger, to validate G, the goal hierarchy, and each goal's kind
     * @param intentionLedger the intention ledger, to validate each goal's current status for WF6
     */
    public static Result check(CoreTuple tuple, Set<String> groundedSource, GoalLedger goalLedger,
                                IntentionLedger intentionLedger) {
        List<String> violations = new ArrayList<>();

        // WF1 — groundedness
        for (String w : tuple.W) {
            if (!groundedSource.contains(w)) {
                violations.add("WF1 violated: cited belief not traceable to any available source: " + w);
            }
        }

        // WF2 — goal validity
        if (!tuple.isReactive() && !goalLedger.isRegistered(tuple.G)) {
            violations.add("WF2 violated: action cites goal id '" + tuple.G + "' as a goal-directed target, "
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
        for (String goalId : goalLedger.allGoalIds()) {
            String parentGoalId = goalLedger.parentOf(goalId);
            if (parentGoalId == null) continue;
            if (!goalLedger.isRegistered(parentGoalId)) {
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
                current = goalLedger.parentOf(current);
            }
        }

        // WF6 — goal-kind/status consistency: a MAINTENANCE-kind goal — a standing condition with
        // no terminal state, by definition — must never reach GoalStatus.ACHIEVED. ONGOING and
        // DROPPED both remain legitimate (a standing watch can always be explicitly called off),
        // only ACHIEVED is a contradiction for a goal kind that has no terminal state to reach.
        // Checked over the ledger's full current state each cycle, the same way WF5 is, since a
        // goal's kind never changes once declared and its status can change on any later cycle.
        for (String goalId : goalLedger.allGoalIds()) {
            if (goalLedger.kindOf(goalId) != GoalKind.MAINTENANCE) continue;
            if (intentionLedger.statusOf(goalId) == GoalStatus.ACHIEVED) {
                violations.add("WF6 violated: goal '" + goalId + "' is MAINTENANCE-kind (a standing "
                        + "condition with no terminal state) but its status is ACHIEVED — a maintenance "
                        + "goal may be DROPPED, never ACHIEVED");
            }
        }

        // WF7 — kind classification currency: the goal actually cited as G this cycle must have a
        // genuinely declared kind, not be riding the ACHIEVEMENT fallback TupleExtractor falls back
        // to for a TO_BE_DECIDED goal (see that class's own comment on why the fallback still exists
        // — Relation needs a concrete value every cycle — and why it is no longer silent). Checked
        // only against this cycle's own G, not the full ledger the way WF5/WF6 are: an unclassified
        // goal sitting unused in the ledger is not yet a problem, only citing one while it is still
        // unclassified is — the same moment its own Relation would otherwise silently default.
        if (!tuple.isReactive() && goalLedger.kindOf(tuple.G) == GoalKind.TO_BE_DECIDED) {
            violations.add("WF7 violated: action cites goal '" + tuple.G + "' whose kind is still "
                    + "TO_BE_DECIDED — J was computed as ACHIEVEMENT by fallback, not because this "
                    + "goal was ever actually classified; declare its kind before or as it is cited");
        }

        // WF8 — goal presence: every agent is seeded with a goal from before its very first cycle
        // even runs (see SeedGoal), and that goal, or something decomposed from it, is always
        // available to cite. Given that, an action citing no goal at all (G=BOTTOM, J=REACTIVE) is
        // never a genuine category of action, only a citation the model failed to make — "no
        // intention, no action" holds without exception, not as a guideline with a reactive escape
        // hatch. Confirmed on a real run, not hypothetical: a recurring trigger fired exactly as
        // registered, the model sent precisely the right response to it, and still cited nothing,
        // as though the action had no goal behind it when its own watch's standing goal was
        // genuinely what was being fulfilled.
        if (tuple.isReactive()) {
            violations.add("WF8 violated: action cites no goal at all (G=BOTTOM, J=REACTIVE) — an "
                    + "agent is never spawned without a goal, so there is never a genuinely reactive "
                    + "moment with nothing to cite; this is a missed citation, not a legitimate action");
        }

        return new Result(violations.isEmpty(), violations);
    }
}

