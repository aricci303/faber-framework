package faber.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional additions to the system prompt, switched on with -Dfaber.prompt.additions=A,B,C,D (a VM argument: the prompt is built once, at class
 * load). Off by default, so every earlier result stays comparable. They exist so that the same build can run both arms of an A/B test of
 * prompt wording; the text lives here, in one place, for review and editing — nothing else in the code depends on its wording.
 *
 *   A  batching: a third question for the independence test — does what comes back from one action bear on how to do the others?
 *   B  the first actions on unfamiliar ground are a test of what you hold: commit a few, read what comes back, then continue
 *   C  cross-check the details, not the gist; report what you actually checked
 *   D  keep a running record of what contradicts what you hold, and report from it, not from memory
 *
 * Each addition is inserted after an anchor sentence of the prompt, matched with flexible whitespace (the resource is line-wrapped). If an anchor
 * is missing or occurs more than once — the prompt has been edited — this fails loudly instead of silently testing something else.
 */
public final class PromptAdditions {
    private PromptAdditions() {}

    static final String ANCHOR_BATCHING = "or changes a condition another assumes.";
    static final String ANCHOR_REAL = "the line is set by your designers and is truthful.";

    static final String A = String.join("\n",
        "There is a third question, easy to miss because it is about what you will",
        "learn rather than what will happen: does what comes back from one of these",
        "actions bear on how you should do the others? Feedback is evidence. If",
        "several actions all rest on the same assumption — the same rule, the same",
        "belief you have not yet seen confirmed — then the first result is a test of",
        "that assumption for all of them. When those actions also cannot be undone,",
        "commit one, or a few, and read what comes back before committing the rest.");

    static final String B = String.join("\n",
        "Your first actions on cases you have not seen are also a test of what you",
        "hold, and what comes back with them — a flag, a warning, anything that",
        "quotes a rule — is the cheapest information you will get. Where actions",
        "cannot be undone, do not spend that opportunity on all of them at once:",
        "commit a few, read everything that returns, and only then continue.");

    static final String C = String.join("\n",
        "When you compare what came back with what you hold, compare the details —",
        "names, numbers, thresholds, conditions — not only the gist: a rule that",
        "looks the same can differ in a number. And report what you actually",
        "checked. If you did not compare the details, do not say that it matched;",
        "say what you looked at and what you did not.");

    static final String D = String.join("\n",
        "Keep a record as you go. When something comes back that corrects or",
        "contradicts what you hold, write it down at once — what it said, and which",
        "action it came back on — and when you report, write from that record, not",
        "from memory of how things went.");

    /** The names understood in the specification, in the order they are inserted. */
    public static List<String> names() { return List.of("A", "B", "C", "D"); }

    /** The specification currently in force, normalised ("none", or e.g. "A,C"). */
    public static String specInForce() {
        List<String> chosen = parse(System.getProperty("faber.prompt.additions", ""));
        return chosen.isEmpty() ? "none" : String.join(",", chosen);
    }

    static List<String> parse(String spec) {
        List<String> out = new ArrayList<>();
        if (spec == null) return out;
        for (String t : spec.split("[,\\s]+")) {
            if (t.isBlank() || t.equalsIgnoreCase("none")) continue;
            String n = t.toUpperCase(Locale.ROOT);
            if (!names().contains(n)) throw new IllegalArgumentException("unknown prompt addition '" + t + "' in -Dfaber.prompt.additions (known: A, B, C, D, or none)");
            if (!out.contains(n)) out.add(n);
        }
        out.sort((x, y) -> names().indexOf(x) - names().indexOf(y));
        return out;
    }

    /** Returns the prompt with the requested additions inserted; with an empty specification, the prompt unchanged. */
    public static String apply(String promptText, String spec) {
        List<String> chosen = parse(spec);
        if (chosen.isEmpty()) return promptText;
        String text = promptText;
        if (chosen.contains("A")) text = insertAfter(text, ANCHOR_BATCHING, "A", A);
        StringBuilder bcd = new StringBuilder();
        for (String n : List.of("B", "C", "D")) if (chosen.contains(n)) bcd.append("\n\n").append(n.equals("B") ? B : n.equals("C") ? C : D);
        if (bcd.length() > 0) text = insertAfter(text, ANCHOR_REAL, "B/C/D", bcd.substring(2));
        return text;
    }

    private static String insertAfter(String text, String anchor, String what, String insertion) {
        StringBuilder rx = new StringBuilder();
        for (String w : anchor.trim().split("\\s+")) { if (rx.length() > 0) rx.append("\\s+"); rx.append(Pattern.quote(w)); }
        Matcher m = Pattern.compile(rx.toString()).matcher(text);
        if (!m.find()) throw new IllegalStateException("prompt addition " + what + ": anchor sentence not found in the system prompt (was it edited?): \"" + anchor + "\"");
        int end = m.end();
        if (m.find()) throw new IllegalStateException("prompt addition " + what + ": anchor sentence occurs more than once in the system prompt: \"" + anchor + "\"");
        return text.substring(0, end) + "\n\n" + insertion + text.substring(end);
    }
}
