package faber.devl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The release artifact: the agent's playbook as it stood at a moment, with where it came from. A fresh instance is made from it and from the environment — which is NOT in the snapshot:
 * the curated domain model and the manuals are provisioned by the environment, and the snapshot only records which model it was learned against (the source file and its hash), so that
 * a mismatch is a warning and not a surprise.
 *
 * Two kinds: the playbook written at the end of learning, and a CANDIDATE — the playbook revised by an assessed instance in a consolidation, with its lineage (parent, round, what was
 * assessed before it and how it went, whether the gate passed). What is assessed is a candidate, never a mind (LIFECYCLE.md).
 */
public final class PlaybookSnapshot {
    public static final String FORMAT = "faber-playbook/1";

    public final JSONObject manifest; private final String text;

    private PlaybookSnapshot(JSONObject manifest, String text) { this.manifest = manifest; this.text = text; }

    public String id() { return manifest.optString("id"); }
    public String text() { return text; }
    public JSONArray learningHistory() { return manifest.optJSONArray("learning"); }
    public JSONObject environment() { return manifest.optJSONObject("environment"); }
    public boolean isCandidate() { return manifest.has("lineage"); }

    public static String sha256(String s) {
        try { StringBuilder sb = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b)); return sb.toString(); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static JSONObject playbookRecord(String text) { return new JSONObject().put("chars", text.length()).put("sha256", sha256(text)); }

    /** The playbook written at the end of learning, against a curated model alone (no task library recorded). */
    public static PlaybookSnapshot ofLearning(String id, String text, JSONObject options, JSONObject source, JSONArray learning, JSONObject domainModelRecord, String caseId, String caseTitle) {
        return ofLearning(id, text, options, source, learning, domainModelRecord, null, caseId, caseTitle);
    }

    /** The playbook written at the end of learning, with the records of the environment it was learned against: the curated model and the task library (either may be null). */
    public static PlaybookSnapshot ofLearning(String id, String text, JSONObject options, JSONObject source, JSONArray learning, JSONObject domainModelRecord, JSONObject taskLibraryRecord, String caseId, String caseTitle) {
        JSONObject m = new JSONObject().put("id", id).put("built_at", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()).put("case", caseId).put("domain", caseTitle)
                .put("materials", options.optString("materials", "?")).put("source", source).put("options", options).put("learning", learning == null ? new JSONArray() : learning).put("playbook", playbookRecord(text));
        if (domainModelRecord != null || taskLibraryRecord != null) {
            JSONObject env = new JSONObject(); if (domainModelRecord != null) env.put("domain_model", domainModelRecord); if (taskLibraryRecord != null) env.put("task_library", taskLibraryRecord);
            m.put("environment", env);
        }
        return new PlaybookSnapshot(m, text);
    }

    /** A candidate: the parent's playbook as an assessed instance revised it. The environment record is the parent's: it is the model the playbook was learned against. */
    public static PlaybookSnapshot candidateOf(PlaybookSnapshot parent, String id, String text, JSONObject source, JSONObject lineage, JSONArray learning) {
        JSONObject m = new JSONObject(parent.manifest.toString()); m.put("id", id).put("built_at", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
                .put("source", source).put("lineage", lineage).put("learning", learning).put("playbook", playbookRecord(text));
        return new PlaybookSnapshot(m, text);
    }

    public JSONObject toJson() { return new JSONObject().put("format", FORMAT).put("manifest", manifest).put("playbook", text); }

    public void write(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, toJson().toString(2), StandardCharsets.UTF_8);
    }

    public static PlaybookSnapshot read(Path file) throws IOException { return fromJson(new JSONObject(Files.readString(file, StandardCharsets.UTF_8))); }

    public static PlaybookSnapshot fromJson(JSONObject j) {
        return new PlaybookSnapshot(j.optJSONObject("manifest") == null ? new JSONObject() : j.getJSONObject("manifest"), j.optString("playbook", ""));
    }

    private String format = FORMAT;
    /** For a file whose format field is wrong, kept so that errors() can say so. */
    public static PlaybookSnapshot readChecked(Path file) throws IOException {
        JSONObject j = new JSONObject(Files.readString(file, StandardCharsets.UTF_8)); PlaybookSnapshot s = fromJson(j); s.format = j.optString("format", ""); return s;
    }

    /** What makes a snapshot impossible to instantiate from. */
    public List<String> errors() {
        List<String> out = new ArrayList<>();
        if (!format.equals(FORMAT)) out.add("unknown format '" + format + "' (expected " + FORMAT + ")");
        if (id().isEmpty()) out.add("no id in the manifest");
        if (text.isBlank()) out.add("the playbook is empty");
        return out;
    }

    /** True if the playbook text no longer matches the checksum written with it: the file was edited by hand. */
    public boolean edited() { JSONObject r = manifest.optJSONObject("playbook"); return r == null || !r.optString("sha256").equals(sha256(text)); }

    /** What differs between how this playbook was written and how this run is made. Printed and recorded, never refused. */
    public List<String> warnings(String materialsNow) {
        List<String> w = new ArrayList<>();
        String then = manifest.optString("materials", "?");
        if (!then.equals(materialsNow)) w.add("built under materials version " + then + ", running under " + materialsNow + ": the texts the playbook was written against (the manuals, the stage messages) may have changed");
        if (edited()) w.add("the playbook does not match the checksum written with it: it was edited after it was written");
        return w;
    }

