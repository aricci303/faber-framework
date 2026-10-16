package faber.devl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * The tutor, reachable like any other resource: an `ask(question)` operation. Availability is a
 * per-stage scaffold that fades: FULL answers with the substance, HINTS points the asker back to
 * its own notes without giving the content, NONE fails — as in the assessments. Every exchange is
 * logged (with the stage it happened in), both as an instrument (ask rate over stages is a progress
 * signal) and as a seed for a scripted tutor once a human-backed one has produced real transcripts.
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

    private final Responder responder;
    private final List<Exchange> log = new ArrayList<>();
    private volatile Availability availability = Availability.FULL;
    private volatile String stageLabel = "";
    private volatile boolean recallEnabled = false;
    private final java.util.Map<String, Integer> recalls = new java.util.TreeMap<>();

    /**
     * Optional scaffold, off by default: recall() returns the transcript of everything the tutor has said so far, while the tutor is fully
     * available (stages 1-2 and their debriefs). It is the agent's own record, like lecture notes: not new help. It fades with the tutor:
     * not at hints (stage 3 tests what the agent KEPT) and not in the assessments.
     * Motivation, from a long run: tutor answers lived in the agent's context for a few cycles, a later rewrite of its notes dropped them,
     * the next consolidation was written from memory and stored the gap as a "method lesson" about the tutor, and every later stage inherited
     * it. With a transcript, consolidation can be done from the source instead of from memory.
     */
    public void setRecallEnabled(boolean on) { this.recallEnabled = on; }
    public synchronized int recallCount(String stage) { return recalls.getOrDefault(stage, 0); }
    public synchronized java.util.Map<String, Integer> recallCounts() { return new java.util.TreeMap<>(recalls); }

    public TutorArtifact(String id, Workspace workspace, Responder responder) {
        super(id, type, workspace);
        this.responder = responder;
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
    protected synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        if (op.equals("recall")) {
            if (!recallEnabled) throw new IllegalArgumentException("unknown operation: recall");
            if (availability == Availability.NONE) throw new IllegalStateException("the tutor is not available right now");
            // Recall fades WITH the tutor, not after it: at hints the stage is meant to test whether the agent can apply what IT kept. If the
            // transcript were still readable there, the agent could lean on the tutor's old answers instead of its own notes, and the stage
            // would look clean even if the notes had been destroyed — hiding exactly the failure the transcript exists to prevent.
            if (availability != Availability.FULL) throw new IllegalStateException("recall is only offered while the tutor is fully available; at this stage the tutor is giving hints only");
            recalls.merge(stageLabel, 1, Integer::sum);
            if (log.isEmpty()) return List.of("(nothing yet: you have not asked the tutor anything)");
            StringBuilder sb = new StringBuilder(); int i = 1;
            for (Exchange e : log) { if (e.answer.equals("(unavailable)")) continue; sb.append("#").append(i++).append(" [").append(e.stage).append("] you asked: ").append(e.question).append("\n   the tutor answered: ").append(e.answer).append("\n\n"); }
            return List.of(sb.toString().trim());
        }
        if (!op.equals("ask"))
            throw new IllegalArgumentException("unknown operation '" + op + "'. The operations of this artifact are: ask(question)" + (recallEnabled ? "; recall()" : "") + ". get_manual shows what each does.");
        String q = null;
        for (String k : new String[] { "question", "q", "query", "text", "message", "prompt", "content" }) if (p.has(k)) { q = String.valueOf(p.get(k)); break; }
        if (q == null)
            throw new IllegalArgumentException("missing parameter 'question' for ask(question): your question for the tutor, in plain language. You passed: "
                    + (p.isEmpty() ? "no parameters" : p.keySet()) + ". Example: ask(question='What is the policy for meals?').");
        if (availability == Availability.NONE) {
            log.add(new Exchange(stageLabel, q, "(unavailable)", availability));
            throw new IllegalStateException("the tutor is not available right now");
        }
        String a = responder.answer(q, availability);
        log.add(new Exchange(stageLabel, q, a, availability));
        return List.of(a);
    }

    public static Manual manual() { return manual(false); }

    /** recall must match setRecallEnabled: the manual lists recall() only when the tutor really offers it. */
    public static Manual manual(boolean recall) {
        List<Manual.Operation> ops = new ArrayList<>();
        ops.add(new Manual.Operation("ask(question)", "ask the tutor a question in plain language; fails if the tutor is unavailable. Example: ask(question='What is the policy for meals?')", List.of(new Manual.Param("answer", "the tutor's reply"))));
        if (recall) ops.add(new Manual.Operation("recall()", "everything the tutor has told you so far, as a transcript of your questions and its answers; only offered while the tutor is fully available, like the tutor's own explanations",
                List.of(new Manual.Param("transcript", "your earlier questions and the tutor's answers"))));
        return new Manual(type,
                "a tutor you can ask questions about the job you are learning — for clarification, suggestions and advice",
                null, null,
                List.of(new Manual.Param("availability", "full, hints or none — how much help the tutor can currently give")),
                List.of(),
                ops,
                "How much the tutor tells you changes as you progress. At full it explains the rules, with examples. At hints it says so in every reply, and "
                + "points you back to what you were already told, without repeating it. When none, the call fails. A reply always says which of these it is.");
    }

    private static final java.util.Map<String, String[]> KEYWORDS = new java.util.LinkedHashMap<>();
    static {
        KEYWORDS.put("procedure", new String[] { "procedure", "precedence", "sequence", "order of", "in what order", "which order", "check first", "checks first" });
        KEYWORDS.put("receipts", new String[] { "receipt" });
        KEYWORDS.put("duplicates", new String[] { "duplicat" });
        KEYWORDS.put("meals", new String[] { "meal", "lunch", "dinner", "attendee" });
        KEYWORDS.put("flights", new String[] { "flight", "business class", "economy", "cabin" });   // "business" alone is NOT business class
        KEYWORDS.put("lodging", new String[] { "lodging", "hotel", "night" });
        KEYWORDS.put("software", new String[] { "software", "vendor", "licen" });
        KEYWORDS.put("equipment", new String[] { "equipment" });
        KEYWORDS.put("training", new String[] { "training", "course", "manager_approved", "manager approval" });
    }

    /** A repeatable tutor that knows the company's policy for the pilot domain; answers every topic the question mentions. */
    public static Responder scriptedExpenseTutor(ExpensePolicy policyToTeach) { return scriptedExpenseTutor(policyToTeach, 0); }

    /**
     * As above, but with maxTopics > 0 the tutor answers at most maxTopics topics — those it has not explained before first,
     * each group in the order the question mentions them — and says which others to ask about separately — so a single broad question cannot
     * transfer the whole policy at once, and the agent has to prioritise and ask again. 0 means no limit.
     */
    public static Responder scriptedExpenseTutor(ExpensePolicy policyToTeach, int maxTopics) {
        final java.util.Set<String> taught = new java.util.LinkedHashSet<>();   // what this tutor has already explained
        return (question, availability) -> {
            String q = question.toLowerCase(Locale.ROOT);
            java.util.TreeMap<Integer, String> byPosition = new java.util.TreeMap<>();
            for (var e : KEYWORDS.entrySet()) {
                int pos = Integer.MAX_VALUE;
                for (String kw : e.getValue()) { int p = q.indexOf(kw); if (p >= 0) pos = Math.min(pos, p); }
                if (pos != Integer.MAX_VALUE) byPosition.merge(pos, e.getKey(), (a, b) -> a);
            }
            List<String> topics = new ArrayList<>(byPosition.values());
            if (maxTopics > 0 && topics.size() > maxTopics) {
                // Under a cap, topics not explained before come first: a topic mentioned only as background ("I already
                // know the meals rules, but...") must not use up the answer that the new topics need.
                List<String> fresh = new ArrayList<>(), again = new ArrayList<>();
                for (String tp : topics) (taught.contains(tp) ? again : fresh).add(tp);
                topics = new ArrayList<>(fresh); topics.addAll(again);
            }
            final String HINTS_ONLY = "At this stage I can only give hints, not the rules themselves. ";
            if (topics.isEmpty())
                return availability == Availability.FULL
                        ? "I can explain the approval rules for: procedure (the order of the checks), receipts, duplicates, meals, flights, lodging, software, equipment and training. "
                          + "Ask about a topic, or describe an expense you are unsure about."
                        : HINTS_ONLY + "Which categories of expense do you see, and what do you already have written down about each?";
            List<String> answered = maxTopics > 0 && topics.size() > maxTopics ? topics.subList(0, maxTopics) : topics;
            List<String> deferred = topics.subList(answered.size(), topics.size());
            StringBuilder sb = new StringBuilder();
            if (availability != Availability.FULL) sb.append(HINTS_ONLY);
            for (String tp : answered) {
                if (availability == Availability.FULL) { sb.append(policyToTeach.explain(tp)).append(" "); taught.add(tp); }
                else if (taught.contains(tp)) sb.append("On ").append(tp).append(": I explained this to you earlier. Look for it in your own notes first. ");
                else sb.append("On ").append(tp).append(": I have not explained this to you. There are specific limits and conditions; check your own notes for anything you have learned about it, and test your reading of the expense in front of you against that. ");
            }
            if (!deferred.isEmpty()) sb.append("That is as much as I will cover in one answer — ask me separately about: ").append(String.join(", ", deferred)).append(".");
            return sb.toString().trim();
        };
    }

    /** A human at the console answers — the Wizard-of-Oz option for the first real runs. */
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
