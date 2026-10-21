package faber.devl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a queue judged in a changed world shows about an agent that learned the old one. Applies to the change assessment (A2a) and to operation after the change.
 *
 * An item CHANGED if the right outcome differs between the old world and the new. A wrong decision on a changed item is STALE when it is exactly what the old world would have wanted: the
 * agent acted on what it knew, and did not check it against what is in force. Any other wrong decision on a changed item is an error of another kind. A wrong decision on an item that did
 * NOT change is over-correction (the agent distrusted something sound) — or, if the clean assessment was not passed, simply a lack of the competence the change presupposes.
 *
 * With a curated, versioned model published in step with the world, a stale decision is a failure to consult; with a model that is left behind, it may be the model's.
 */
public final class ChangeAnalysis {
    private ChangeAnalysis() {}

    public static final class Result {
        public final List<String> changedIds = new ArrayList<>(), changedGroups = new ArrayList<>(), staleIds = new ArrayList<>(), otherWrongOnChanged = new ArrayList<>(), overCorrectionIds = new ArrayList<>();
        public int correct, total;
        public final List<String> lines = new ArrayList<>();
    }

    public static Result analyse(String title, List<? extends WorkItem> items, List<Outcome> outcomes, int before, int now, boolean baselineClean) {
        Result r = new Result(); Set<String> groups = new TreeSet<>();
        java.util.Map<String, WorkItem> byId = new java.util.LinkedHashMap<>(); for (WorkItem c : items) byId.put(c.id(), c);
        for (WorkItem c : items) if (!c.expected(before).equals(c.expected(now))) { r.changedIds.add(c.id()); groups.add(c.group()); }
        r.changedGroups.addAll(groups);
        List<Outcome> ordered = new ArrayList<>(outcomes); ordered.sort(java.util.Comparator.comparing(d -> d.itemId));      // outcomes are recorded in the order concurrent operations ran: report them in a stable order
        for (var d : ordered) {
            r.total++;
            if (d.correct) { r.correct++; continue; }
            if (r.changedIds.contains(d.itemId)) {
                if (d.label.equals(byId.get(d.itemId).expected(before))) r.staleIds.add(d.itemId); else r.otherWrongOnChanged.add(d.itemId);
            } else r.overCorrectionIds.add(d.itemId);
        }
        r.lines.add("=== change analysis (" + title + ") ===");
        if (!baselineClean)
            r.lines.add("  !! A1 (the clean assessment) was NOT passed: the agent lacked baseline competence before the change, so errors on unchanged items are not over-correction, and the figures below\n"
                      + "     say little about how it handled the change.");
        r.lines.add("  " + r.changedIds.size() + " of " + items.size() + " items change outcome in the new world " + r.changedIds + ", in the groups " + r.changedGroups + ".");
        r.lines.add("  got " + r.correct + "/" + r.total + ".  wrong on changed items: " + (r.staleIds.size() + r.otherWrongOnChanged.size())
                + " — STALE (exactly what the old world would have wanted): " + r.staleIds.size() + " " + r.staleIds + ", other: " + r.otherWrongOnChanged.size() + " " + r.otherWrongOnChanged
                + ".  wrong on unchanged items (" + (baselineClean ? "over-correction" : "NOT over-correction: A1 failed") + "): " + r.overCorrectionIds.size() + " " + r.overCorrectionIds + ".");
        return r;
    }

    public static List<String> lines(String title, List<? extends WorkItem> items, List<Outcome> outcomes, int before, int now, boolean baselineClean) { return analyse(title, items, outcomes, before, now, baselineClean).lines; }
}
