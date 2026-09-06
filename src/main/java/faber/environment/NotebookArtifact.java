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
 * GoalLedger and PENDING INTENTIONS are both tied to the goal-commitment
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

    private final Map<String, String> notes = new LinkedHashMap<>();

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
                notes.put(key, content);
                notifyObsPropertyChanged("notes", oldKeys, currentKeys());
                return List.of();
            }
            case "retract_note": {
                String key = params.getString("key");
                if (!notes.containsKey(key)) {
                    throw new IllegalArgumentException("no such note: " + key);
                }
                JSONArray oldKeys = currentKeys();
                notes.remove(key);
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

    private JSONArray currentKeys() {
        return new JSONArray(List.copyOf(notes.keySet()));
    }

    public static Manual manual() {
        return new Manual(
                NotebookArtifact.type,
                "record, retrieve, and drop your own standing beliefs — content you have "
                + "concluded or derived that is worth keeping independent of any specific goal or plan",
                null, null,
                List.of(new Manual.Param("notes",
                        "the current set of keys you hold a note under — not their content; "
                        + "use read_note to retrieve a specific one")),
                List.of(),
                List.of(
                        new Manual.Operation("write_note(key, content)",
                                "record a note under the given key, creating it or overwriting an existing one",
                                List.of()),
                        new Manual.Operation("retract_note(key)",
                                "drop a note you no longer consider useful or true; fails if the key doesn't exist",
                                List.of()),
                        new Manual.Operation("read_note(key)",
                                "retrieve the full content of a specific note; fails if the key doesn't exist",
                                List.of(new Manual.Param("content", "the note's full content")))
                ),
                null
        );
    }
}