    /** The case this playbook was learned for, or null if the file does not say (snapshots written before cases were separated from the framework). */
    public String caseId() { return manifest.has("case") ? manifest.getString("case") : null; }

    /** Why this playbook cannot be used in a run of the given case, or null if it can: a playbook is knowledge of one job. A snapshot that does not name its case is let through (the runner warns). */
    public String caseMismatch(String runCaseId) { return caseMismatch(runCaseId, java.util.List.of()); }

    /** The same, accepting also the ids the project was known by before (project.json: previousIds), so that the playbooks of a renamed project are not refused. */
    public String caseMismatch(String runCaseId, java.util.Collection<String> alsoKnownAs) {
        String c = caseId();
        return c == null || c.equals(runCaseId) || alsoKnownAs.contains(c) ? null : "this playbook was learned for the project '" + c + "' and this run is for the project '" + runCaseId + "': a playbook is knowledge of one job";
    }

    /** A short hash is a prefix: a snapshot rebuilt from a log alone carries the first 12 digits of the model's source hash (the log holds no more), and is compared as such. */
    public static boolean sameHash(String a, String b) { return a.equals(b) || (a.length() < 64 && !a.isEmpty() && b.startsWith(a)) || (b.length() < 64 && !b.isEmpty() && a.startsWith(b)); }

    /** What differs between the curated model this was learned against and the one a run provides (null for none). */
    public List<String> environmentWarnings(JSONObject nowDomainModel) {
        List<String> w = new ArrayList<>(); JSONObject env = environment(), then = env == null ? null : env.optJSONObject("domain_model");
        if (then == null && nowDomainModel == null) return w;
        if (then == null) w.add("learned without a recorded domain model; this run provides '" + nowDomainModel.optString("id") + "'");
        else if (nowDomainModel == null) w.add("learned against the curated domain model '" + then.optString("id") + "', which this run does not provide: a playbook that refers to it points at nothing");
        else if (!sameHash(then.optString("sha256"), nowDomainModel.optString("sha256")))
            w.add("learned against the curated domain model '" + then.optString("id") + "' (source hash " + then.optString("sha256").substring(0, Math.min(12, then.optString("sha256").length()))
                  + "), running against another one (hash " + nowDomainModel.optString("sha256").substring(0, Math.min(12, nowDomainModel.optString("sha256").length())) + "): the model has changed since");
        return w;
    }

    /** What differs between the task library this was learned against and the one a run provides: a playbook from before the library existed says so, and the run is not refused. */
    public List<String> environmentWarnings(JSONObject nowDomainModel, JSONObject nowTaskLibrary) {
        List<String> w = environmentWarnings(nowDomainModel); JSONObject env = environment(), then = env == null ? null : env.optJSONObject("task_library");
        if (then == null && nowTaskLibrary == null) return w;
        if (then == null) w.add("learned without a task library (it predates it or was learned without one); this run provides '" + nowTaskLibrary.optString("id") + "': the practices in it are new to this playbook");
        else if (nowTaskLibrary == null) w.add("learned against the task library '" + then.optString("id") + "', which this run does not provide: a playbook that refers to it points at nothing");
        else if (!sameHash(then.optString("sha256"), nowTaskLibrary.optString("sha256")))
            w.add("learned against the task library '" + then.optString("id") + "' (hash " + then.optString("sha256").substring(0, Math.min(12, then.optString("sha256").length())) + "), running against another one (hash "
                  + nowTaskLibrary.optString("sha256").substring(0, Math.min(12, nowTaskLibrary.optString("sha256").length())) + "): the library has changed since");
        return w;
    }

    public String provenanceLine() {
        JSONObject src = manifest.optJSONObject("source") == null ? new JSONObject() : manifest.getJSONObject("source"), env = environment(), dm = env == null ? null : env.optJSONObject("domain_model"), tl = env == null ? null : env.optJSONObject("task_library"), lin = manifest.optJSONObject("lineage");
        return id() + ": playbook of " + text.length() + " characters; built " + manifest.optString("built_at") + " from " + src.optString("run", "?") + " after " + src.optString("snapshot_after", "?")
             + " (cycle " + src.optInt("cycle") + (src.optBoolean("reconstructed_from_log") ? ", reconstructed from the log" : "") + ")"
             + (manifest.optJSONObject("options") != null && manifest.getJSONObject("options").has("llm_model") ? "; written by the model " + manifest.getJSONObject("options").getString("llm_model") : "")
             + (dm == null ? "" : "; learned against the curated model '" + dm.optString("id") + "' (hash " + dm.optString("sha256").substring(0, Math.min(12, dm.optString("sha256").length())) + ")")
             + (tl == null ? "" : " and the task library '" + tl.optString("id") + "' (hash " + tl.optString("sha256").substring(0, Math.min(12, tl.optString("sha256").length())) + ")")
             + (lin == null ? "" : "; CANDIDATE round " + lin.optInt("round") + " from " + lin.optString("parent") + " (" + lin.optString("instruction") + ")");
    }
}
