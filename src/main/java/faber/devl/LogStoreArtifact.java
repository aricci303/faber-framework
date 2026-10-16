package faber.devl;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/** A creatable append-only log with tags — suited to episodes and lessons: what happened, what was tried, what worked. */
public final class LogStoreArtifact extends Artifact {
    public static final String type = "LogStore";
    private static final class Entry { final int n; final String text; final String tag; Entry(int n, String t, String g) { this.n = n; this.text = t; this.tag = g; } }
    private final List<Entry> entries = new ArrayList<>();

    public LogStoreArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
        currentObsProperties().put("entry_count", 0);
    }

    public synchronized List<String> dump() { List<String> l = new ArrayList<>(); for (Entry e : entries) l.add("#" + e.n + " [" + e.tag + "] " + e.text); return l; }

    @Override
    protected synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        switch (op) {
            case "append": { int old = entries.size();
                             entries.add(new Entry(old + 1, p.getString("entry"), p.optString("tag", "untagged")));
                             notifyObsPropertyChanged("entry_count", old, entries.size()); return List.of(old + 1); }
            case "search": { String tag = p.getString("tag"); StringBuilder sb = new StringBuilder();
                             for (Entry e : entries) if (e.tag.equalsIgnoreCase(tag)) sb.append("#").append(e.n).append(" ").append(e.text).append("\n");
                             return List.of(sb.length() == 0 ? "(no entries with that tag)" : sb.toString().trim()); }
            case "recent": { int n = p.has("n") ? p.getInt("n") : 5; StringBuilder sb = new StringBuilder();
                             for (int i = Math.max(0, entries.size() - n); i < entries.size(); i++)
                                 sb.append("#").append(entries.get(i).n).append(" [").append(entries.get(i).tag).append("] ").append(entries.get(i).text).append("\n");
                             return List.of(sb.length() == 0 ? "(empty)" : sb.toString().trim()); }
            default: throw new IllegalArgumentException("unknown operation: " + op);
        }
    }

    public static Manual manual() {
        return new Manual(type,
                "an append-only log for episodes and lessons — what happened, what you tried, what worked — each entry optionally tagged and searchable by tag",
                "LogStore()", "creates an empty log; give it a purpose when you create it, so you know when to consult it",
                List.of(new Manual.Param("entry_count", "how many entries the log holds")),
                List.of(),
                List.of(new Manual.Operation("append(entry, tag)", "add an entry; tag is optional", List.of(new Manual.Param("entry_number", "its number in the log"))),
                        new Manual.Operation("search(tag)", "every entry with the given tag", List.of(new Manual.Param("entries", "matching entries"))),
                        new Manual.Operation("recent(n)", "the last n entries (default 5)", List.of(new Manual.Param("entries", "recent entries")))),
                null);
    }
}
