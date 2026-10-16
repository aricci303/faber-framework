package faber.devl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/**
 * The primer pilot: does a notebook that an agent has learned its job into work better for a FRESH agent if it also holds a letter, from the agent to its future self, that tells the whole story?
 *
 * The Notebook was given to an agent to remember what it needs while doing a task. What a fresh agent needs is different: the job, how the systems behave, the rules in order with
 * the cases that taught them, the mistakes to avoid, what is uncertain and how each thing is known — in ONE narrative, in the agent's own words, not a ledger and not a diary.
 * In this pilot the letter is written by an instance made from a knowledge snapshot (so it can only reorganise what the notes say; the version written during learning, by the agent that lived it, is --letter=on),
 * and two VARIANT snapshots are derived from it, differing from the original in nothing but the letter:
 *   P   the primer alone                (what the letter carries, by itself)
 *   RP  the primer, then the original notes   (does the letter help a fresh reader who also has the reference notes?)
 * Anything else the writing instance changed in its notebook is reported and ignored, so the letter is the only variable.
 */
final class PrimerPilot {
    private PrimerPilot() {}

    static final String VERSION = "primer-v1";
    static final String KEY = "primer";
    static final String LABEL = "primer-writing";

    /**
     * The instruction. It says nothing about assessment, or about what might change: the letter is for the job. It asks for completeness, not brevity — every consolidation so far
     * lost boundary values and reject branches — and for exact limits, in the order the rules are applied.
     */
    static final String INSTRUCTION =
            "Your learning is complete. Write a letter to a future version of yourself, who will be created fresh to do this job and will remember nothing of this period: "
          + "only what you write here, and the manuals of the systems it works with. Write it as one narrative, in your own words, covering: what the job is and for whom; "
          + "how the systems you work with behave; what you learned about doing the job well, with the rules in the order you apply them, each with its exact limits, and the cases that taught you; "
          + "what you got wrong along the way and what to watch for; and what you are unsure of, and how you came to know each thing. It is not a diary of what you did. "
          + "Keep it complete rather than short: leave out nothing that the job needs. Put it in your notebook under the key '" + KEY + "', replacing any earlier version completely, "
          + "then tell me when it is written.";

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------------
    // The version written DURING learning, by the agent itself, at each debrief (--letter=on). The author is then the agent that lived it: the tutor is still available at the
    // first two debriefs, and recall() and the manuals can be consulted, so the letter can be checked against sources and not only against notes. Written incrementally: each
    // version replaces the last. The harness measures each version (what it holds of the rules, whether it changed) but never nudges the agent to write it.
    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------------
    static final String LEARNING_VERSION = "primer-v2-learning";

    static final String CONTENT =
            "Write it as one narrative, in your own words, covering: what the job is and for whom; how the systems you work with behave; what you learned about doing the job well, "
          + "with the rules in the order you apply them, each with its exact limits, and the cases that taught you; what you got wrong along the way and what to watch for; "
          + "and what you are unsure of, and how you came to know each thing. It is not a diary of what you did. Keep it complete rather than short: leave out nothing that the job needs. "
          + "Check it against what you can still consult — the manuals, and the tutor's own words if the tutor is still available — rather than relying on memory.";

    /** The request added to a debrief. Silent about assessment: the letter is for the job. first: the first version; otherwise a revision that replaces the last. */
    static String learningRequest(boolean first) {
        return first
            ? "One more thing, in addition to your notes: start a letter to a future version of yourself, who will be created fresh to do this job and will remember nothing of this period — "
            + "only what you write here, and the manuals of the systems it works with. " + CONTENT + " Put it in your notebook under the key '" + KEY + "', and tell me when it is written."
            : "One more thing, in addition to your notes: write your letter to your future self if you have not yet, or revise it — a new version that replaces the earlier one completely: "
            + "keep what is still true, correct what is not, add what you have learned since. A future version of yourself will be created fresh to do this job and will remember nothing of this period — "
            + "only the letter and the manuals. " + CONTENT + " Keep it under the key '" + KEY + "', and tell me when it is written.";
    }

    /** What the harness reports after a debrief in letter mode: is there a letter, did it change, and what does it hold of the 8 policy rules, alone and next to the notes. */
    static List<String> debriefLines(String stageLabel, Map<String, String> notebookNow, String previousLetter, DomainReference reference) {
        List<String> out = new ArrayList<>();
        String letter = notebookNow.get(KEY);
        if (letter == null || letter.isBlank()) { out.add("  letter after debrief " + stageLabel + ": NONE — the notebook has no note named '" + KEY + "'"); return out; }
        Map<String, String> notesOnly = new LinkedHashMap<>(notebookNow); notesOnly.remove(KEY);
        int paragraphs = (int) java.util.Arrays.stream(letter.split("\\n\\s*\\n")).filter(x -> !x.isBlank()).count();
        String change = previousLetter == null ? "first version" : previousLetter.strip().equals(letter.strip()) ? "UNCHANGED since the previous version" : "revised (" + (letter.length() - previousLetter.length() >= 0 ? "+" : "") + (letter.length() - previousLetter.length()) + " characters)";
        out.add("  letter after debrief " + stageLabel + ": " + letter.length() + " characters, " + paragraphs + " paragraphs; " + change + "; policy rules held (of 8, keyword probes): the letter alone "
                + rules(reference, Map.of(KEY, letter)) + ", the other notes " + rules(reference, notesOnly) + ", all together " + rules(reference, notebookNow));
        return out;
    }

