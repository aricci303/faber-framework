package faber.devl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Consolidation after assessment (--consolidate=on).
 *
 * An assessment is not only a measurement: an agent that is audited learns from it — in the first letter run the agent corrected three category notes after the audit flags
 * and never touched its letter, which went stale (the agent that is assessed works from its working notes; the letter is written for a fresh reader and nothing in its own
 * workflow maintains it). So after the last assessment the agent is sent back to the learning phase for a moment, and asked to bring the letter up to date. The harness measures
 * what the revision holds, and derives from the notebook as the agent left it a RELEASE CANDIDATE: a knowledge snapshot with its lineage (parent, round, what was assessed and how it went).
 * The candidate is then assessed in its turn, by a fresh instance — what is assessed is a candidate, never a mind.
 *
 * "Current knowledge" is defined against the policy change of the assessment: the 5 rules it left alone (receipt, duplicate, flight, lodging, training) and the 3 it changed
 * (meals 35/75, vendors with Orbit, equipment 400): 8 in all, by the same keyword probes as everywhere else. In a run without the policy-change assessment the 3 do not apply.
 */
final class Consolidation {
    private Consolidation() {}

    static final String VERSION = "consolidation-v1";
    static final String LABEL = "consolidation";
    static final List<String> UNCHANGED_RULES = List.of("r-receipt", "r-duplicate", "r-flight", "r-lodging", "r-training");
    static final List<String> CHANGED_RULES = List.of("v2-meals", "v2-software", "v2-equipment");

    /** The request. It says that the assessment is over, that the agent is back in learning, and what the letter is for; it names no error and gives no hint about what to change. */
    static final String REQUEST =
            "The assessment is over, and you are back in the learning phase for a moment. Whatever it taught you should not be lost: bring your letter to your future self up to date "
          + "(write it, if you have none yet), so that a future version of yourself, created fresh from the letter and the manuals, would do this job correctly from the start. "
          + "Write a new version that replaces the earlier one completely: keep what is still true, correct what is not, add what you have learned since. " + PrimerPilot.CONTENT
          + " If your other notes disagree with the letter, bring them in line with it. Keep the letter under the key '" + PrimerPilot.KEY + "', and tell me when it is written.";

    /** The round: how many consolidations lie in the line of descent, counting this one (the parent's lineage knows its own). */
    static int roundOf(KnowledgeSnapshot parent) {
        JSONObject l = parent.manifest.optJSONObject("lineage");
        return l == null ? 1 : l.optInt("round") + 1;
    }

    /**
     * The candidate's id. Written by the same run that wrote its parent (a learning run, consolidating after its own assessments): S-x-d3 -> S-x-d3-c1. Written by another run
     * (an instance of the parent, consolidating after ITS assessments): S-x-d3 -> S-x-d3-c1-123456 (the producing run's time), so two candidates of one parent never share an id.
     */
    static String candidateId(KnowledgeSnapshot parent, String stamp, boolean parentWrittenByThisRun) {
        boolean sameRun = parentWrittenByThisRun;     // told by the caller: comparing log names fails for two runs that start in the same second
        return parent.id() + "-c" + roundOf(parent) + (sameRun ? "" : "-" + (stamp.length() >= 6 ? stamp.substring(stamp.length() - 6) : stamp));
    }

    static final class Knowledge {
        final List<String> unchanged = new ArrayList<>(), changed = new ArrayList<>();
        String text(boolean changedApplies) { return unchanged.size() + "/5 of the unchanged rules" + (changedApplies ? " + " + changed.size() + "/3 of the changed ones = " + (unchanged.size() + changed.size()) + "/8" : ""); }
        String short8(boolean changedApplies) { return changedApplies ? (unchanged.size() + changed.size()) + "/8" : unchanged.size() + "/5"; }
    }

    static Knowledge currentKnowledge(DomainReference reference, Map<String, String> notes) {
        Knowledge k = new Knowledge(); var concepts = AssessmentMetrics.asConcepts(notes);
        for (String id : reference.matchedIds("rules", concepts)) if (UNCHANGED_RULES.contains(id)) k.unchanged.add(id);
        for (String id : reference.matchedIds("v2", concepts)) if (CHANGED_RULES.contains(id)) k.changed.add(id);
        return k;
    }

