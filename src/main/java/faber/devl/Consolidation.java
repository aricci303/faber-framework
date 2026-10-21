package faber.devl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Consolidation after assessment (--consolidate).
 *
 * An assessment is not only a measurement: an agent that is audited learns from it, and what it learns is lost unless it is written into the playbook before the mind is dropped. So after the
 * last assessment the agent is sent back to the learning phase for a moment, and asked to bring its playbook up to date (Playbook.consolidationRequest). The harness measures what the
 * revision holds and derives a RELEASE CANDIDATE: a playbook snapshot with its lineage (parent, round, what was assessed and how it went). The candidate is then assessed in its turn, by
 * a fresh instance — what is assessed is a candidate, never a mind (LIFECYCLE.md).
 *
 * What is checked is not that the playbook holds the rules (it must not copy them: they are the model's) but that it does not state as current a value the model no longer states; the
 * duplication of rules is reported, not failed. The gate is advisory: it says what to read before promoting, and enforces nothing.
 */
public final class Consolidation {
    private Consolidation() {}

    public static final String VERSION = "consolidation-v1";
    public static final String LABEL = "consolidation";

    /** The round: how many consolidations lie in the line of descent, counting this one. */
    public static int roundOf(PlaybookSnapshot parent) {
        JSONObject l = parent.manifest.optJSONObject("lineage");
        return l == null ? 1 : l.optInt("round") + 1;
    }

    /** S-parent -> S-parent-c1-123456: the round, and the time of the run that wrote it, so that two candidates of one parent never share an id. */
    public static String candidateId(PlaybookSnapshot parent, String stamp) { return parent.id() + "-c" + roundOf(parent) + "-" + (stamp.length() >= 6 ? stamp.substring(stamp.length() - 6) : stamp); }

    public static final class Result {
        public boolean playbookRevised, candidateWritten; public Path file; public String candidateId; public final List<String> lines = new ArrayList<>(), warnings = new ArrayList<>();
    }

    /**
     * before, after: the playbook when the consolidation began and as the agent left it (either may be null). assessed: the result of each assessment stage. changedApplies: the
     * policy-change assessment was part of the run (the world is at version 2). modelTexts: the rules the model states in the version in force. endCycle: the cumulative cycle of the
     * run at which the consolidation ended; cycles: the consolidation stage's own count.
     */
    public static Result finish(PlaybookSnapshot parent, String before, String after, Map<String, String> assessed, boolean changedApplies, Path dir, String stamp, String runLog, int cycles, int endCycle,
                         RuleReference reference, List<String> modelTexts, String caseId) throws IOException {
        return finish(parent, before, after, assessed, changedApplies, dir, stamp, runLog, cycles, endCycle, reference, modelTexts, caseId, null);
    }

    /**
     * The same, with what the playbook artifact recorded: whether the assessed object was left alone (it is frozen while the agent is assessed, so `before` is the parent's text unless something broke that) and what
     * the instance tried. The candidate is also compared with the PARENT's text (advisory, separate from the gate), and the lineage records both.
     */
    public static Result finish(PlaybookSnapshot parent, String before, String after, Map<String, String> assessed, boolean changedApplies, Path dir, String stamp, String runLog, int cycles, int endCycle,
                         RuleReference reference, List<String> modelTexts, String caseId, PlaybookArtifact.Summary watch) throws IOException {
        Result r = new Result(); final String world = changedApplies ? "2" : "1";
        boolean had = before != null && !before.isBlank(), has = after != null && !after.isBlank();
        r.playbookRevised = has && (!had || !before.strip().equals(after.strip()));
        Homes.Result hb = Homes.measure(reference, had ? before : null, modelTexts, world), ha = Homes.measure(reference, has ? after : null, modelTexts, world);
        r.lines.add("=== consolidation (" + VERSION + "): the playbook after the assessments ===");
        r.lines.add("  playbook: " + (!has ? "NONE — the playbook is empty after the consolidation"
                : (had ? before.length() + " -> " : "none -> ") + after.length() + " characters; " + (!had ? "written now" : r.playbookRevised ? "REVISED (" + (after.length() - before.length() >= 0 ? "+" : "") + (after.length() - before.length()) + " characters)" : "UNCHANGED")));
        r.lines.addAll(Homes.lines("before", hb)); r.lines.addAll(Homes.lines("after", ha));
        if (has) r.lines.add("  refers to the domain model: " + (Playbook.refersToModel(after) ? "yes" : "NO") + ", to the manuals: " + (Playbook.refersToManuals(after) ? "yes" : "NO") + ", to the task library: " + (Playbook.refersToLibrary(after) ? "yes" : "NO"));
        if (!has) r.warnings.add("no playbook at the end: the candidate carries none");
        String parentText = parent.text();
        if (has && parentText != null && !parentText.isBlank() && after.length() * 2 < parentText.length()) r.warnings.add("the candidate is less than half the length of its PARENT (" + parentText.length() + " -> " + after.length() + "): check what was dropped");
        if (has && had && after.length() * 2 < before.length()) r.warnings.add("the revised playbook is less than half the length of the one it replaced (" + before.length() + " -> " + after.length() + "): check what was dropped");
        if (has && !ha.oldAsserted.isEmpty()) r.warnings.add("the playbook still states old value(s) of " + Homes.words(ha.oldAsserted) + " as current, which the curated model no longer states");
        for (String w : r.warnings) r.lines.add("  WARNING — " + w);
        if (has && !ha.restated.isEmpty()) r.lines.add("  note — the playbook restates rule(s) the model holds: " + Homes.words(ha.restated) + " (a second home: it goes stale when the model changes)");
        if (has && !ha.oldAnnotated.isEmpty()) r.lines.add("  note — old values appear only as annotation or history (not as current): " + Homes.words(ha.oldAnnotated) + " — a keyword reading: read them");
        boolean gate = has && ha.oldAsserted.isEmpty();
        r.lines.add("  advisory gate for promotion: " + (gate ? "PASS (the playbook states no value as current that the curated model no longer states)" : "FAIL — see the warnings; promote only after reading the diff (tools/playbook_diff.py)"));

        PlaybookWatch.Comparison cmp = has ? PlaybookWatch.compare(parentText, after) : null;
        if (watch != null) r.lines.addAll(watch.lines(parent.id()));
        if (cmp != null) r.lines.addAll(cmp.lines());

        // the release candidate: the playbook as the agent left it, with its lineage
        int round = roundOf(parent); String id = candidateId(parent, stamp); r.candidateId = id;
        JSONArray history = parent.learningHistory() == null ? new JSONArray() : new JSONArray(parent.learningHistory().toString());
        history.put(new JSONObject().put("stage", LABEL).put("cycles", cycles));
        JSONObject assessedJson = new JSONObject(); for (var e : assessed.entrySet()) assessedJson.put(e.getKey(), e.getValue());
        PlaybookSnapshot cand = PlaybookSnapshot.candidateOf(parent, id, has ? after : "",
                new JSONObject().put("run", runLog).put("snapshot_after", LABEL).put("cycle", endCycle).put("reconstructed_from_log", false).put("kind", "candidate"),
                new JSONObject().put("parent", parent.id()).put("round", round).put("produced_by", runLog).put("instruction", VERSION).put("assessed", assessedJson)
                        .put("playbook_revised", r.playbookRevised).put("advisory_gate", gate)
                        .put("revised_with_model", DevlRunner.llmModel).put("playbook_during_assessment", watch == null ? JSONObject.NULL : watch.json()).put("parent_comparison", cmp == null ? JSONObject.NULL : cmp.json()), history);
        r.file = dir.resolve("devl-playbook-" + stamp + "-c" + round + ".json");
        cand.write(r.file); r.candidateWritten = true;
        r.lines.add("  release candidate written: " + r.file.getFileName() + " (" + id + ", round " + round + ", from " + parent.id() + "; assessed before it: " + assessed + ")");
        r.lines.add("  next: assess it with FRESH instances (DevlAssessmentMain --project=" + caseId + " --playbook=" + r.file.getFileName() + "), and read what it changed first: python3 tools/playbook_diff.py <parent> " + r.file.getFileName());
        return r;
    }
}