    static final class Result {
        boolean written; String primer; Path pFile, rpFile; String pId, rpId; final List<String> lines = new ArrayList<>();
    }

    /**
     * Reads the letter from the notebook as it is now and writes the two variant snapshots into dir. notebookNow is the instance's notebook after the stage; base is the snapshot it
     * was instantiated from; reference scores how many of the 8 policy rules each version holds (keyword probes: lenient, but the same ones used everywhere else).
     */
    static Result finish(KnowledgeSnapshot base, Map<String, String> notebookNow, Path dir, String stamp, String runLog, int cycles, DomainReference reference) throws IOException {
        Result r = new Result();
        String primer = notebookNow.get(KEY);
        List<String> changedOthers = new ArrayList<>(), newKeys = new ArrayList<>();
        for (var e : notebookNow.entrySet()) {
            if (e.getKey().equals(KEY)) continue;
            if (!base.notes.containsKey(e.getKey())) newKeys.add(e.getKey());
            else if (!base.notes.get(e.getKey()).strip().equals(e.getValue().strip())) changedOthers.add(e.getKey());
        }
        r.lines.add("=== primer pilot (" + VERSION + ") ===");
        if (primer == null || primer.isBlank()) {
            r.lines.add("  PRIMER NOT WRITTEN: the notebook has no note named '" + KEY + "' after the stage." + (newKeys.isEmpty() ? "" : " New notes it does have: " + newKeys + " (the letter may be under another key)."));
            r.lines.add("  No variant snapshots were written.");
            return r;
        }
        r.written = true; r.primer = primer;
        String t = stamp.length() >= 6 ? stamp.substring(stamp.length() - 6) : stamp;
        r.pId = base.id() + "-P" + t; r.rpId = base.id() + "-RP" + t;

        LinkedHashMap<String, String> pNotes = new LinkedHashMap<>(); pNotes.put(KEY, primer);
        LinkedHashMap<String, String> rpNotes = new LinkedHashMap<>(); rpNotes.put(KEY, primer);
        for (var e : base.notes.entrySet()) if (!e.getKey().equals(KEY)) rpNotes.put(e.getKey(), e.getValue());

        String baseRules = rules(reference, base.notes), pRules = rules(reference, pNotes), rpRules = rules(reference, rpNotes);
        JSONObject how = new JSONObject().put("run", runLog).put("cycle", cycles).put("instruction", VERSION);
        KnowledgeSnapshot p = KnowledgeSnapshot.derived(base, "P" + t, "primer-only", pNotes, how, pRules);
        KnowledgeSnapshot rp = KnowledgeSnapshot.derived(base, "RP" + t, "primer+notes", rpNotes, how, rpRules);
        r.pFile = dir.resolve(p.id() + ".json"); r.rpFile = dir.resolve(rp.id() + ".json");
        p.write(r.pFile); rp.write(r.rpFile);

        int paragraphs = (int) java.util.Arrays.stream(primer.split("\\n\\s*\\n")).filter(s -> !s.isBlank()).count();
        r.lines.add("  the letter: " + primer.length() + " characters, " + paragraphs + " paragraphs (the original notes: " + base.notes.values().stream().mapToInt(String::length).sum() + " characters in " + base.notes.size() + " notes)");
        r.lines.add("  policy rules held (of 8, keyword probes): original notes " + baseRules + "; the letter alone " + pRules + "; letter + original notes " + rpRules);
        List<String> dangling = new ArrayList<>();
        for (String k : base.notes.keySet()) if (!k.equals(KEY) && primer.toLowerCase().contains(k.toLowerCase())) dangling.add(k);
        if (!dangling.isEmpty()) r.lines.add("  WARNING — the letter refers to note(s) " + dangling + ", which the primer-only variant does not contain: in that variant those references dangle.");
        if (!changedOthers.isEmpty() || !newKeys.isEmpty()) r.lines.add("  the instance also changed other notes (changed: " + changedOthers + ", new: " + newKeys + "); the variants use the ORIGINAL notes, so the letter is the only difference.");
        r.lines.add("  variant P  (primer alone):            " + r.pFile.getFileName() + "   [" + p.id() + "]");
        r.lines.add("  variant RP (primer + original notes): " + r.rpFile.getFileName() + "   [" + rp.id() + "]");
        r.lines.add("  use them like any snapshot:  --arm=notebook --from-knowledge=<file>   (the original is the third condition)");
        r.lines.add("--- the letter, as written ---");
        r.lines.add(primer.strip());
        r.lines.add("--- end of the letter ---");
        return r;
    }

    private static String rules(DomainReference reference, Map<String, String> notes) {
        var res = reference.assess("rules", AssessmentMetrics.asConcepts(notes));
        return res.matched.size() + "/" + res.total();
    }
}