    static final class Result {
        boolean letterRevised, candidateWritten; Path file; String candidateId; final List<String> lines = new ArrayList<>(), warnings = new ArrayList<>();
    }

    /**
     * parentWrittenByThisRun: true for a learning run consolidating after its own assessments (the parent is the snapshot it wrote at the end of learning).
     * notesBefore: the notebook when the consolidation began; notesAfter: as the agent left it. parent: the snapshot this run started from (an instance) or wrote at the end of learning.
     * assessed: the result of each assessment stage ("a1-clean-assessment" -> "6/6"). changedApplies: the policy-change assessment was part of the run.
     */
    static Result finish(KnowledgeSnapshot parent, boolean parentWrittenByThisRun, Map<String, String> notesBefore, Map<String, String> notesAfter, Map<String, String> assessed, boolean changedApplies,
                         Path dir, String arm, String stamp, String runLog, int cycles, DomainReference reference) throws IOException {
        Result r = new Result();
        String before = notesBefore.get(PrimerPilot.KEY), after = notesAfter.get(PrimerPilot.KEY);
        boolean hadLetter = before != null && !before.isBlank(), hasLetter = after != null && !after.isBlank();
        r.letterRevised = hasLetter && (!hadLetter || !before.strip().equals(after.strip()));
        Map<String, String> otherBefore = new LinkedHashMap<>(notesBefore), otherAfter = new LinkedHashMap<>(notesAfter); otherBefore.remove(PrimerPilot.KEY); otherAfter.remove(PrimerPilot.KEY);
        List<String> changedNotes = new ArrayList<>(), addedNotes = new ArrayList<>(), removedNotes = new ArrayList<>();
        for (var e : otherAfter.entrySet()) { if (!otherBefore.containsKey(e.getKey())) addedNotes.add(e.getKey()); else if (!otherBefore.get(e.getKey()).strip().equals(e.getValue().strip())) changedNotes.add(e.getKey()); }
        for (String k : otherBefore.keySet()) if (!otherAfter.containsKey(k)) removedNotes.add(k);

        Knowledge letterB = currentKnowledge(reference, hadLetter ? Map.of(PrimerPilot.KEY, before) : Map.of()), letterA = currentKnowledge(reference, hasLetter ? Map.of(PrimerPilot.KEY, after) : Map.of());
        Knowledge othersB = currentKnowledge(reference, otherBefore), othersA = currentKnowledge(reference, otherAfter), allA = currentKnowledge(reference, notesAfter);

        r.lines.add("=== consolidation (" + VERSION + "): the letter after the assessments ===");
        r.lines.add("  letter: " + (!hasLetter ? "NONE — the notebook has no note named '" + PrimerPilot.KEY + "' after the consolidation"
                : (hadLetter ? before.length() + " -> " : "none -> ") + after.length() + " characters; " + (!hadLetter ? "written now" : r.letterRevised ? "REVISED (" + (after.length() - before.length() >= 0 ? "+" : "") + (after.length() - before.length()) + " characters)" : "UNCHANGED")));
        r.lines.add("  current knowledge (keyword probes; " + (changedApplies ? "the 5 rules the policy change left alone + the 3 it changed" : "the 5 rules; the policy-change assessment was not part of this run, so the 3 changed ones do not apply") + "):");
        r.lines.add("    the letter alone:  before " + letterB.short8(changedApplies) + "  ->  after " + letterA.short8(changedApplies) + "      the other notes: before " + othersB.short8(changedApplies) + "  ->  after " + othersA.short8(changedApplies) + "      all together after: " + allA.short8(changedApplies));
        r.lines.add("  other notes: changed " + changedNotes + ", added " + addedNotes + ", removed " + removedNotes);

        List<String> lostRules = new ArrayList<>(letterB.unchanged); lostRules.removeAll(letterA.unchanged);
        if (!hasLetter) r.warnings.add("no letter at the end: the candidate carries only the notes");
        if (hasLetter && hadLetter && !r.letterRevised && (!changedNotes.isEmpty() || !addedNotes.isEmpty()))
            r.warnings.add("the letter was NOT revised although the agent changed other notes " + (changedNotes.isEmpty() ? addedNotes : changedNotes) + ": the letter and the notes may now disagree");
        if (hasLetter && hadLetter && after.length() * 2 < before.length()) r.warnings.add("the revised letter is less than half the length of the one it replaced (" + before.length() + " -> " + after.length() + "): check what was dropped");
        if (!lostRules.isEmpty()) r.warnings.add("the revised letter no longer holds rule(s) it held before: " + lostRules + " — a consolidation that forgets");
        if (changedApplies && hasLetter && letterA.changed.size() < 3) r.warnings.add("the revised letter reflects only " + letterA.changed.size() + "/3 of the changes the assessment showed: " + diff(CHANGED_RULES, letterA.changed));
        if (hasLetter) { List<String> dangling = new ArrayList<>(); for (String k : removedNotes) if (after.toLowerCase().contains(k.toLowerCase())) dangling.add(k); if (!dangling.isEmpty()) r.warnings.add("the letter refers to note(s) " + dangling + " that the agent removed"); }
        for (String w : r.warnings) r.lines.add("  WARNING — " + w);
        r.lines.add("  advisory gate for promotion: " + (lostRules.isEmpty() && hasLetter && (!changedApplies || letterA.changed.size() == 3) ? "PASS (nothing the letter held was lost" + (changedApplies ? "; all three changes reflected" : "") + ")" : "FAIL — see the warnings; promote only after reading the diff (tools/snapshot_diff.py)"));

        // the release candidate: the notebook as the agent left it, with its lineage
        int round = roundOf(parent); String id = candidateId(parent, stamp, parentWrittenByThisRun); r.candidateId = id;
        JSONObject opts = new JSONObject(parent.manifest.getJSONObject("options").toString()).put("consolidate", "on");
        JSONArray history = parent.learningHistory() == null ? new JSONArray() : new JSONArray(parent.learningHistory().toString());
        history.put(new JSONObject().put("stage", LABEL).put("cycles", cycles));
        KnowledgeSnapshot cand = KnowledgeSnapshot.ofLive(id, notesAfter, opts,
                new JSONObject().put("run", runLog).put("snapshot_after", LABEL).put("cycle", cycles).put("reconstructed_from_log", false).put("kind", "candidate"),
                history, allA.short8(changedApplies), List.of());
        JSONObject assessedJson = new JSONObject(); for (var e : assessed.entrySet()) assessedJson.put(e.getKey(), e.getValue());
        cand.manifest.put("lineage", new JSONObject().put("parent", parent.id()).put("round", round).put("produced_by", runLog).put("instruction", VERSION).put("assessed", assessedJson)
                .put("letter_revised", r.letterRevised).put("advisory_gate", r.lines.get(r.lines.size() - 1).contains("PASS")));
        cand.manifest.put("letter", new JSONObject().put("present", hasLetter).put("chars", hasLetter ? after.length() : 0).put("revised_in_consolidation", r.letterRevised));
        r.file = dir.resolve("devl-snapshot-" + arm + "-" + stamp + "-c" + round + ".json");
        cand.write(r.file); r.candidateWritten = true;
        r.lines.add("  release candidate written: " + r.file.getFileName() + " (" + id + ", " + notesAfter.size() + " notes, round " + round + ", from " + parent.id() + "; assessed before it: " + assessed + ")");
        r.lines.add("  next: assess it with FRESH instances (--from-knowledge=" + r.file.getFileName() + "), and read what it changed first: python3 tools/snapshot_diff.py <parent snapshot> " + r.file.getFileName());
        return r;
    }

    private static List<String> diff(List<String> all, List<String> held) { List<String> out = new ArrayList<>(all); out.removeAll(held); return out; }
}
