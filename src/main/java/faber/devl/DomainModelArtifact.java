package faber.devl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * A cognitive artifact holding a structured model of the domain the agent works in: its concepts (the
 * ubiquitous language), how they relate, the rules that hold, and how each concept maps onto what the
 * agent can actually do in the environment (groundings). It is the stable, queryable counterpart of
 * what a tutor says in conversation and of what a manual says about individual artifacts.
 *
 * Two things make it more than a notebook. Provenance: every rule records where it came from and how
 * sure the agent is, so a developer can audit what the agent believes and the agent can notice when a
 * belief has gone stale. And origin: entries seeded by the designer (with domain experts) are marked,
 * and overwriting a seeded definition is recorded, so what the agent changed is visible.
 *
 * A model may start empty (the agent constructs it) or seeded from a designer-authored JSON file
 * (see {@link #seed(JSONObject)}); {@link #toMarkdown()} exports it for review with domain experts.
 */
public final class DomainModelArtifact extends Artifact {

    public static final String type = "DomainModel";

    public static final class Rule {
        public final String text, source, confidence;
        Rule(String text, String source, String confidence) { this.text = text; this.source = source; this.confidence = confidence; }
    }

    public static final class Concept {
        public final String name; public String kind = "concept", definition = "", origin = "agent";
        public String seededDefinition = null;                       // set when the agent overwrote a designer's definition
        public final List<String[]> relations = new ArrayList<>();   // {type, target}
        public final List<Rule> rules = new ArrayList<>();
        public final List<String> groundings = new ArrayList<>();
        Concept(String name) { this.name = name; }
        Concept copy() {
            Concept c = new Concept(name); c.kind = kind; c.definition = definition; c.origin = origin; c.seededDefinition = seededDefinition;
            for (String[] r : relations) c.relations.add(r.clone());
            c.rules.addAll(rules); c.groundings.addAll(groundings); return c;
        }
        /** Everything the concept says, for tolerant matching by assessments. */
        public String allText() {
            StringBuilder sb = new StringBuilder(name).append(' ').append(definition);
            for (String[] r : relations) sb.append(' ').append(r[0]).append(' ').append(r[1]);
            for (Rule r : rules) sb.append(' ').append(r.text);
            return sb.toString();
        }
    }

    private final Map<String, Concept> concepts = new LinkedHashMap<>();   // key: lower-case name
    private final Map<String, Map<String, Integer>> opsByStage = new TreeMap<>();
    private String stage = "";

    public DomainModelArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
        currentObsProperties().put("concepts", new JSONArray());
    }

    // ---- designer-side API (not agent operations) --------------------------------------------

    /** Loads a designer-authored model. Everything seeded is marked origin=designer, rules source=designer, confidence=confirmed. */
    public synchronized void seed(JSONObject model) {
        for (Object o : model.getJSONArray("concepts")) {
            JSONObject j = (JSONObject) o;
            Concept c = new Concept(j.getString("name"));
            c.kind = j.optString("kind", "concept"); c.definition = j.optString("definition", ""); c.origin = "designer";
            for (Object r : j.optJSONArray("relations") == null ? new JSONArray() : j.getJSONArray("relations")) {
                JSONObject rj = (JSONObject) r; c.relations.add(new String[] { rj.getString("type"), rj.getString("target") });
            }
            for (Object r : j.optJSONArray("rules") == null ? new JSONArray() : j.getJSONArray("rules")) c.rules.add(new Rule((String) r, "designer", "confirmed"));
            for (Object g : j.optJSONArray("groundings") == null ? new JSONArray() : j.getJSONArray("groundings")) c.groundings.add((String) g);
            concepts.put(c.name.toLowerCase(Locale.ROOT), c);
        }
        currentObsProperties().put("concepts", names());
    }

    public static JSONObject loadJsonResource(String name) {
        try (InputStream in = DomainModelArtifact.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("resource not found: faber/devl/" + name);
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public synchronized void setStage(String label) { this.stage = label; }
    public synchronized List<Concept> snapshot() { List<Concept> l = new ArrayList<>(); for (Concept c : concepts.values()) l.add(c.copy()); return l; }
    public synchronized Map<String, Map<String, Integer>> opCountsByStage() {
        Map<String, Map<String, Integer>> m = new TreeMap<>(); opsByStage.forEach((k, v) -> m.put(k, new TreeMap<>(v))); return m;
    }

    /** Markdown rendering for review sessions with domain experts: what the agent's model says, with provenance. */
    public synchronized String toMarkdown() {
        StringBuilder sb = new StringBuilder("# Domain model\n\n");
        for (Concept c : concepts.values()) {
            sb.append("## ").append(c.name).append("  _(").append(c.kind).append("; origin: ").append(c.origin).append(")_\n\n");
            sb.append(c.definition.isBlank() ? "_(no definition)_" : c.definition).append("\n\n");
            if (c.seededDefinition != null) sb.append("> definition revised by the agent; the designer's was: ").append(c.seededDefinition).append("\n\n");
            for (String[] r : c.relations) sb.append("- ").append(r[0]).append(" -> ").append(r[1]).append("\n");
            for (Rule r : c.rules) sb.append("- RULE: ").append(r.text).append("  _[").append(r.source).append("; ").append(r.confidence).append("]_\n");
            for (String g : c.groundings) sb.append("- grounded in: `").append(g).append("`\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    // ---- agent operations ---------------------------------------------------------------------

    private JSONArray names() { JSONArray a = new JSONArray(); for (Concept c : concepts.values()) a.put(c.name); return a; }
    private void changed(JSONArray old) { notifyObsPropertyChanged("concepts", old, names()); }
    private Concept need(String name) {
        Concept c = concepts.get(name.toLowerCase(Locale.ROOT));
        if (c == null) throw new IllegalArgumentException("no such concept: " + name + " (define it first)");
        return c;
    }

    @Override
    protected synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        // counts invocations, failed attempts included: the measure is how much the agent consults and edits its model per stage
        opsByStage.computeIfAbsent(stage, k -> new TreeMap<>()).merge(op, 1, Integer::sum);
        switch (op) {
            case "define": {
                String name = p.getString("concept").trim(); JSONArray old = names();
                Concept c = concepts.get(name.toLowerCase(Locale.ROOT));
                boolean isNew = c == null;
                if (isNew) { c = new Concept(name); concepts.put(name.toLowerCase(Locale.ROOT), c); }
                String def = p.getString("definition");
                if (!isNew && c.origin.equals("designer") && c.seededDefinition == null && !c.definition.equals(def)) c.seededDefinition = c.definition;
                c.definition = def;
                if (p.has("kind")) c.kind = p.getString("kind");
                changed(old);
                return List.of((isNew ? "defined " : "updated ") + c.name);
            }
            case "relate": {
                Concept c = need(p.getString("concept")); String type = p.getString("relation"), target = p.getString("target");
                c.relations.add(new String[] { type, target });
                boolean known = concepts.containsKey(target.toLowerCase(Locale.ROOT));
                return List.of("related " + c.name + " " + type + " " + target + (known ? "" : " (note: '" + target + "' is not defined as a concept yet)"));
            }
            case "add_rule": {
                Concept c = need(p.getString("concept"));
                String text = p.getString("rule"), source = p.optString("source", "agent"), conf = p.optString("confidence", "hypothesis");
                c.rules.add(new Rule(text, source, conf));
                return List.of("rule " + c.rules.size() + " added to " + c.name);
            }
            case "retract_rule": {
                Concept c = need(p.getString("concept")); int n = p.getInt("rule_number");
                if (n < 1 || n > c.rules.size()) throw new IllegalArgumentException(c.name + " has " + c.rules.size() + " rules; no rule " + n);
                c.rules.remove(n - 1);
                return List.of("rule " + n + " retracted from " + c.name);
            }
            case "ground": {
                Concept c = need(p.getString("concept")); c.groundings.add(p.getString("link"));
                return List.of("grounded " + c.name + " in " + p.getString("link"));
            }
            case "retract": {
                Concept c = need(p.getString("concept")); JSONArray old = names();
                concepts.remove(c.name.toLowerCase(Locale.ROOT)); changed(old);
                return List.of("retracted " + c.name);
            }
            case "describe": {
                Concept c = need(p.getString("concept")); StringBuilder sb = new StringBuilder();
                sb.append(c.name).append(" [").append(c.kind).append("; origin: ").append(c.origin).append("]\n").append(c.definition.isBlank() ? "(no definition)" : c.definition);
                if (c.seededDefinition != null) sb.append("\n(you revised the designer's definition: ").append(c.seededDefinition).append(")");
                for (String[] r : c.relations) sb.append("\n- ").append(r[0]).append(" ").append(r[1]);
                int i = 1; for (Rule r : c.rules) sb.append("\n- rule ").append(i++).append(": ").append(r.text).append(" [").append(r.source).append("; ").append(r.confidence).append("]");
                for (String g : c.groundings) sb.append("\n- grounded in: ").append(g);
                return List.of(sb.toString());
            }
            case "list": {
                if (concepts.isEmpty()) return List.of("(the model is empty)");
                StringBuilder sb = new StringBuilder();
                for (Concept c : concepts.values()) {
                    String d = c.definition.replace('\n', ' ');
                    sb.append(c.name).append(" [").append(c.kind).append("]: ").append(d.length() > 80 ? d.substring(0, 80) + "..." : d)
                      .append(" (").append(c.rules.size()).append(" rules)\n");
                }
                return List.of(sb.toString().trim());
            }
            default: throw new IllegalArgumentException("unknown operation: " + op);
        }
    }

    public static Manual manual() {
        return new Manual(type,
                "a structured model of the domain you work in: its concepts, how they relate, the rules that hold, and how each concept maps onto what you can do — kept current by you",
                null, null,
                List.of(new Manual.Param("concepts", "the names of the concepts currently in the model")),
                List.of(),
                List.of(
                    new Manual.Operation("define(concept, definition, kind)", "create a concept, or update its definition. kind is optional (e.g. entity, actor, value, process, term)", List.of(new Manual.Param("status", "confirmation"))),
                    new Manual.Operation("relate(concept, relation, target)", "state a relation from a concept to another (e.g. made-by, belongs-to, decided-by)", List.of(new Manual.Param("status", "confirmation"))),
                    new Manual.Operation("add_rule(concept, rule, source, confidence)",
                            "record a rule that holds in this domain. source: where you learned it (e.g. tutor, observed, practice feedback, inferred). confidence: confirmed or hypothesis — something seen once is a hypothesis",
                            List.of(new Manual.Param("status", "confirmation with the rule's number"))),
                    new Manual.Operation("retract_rule(concept, rule_number)", "remove a rule that no longer holds or was wrong; rules are numbered as in describe", List.of(new Manual.Param("status", "confirmation"))),
                    new Manual.Operation("ground(concept, link)", "say where a concept lives in what you can do, e.g. an artifact's operation", List.of(new Manual.Param("status", "confirmation"))),
                    new Manual.Operation("describe(concept)", "everything the model says about one concept, with where each rule came from and how sure it is", List.of(new Manual.Param("entry", "the concept's entry"))),
                    new Manual.Operation("list()", "every concept with a short definition and its number of rules", List.of(new Manual.Param("concepts", "one line per concept"))),
                    new Manual.Operation("retract(concept)", "remove a concept from the model", List.of(new Manual.Param("status", "confirmation")))),
                "Your general knowledge of a domain is a starting guess, not the truth about this organization. "
                + "What this model records as given by the designer or the tutor holds here, even where it differs from what you would assume. "
                + "When something you observe contradicts the model, or the model contradicts what you observe, say which and fix the model. "
                + "Entries seeded by the designer are marked origin: designer; if you change one of their definitions, that is recorded.");
    }
}
