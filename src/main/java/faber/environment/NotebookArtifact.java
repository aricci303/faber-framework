package faber.environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A general-purpose belief store — the fourth standard artifact,
 * completing a natural quartet alongside UserConsole (communication),
 * WorkspaceArtifact (lifecycle), and Alarm (time): this one is memory.
 *
 * None of the three existing persistence mechanisms fit this role.
 * GoalLedger and ONGOING INTENTIONS are both tied to the goal-commitment
 * structure — desires and plans, not beliefs. Belief-mapped observable
 * properties elsewhere (inbox_count, fired_alarms, last_request) are
 * all artifact-owned: the artifact's own logic decides the true value,
 * and the agent can only read it. A mental note is the opposite —
 * agent-declared, agent-owned, and — crucially — agent-retractable.
 * Nothing else in this architecture currently lets an agent deliberately
 * say "this no longer applies, drop it"; retract_note is the first
 * place that becomes possible.
 *
 * Follows the same lightweight-property/full-detail-operation split
 * already used for EmailArtifact.inbox_count/list_emails and
 * AlarmArtifact.fired_alarms/list_fired_alarms: the "notes" property
 * shows only the current set of keys, cheaply, always current, so an
 * agent never loses track of *having* notes even across a long gap —
 * exactly the manuals/last_request lesson applied here. Full content
 * is deliberately not always shown in full, unlike a manual: unlike an
 * operation signature, a note's relevance genuinely varies by cycle,
 * and read_note(key) is a legitimate deliberate epistemic act, not
 * something an agent needs unconditionally to be able to act at all.
 */
public final class NotebookArtifact extends Artifact {

	public static final String type = "Notebook";

    // Artifact.invoke() now guarantees doOperation() is never called concurrently for a given
    // instance — every artifact is a monitor, matching real A&A/CArtAgO semantics — so a plain,
    // unsynchronized map here is genuinely safe: no artifact-specific locking is needed, or should
    // be added, since the guarantee already comes from the base class, uniformly, for every artifact.
    private final Map<String, String> notes = new LinkedHashMap<>();

    /**
     * Safe mode, off by default (so every earlier result stays comparable): -Dfaber.notebook.safe=true.
     *
     * The manual says write_note overwrites. In a long run an agent that meant to ADD to a note ("persisting the lodging policy into the
     * note so it isn't lost") replaced an 8,858-character consolidated note with 579 characters, losing the policies the tutor had
     * taught; later consolidation stored the resulting gap as fact. Safe mode keeps the semantics and makes the loss visible and
     * recoverable: write_note says what it replaced (and warns if the new content is much shorter); append_note adds instead of
     * replacing; restore_note brings back the previous version of a note, including one that was retracted.
     */
    /**
     * Harness-side, not an operation: puts the Notebook into a given state, replacing everything and clearing the restore history. Used to instantiate an
     * agent from a knowledge snapshot (what a developed agent had written down), before the agent starts. The agent cannot call it.
     */
    public synchronized void restoreNotes(Map<String, String> state) {
        JSONArray oldKeys = currentKeys();
        notes.clear(); previous.clear();
        notes.putAll(state);
        notifyObsPropertyChanged("notes", oldKeys, currentKeys());
    }

    public static boolean safeMode() { return Boolean.getBoolean("faber.notebook.safe"); }

    private final Map<String, String> previous = new LinkedHashMap<>();   // one level of history per key, for restore_note (safe mode)


