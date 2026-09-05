package faber.agent.formal;

import java.util.ArrayList;
import java.util.List;

import faber.agent.PlanResult;
import faber.agent.PlanResult.ActionKind;

/**
 * C1-C4 from the Core semantics note. C1 (means-end coherence) and C3
 * (consistency) are given deliberately simplified, best-effort
 * implementations — genuinely checking them needs semantic
 * understanding of the domain that a small, honest Stage 0 skeleton
 * shouldn't pretend to have. C2 (non-vacuous citation) is not checked
 * online at all: it is defined as a perturb-and-replay audit procedure
 * in the Core semantics note (expensive, audit-only, not a runtime
 * gate) and is deliberately left out of this online check. C4 (delta
 * discipline) is fully and correctly mechanically checkable and is
 * implemented for real.
 */
public final class Coherence {

    public static final class Result {
        public final boolean pass;
        public final List<String> notes;
        Result(boolean pass, List<String> notes) { this.pass = pass; this.notes = notes; }
    }

    /**
     * C1, simplified: for a means-end tuple, require some keyword overlap
     * between the cited subset and the goal's content. A crude proxy for
     * "the action plausibly bears on the goal" — not a substitute for
     * real semantic checking, and known to both over- and under-accept.
     */
    static boolean simplifiedMeansEndPlausible(CoreTuple tuple, String goalContent) {
        if (tuple.r != Relation.MEANS_END || goalContent == null) return true;
        String[] goalWords = goalContent.toLowerCase().split("\\W+");
        for (String w : tuple.WPrime) {
            String lw = w.toLowerCase();
            for (String gw : goalWords) {
                if (gw.length() > 3 && lw.contains(gw)) return true;
            }
        }
        return false;
    }

    /** C3, simplified: a goal already marked satisfied cannot still be the target of a means-end action. */
    static boolean simplifiedConsistent(CoreTuple tuple, boolean goalAlreadySatisfied) {
        return !(tuple.r == Relation.MEANS_END && goalAlreadySatisfied);
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
            notes.add("C3 (simplified) failed: goal already satisfied but still targeted by a means-end action");
        }
        if (!deltaDisciplineHolds(current, previous)) {
            pass = false;
            notes.add("C4 failed: tuple identical to the previous cycle's, and action is not WAIT");
        }
        notes.add("C2 (non-vacuous citation) is audit-only and not checked online — see the perturb-and-replay procedure in the Core semantics note");

        return new Result(pass, notes);
    }
}
