package faber.agent.formal;

import java.util.ArrayList;
import java.util.List;

import faber.agent.PlanResult;
import faber.agent.PlanResult.ActionKind;

/**
 * C1-C4 from the Core semantics note. C1 (goal-directed coherence,
 * covering both ACHIEVEMENT and MAINTENANCE) and C3 (consistency) are
 * given deliberately simplified, best-effort implementations —
 * genuinely checking them needs semantic understanding of the domain
 * that a small, honest Stage 0 skeleton shouldn't pretend to have. C2
 * (non-vacuous citation) is not checked online at all: it is defined
 * as a perturb-and-replay audit procedure in the Core semantics note
 * (expensive, audit-only, not a runtime gate) and is deliberately left
 * out of this online check. C4 (delta discipline) is fully and
 * correctly mechanically checkable and is implemented for real.
 */
public final class Coherence {

    public static final class Result {
        public final boolean pass;
        public final List<String> notes;
        Result(boolean pass, List<String> notes) { this.pass = pass; this.notes = notes; }
    }

    /**
     * C1, simplified: for a goal-directed tuple (ACHIEVEMENT or MAINTENANCE — anything not
     * REACTIVE), require some keyword overlap between the cited subset and the goal's content. A
     * crude proxy for "the action plausibly bears on the goal" — not a substitute for real semantic
     * checking, and known to both over- and under-accept.
     */
    static boolean simplifiedMeansEndPlausible(CoreTuple tuple, String goalContent) {
        // A REACTIVE tuple reaching this check is now also, always, a WF8 violation on the same
        // tuple — this short-circuit is defensive at this point, not a normal path a well-formed
        // tuple should ever actually take.
        if (tuple.r == Relation.REACTIVE || goalContent == null) return true;
        // An empty WPrime means nothing was cited at all — there is nothing to check for
        // irrelevance, a different situation from something being cited and failing to overlap.
        // Without this, any goal-directed action taken with zero new percepts (the standing goal,
        // correctly cited during a genuinely idle cycle) would fail here every single time,
        // regardless of how well-justified the citation is — not an occasional false positive,
        // a structural one, confirmed directly once citing the standing goal on empty cycles
        // became the correct behavior rather than something REACTIVE's short-circuit above
        // always caught first.
        if (tuple.WPrime.isEmpty()) return true;
        String[] goalWords = goalContent.toLowerCase().split("\\W+");
        for (String w : tuple.WPrime) {
            String lw = w.toLowerCase();
            for (String gw : goalWords) {
                if (gw.length() > 3 && lw.contains(gw)) return true;
            }
        }
        return false;
    }

    /** C3, simplified: a goal already resolved (achieved or dropped) cannot still be the target of a
     *  goal-directed (ACHIEVEMENT or MAINTENANCE) action — including a MAINTENANCE goal that was
     *  dropped: a called-off standing watch being kept anyway is just as inconsistent as continuing
     *  to pursue an already-achieved one.
     *
     *  STOP_OBSERVING is exempted from this specifically: unlike every other action kind, it can
     *  never represent continuing to pursue a goal — it is structurally the act of ceasing to, never
     *  continuing to. Citing an already-resolved goal on a STOP_OBSERVING is not "a called-off watch
     *  kept anyway" — it is the literal calling-off, completed, and is in fact the single most common
     *  legitimate reason to cite a resolved goal at all (tearing down observation that existed only
     *  in service of it). Confirmed as a real, recurring pattern: four separate real cycles this
     *  session committed a STOP_OBSERVING motivated by a goal resolved earlier the same session,
     *  every one of them a genuine cleanup, none a goal being kept alive past its own closure. The
     *  exemption is scoped to this one action kind only — an INVOKE, FOCUS, or WAIT citing an
     *  already-resolved goal remains exactly the inconsistency this check exists to catch. */
    static boolean simplifiedConsistent(CoreTuple tuple, boolean goalAlreadySatisfied) {
        if (tuple.A == PlanResult.ActionKind.STOP_OBSERVING) return true;
        return !(tuple.r != Relation.REACTIVE && goalAlreadySatisfied);
    }

    /** C4, real: the tuple must differ from the previous cycle's tuple, unless the action is WAIT. */
    static boolean deltaDisciplineHolds(CoreTuple current, CoreTuple previous) {
        if (previous == null) return true;
        if (current.A == PlanResult.ActionKind.WAIT) return true;
        return !current.equals(previous);
    }

    public static Result check(CoreTuple current, CoreTuple previous, String goalContent, boolean goalAlreadySatisfied) {
        List<String> notes = new ArrayList<>();
        boolean pass = true;

        if (!simplifiedMeansEndPlausible(current, goalContent)) {
            pass = false;
            notes.add("C1 (simplified) failed: no keyword overlap between cited beliefs and goal content");
        }
        if (!simplifiedConsistent(current, goalAlreadySatisfied)) {
            pass = false;
            notes.add("C3 (simplified) failed: goal already resolved (achieved or dropped) but still targeted by a goal-directed action");
        }
        if (!deltaDisciplineHolds(current, previous)) {
            pass = false;
            notes.add("C4 failed: tuple identical to the previous cycle's, and action is not WAIT");
        }
        notes.add("C2 (non-vacuous citation) is audit-only and not checked online — see the perturb-and-replay procedure in the Core semantics note");

        return new Result(pass, notes);
    }
}
