package faber.devl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Instrument, harness-side, never shown to the agent: does an assessment leave the playbook it assesses alone?
 *
 * The assessed object is the PARENT playbook: the text the instance is made from. An instance is free to write to its notebook, including to the key the playbook sits under — and
 * a playbook may even tell it to ("replace this note wholesale") and the system prompt tells it to correct what it holds. When it does, three things stop being what they seem: the
 * measures that read "the playbook at the end of A2a" read the instance's text and not the assessed one; the consolidation starts from the instance's text and not from the parent;
 * and the lineage of the candidate says it comes from a parent whose content it may no longer carry. LIFECYCLE.md, refinement 3: assessment must not contaminate the release.
 *
 * This class does not prevent anything (that would change what the agent sees); it MEASURES, so that it is never unseen: the writes the instance made to the key, how small the note got, where
 * the consolidation started from, and — for a candidate — what it keeps of its parent and which markers of practice the parent had and the candidate has not. All crude, at the level of the
 * line, advisory, with the evidence printed.
 */
public final class PlaybookWatch {
    private PlaybookWatch() {}

    // ---- units and similarity ---------------------------------------------------------------------------------------------------------------------------------------------------

    /** The units of a playbook: its non-empty lines of some substance (a bullet, a paragraph, a numbered item); titles and rulers are not units. */
    public static List<String> units(String text) {
        List<String> out = new ArrayList<>(); if (text == null) return out;
        for (String line : text.split("\\n")) { String t = line.strip(); if (t.length() >= 25 && tokens(t).size() >= 4) out.add(t); }
        return out;
    }

    static Set<String> tokens(String s) {
        Set<String> t = new HashSet<>(); for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_-]+")) if (w.length() >= 4) t.add(w);
        return t;
    }

    /** How well a unit of the parent is found in a candidate unit: the larger of the Jaccard overlap and 0.8 of the share of the parent unit's words the candidate unit holds (a unit that was reflowed or extended). */
    static double score(Set<String> parent, Set<String> cand) {
        if (parent.isEmpty() || cand.isEmpty()) return 0;
        int common = 0; for (String w : parent) if (cand.contains(w)) common++;
        double jaccard = (double) common / (parent.size() + cand.size() - common), containment = (double) common / parent.size();
        return Math.max(jaccard, 0.8 * containment);
    }
    public static final double RETAINED = 0.4;

    /** The units of `parent` that have a counterpart in `other`, and the ones that do not (in the order of the parent). */
    public static List<List<String>> compareUnits(String parent, String other) {
        List<String> kept = new ArrayList<>(), dropped = new ArrayList<>(); List<Set<String>> cu = new ArrayList<>(); for (String u : units(other)) cu.add(tokens(u));
        for (String pu : units(parent)) {
            Set<String> pt = tokens(pu); double best = 0; for (Set<String> c : cu) best = Math.max(best, score(pt, c));
            (best >= RETAINED ? kept : dropped).add(pu);
        }
        return List.of(kept, dropped);
    }

    // ---- markers of practice: framework words, no word of a domain ---------------------------------------------------------------------------------------------------------------

    private static final Map<String, Pattern> MARKERS = new LinkedHashMap<>();
    static {
        MARKERS.put("proposing a change to a source", Pattern.compile("propos", Pattern.CASE_INSENSITIVE));
        MARKERS.put("escalating", Pattern.compile("escalat", Pattern.CASE_INSENSITIVE));
        MARKERS.put("sources that disagree", Pattern.compile("disagree|conflict|contradict|discrepan", Pattern.CASE_INSENSITIVE));
        MARKERS.put("verifying before committing", Pattern.compile("verif|reconfirm|double-check", Pattern.CASE_INSENSITIVE));
        MARKERS.put("asking the tutor", Pattern.compile("tutor", Pattern.CASE_INSENSITIVE));
        MARKERS.put("reporting", Pattern.compile("\\breport", Pattern.CASE_INSENSITIVE));
        MARKERS.put("versions and what changed", Pattern.compile("changes_since|\\bversion", Pattern.CASE_INSENSITIVE));
        MARKERS.put("the notebook discipline", Pattern.compile("notebook|write_note|append_note", Pattern.CASE_INSENSITIVE));
    }
    public static List<String> markersOf(String text) { List<String> m = new ArrayList<>(); if (text != null) for (var e : MARKERS.entrySet()) if (e.getValue().matcher(text).find()) m.add(e.getKey()); return m; }

    // ---- a candidate against its parent --------------------------------------------------------------------------------------------------------------------------------------------

    public static final class Comparison {
        public final int parentUnits; public final List<String> kept, dropped, markersLost;
        Comparison(List<String> kept, List<String> dropped, List<String> markersLost) { this.kept = kept; this.dropped = dropped; this.markersLost = markersLost; this.parentUnits = kept.size() + dropped.size(); }
        /** REVIEW when the candidate keeps fewer than half of its parent's lines, or the parent has a marker of practice that the candidate has not. */
        public boolean review() { return parentUnits > 0 && (kept.size() * 2 < parentUnits || !markersLost.isEmpty()); }
        public JSONObject json() { return new JSONObject().put("parent_units", parentUnits).put("retained_units", kept.size()).put("markers_lost", new JSONArray(markersLost)).put("review", review()); }
        public List<String> lines() {
            List<String> l = new ArrayList<>();
            l.add("  parent comparison (advisory, crude: a line counts as kept if a line of the candidate shares enough of its words): the candidate keeps " + kept.size() + " of the parent's " + parentUnits + " lines; markers of practice in the parent and not in the candidate: "
                    + (markersLost.isEmpty() ? "none" : markersLost) + " — " + (review() ? "REVIEW (read what was dropped before promoting)" : "nothing to review by this measure"));
            if (review()) { int n = 0; for (String d : dropped) { if (n++ >= 6) { l.add("    … and " + (dropped.size() - 6) + " more"); break; } l.add("    dropped: \u201c" + (d.length() > 110 ? d.substring(0, 110) + "…" : d) + "\u201d"); } }
            return l;
        }
    }

    public static Comparison compare(String parentText, String candidateText) {
        var parts = compareUnits(parentText, candidateText); List<String> lost = new ArrayList<>(); List<String> inCand = markersOf(candidateText);
        for (String m : markersOf(parentText)) if (!inCand.contains(m)) lost.add(m);
        return new Comparison(parts.get(0), parts.get(1), lost);
    }
}
