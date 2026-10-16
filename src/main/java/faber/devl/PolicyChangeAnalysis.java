package faber.devl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Makes the policy-change assessment (A2a) comparable across runs. When the policy changes under an agent, and the only
 * thing that can tell it so is an audit flag that arrives AFTER a decision is recorded, the first changed
 * claim in each changed category is necessarily decided on stale knowledge: nothing could have warned the
 * agent. That is a floor, not a failure. What a run can be judged on is the rest:
 *
 *   avoidable errors     — wrong on a changed claim beyond that floor (a flag had, or could have, revealed the change);
 *   over-correction      — wrong on a claim whose outcome did NOT change (the agent distrusted something sound).
 *
 * The first complete run (then called "deployment") scored 5/10 with 2 avoidable errors (it committed all ten at once); the first lean run
 * scored 5/8 with none. The raw scores look alike; this is the difference.
 */
public final class PolicyChangeAnalysis {
    private PolicyChangeAnalysis() {}

    public static List<String> lines(List<ExpenseCase> cases, List<ExpenseSystemArtifact.Decision> decisions, ExpensePolicy before, ExpensePolicy now) {
        return lines(cases, decisions, before, now, true);
    }

    /**
     * baselineClean: did the agent pass the clean assessment (A1)? If not, it lacked baseline competence BEFORE the policy changed, so
     * errors on claims whose outcome did not change cannot be read as over-correction (the agent was not distrusting something sound;
     * it did not have the knowledge), and the whole analysis says so. A1 is the gate for reading A2a.
     */
    public static List<String> lines(List<ExpenseCase> cases, List<ExpenseSystemArtifact.Decision> decisions, ExpensePolicy before, ExpensePolicy now, boolean baselineClean) {
        Set<String> changedIds = new TreeSet<>(), changedCategories = new TreeSet<>();
        for (ExpenseCase c : cases)
            if (!before.decide(c).label().equals(now.decide(c).label())) { changedIds.add(c.id); changedCategories.add(c.category); }
        int wrongChanged = 0, wrongControls = 0, correct = 0;
        List<String> wrongChangedIds = new ArrayList<>(), wrongControlIds = new ArrayList<>();
        for (var d : decisions) {
            if (d.correct) { correct++; continue; }
            if (changedIds.contains(d.expenseId)) { wrongChanged++; wrongChangedIds.add(d.expenseId); }
            else { wrongControls++; wrongControlIds.add(d.expenseId); }
        }
        int floor = changedCategories.size();
        int avoidable = Math.max(0, wrongChanged - floor);
        List<String> out = new ArrayList<>();
        out.add("=== policy-change analysis (A2a) ===");
        if (!baselineClean)
            out.add("  !! A1 (the clean assessment) was NOT passed: the agent lacked baseline competence before the policy changed, so A2a errors on unchanged claims are not\n"
                  + "     over-correction, and none of the figures below can be read as how it handled the change. Read A2a only for what it shows about recovery.");
        out.add("  " + changedIds.size() + " of " + cases.size() + " claims changed outcome under the new policy, in " + floor + " categories " + changedCategories + ".");
        out.add("  A learner informed only by audit flags (which arrive after the decision) must get the first changed claim of each category wrong: floor = "
                + floor + " errors, best possible score " + (cases.size() - floor) + "/" + cases.size() + ".");
        out.add("  got " + correct + "/" + decisions.size() + ".  wrong on changed claims: " + wrongChanged + " " + wrongChangedIds
                + " -> avoidable beyond the floor: " + avoidable + ".  wrong on unchanged claims (" + (baselineClean ? "over-correction" : "NOT over-correction: A1 failed") + "): " + wrongControls + " " + wrongControlIds + ".");
        return out;
    }
}