    public NotebookArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "write_note": {
                String key = params.getString("key");
                String content = params.getString("content");
                JSONArray oldKeys = currentKeys();
                String replaced = notes.put(key, content);
                notifyObsPropertyChanged("notes", oldKeys, currentKeys());
                if (!safeMode()) return List.of();
                if (replaced == null) return List.of("created '" + key + "' (" + content.length() + " chars)");
                previous.put(key, replaced);
                String msg = "replaced '" + key + "': " + replaced.length() + " chars -> " + content.length() + " chars";
                if (content.length() < replaced.length() / 2)
                    msg += ". WARNING: the new content is less than half the length of what it replaced. If you meant to ADD to the note, use append_note; "
                         + "restore_note('" + key + "') brings the previous version back";
                return List.of(msg);
            }
            case "append_note": {
                if (!safeMode()) throw new IllegalArgumentException("unknown operation: append_note");
                String key = params.getString("key");
                String content = params.getString("content");
                JSONArray oldKeys = currentKeys();
                String old = notes.get(key);
                if (old != null) previous.put(key, old);
                notes.put(key, old == null ? content : old + "\n" + content);
                notifyObsPropertyChanged("notes", oldKeys, currentKeys());
                return List.of((old == null ? "created '" : "appended to '") + key + "' (now " + notes.get(key).length() + " chars)");
            }
            case "restore_note": {
                if (!safeMode()) throw new IllegalArgumentException("unknown operation: restore_note");
                String key = params.getString("key");
                String prev = previous.get(key);
                if (prev == null) throw new IllegalArgumentException("no previous version of: " + key);
                JSONArray oldKeys = currentKeys();
                String current = notes.put(key, prev);
                if (current != null) previous.put(key, current); else previous.remove(key);   // restoring twice swaps back
                notifyObsPropertyChanged("notes", oldKeys, currentKeys());
                return List.of("restored '" + key + "' (" + prev.length() + " chars)");
            }
            case "retract_note": {
                String key = params.getString("key");
                if (!notes.containsKey(key)) {
                    throw new IllegalArgumentException("no such note: " + key);
                }
                JSONArray oldKeys = currentKeys();
                String removed = notes.remove(key);
                if (safeMode() && removed != null) previous.put(key, removed);   // a retracted note can be restored
                notifyObsPropertyChanged("notes", oldKeys, currentKeys());
                return List.of();
            }
            case "read_note": {
                String key = params.getString("key");
                if (!notes.containsKey(key)) {
                    throw new IllegalArgumentException("no such note: " + key);
                }
                return List.of(notes.get(key));
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /** Read-only snapshot for scenario drivers and result dumps; not an agent operation. */
    public synchronized Map<String, String> dump() { return new LinkedHashMap<>(notes); }

    private JSONArray currentKeys() {
        return new JSONArray(List.copyOf(notes.keySet()));
    }

    public static Manual manual() {
        boolean safe = safeMode();
        List<Manual.Operation> ops = new java.util.ArrayList<>();
        ops.add(new Manual.Operation("write_note(key, content)",
                safe ? "record a note under the given key. If the key already exists its content is REPLACED, not added to: include everything you want to keep. "
                       + "The result says what was replaced, and warns if the new content is much shorter"
                     : "record a note under the given key, creating it or overwriting an existing one",
                safe ? List.of(new Manual.Param("result", "what was created or replaced, with sizes")) : List.of()));
        if (safe) {
            ops.add(new Manual.Operation("append_note(key, content)", "add content to the end of an existing note (or create it), keeping what is already there",
                    List.of(new Manual.Param("result", "the note's new size"))));
            ops.add(new Manual.Operation("restore_note(key)", "bring back the previous version of a note that was replaced or retracted (restoring twice swaps back)",
                    List.of(new Manual.Param("result", "confirmation"))));
        }
        ops.add(new Manual.Operation("retract_note(key)",
                safe ? "drop a note you no longer consider useful or true; fails if the key doesn't exist. It can still be brought back with restore_note"
                     : "drop a note you no longer consider useful or true; fails if the key doesn't exist",
                List.of()));
        ops.add(new Manual.Operation("read_note(key)",
                "retrieve the full content of a specific note; fails if the key doesn't exist",
                List.of(new Manual.Param("content", "the note's full content"))));
        return new Manual(
                NotebookArtifact.type,
                "record, retrieve, and drop your own standing beliefs — content you have "
                + "concluded or derived that is worth keeping independent of any specific goal or plan",
                null, null,
                List.of(new Manual.Param("notes",
                        "the current set of keys you hold a note under — not their content; "
                        + "use read_note to retrieve a specific one")),
                List.of(),
                ops,
                null
        );
    }
}
