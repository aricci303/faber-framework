package faber.devl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * The domain model as a CURATED, VERSIONED, READ-ONLY source of truth (KNOWLEDGE-LAYERS.md).
 *
 * The discipline: designers and domain experts must provide it; it is part of the environment, provisioned identically in the learning, the assessment and the operation
 * workspace; an instance consults it, it does not carry a copy; the agent cannot change it. It is authored as a source file (a JSON resource, versioned like code): concepts,
 * relations, groundings, and rules — each with its source, its confidence and the versions in which it applies — and a history of what changed in each version.
 *
 * The agent's operations are about KNOWLEDGE, never about the task: overview, describe, rules_for, search, version, changes_since — and propose, which records a suggestion for the
 * domain experts and changes nothing. There is no operation that decides a case, and none that evaluates a rule against one: consult, do not delegate.
 *
 * The designer side (not agent operations): publish(version), the proposals, a content hash, the rules in force as texts for the measurements, a Markdown export for review.
 */
public final class CuratedModelArtifact extends Artifact {

    public static final String type = "CuratedDomainModel";

    static final class RuleEntry {
        final String id, text, source, confidence; final int since; final Integer until;
        RuleEntry(JSONObject j) {
            id = j.getString("id"); text = j.getString("text"); source = j.optString("source", "domain experts"); confidence = j.optString("confidence", "confirmed");
            since = j.optInt("since", 1); until = j.has("until") ? j.getInt("until") : null;
        }
        boolean activeAt(int v) { return since <= v && (until == null || v < until); }
    }

    static final class ConceptEntry {
        final String name, kind, definition; final List<String[]> relations = new ArrayList<>(); final List<String> groundings = new ArrayList<>(); final List<RuleEntry> rules = new ArrayList<>();
        ConceptEntry(JSONObject j) {
            name = j.getString("name"); kind = j.optString("kind", "concept"); definition = j.optString("definition", "");
            for (Object r : j.optJSONArray("relations") == null ? new JSONArray() : j.getJSONArray("relations")) { JSONObject rj = (JSONObject) r; relations.add(new String[] { rj.getString("type"), rj.getString("target") }); }
            for (Object g : j.optJSONArray("groundings") == null ? new JSONArray() : j.getJSONArray("groundings")) groundings.add((String) g);
            for (Object r : j.optJSONArray("rules") == null ? new JSONArray() : j.getJSONArray("rules")) rules.add(new RuleEntry((JSONObject) r));
        }
        List<RuleEntry> activeRules(int v) { List<RuleEntry> l = new ArrayList<>(); for (RuleEntry r : rules) if (r.activeAt(v)) l.add(r); return l; }
    }

    static final class VersionEntry {
        final int id; final String released; final List<String> changes = new ArrayList<>();
        VersionEntry(JSONObject j) { id = Integer.parseInt(j.getString("id")); released = j.optString("released", ""); for (Object c : j.optJSONArray("changes") == null ? new JSONArray() : j.getJSONArray("changes")) changes.add((String) c); }
    }

    private final String modelId, title, sha256, resourceName;
    private final Map<String, ConceptEntry> concepts = new LinkedHashMap<>();       // key: lower-case name
    private final List<VersionEntry> versions = new ArrayList<>();
    private final List<String[]> proposals = new ArrayList<>();                      // {concept, change, evidence, version in force}
    private final Map<String, Map<String, Integer>> opsByStage = new TreeMap<>();
    private volatile int current = 1; private String stage = "";

    public CuratedModelArtifact(String id, Workspace workspace, JSONObject model, String sha256, String resourceName) {
        super(id, type, workspace);
        this.modelId = model.getString("id"); this.title = model.optString("title", modelId); this.sha256 = sha256; this.resourceName = resourceName;
        for (Object o : model.getJSONArray("concepts")) { ConceptEntry c = new ConceptEntry((JSONObject) o); concepts.put(c.name.toLowerCase(Locale.ROOT), c); }
        for (Object o : model.getJSONArray("versions")) versions.add(new VersionEntry((JSONObject) o));
        if (versions.isEmpty()) throw new IllegalArgumentException("a curated model needs at least one version");
        currentObsProperties().put("version", String.valueOf(current));
        currentObsProperties().put("concepts", names());
        currentObsProperties().put("proposals", 0);
    }

    /** The file name of a classpath path: what a snapshot records as the source, and what the Markdown export prints. */
    static String fileName(String resource) { return resource.substring(resource.lastIndexOf('/') + 1); }

