package faber.devl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Assesses an agent's domain model against the designer's reference model. Deliberately coarse: it
 * answers "does the model say this?" by tolerant matching on concept names and key facts, so a
 * human can then look at the gaps. It is a prompt for review, not a verdict on understanding.
 */
public final class DomainReference {

    public static final class Item {
        public final String id, layer, label; public final List<String> aliases = new ArrayList<>(), facts = new ArrayList<>(), groundings = new ArrayList<>();
        Item(JSONObject j) {
            id = j.getString("id"); layer = j.getString("layer"); label = j.getString("label");
            for (Object o : j.optJSONArray("aliases") == null ? new JSONArray() : j.getJSONArray("aliases")) aliases.add(((String) o).toLowerCase(Locale.ROOT));
            for (Object o : j.optJSONArray("facts") == null ? new JSONArray() : j.getJSONArray("facts")) facts.add(((String) o).toLowerCase(Locale.ROOT));
            for (Object o : j.optJSONArray("groundings") == null ? new JSONArray() : j.getJSONArray("groundings")) groundings.add(((String) o).toLowerCase(Locale.ROOT));
        }
    }

    public static final class Result {
        public final String layer; public final List<String> matched = new ArrayList<>(), missing = new ArrayList<>();
        public int groundedOf = 0, groundedTotal = 0;
        Result(String layer) { this.layer = layer; }
        public int total() { return matched.size() + missing.size(); }
        @Override public String toString() {
            return layer + ": " + matched.size() + "/" + total() + (groundedTotal > 0 ? "  (grounded " + groundedOf + "/" + groundedTotal + ")" : "")
                    + (missing.isEmpty() ? "" : "  missing: " + String.join("; ", missing));
        }
    }

    private final List<Item> items = new ArrayList<>();

    public DomainReference(JSONObject reference) { for (Object o : reference.getJSONArray("items")) items.add(new Item((JSONObject) o)); }
    public static DomainReference expense() { return new DomainReference(DomainModelArtifact.loadJsonResource("expense-domain-reference.json")); }

    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    private static boolean has(String textLower, String fact) {
        if (fact.chars().allMatch(Character::isDigit)) return Pattern.compile("(?<![0-9])" + fact + "(?![0-9])").matcher(textLower).find();
        return textLower.contains(fact);
    }
    private static boolean hasAll(String text, List<String> facts) {
        String t = text.toLowerCase(Locale.ROOT); for (String f : facts) if (!has(t, f)) return false; return true;
    }

    /** The ids of the items of a (non-structure) layer that the model holds — the same test as assess(), for callers that need to know WHICH items, not how many. */
    public List<String> matchedIds(String layer, List<DomainModelArtifact.Concept> model) {
        List<String> out = new ArrayList<>();
        for (Item it : items) {
            if (!it.layer.equals(layer) || layer.equals("structure")) continue;
            for (DomainModelArtifact.Concept c : model) if (hasAll(c.allText(), it.facts)) { out.add(it.id); break; }
        }
        return out;
    }

    public Result assess(String layer, List<DomainModelArtifact.Concept> model) {
        Result r = new Result(layer);
        for (Item it : items) {
            if (!it.layer.equals(layer)) continue;
            boolean ok = false; boolean grounded = false;
            for (DomainModelArtifact.Concept c : model) {
                if (layer.equals("structure")) {
                    String n = norm(c.name); boolean nameMatch = n.equals(norm(it.label));
                    for (String a : it.aliases) if (n.contains(norm(a))) nameMatch = true;
                    if (nameMatch && hasAll(c.allText(), it.facts)) {
                        ok = true;
                        if (!it.groundings.isEmpty()) { String g = String.join(" ", c.groundings).toLowerCase(Locale.ROOT); grounded = it.groundings.stream().allMatch(g::contains); }
                    }
                } else if (hasAll(c.allText(), it.facts)) ok = true;
            }
            if (ok) r.matched.add(it.label); else r.missing.add(it.label);
            if (layer.equals("structure") && !it.groundings.isEmpty()) { r.groundedTotal++; if (grounded) r.groundedOf++; }
        }
        return r;
    }
}
