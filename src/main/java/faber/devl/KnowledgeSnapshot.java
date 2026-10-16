package faber.devl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A knowledge snapshot: what an agent that has learned its job had WRITTEN DOWN — its Notebook — plus a manifest saying where it came from. It is the release artifact of the
 * learning phase: an agent can be instantiated from it, as often as wanted and in different contexts, without repeating learning; assessments run on instances,
 * and the snapshot itself never changes. (Not the agent's mind — its goals, intentions and last state of mind — which is a larger and later step.)
 *
 * The notes are the learned knowledge, as inspectable text: a snapshot can be diffed, reviewed, even edited by a person (the manifest then says it was edited, since the
 * checksum no longer matches). Two writers produce this format: the harness, at the end of a learning run, and tools/snapshot_from_log.py, which rebuilds one from
 * a log by replaying the Notebook's operations. Both must agree on the checksum, so it is defined byte by byte (see checksum()).
 */
public final class KnowledgeSnapshot {
    public static final String FORMAT = "faber-knowledge-snapshot/1";

    public final JSONObject manifest;
    public final LinkedHashMap<String, String> notes;     // in the Notebook's own order

    private KnowledgeSnapshot(JSONObject manifest, LinkedHashMap<String, String> notes) { this.manifest = manifest; this.notes = notes; }

    /**
     * SHA-256, in hex, over the notes in the order of their keys' UTF-8 bytes, each entry being key, a zero byte, content, a zero byte (all UTF-8). Ordering by UTF-8
     * bytes — not by Java's UTF-16 order — so that the Python writer, which sorts the same bytes, gets the same answer for any text.
     */
    public static String checksum(Map<String, String> notes) {
        try {
            List<String> keys = new ArrayList<>(notes.keySet());
            keys.sort((a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String k : keys) {
                md.update(k.getBytes(StandardCharsets.UTF_8)); md.update((byte) 0);
                md.update(notes.get(k).getBytes(StandardCharsets.UTF_8)); md.update((byte) 0);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Builds a snapshot from live state. options: the run's options (strings). learning: one object per learning stage (and debrief). unsupported: ids of artifacts that were NOT captured. */
    public static KnowledgeSnapshot ofLive(String id, Map<String, String> notes, JSONObject options, JSONObject source, JSONArray learning, String retention, List<String> unsupported) {
        JSONObject m = new JSONObject();
        m.put("id", id);
        m.put("built_at", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        m.put("domain", "expense approval at Halden & Co.");
        m.put("source", source);
        m.put("options", options);
        m.put("learning", learning);                  // called "development" in snapshots written before the phase was renamed; see learningHistory()
        if (retention != null) m.put("retention_at_snapshot", retention);
        int chars = 0; for (String v : notes.values()) chars += v.length();
        m.put("notes", new JSONObject().put("count", notes.size()).put("chars", chars).put("sha256", checksum(notes)));
        m.put("unsupported_artifacts", new JSONArray(unsupported));
        return new KnowledgeSnapshot(m, new LinkedHashMap<>(notes));
    }

    /**
     * A variant derived from another snapshot — same learning history and options, other notes — with a record of what it was derived from. The variant's checksum is its own, so it
     * is a snapshot in every respect (loadable, not "edited"); derived_from says where it came from and how: how.run, how.cycle, how.instruction.
     */
    public static KnowledgeSnapshot derived(KnowledgeSnapshot base, String suffix, String variant, Map<String, String> notes, JSONObject how, String retention) {
        JSONObject m = new JSONObject(base.manifest.toString());
        m.put("id", base.id() + "-" + suffix);
        m.put("built_at", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        m.put("source", new JSONObject().put("run", how.optString("run", "?")).put("snapshot_after", how.optString("snapshot_after", "primer-pilot")).put("cycle", how.optInt("cycle", 0)).put("reconstructed_from_log", false).put("kind", "derived"));
        int chars = 0; for (String v : notes.values()) chars += v.length();
        m.put("notes", new JSONObject().put("count", notes.size()).put("chars", chars).put("sha256", checksum(notes)));
        m.put("derived_from", new JSONObject(how.toString()).put("id", base.id()).put("variant", variant));
        if (retention != null) m.put("retention_at_snapshot", retention); else m.remove("retention_at_snapshot");
        return new KnowledgeSnapshot(m, new LinkedHashMap<>(notes));
    }

    public JSONObject toJson() {
        JSONArray arr = new JSONArray();
        for (var e : notes.entrySet()) arr.put(new JSONObject().put("key", e.getKey()).put("content", e.getValue()));
        return new JSONObject().put("format", FORMAT).put("manifest", manifest).put("notebook", new JSONObject().put("id", "notebook-01").put("notes", arr));
    }

    public void write(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, toJson().toString(2), StandardCharsets.UTF_8);
    }

    public static KnowledgeSnapshot read(Path file) throws IOException {
        JSONObject j;
        try { j = new JSONObject(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (org.json.JSONException e) { throw new IOException("not a valid knowledge snapshot (not JSON): " + e.getMessage()); }
        return fromJson(j);
    }

    public static KnowledgeSnapshot fromJson(JSONObject j) throws IOException {
        if (!FORMAT.equals(j.optString("format"))) throw new IOException("not a knowledge snapshot of format " + FORMAT + " (found: " + j.optString("format", "no format field") + ")");
        JSONObject m = j.optJSONObject("manifest"); JSONObject nb = j.optJSONObject("notebook");
        if (m == null || nb == null || nb.optJSONArray("notes") == null) throw new IOException("a knowledge snapshot needs a manifest and a notebook with a list of notes");
        LinkedHashMap<String, String> notes = new LinkedHashMap<>();
        JSONArray arr = nb.getJSONArray("notes");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!o.has("key") || !o.has("content")) throw new IOException("note " + (i + 1) + " lacks a key or a content");
            if (notes.put(o.getString("key"), o.getString("content")) != null) throw new IOException("duplicate note key: " + o.getString("key"));
        }
        return new KnowledgeSnapshot(m, notes);
    }

    /** The learning history (one row per stage and debrief). Snapshots written before the first phase was renamed call it "development": both are read. */
    public JSONArray learningHistory() {
        JSONArray a = manifest.optJSONArray("learning");
        return a != null ? a : manifest.optJSONArray("development");
    }

    public String id() { return manifest.optString("id", "(no id)"); }

    /** Reasons this snapshot cannot be loaded at all. */
    public List<String> errors() {
        List<String> out = new ArrayList<>();
        if (!manifest.has("id")) out.add("the manifest has no id");
        JSONArray un = manifest.optJSONArray("unsupported_artifacts");
        if (un != null && un.length() > 0) {
            List<String> ids = new ArrayList<>(); for (int i = 0; i < un.length(); i++) ids.add(un.getString(i));
            out.add("it comes from a run that also had artifacts a knowledge snapshot does not capture " + ids + " — only the Notebook is restored, so loading it would silently drop part of what the agent had");
        }
        String arm = manifest.optJSONObject("options") == null ? "" : manifest.getJSONObject("options").optString("arm", "");
        if (!arm.isEmpty() && !arm.equals("notebook")) out.add("it comes from the '" + arm + "' arm; only the notebook arm's snapshots can be loaded");
        return out;
    }

    /** True if the notes no longer match the checksum in the manifest: somebody edited them after the snapshot was built. Legitimate (curation), but recorded. */
    public boolean edited() {
        JSONObject n = manifest.optJSONObject("notes");
        return n == null || !checksum(notes).equals(n.optString("sha256"));
    }

    /** Differences between what the snapshot was built under and what it will run under. They do not stop a run, but they are printed and recorded. */
    public List<String> warnings(String currentMaterials, boolean currentSafeMode) {
        List<String> out = new ArrayList<>();
        JSONObject o = manifest.optJSONObject("options") == null ? new JSONObject() : manifest.getJSONObject("options");
        String mat = o.optString("materials", "1");
        if (!mat.equals(currentMaterials))
            out.add("built under materials version " + mat + ", running under " + currentMaterials + ": its notes were written from the old manuals, tutor replies and wording, and may transcribe them");
        String safe = o.optString("notebook_safe_mode", "");
        if (!safe.isEmpty() && !safe.equals(currentSafeMode ? "on" : "off"))
            out.add("built with the Notebook's safe mode " + safe + ", running with it " + (currentSafeMode ? "on" : "off") + ": the notes may refer to operations (append_note, restore_note) that this Notebook " + (currentSafeMode ? "has" : "does not have"));
        if (edited()) out.add("EDITED since it was built: the notes no longer match the checksum in the manifest, so this instance runs on edited notes");
        return out;
    }

    /** One line saying what this is and where it came from. */
    public String provenanceLine() {
        JSONObject s = manifest.optJSONObject("source") == null ? new JSONObject() : manifest.getJSONObject("source");
        JSONObject d = manifest.optJSONObject("derived_from");
        JSONObject lin = manifest.optJSONObject("lineage");
        String lineageNote = lin == null ? "" : "; CANDIDATE round " + lin.optInt("round") + " from " + lin.optString("parent") + " (" + lin.optString("instruction") + ")";
        String letterNote = lineageNote + (notes.containsKey("primer") ? "; contains a letter ('primer', " + notes.get("primer").length() + " chars)" : "");
        String derivedNote = letterNote + (d == null ? "" : "; DERIVED from " + d.optString("id") + " as variant '" + d.optString("variant") + "' (" + d.optString("instruction") + ")");
        return id() + ": " + notes.size() + " notes, " + notes.values().stream().mapToInt(String::length).sum() + " chars; built " + manifest.optString("built_at", "?")
                + " from " + s.optString("run", s.optString("log", "?")) + " after " + s.optString("snapshot_after", "?") + " (cycle " + s.opt("cycle") + ", "
                + (s.optBoolean("reconstructed_from_log", false) ? "reconstructed from the log" : "written by the run") + ")" + derivedNote;
    }

    /** The statements an instance may be told about where it came from — facts only; nothing about what to do with them, or about what has changed. */
    public Set<String> noteKeys() { return new LinkedHashSet<>(notes.keySet()); }
}