    /** Loads the curated source file from an absolute classpath path (the case's: "/faber/devl/cases/expense/expense-domain-curated.json") and hashes its bytes, so that a snapshot can say which
     *  source it was learned against. */
    public static CuratedModelArtifact load(String artifactId, Workspace workspace, String resource) {
        String resourceName = fileName(resource);
        try (InputStream in = CuratedModelArtifact.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("resource not found: " + resource);
            byte[] bytes = in.readAllBytes();
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) sb.append(String.format("%02x", b));
            return new CuratedModelArtifact(artifactId, workspace, new JSONObject(new String(bytes, StandardCharsets.UTF_8)), sb.toString(), resourceName);
        } catch (IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** The environment record of a curated source file, without provisioning it: {kind, id, source, sha256} — what a snapshot records and what a run compares against. */
    public static JSONObject sourceRecord(String resource) {
        String resourceName = fileName(resource);
        try (InputStream in = CuratedModelArtifact.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("resource not found: " + resource);
            byte[] bytes = in.readAllBytes(); StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) sb.append(String.format("%02x", b));
            return new JSONObject().put("kind", "curated").put("id", new JSONObject(new String(bytes, StandardCharsets.UTF_8)).getString("id")).put("source", resourceName).put("sha256", sb.toString());
        } catch (IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    // ---- designer side (not agent operations) --------------------------------------------------------------------------------------------------------

    /** The domain experts release a version: from now on every instance reads that version. Observable, so an agent that looks sees the new number. */
    public synchronized void publish(String versionId) {
        int v; try { v = Integer.parseInt(versionId); } catch (NumberFormatException e) { throw new IllegalArgumentException("no such version: " + versionId); }
        if (versions.stream().noneMatch(x -> x.id == v)) throw new IllegalArgumentException("no such version: " + versionId);
        String old = String.valueOf(current); current = v; notifyObsPropertyChanged("version", old, String.valueOf(v));
    }

    public String currentVersion() { return String.valueOf(current); }
    public String sha256() { return sha256; }
    public String modelId() { return modelId; }
    public String resourceName() { return resourceName; }
    public synchronized void setStage(String label) { this.stage = label; }
    public synchronized List<String[]> proposals() { List<String[]> l = new ArrayList<>(); for (String[] p : proposals) l.add(p.clone()); return l; }
    public synchronized Map<String, Map<String, Integer>> opCountsByStage() { Map<String, Map<String, Integer>> m = new TreeMap<>(); opsByStage.forEach((k, v) -> m.put(k, new TreeMap<>(v))); return m; }
    /** The environment record a snapshot keeps: which curated source it was learned against. */
    public JSONObject environmentRecord() { return new JSONObject().put("kind", "curated").put("id", modelId).put("source", resourceName).put("sha256", sha256); }

    /** The rules in force at the version in force, one text each — what the rule probes read, and what is compared with an agent's playbook. */
    public synchronized List<String> ruleTexts() {
        List<String> out = new ArrayList<>();
        for (ConceptEntry ce : concepts.values()) for (RuleEntry r : ce.activeRules(current)) out.add(r.text);
        return out;
    }

    public synchronized String toMarkdown() {
        StringBuilder sb = new StringBuilder("# ").append(title).append("\n\nVersion in force: ").append(current).append(" (source ").append(resourceName).append(", sha256 ").append(sha256, 0, 12).append(")\n\n");
        for (ConceptEntry c : concepts.values()) {
            sb.append("## ").append(c.name).append(" (").append(c.kind).append(")\n\n").append(c.definition).append("\n\n");
            for (String[] r : c.relations) sb.append("- ").append(r[0]).append(" ").append(r[1]).append("\n");
            for (String g : c.groundings) sb.append("- grounded in: ").append(g).append("\n");
            for (RuleEntry r : c.activeRules(current)) sb.append("- rule ").append(r.id).append(": ").append(r.text).append(" [").append(r.source).append("; ").append(r.confidence).append("; since version ").append(r.since).append("]\n");
            sb.append("\n");
        }
        if (!proposals.isEmpty()) {
            sb.append("## Proposals from the agent, for the domain experts to decide\n\n"); int i = 1;
            for (String[] p : proposals) sb.append(i++).append(". ").append(p[0].isBlank() ? "(general)" : p[0]).append(": ").append(p[1]).append(" — evidence: ").append(p[2]).append(" (made at version ").append(p[3]).append(")\n");
        }
        return sb.toString();
    }

    // ---- agent operations: about knowledge, never about the task -------------------------------------------------------------------------------------

    private JSONArray names() { JSONArray a = new JSONArray(); for (ConceptEntry c : concepts.values()) a.put(c.name); return a; }

    private String req(JSONObject p, String op, String name) {
        String v = p.optString(name, "").trim();
        if (v.isEmpty()) throw new IllegalArgumentException("missing parameter '" + name + "': " + op + " needs it; nothing was done");
        return v;
    }

    private ConceptEntry need(String name) {
        ConceptEntry c = concepts.get(name.toLowerCase(Locale.ROOT));
        if (c == null) throw new IllegalArgumentException("no such concept: " + name + " (overview() lists the concepts of this model)");
        return c;
    }

    private String ruleLine(RuleEntry r) { return "rule " + r.id + ": " + r.text + " [" + r.source + "; " + r.confidence + "; applies since version " + r.since + "]"; }

    /**
     * What every lookup that returns rules ends with. The agent reads the manual once, at the start, and meets a conflict between this model and the system many cycles later with only what it carried; what it
     * reads at each lookup, just before it decides, is the text nearest to that moment. Domain-independent: it names no rule and no domain.
     */
    static String footer(int version) {
        return "\n[Version " + version + " of the model. If what the system tells you differs from it — a flag, an audit, any feedback — say which source said what, and propose a change; do not settle it silently.]";
    }

    @Override
    public synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        opsByStage.computeIfAbsent(stage, k -> new TreeMap<>()).merge(op, 1, Integer::sum);       // consulting is what is measured: failed attempts included
        int v = current;
        switch (op) {
            case "overview": {
                VersionEntry ve = versions.stream().filter(x -> x.id == v).findFirst().get();
                StringBuilder sb = new StringBuilder(title).append(" — version ").append(v).append(ve.released.isBlank() ? "" : " (released " + ve.released + ")").append(". Concepts:\n");
                for (ConceptEntry c : concepts.values()) {
                    String d = c.definition.replace('\n', ' ');
                    sb.append("- ").append(c.name).append(" [").append(c.kind).append("]: ").append(d.length() > 80 ? d.substring(0, 80) + "..." : d).append(" (").append(c.activeRules(v).size()).append(" rules)\n");
                }
                sb.append("describe(concept) gives a concept in full; rules_for(concept) its rules; search(term) finds where a term is used.");
                return List.of(sb.toString());
            }
            case "describe": {
                ConceptEntry c = need(req(p, op, "concept")); StringBuilder sb = new StringBuilder();
                sb.append(c.name).append(" [").append(c.kind).append("]\n").append(c.definition.isBlank() ? "(no definition)" : c.definition);
                for (String[] r : c.relations) sb.append("\n- ").append(r[0]).append(" ").append(r[1]);
                for (String g : c.groundings) sb.append("\n- grounded in: ").append(g);
                for (RuleEntry r : c.activeRules(v)) sb.append("\n- ").append(ruleLine(r));
                return List.of(sb.toString() + (c.activeRules(v).isEmpty() ? "" : footer(v)));
            }
            case "rules_for": {
                ConceptEntry c = need(req(p, op, "concept")); List<RuleEntry> rs = c.activeRules(v);
                if (rs.isEmpty()) return List.of("No rules are recorded for " + c.name + " in version " + v + ".");
                StringBuilder sb = new StringBuilder("Rules for " + c.name + ", version " + v + ":");
                for (RuleEntry r : rs) sb.append("\n- ").append(ruleLine(r));
                return List.of(sb.toString() + footer(v));
            }
            case "search": {
                String term = req(p, op, "term").toLowerCase(Locale.ROOT); StringBuilder sb = new StringBuilder(); int hits = 0;
                for (ConceptEntry c : concepts.values()) {
                    if (c.name.toLowerCase(Locale.ROOT).contains(term) || c.definition.toLowerCase(Locale.ROOT).contains(term)) { sb.append("\n- ").append(c.name).append(" (concept): ").append(c.definition.length() > 100 ? c.definition.substring(0, 100) + "..." : c.definition); hits++; }
                    for (RuleEntry r : c.activeRules(v)) if (r.text.toLowerCase(Locale.ROOT).contains(term)) { sb.append("\n- ").append(c.name).append(", ").append(r.id).append(": ").append(r.text); hits++; }
                }
                return List.of(hits == 0 ? "Nothing in version " + v + " mentions '" + p.getString("term") + "'." : hits + " match(es) in version " + v + ":" + sb + footer(v));
            }
            case "version": {
                StringBuilder sb = new StringBuilder("Version ").append(v).append(" is in force. Published versions:");
                for (VersionEntry ve : versions) if (ve.id <= v) sb.append(" ").append(ve.id).append(ve.released.isBlank() ? "" : " (" + ve.released + ")").append(";");
                return List.of(sb.toString());
            }
            case "changes_since": {
                int since; try { since = Integer.parseInt(req(p, op, "version")); } catch (NumberFormatException e) { throw new IllegalArgumentException("'" + p.getString("version") + "' is not a version number: versions are whole numbers (version() lists them)"); }
                if (since < 1) throw new IllegalArgumentException("versions start at 1");
                if (since >= v) return List.of("No changes: version " + since + (since == v ? " is the version in force." : " has not been published."));
                StringBuilder sb = new StringBuilder("Changes since version " + since + " (version " + v + " is in force):");
                for (VersionEntry ve : versions) if (ve.id > since && ve.id <= v) { sb.append("\nVersion ").append(ve.id).append(ve.released.isBlank() ? "" : " (" + ve.released + ")").append(":"); for (String c : ve.changes) sb.append("\n- ").append(c); }
                return List.of(sb.toString());
            }
            case "propose": {
                String change = req(p, op, "change"), evidence = req(p, op, "evidence"), concept = p.optString("concept", "").trim();
                if (!concept.isEmpty()) need(concept);
                proposals.add(new String[] { concept, change, evidence, String.valueOf(v) });
                currentObsProperties().put("proposals", proposals.size());
                return List.of("Proposal " + proposals.size() + " recorded for the domain experts. The model itself is unchanged: the experts decide, and a change they accept takes effect in a new version.");
            }
            default: throw new IllegalArgumentException("unknown operation: " + op + " (this model can be read — overview, describe, rules_for, search, version, changes_since — and you can propose a change; you cannot change it)");
        }
    }

    public static Manual manual(String company) {
        return new Manual(type,
                "the domain you work in, as the " + company + "'s domain experts keep it: its concepts, how they relate, and the rules that hold, each rule with its source and the version in which it applies. "
                + "It is the " + company + "'s source of truth about the domain. You can read it; you cannot change it",
                null, null,
                List.of(new Manual.Param("version", "the version of the model in force"), new Manual.Param("concepts", "the names of the concepts in the model"),
                        new Manual.Param("proposals", "how many changes you have proposed so far")),
                List.of(),
                List.of(
                    new Manual.Operation("overview()", "the model's map: its version, and every concept with a short definition and its number of rules", List.of(new Manual.Param("overview", "one line per concept"))),
                    new Manual.Operation("describe(concept)", "everything the model says about one concept: its definition, its relations, where it lives in what you can do, and its rules with their sources", List.of(new Manual.Param("entry", "the concept's entry"))),
                    new Manual.Operation("rules_for(concept)", "the rules of one concept, each with its source and the version since which it applies", List.of(new Manual.Param("rules", "the concept's rules"))),
                    new Manual.Operation("search(term)", "the concepts and rules that mention a term", List.of(new Manual.Param("matches", "where the term is used"))),
                    new Manual.Operation("version()", "the version in force, and the versions published so far", List.of(new Manual.Param("version", "the version in force"))),
                    new Manual.Operation("changes_since(version)", "what the domain experts changed in the model since a version you know", List.of(new Manual.Param("changes", "the changes, version by version"))),
                    new Manual.Operation("propose(concept, change, evidence)", "suggest a change to the domain experts, with what you observed that supports it; concept is optional. The model itself does not change: the experts decide, and an accepted change takes effect in a new version",
                            List.of(new Manual.Param("status", "confirmation with the proposal's number")))),
                "What this model records holds here, even where it differs from what you would assume. That is about your assumptions, not about the world: like any source, it can be behind it. "
                + "It is changed only by the domain experts, in new versions (see version and changes_since). "
                + "If what the system tells you contradicts the model — a flag, an audit, any feedback — do not settle that silently, whichever of them you believe is right: say which source said what, and propose a change; the model is not yours to change.");
    }
}
