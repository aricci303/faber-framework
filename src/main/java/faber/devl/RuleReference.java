package faber.devl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A case's rules as keyword probes — what a text says about them. Layer "rules": the rules of the domain as first released; layer "v2": the ones the change replaced, as they read after it.
 * An item is held by a text when the text contains every one of its facts (a number only as a whole number, so that 400 is not 40). Deliberately coarse: it answers "does this text say
 * this?" so that a person can then look at the gaps. A prompt for review, not a verdict on understanding.
 *
 * The file also says what the change did (block "change", with ordered arrays): which rules it left alone, which it replaced and by what, and for each replaced rule the facts that made up its OLD
 * value and the words that say which rule a sentence is about — what the measurements by home need to tell a playbook that still states an old value as current from one that only mentions it.
 */
public final class RuleReference {

    public static final class Item {
        public final String id, layer, label; public final List<String> facts = new ArrayList<>();
        Item(JSONObject j) {
            id = j.getString("id"); layer = j.getString("layer"); label = j.getString("label");
            for (Object o : j.optJSONArray("facts") == null ? new JSONArray() : j.getJSONArray("facts")) facts.add(((String) o).toLowerCase(Locale.ROOT));
        }
    }

    public static final class Result {
        public final String layer; public final List<String> matched = new ArrayList<>(), missing = new ArrayList<>();
        Result(String layer) { this.layer = layer; }
        public int total() { return matched.size() + missing.size(); }
        @Override public String toString() { return layer + ": " + matched.size() + "/" + total() + (missing.isEmpty() ? "" : "  missing: " + String.join("; ", missing)); }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<String> unchanged = new ArrayList<>(); private final Map<String, String> replaced = new LinkedHashMap<>();
    private final Map<String, List<String>> oldFacts = new LinkedHashMap<>(), oldTopic = new LinkedHashMap<>();

    public RuleReference(JSONObject reference) {
        for (Object o : reference.getJSONArray("items")) items.add(new Item((JSONObject) o));
        JSONObject ch = reference.optJSONObject("change");
        if (ch != null) {
            for (Object o : ch.optJSONArray("unchanged") == null ? new JSONArray() : ch.getJSONArray("unchanged")) unchanged.add((String) o);
            // an ARRAY, not an object: a JSON object has no order, and the order in which a report lists the replaced rules is the file's
            for (Object o : ch.optJSONArray("replaced") == null ? new JSONArray() : ch.getJSONArray("replaced")) {
                JSONObject r = (JSONObject) o; String id = r.getString("rule");
                replaced.put(id, r.getString("by")); oldFacts.put(id, strings(r.getJSONArray("oldFacts"))); oldTopic.put(id, strings(r.getJSONArray("topic")));
            }
        }
    }

    private static List<String> strings(JSONArray a) { List<String> l = new ArrayList<>(); for (Object o : a) l.add(((String) o).toLowerCase(Locale.ROOT)); return l; }

    /** The rule probes of a case, from an absolute classpath path. */
    public static RuleReference load(String absoluteResourcePath) { return new RuleReference(loadJson(absoluteResourcePath)); }

    /** A JSON resource, from an absolute classpath path. */
    public static JSONObject loadJson(String absoluteResourcePath) {
        try (InputStream in = RuleReference.class.getResourceAsStream(absoluteResourcePath)) {
            if (in == null) throw new IllegalStateException("resource not found: " + absoluteResourcePath);
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    // ---- what the change did ---------------------------------------------------------------------------------------------------------------------------------------------
    /** The ids of the rules of the first layer, in the file's order. */
    public List<String> ruleIds() { List<String> l = new ArrayList<>(); for (Item i : items) if (i.layer.equals("rules")) l.add(i.id); return l; }
    /** The rules the change left alone. */
    public List<String> unchangedIds() { return unchanged; }
    /** The rules the change replaced (ids of the first layer) with the item of layer "v2" that states each now. */
    public Map<String, String> replaced() { return replaced; }
    public List<String> changedIds() { return new ArrayList<>(replaced.keySet()); }
    public List<String> oldValueFacts(String ruleId) { return oldFacts.getOrDefault(ruleId, List.of()); }
    public List<String> topicWords(String ruleId) { return oldTopic.getOrDefault(ruleId, List.of()); }

    // ---- probes ----------------------------------------------------------------------------------------------------------------------------------------------------------
    private static boolean has(String textLower, String fact) {
        if (fact.chars().allMatch(Character::isDigit)) return Pattern.compile("(?<![0-9])" + fact + "(?![0-9])").matcher(textLower).find();
        return textLower.contains(fact);
    }

    /** Does one text hold every fact of an item? */
    static boolean holds(String text, List<String> facts) {
        String t = text.toLowerCase(Locale.ROOT); for (String f : facts) if (!has(t, f)) return false; return true;
    }

    /** The ids of the items of a layer that some text of the given texts holds. */
    public List<String> matchedIds(String layer, List<String> texts) {
        List<String> out = new ArrayList<>();
        for (Item it : items) {
            if (!it.layer.equals(layer)) continue;
            for (String t : texts) if (holds(t, it.facts)) { out.add(it.id); break; }
        }
        return out;
    }

    public Result assess(String layer, List<String> texts) {
        Result r = new Result(layer);
        for (Item it : items) {
            if (!it.layer.equals(layer)) continue;
            boolean ok = false; for (String t : texts) if (holds(t, it.facts)) { ok = true; break; }
            if (ok) r.matched.add(it.label); else r.missing.add(it.label);
        }
        return r;
    }
}
