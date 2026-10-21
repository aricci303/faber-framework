package faber.devl;

import java.util.ArrayList;
import java.util.List;

/**
 * The playbook (KNOWLEDGE-LAYERS.md): what the agent writes for its future self — task knowledge and self knowledge, in a narrative. It is the release artifact: what a fresh instance
 * is made from, together with the environment (the manuals, the curated domain model). It lives in the agent's notebook under one key.
 *
 * What it must NOT carry is a copy of what the environment already holds: the rules (the domain model's), the operations and parameters (the manuals'). It refers to them, and
 * records what neither holds: how the job is done, how the environment behaves as found, what the agent is unsure of and how it treats its sources. And it is written as lasting
 * practice, for a reader who was not there — not as an account of what happened to its writer: in the earliest runs the agent's note to its future self carried "this session",
 * item numbers and a history of events, and a fresh instance reported its predecessor's past as its own.
 *
 * What the agent is ASKED (the request, in every phase it is made) is text, and is in Messages (devl-messages.json); this class holds the key, the version of that text, and the measures
 * taken on what the agent wrote.
 */
public final class Playbook {
    private Playbook() {}

    /** The version of the requests (devl-messages.json: playbookContent, playbookFirst, playbookLater, playbookConsolidation); recorded in every snapshot and candidate, so that a change in what
     *  the writer was ASKED is never mistaken for a change in what it wrote. */
    public static final String VERSION = "playbook-v4";

    public static int paragraphs(String text) { return text == null || text.isBlank() ? 0 : text.strip().split("\\n\\s*\\n").length; }

    /** Does the playbook refer to the model, and to the manuals? (a keyword reading: it says whether the pointers are there, not whether they are good) */
    public static boolean refersToModel(String text) { return text != null && text.toLowerCase(java.util.Locale.ROOT).contains("domain-model") || text != null && text.toLowerCase(java.util.Locale.ROOT).contains("domain model"); }
    public static boolean refersToManuals(String text) { return text != null && text.toLowerCase(java.util.Locale.ROOT).contains("manual"); }
    public static boolean refersToLibrary(String text) { return text != null && (text.toLowerCase(java.util.Locale.ROOT).contains("task library") || text.toLowerCase(java.util.Locale.ROOT).contains("task-library")); }

    /** The measurements after a debrief: size, whether it changed, whether it points to the environment's sources, and how many of the case's rules it restates. */
    public static List<String> debriefLines(String stageLabel, String text, String previous, RuleReference reference) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            out.add("  playbook after debrief " + stageLabel + ": NONE — the playbook is empty");
            return out;
        }
        String change = previous == null || previous.isBlank() ? "first version" : previous.strip().equals(text.strip()) ? "UNCHANGED" : "revised (" + (text.length() - previous.length() >= 0 ? "+" : "") + (text.length() - previous.length()) + " characters)";
        int restated = reference.matchedIds("rules", Homes.sentences(text)).size();
        out.add("  playbook after debrief " + stageLabel + ": " + text.length() + " characters, " + paragraphs(text) + " paragraphs; " + change + "; refers to the domain model: "
                + (refersToModel(text) ? "yes" : "NO") + ", to the manuals: " + (refersToManuals(text) ? "yes" : "NO") + ", to the task library: " + (refersToLibrary(text) ? "yes" : "NO") + "; restates " + restated + " of the " + reference.ruleIds().size() + " rules");
        return out;
    }
}
