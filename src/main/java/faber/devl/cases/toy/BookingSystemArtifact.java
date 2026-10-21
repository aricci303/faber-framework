package faber.devl.cases.toy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import faber.devl.CaseWorld;
import faber.devl.Feedback;
import faber.devl.Outcome;
import faber.devl.WorkItem;
import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/** The toy case's business system: list the pending booking requests, look at one, grant or refuse it (final). Feedback: none, immediate (practice), or a flag after a wrong decision (audit). */
public final class BookingSystemArtifact extends Artifact implements CaseWorld {
    public static final String type = "BookingSystem";

    private final Map<String, BookingRequest> pending = new LinkedHashMap<>(); private final List<Outcome> outcomes = new ArrayList<>();
    private int world = 1; private Feedback feedback = Feedback.NONE; private Runnable onQueueEmpty = null;

    public BookingSystemArtifact(String id, Workspace workspace) { super(id, type, workspace); currentObsProperties().put("pending_count", 0); }

    @Override @SuppressWarnings("unchecked")
    public synchronized void loadStage(List<? extends WorkItem> items, int world, Feedback feedback) {
        pending.clear(); outcomes.clear(); this.world = world; this.feedback = feedback; this.onQueueEmpty = null;
        for (BookingRequest r : (List<BookingRequest>) items) pending.put(r.id, r);
        notifyObsPropertyChanged("pending_count", currentObsProperties().get("pending_count"), pending.size());
    }
    @Override public synchronized void setOnQueueEmpty(Runnable r) { this.onQueueEmpty = r; }
    public synchronized List<String> pendingIds() { return new ArrayList<>(pending.keySet()); }
    @Override public synchronized int pendingCount() { return pending.size(); }
    @Override public synchronized int decidedCount() { return outcomes.size(); }
    @Override public synchronized int correctCount() { int n = 0; for (Outcome o : outcomes) if (o.correct) n++; return n; }
    @Override public synchronized List<Outcome> outcomes() { return new ArrayList<>(outcomes); }

    @Override
    public synchronized List<Object> doOperation(String op, JSONObject p) {
        switch (op) {
            case "list_pending": { StringBuilder sb = new StringBuilder(); for (BookingRequest r : pending.values()) sb.append(r.summary()).append('\n'); return List.of(sb.length() == 0 ? "no pending requests" : sb.toString().stripTrailing()); }
            case "get_request": return List.of(need(p).toJson().toString());
            case "grant_request": return decide(need(p), "grant");
            case "refuse_request": return decide(need(p), "refuse");
            default: throw new IllegalArgumentException("unknown operation '" + op + "' (list_pending, get_request, grant_request, refuse_request)");
        }
    }

    private BookingRequest need(JSONObject p) {
        String id = p.optString("request_id", "");
        BookingRequest r = pending.get(id);
        if (r == null) throw new IllegalArgumentException(id.isEmpty() ? "missing parameter 'request_id'" : "no pending request " + id);
        return r;
    }

    private List<Object> decide(BookingRequest r, String label) {
        pending.remove(r.id); boolean ok = r.expected(world).equals(label);
        outcomes.add(new Outcome(r.id, label, ok, r.expected(world)));
        currentObsProperties().put("pending_count", pending.size());
        String reply = feedback == Feedback.PRACTICE ? (ok ? "recorded — correct" : "recorded — incorrect: " + r.rule(world))
                     : feedback == Feedback.AUDIT && !ok ? "recorded — flagged by audit: " + r.rule(world) : "recorded";
        if (pending.isEmpty() && onQueueEmpty != null) { Runnable run = onQueueEmpty; onQueueEmpty = null; run.run(); }
        return List.of(reply);
    }

    public static Manual manual() {
        return new Manual(type, "the office's meeting-room booking requests: look at what is pending and grant or refuse each, once and for good", null, null,
                List.of(new Manual.Param("pending_count", "how many requests are waiting")), List.of(),
                List.of(new Manual.Operation("list_pending()", "the requests waiting, one per line", List.of(new Manual.Param("requests", "id, person, room kind, hours"))),
                        new Manual.Operation("get_request(request_id)", "one request in full", List.of(new Manual.Param("request", "its fields"))),
                        new Manual.Operation("grant_request(request_id)", "grant it; final", List.of(new Manual.Param("result", "'recorded', with feedback where the stage gives it"))),
                        new Manual.Operation("refuse_request(request_id)", "refuse it; final", List.of(new Manual.Param("result", "'recorded', with feedback where the stage gives it")))),
                "A decision cannot be changed. Depending on the stage, the system tells you at once whether a decision was right, flags a wrong one later, or says nothing.");
    }
}
