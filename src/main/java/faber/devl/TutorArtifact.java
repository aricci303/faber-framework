package faber.devl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * The tutor, reachable like any other resource: an `ask(question)` operation. Availability is a per-stage scaffold that fades: FULL answers, HINTS says so and gives only pointers,
 * NONE fails — as in the assessments and in operation. Every exchange is logged with the stage it happened in (the ask rate over stages is a progress signal).
 *
 * The domain's rules are the curated model's, not the tutor's: the scripted tutor points to the model and does not teach a rule. A human at the console can be the tutor instead, and
 * is then free to teach what a scripted one cannot — how to work, where judgment is needed (a curriculum of practice, KNOWLEDGE-LAYERS.md).
 */
public final class TutorArtifact extends Artifact {

    public static final String type = "Tutor";
    public enum Availability { FULL, HINTS, NONE }

    /** Pluggable answering strategy: scripted (repeatable) or a human at the console. */
    public interface Responder { String answer(String question, Availability availability); }

    public static final class Exchange {
        public final String stage, question, answer; public final Availability availability;
        Exchange(String stage, String q, String a, Availability av) { this.stage = stage; this.question = q; this.answer = a; this.availability = av; }
    }

    private final Responder responder; private final String example;      // example: a question the agent might ask, for the manual and the error message (the case's)
    private final List<Exchange> log = new ArrayList<>();
    private volatile Availability availability = Availability.FULL;
    private volatile String stageLabel = "";

    public TutorArtifact(String id, Workspace workspace, Responder responder, String example) {
        super(id, type, workspace);
        this.responder = responder; this.example = example;
        currentObsProperties().put("availability", availability.name().toLowerCase());
    }

    public void setStage(String label, Availability a) {
        this.stageLabel = label;
        Availability old = this.availability;
        this.availability = a;
        notifyObsPropertyChanged("availability", old.name().toLowerCase(), a.name().toLowerCase());
    }

    public synchronized List<Exchange> exchanges() { return new ArrayList<>(log); }
    public synchronized int askCount(String stage) { int n = 0; for (Exchange e : log) if (e.stage.equals(stage)) n++; return n; }

    @Override
    public synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        if (!op.equals("ask"))
            throw new IllegalArgumentException("unknown operation '" + op + "'. The operation of this artifact is: ask(question). get_manual shows what it does.");
        String q = null;
        for (String k : new String[] { "question", "q", "query", "text", "message", "prompt", "content" }) if (p.has(k)) { q = String.valueOf(p.get(k)); break; }
        if (q == null)
            throw new IllegalArgumentException("missing parameter 'question' for ask(question): your question for the tutor, in plain language. You passed: "
                    + (p.isEmpty() ? "no parameters" : p.keySet()) + ". Example: ask(question='" + example + "').");
        if (availability == Availability.NONE) {
            log.add(new Exchange(stageLabel, q, "(unavailable)", availability));
            throw new IllegalStateException("the tutor is not available right now");
        }
        String a = responder.answer(q, availability);
        log.add(new Exchange(stageLabel, q, a, availability));
        return List.of(a);
    }

    public Manual manual() { return manual(example); }

    public static Manual manual(String example) {
        List<Manual.Operation> ops = new ArrayList<>();
        ops.add(new Manual.Operation("ask(question)", "ask the tutor a question in plain language; fails if the tutor is unavailable. Example: ask(question='" + example + "')",
                List.of(new Manual.Param("answer", "the tutor's reply"))));
        return new Manual(type,
                "a tutor you can ask questions about the job you are learning — for clarification, suggestions and advice",
                null, null,
                List.of(new Manual.Param("availability", "full, hints or none — how much help the tutor can currently give")),
                List.of(),
                ops,
                "How much the tutor tells you changes as you progress. At full it answers as well as it can. At hints it says so in every reply and gives only pointers. "
                + "When none, the call fails.");
    }

    /**
     * The scripted tutor: the rules are the curated model's, so a question about one of the case's topics is answered with a pointer to it, and the tutor teaches none of them. The topics, and
     * the keywords that signal each, are the case's (CaseData.tutorTopics); the words of the reply are the framework's (Messages).
     */
    public static Responder pointingToModel(Messages messages, java.util.Map<String, String[]> topicKeywords) {
        return (question, availability) -> {
            String q = question.toLowerCase(Locale.ROOT); List<String> topics = new ArrayList<>();
            for (var e : topicKeywords.entrySet()) for (String kw : e.getValue()) if (q.contains(kw)) { topics.add(e.getKey()); break; }
            return messages.tutorPointer(topics.isEmpty() ? null : String.join(", ", topics), availability != Availability.FULL);
        };
    }

    /** A human at the console answers — free to teach what the scripted tutor cannot. */
    public static Responder humanAtConsole(java.io.BufferedReader in) {
        return (question, availability) -> {
            System.out.println("\n[TUTOR — availability: " + availability + "] the agent asks: " + question);
            System.out.print("[TUTOR] your answer> ");
            System.out.flush();
            try { String line = in.readLine(); return line == null ? "(no answer)" : line; }
            catch (java.io.IOException e) { return "(no answer)"; }
        };
    }
}
