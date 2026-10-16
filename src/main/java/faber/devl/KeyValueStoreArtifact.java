package faber.devl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/** A creatable store for named pieces of knowledge — the agent can make several, each with its own purpose. */
public final class KeyValueStoreArtifact extends Artifact {
    public static final String type = "KeyValueStore";
    private final Map<String, String> entries = new LinkedHashMap<>();

    public KeyValueStoreArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
        currentObsProperties().put("keys", new JSONArray());
    }

    public synchronized Map<String, String> dump() { return new LinkedHashMap<>(entries); }

    @Override
    protected synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        switch (op) {
            case "put": { JSONArray old = keys(); entries.put(p.getString("key"), p.getString("value"));
                          notifyObsPropertyChanged("keys", old, keys()); return List.of(); }
            case "get": { String k = p.getString("key");
                          if (!entries.containsKey(k)) throw new IllegalArgumentException("no such key: " + k);
                          return List.of(entries.get(k)); }
            case "delete": { String k = p.getString("key");
                          if (!entries.containsKey(k)) throw new IllegalArgumentException("no such key: " + k);
                          JSONArray old = keys(); entries.remove(k); notifyObsPropertyChanged("keys", old, keys()); return List.of(); }
            case "list": { StringBuilder sb = new StringBuilder();
                          for (var e : entries.entrySet()) {
                              String v = e.getValue().replace('\n', ' ');
                              sb.append(e.getKey()).append(": ").append(v.length() > 70 ? v.substring(0, 70) + "..." : v).append("\n");
                          }
                          return List.of(entries.isEmpty() ? "(empty)" : sb.toString().trim()); }
            default: throw new IllegalArgumentException("unknown operation: " + op);
        }
    }
    private JSONArray keys() { return new JSONArray(List.copyOf(entries.keySet())); }

    public static Manual manual() {
        return new Manual(type,
                "hold named pieces of knowledge: put a value under a key, look it up later by key, list what is stored",
                "KeyValueStore()", "creates an empty store; give it a purpose when you create it, so you know when to consult it",
                List.of(new Manual.Param("keys", "the keys currently stored — not their values")),
                List.of(),
                List.of(new Manual.Operation("put(key, value)", "store a value under a key, creating or overwriting it", List.of()),
                        new Manual.Operation("get(key)", "the value under a key; fails if the key does not exist", List.of(new Manual.Param("value", "the stored value"))),
                        new Manual.Operation("delete(key)", "remove a key; fails if it does not exist", List.of()),
                        new Manual.Operation("list()", "every key with a short preview of its value", List.of(new Manual.Param("entries", "one line per key")))),
                null);
    }
}
