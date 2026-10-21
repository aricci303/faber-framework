package faber.devl;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * The playbook: what the agent writes down for the future instances of itself — its own practice — as an ARTIFACT of the environment, with a manual, observable state and operations, like the domain
 * model and the task library, and unlike them AUTHORED BY THE AGENT: the one artifact here that is the agent's own (KNOWLEDGE-LAYERS.md, K6).
 *
 * Until materials 8 it was a note under a key in the agent's notebook. The runs showed what that costs: it shared a key space with the agent's scratch notes, an instance could overwrite it in the
 * middle of an assessment (9 of the first 28 assessment logs), and it had no manual, so the principles that apply to it (refer, do not copy; lasting practice; only the playbook is carried) could
 * only be said in messages read once. As an artifact it has a place to say them, a state the agent sees at every cycle, and a PHASE POLICY — LIFECYCLE.md, refinement 3: assessment must not
 * contaminate the release.
 *
 * FORM: text first. The playbook is a narrative the agent writes in its own words; there are no sections or typed references (a later comparison of forms may add them).
 * OPERATIONS (the agent's): read, lineage, diff, revise(text, reason), restore. revise REPLACES the whole text; the previous version is kept for restore; and at the moment of writing it says what the new
 * text has lost of the old (its length, and which markers of practice the old one had and the new one has not: PlaybookWatch) — a check on the agent's own text and not on the domain.
 * POLICY (the harness's): the playbook is revised in the learning phase and in the consolidation that follows an assessment; it is FROZEN while the agent is assessed or at work. A refused revision changes
 * nothing, tells the agent where to put what it found, and is recorded (the harness reads attempts and their text, apart from what the agent sees).
 * OBSERVABLE: chars, revisions (this session), frozen, parent (the playbook the agent was made from, or "none").
 *
 * What it deliberately does not show the agent: which versions of the model and the library it was learned against. That would tell an assessment's instance that the world has moved.
 */
public final class PlaybookArtifact extends Artifact {

    public static final String type = "Playbook";
    /** The id of the playbook artifact in every phase. */
    public static final String ID = "playbook-01";

    /** An attempt to revise: where, whether it was accepted, the sizes, and why — what the harness keeps of it. */
    public static final class Attempt {
        public final String stage, reason; public final boolean accepted; public final int before, after;
        Attempt(String stage, boolean accepted, int before, int after, String reason) { this.stage = stage; this.accepted = accepted; this.before = before; this.after = after; this.reason = reason; }
    }

    private final String parentId, parentText; private final int round;
    private String text, previous; private boolean frozen; private String stage = ""; private int revisions;
    private final List<Attempt> attempts = new ArrayList<>();
    private final List<String> refusedTexts = new ArrayList<>();

    /** parentId and initialText are null/empty in a learning run from scratch; round is the round of the candidate this playbook is the parent of (0 when there is none). */
    public PlaybookArtifact(String id, Workspace workspace, String parentId, int round, String initialText) {
        super(id, type, workspace);
        this.parentId = parentId; this.round = round; this.parentText = initialText == null ? "" : initialText; this.text = this.parentText;
        refreshProps();
    }

    private void refreshProps() {
        currentObsProperties().put("chars", text.length()); currentObsProperties().put("revisions", revisions); currentObsProperties().put("frozen", frozen); currentObsProperties().put("parent", parentId == null ? "none" : parentId);
    }

    // ---- the harness's side: the phase policy and the record. Not operations; the agent cannot call them. ---------------------------------------------------------------------------

    /** Sets the policy for the stage that starts: frozen while the agent is assessed or at work, open in learning and in the consolidation. */
    public synchronized void setPolicy(boolean frozen, String stageLabel) { this.frozen = frozen; this.stage = stageLabel; refreshProps(); }
    public synchronized String text() { return text; }
    public synchronized String parentText() { return parentText; }
    public synchronized String parentId() { return parentId; }
    public synchronized int revisionsThisSession() { return revisions; }
    public synchronized List<Attempt> attempts() { return new ArrayList<>(attempts); }
    public synchronized List<String> refusedTexts() { return new ArrayList<>(refusedTexts); }
    public synchronized boolean isFrozen() { return frozen; }

    /** What the harness reports about the playbook during the assessments: was the assessed object left alone, and what did the instance try. */
    public static final class Summary {
        public final int parentChars, refused, accepted, currentChars; public final List<Attempt> refusedAttempts; public final boolean unchanged;
        public final Boolean startedFromParent;           // set when a consolidation is about to start
        Summary(int parentChars, int refused, int accepted, int currentChars, List<Attempt> refusedAttempts, boolean unchanged, Boolean startedFromParent) {
            this.parentChars = parentChars; this.refused = refused; this.accepted = accepted; this.currentChars = currentChars; this.refusedAttempts = refusedAttempts; this.unchanged = unchanged; this.startedFromParent = startedFromParent;
        }
        public JSONObject json() {
            JSONArray a = new JSONArray(); for (Attempt t : refusedAttempts) a.put(new JSONObject().put("stage", t.stage).put("chars", t.after).put("reason", t.reason));
            JSONObject j = new JSONObject().put("parent_chars", parentChars).put("revisions_refused", refused).put("revisions_accepted_outside_learning", accepted).put("unchanged", unchanged).put("attempts_refused", a);
            if (startedFromParent != null) j.put("started_from_parent", startedFromParent);
            return j;
        }
        public List<String> lines(String parentId) {
            List<String> l = new ArrayList<>();
            l.add("=== the playbook during the assessment (was the assessed object left alone?) ===");
            l.add("  the assessed playbook " + parentId + " (" + parentChars + " chars) was frozen while the instance was assessed. " + (refused == 0 ? "The instance did not try to revise it." : "The instance tried to revise it " + refused + " time(s); each was refused and changed nothing."));
            for (Attempt t : refusedAttempts) l.add("    refused in " + t.stage + ": a text of " + t.after + " chars; reason given: \u201c" + (t.reason.length() > 140 ? t.reason.substring(0, 140) + "\u2026" : t.reason) + "\u201d");
            if (!unchanged) l.add("  WARNING \u2014 the playbook is not the parent's text at the end of the assessments (" + currentChars + " chars): something revised it while it was meant to be frozen.");
            if (startedFromParent != null) l.add("  the consolidation started from: " + (startedFromParent ? "THE PARENT'S TEXT" : "A TEXT THAT IS NOT THE PARENT'S (" + currentChars + " chars)") + ".");
            return l;
        }
    }

    public synchronized Summary summary(boolean atConsolidation) {
        List<Attempt> ref = new ArrayList<>(); int acc = 0;
        for (Attempt t : attempts) { if (!t.accepted) ref.add(t); else if (t.stage != null && !t.stage.startsWith("stage") && !t.stage.equals(Consolidation.LABEL) && !t.stage.startsWith("debrief")) acc++; }
        boolean same = text.strip().equals(parentText.strip());
        return new Summary(parentText.length(), ref.size(), acc, text.length(), ref, same, atConsolidation ? same : null);
    }

    // ---- the agent's operations -----------------------------------------------------------------------------------------------------------------------------------------------------

    @Override
    public synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        switch (op) {
            case "read": return List.of(text.isBlank() ? "The playbook is empty: nothing has been written to it yet." : text);
            case "lineage": {
                StringBuilder sb = new StringBuilder();
                if (parentId == null || parentText.isBlank()) sb.append("You are writing this playbook for the first time.");
                else sb.append("This playbook was last written by an earlier instance of you: playbook ").append(parentId).append(round > 0 ? " (round " + round + ")" : "").append(".");
                sb.append(" In this session it has been revised ").append(revisions).append(" time(s). It is revised in the learning phase and in the consolidation that follows an assessment; it is frozen while you are assessed or at work. Frozen now: ").append(frozen ? "yes" : "no").append(".");
                return List.of(sb.toString());
            }
            case "diff": {
                String base = !parentText.isBlank() ? parentText : previous; String what = !parentText.isBlank() ? "the playbook you were made from (" + parentId + ")" : "the previous version";
                if (base == null || base.isBlank()) return List.of("There is no earlier version to compare with: this is the first text of the playbook.");
                var parts = PlaybookWatch.compareUnits(base, text); List<String> kept = parts.get(0), dropped = parts.get(1);
                StringBuilder sb = new StringBuilder("Against ").append(what).append(": you keep ").append(kept.size()).append(" of its ").append(kept.size() + dropped.size()).append(" lines; this version has ").append(PlaybookWatch.units(text).size()).append(" lines in all.");
                int n = 0; for (String d : dropped) { if (n++ >= 6) { sb.append("\n  … and ").append(dropped.size() - 6).append(" more dropped"); break; } sb.append("\n  dropped: \u201c").append(d.length() > 110 ? d.substring(0, 110) + "\u2026" : d).append("\u201d"); }
                if (dropped.isEmpty()) sb.append(" Nothing of it is dropped.");
                return List.of(sb.toString());
            }
            case "revise": {
                String neu = p.optString("text", ""), reason = p.optString("reason", "").trim();
                if (neu.isBlank()) throw new IllegalArgumentException("missing parameter 'text': revise needs the whole new text of the playbook; nothing was changed (restore() brings back the previous version after a revision)");
                if (reason.isEmpty()) throw new IllegalArgumentException("missing parameter 'reason': say in a sentence why you are revising it; nothing was changed");
                if (frozen) {
                    attempts.add(new Attempt(stage, false, text.length(), neu.length(), reason)); refusedTexts.add(neu);
                    throw new IllegalArgumentException("the playbook is frozen now: it is revised in the learning phase and in the consolidation that follows an assessment, not while you are assessed or at work. Nothing was changed. "
                            + "What you find that is worth keeping, say in your report; use your notebook for working notes.");
                }
                String old = text; previous = old; text = neu; revisions++; attempts.add(new Attempt(stage, true, old.length(), neu.length(), reason)); refreshProps();
                StringBuilder sb = new StringBuilder("Revised: ").append(old.isBlank() ? "written now" : old.length() + " chars -> " + neu.length() + " chars").append(old.isBlank() ? " (" + neu.length() + " chars)" : "").append(". The previous version is kept: restore() brings it back.");
                if (!old.isBlank() && neu.length() * 2 < old.length()) sb.append(" WARNING: the new text is less than half the length of the one it replaced; revise replaces the whole playbook, so it has to contain everything you want to keep.");
                List<String> lost = new ArrayList<>(); List<String> inNew = PlaybookWatch.markersOf(neu); for (String m : PlaybookWatch.markersOf(old)) if (!inNew.contains(m)) lost.add(m);
                if (!lost.isEmpty()) sb.append(" Note: the previous version spoke of ").append(String.join(", ", lost)).append("; this one does not. If that is intended there is nothing to do.");
                return List.of(sb.toString());
            }
            case "restore": {
                if (frozen) throw new IllegalArgumentException("the playbook is frozen now: it is revised in the learning phase and in the consolidation that follows an assessment. Nothing was changed.");
                if (previous == null) throw new IllegalArgumentException("no previous version: the playbook has not been revised in this session");
                String cur = text; text = previous; previous = cur; refreshProps();
                return List.of("Restored the previous version (" + text.length() + " chars). restore() again swaps back.");
            }
            default: throw new IllegalArgumentException("unknown operation: " + op + " (the playbook can be read, compared (diff), revised (revise) and restored (restore))");
        }
    }

    public static Manual manual() {
        return new Manual(type,
                "your own playbook: the practice you write down for the future instances of yourself — how you go about the job, how you check your work, when you escalate, what you report, how the environment behaves as you have found it, "
                + "what you are unsure of and how you treat your sources when they disagree. It is what a fresh instance of you starts from, together with the manuals and the sources of your environment, and it is the only thing of yours that is carried: "
                + "your notebook and the notes in it are not. You write it, in your own words; it is the one artifact here that is yours",
                null, null,
                List.of(new Manual.Param("chars", "the length of the playbook"), new Manual.Param("revisions", "how many times it has been revised in this session"), new Manual.Param("frozen", "whether it can be revised now"),
                        new Manual.Param("parent", "the playbook you were made from, or none")),
                List.of(),
                List.of(
                    new Manual.Operation("read()", "the playbook, as it is now", List.of(new Manual.Param("text", "the playbook"))),
                    new Manual.Operation("lineage()", "where this playbook comes from, how often it was revised in this session, and whether it can be revised now", List.of(new Manual.Param("lineage", "a short statement"))),
                    new Manual.Operation("diff()", "what you keep and what you drop, against the playbook you were made from (or, if you are writing it for the first time, against the previous version)", List.of(new Manual.Param("diff", "lines kept and dropped"))),
                    new Manual.Operation("revise(text, reason)", "replace the whole playbook by a new text, with the reason in a sentence; it has to contain everything you want to keep. The previous version is kept. The answer says what the new text has lost of the old one",
                            List.of(new Manual.Param("status", "the new length, and what was lost"))),
                    new Manual.Operation("restore()", "bring back the previous version (once more, and it swaps back)", List.of(new Manual.Param("status", "the restored length")))),
                "The playbook is revised in the learning phase and in the consolidation that follows an assessment. While you are assessed or at work it is frozen: a revision is refused and changes nothing; say what you find in your report, and use your notebook for working notes. "
                + "Refer to the sources of your environment — the domain model, the task library, the manuals — and do not copy them: a copy is a second source that nobody maintains. Write lasting practice for a reader who was not here: no account of what happened to you or when.");
    }
}
