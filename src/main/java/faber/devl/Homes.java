package faber.devl;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge by home (KNOWLEDGE-LAYERS.md): where does each rule of the case live — in the curated model, in the agent's playbook?
 *
 * The rules are the model's, and the principle is one home per piece of knowledge: a playbook that restates them is a second home, and the second home goes stale when the model
 * changes. Two things are measured: DUPLICATION — a rule the playbook restates although the model holds it — and OLD VALUES — once the world has changed, a value the model no longer
 * states that the playbook still states as current. Both by keyword probes (RuleReference: what the case's rules are, which the change replaced, what their old values were), which cannot
 * tell a value asserted from a value annotated as history: an old value counts as ASSERTED when a sentence states it and says nothing about the past, and as annotated or historical when every
 * such sentence marks itself as history. Still a heuristic, and it prints what it found, so that the evidence can be read.
 *
 * "Current" depends on the world: before the change all rules at their first values; after it, the rules the change left alone and the ones it replaced, as they read now.
 */
public final class Homes {
    private Homes() {}

    public static final class Result {
        public String world; public int total; public List<String> model = new ArrayList<>(), playbook = new ArrayList<>();         // current rules held, by home
        public List<String> restated = new ArrayList<>();                                                                // held by the playbook AND by the model: duplication
        public List<String> oldAsserted = new ArrayList<>(), oldAnnotated = new ArrayList<>();                           // old values in the playbook (world 2): asserted as current / annotation or history only
        public String summary() {
            return "the model " + model.size() + "/" + total + " | the playbook " + playbook.size() + "/" + total + "; restated by the playbook although the model holds them: " + words(restated)
                 + ("2".equals(world) ? "; old values still stated as current: " + words(oldAsserted) + "; annotated or historical only: " + words(oldAnnotated) : "");
        }
    }

    /** The short name of rule ids: whatever follows the first dash of "r-meals" or "v2-meals". */
    public static String words1(String id) { int i = id.indexOf('-'); return i < 0 ? id : id.substring(i + 1); }
    public static String words(List<String> ids) { List<String> w = new ArrayList<>(); for (String i : ids) w.add(words1(i)); return w.toString(); }

    /** What marks a sentence as saying something about the PAST: an annotation ("v1 was 40"), a record of what changed, or of an earlier round. */
    private static final java.util.regex.Pattern HISTORY = java.util.regex.Pattern.compile("(?i)\\b(v1|version 1|was|were|formerly|previously|used to|no longer|until|before|old|changed|superseded|earlier|prior|removed|replaced|dropped|instead of)\\b");

    private static boolean has(String text, String fact) {
        return java.util.regex.Pattern.compile(fact.chars().allMatch(Character::isDigit) ? "(?<![0-9])" + fact + "(?![0-9])" : "(?i)" + java.util.regex.Pattern.quote(fact)).matcher(text).find();
    }

    /** A text as the sentences and lines it is made of: a rule is restated when ONE of them holds all the facts of the rule, not when the facts are somewhere in the text (a playbook that says
     *  "check the receipt" in one place and "approve, reject or escalate" in another does not state the receipt rule). */
    public static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String s : text.split("(?<=[.!?])\\s+|\\n+")) if (!s.isBlank()) out.add(s);
        return out;
    }

    /** Which old values a text states, and how: ASSERTED (a sentence that holds the old value of a replaced rule, with the rule's topic on its line, and says nothing about the past), or only
     *  ANNOTATED (every sentence that holds one marks itself as history). */
    public static void oldValues(RuleReference ref, String text, List<String> asserted, List<String> annotated) {
        List<String> a = new ArrayList<>(), n = new ArrayList<>();
        for (String line : text.split("\\n")) {
            String low = line.toLowerCase(java.util.Locale.ROOT);
            for (String id : ref.changedIds()) {
                if (ref.topicWords(id).stream().noneMatch(low::contains)) continue;
                for (String sentence : line.split("(?<=[.!?;])\\s+")) {
                    if (ref.oldValueFacts(id).stream().noneMatch(f -> has(sentence, f))) continue;
                    if (HISTORY.matcher(sentence).find()) { if (!n.contains(id)) n.add(id); } else if (!a.contains(id)) a.add(id);
                }
            }
        }
        for (String id : a) if (!asserted.contains(id)) asserted.add(id);
        for (String id : n) if (!a.contains(id) && !annotated.contains(id)) annotated.add(id);
    }

    private static List<String> current(RuleReference ref, List<String> texts, String world) {
        List<String> out = new ArrayList<>();
        List<String> rules = ref.matchedIds("rules", texts);
        if (!"2".equals(world)) { for (String id : rules) if (ref.ruleIds().contains(id)) out.add(id); return out; }
        for (String id : rules) if (ref.unchangedIds().contains(id)) out.add(id);
        for (String id : ref.matchedIds("v2", texts)) if (ref.replaced().containsValue(id)) out.add(id);
        return out;
    }

    /** The rule a replacement id stands for, in the vocabulary of the first layer, so that the same rule held in two homes is recognised: v2-meals ~ r-meals. */
    private static String same(RuleReference ref, String id) { for (var e : ref.replaced().entrySet()) if (e.getValue().equals(id)) return e.getKey(); return id; }

    /** playbookText may be null (no playbook); modelTexts: the rules the model states in the version in force. */
    public static Result measure(RuleReference ref, String playbookText, List<String> modelTexts, String world) {
        Result r = new Result(); r.world = world; r.total = ref.ruleIds().size();
        List<String> pb = sentences(playbookText);
        r.model = current(ref, modelTexts, world); r.playbook = current(ref, pb, world);
        List<String> modelSame = new ArrayList<>(); for (String id : r.model) modelSame.add(same(ref, id));
        for (String id : r.playbook) if (modelSame.contains(same(ref, id))) r.restated.add(id);
        if ("2".equals(world) && playbookText != null) oldValues(ref, playbookText, r.oldAsserted, r.oldAnnotated);
        return r;
    }

    public static List<String> lines(String label, Result r) { return List.of("  knowledge by home " + label + " (world version " + r.world + "; keyword probes): " + r.summary()); }
}
