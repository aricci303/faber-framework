package faber.devl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * The simulated expense system. It holds a queue of pending claims and records the agent's decisions
 * against a hidden ground-truth policy. Two scaffolds can be switched per stage without changing the
 * interface: PRACTICE feedback (each decision answers whether it matched policy, quoting the clause)
 * and, in the policy-change assessment, AUDIT flags (a decision that violates the current policy is flagged after the
 * fact, quoting the current rule). The interface and manual are identical in every stage — what the
 * agent practices on is what it will use.
 */
public final class ExpenseSystemArtifact extends Artifact {

    public static final String type = "ExpenseSystem";
    public enum Feedback { NONE, PRACTICE, AUDIT }

    public static final class Decision {
        public final String expenseId, kind, to; public final boolean correct; public final String expected;
        public final String reason;                       // optional free text the agent gave; recorded, never scored
        Decision(String id, String kind, String to, boolean correct, String expected) { this(id, kind, to, correct, expected, null); }
        Decision(String id, String kind, String to, boolean correct, String expected, String reason) {
            this.expenseId = id; this.kind = kind; this.to = to; this.correct = correct; this.expected = expected; this.reason = reason;
        }
    }

    private final Map<String, ExpenseCase> pending = new LinkedHashMap<>();
    private final List<Decision> decisions = new ArrayList<>();
    private ExpensePolicy policy = ExpensePolicy.v1();
    private Feedback feedback = Feedback.NONE;
    private Runnable onQueueEmpty = null;
    private boolean policyCue = false;                                       // does the system say which policy version is in force?
    private boolean decidedVisible = false;                                   // can get_expense still return a claim once it has been decided?
    private final Map<String, ExpenseCase> decidedCases = new LinkedHashMap<>();

    public ExpenseSystemArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
        currentObsProperties().put("pending_count", 0);
    }

    /** Driver-side: load the next stage's queue and scaffolds. Not an agent operation. */
    public synchronized void loadStage(List<ExpenseCase> cases, ExpensePolicy policy, Feedback feedback) {
        pending.clear();
        decisions.clear();
        decidedCases.clear();
        for (ExpenseCase c : cases) pending.put(c.id, c);
        this.policy = policy;
        this.feedback = feedback;
        notifyObsPropertyChanged("pending_count", currentObsProperties().get("pending_count"), pending.size());
        if (policyCue) {
            Object old = currentObsProperties().get("policy_version");
            if (old == null) currentObsProperties().put("policy_version", policy.name);
            else notifyObsPropertyChanged("policy_version", old, policy.name);
        }
    }

    /** Driver-side hook, run at the exact moment the last pending claim is decided (inside the artifact's monitor). */
    public synchronized void setOnQueueEmpty(Runnable r) { this.onQueueEmpty = r; }

    /**
     * By default a decided claim disappears: get_expense fails for it ("no pending expense"). Real systems keep records,
     * and the default created a quirk that cost agents effort in two runs (a note about how to verify a duplicate that can
     * no longer be fetched; a final report unable to say why an earlier decision was flagged). With visible=true, get_expense
     * also returns decided claims, marked with the decision that was recorded (never whether it was right).
     */
    public synchronized void setDecidedVisible(boolean visible) { this.decidedVisible = visible; }

    /**
     * A detectable cue that the policy changed: with the cue on, every claim record carries the policy version in
     * force and the system exposes it as an observable property. Development and the clean assessment show "v1"; the
     * policy-change assessment shows "v2". It says nothing about WHAT changed. Off by default: then the change is
     * silent, and only an audit flag after a decision can reveal it.
     */
    public synchronized void setPolicyCue(boolean cue) { this.policyCue = cue; }

    /** Driver-side: ids of the claims still awaiting a decision (for scripted agents in tests). */
    public synchronized List<String> pendingIds() { return new ArrayList<>(pending.keySet()); }

    public synchronized int pendingCount() { return pending.size(); }
    public synchronized int decidedCount() { return decisions.size(); }
    public synchronized int correctCount() { int n = 0; for (Decision d : decisions) if (d.correct) n++; return n; }
    public synchronized List<Decision> decisions() { return new ArrayList<>(decisions); }

    // ---------------------------------------------------------------------------------------------------------
    // One table describes every operation: what it is for, its parameters, what it does to the world, and an example call.
    // The manual, the parameter checking and the error messages are all generated from it, so the documentation cannot drift away from what
    // the system does; DevlPilotCheck executes every example and checks every claim the manual makes.
    // ---------------------------------------------------------------------------------------------------------
    static final class Usage {
        final String name, summary, effect, exampleText; final List<String> required, optional; final JSONObject exampleParams; final boolean decision;
        Usage(String name, List<String> required, List<String> optional, String summary, String effect, JSONObject exampleParams, boolean decision) {
            this.name = name; this.required = required; this.optional = optional; this.summary = summary; this.effect = effect; this.exampleParams = exampleParams; this.decision = decision;
            StringBuilder sb = new StringBuilder(name).append("(");
            boolean first = true;
            List<String> order = new ArrayList<>(required); order.addAll(optional);       // the signature's order, not the JSON library's hash order
            for (String k : order) if (exampleParams.has(k)) { sb.append(first ? "" : ", ").append(k).append("='").append(exampleParams.get(k)).append("'"); first = false; }
            this.exampleText = sb.append(")").toString();
        }
        String signature() {
            return name + "(" + String.join(", ", required) + (optional.isEmpty() ? "" : "[, " + String.join(", ", optional) + "]") + ")";
        }
    }

    private static final String FINAL = "FINAL: once recorded, a decision cannot be changed or withdrawn, and no operation revises it. "
            + "reason is optional free text, recorded with the decision.";
    static final List<Usage> USAGE = List.of(
        new Usage("list_pending", List.of(), List.of(), "one-line summary of every expense still awaiting a decision: id, employee, category, amount, date.",
                "Read-only.", new JSONObject(), false),
        new Usage("get_expense", List.of("expense_id"), List.of(), "the full details of one expense (%GET%).",
                "Read-only.", new JSONObject().put("expense_id", "E123"), false),
        new Usage("approve_expense", List.of("expense_id"), List.of("reason"), "record approval of a pending expense.",
                FINAL, new JSONObject().put("expense_id", "E123"), true),
        new Usage("reject_expense", List.of("expense_id"), List.of("reason"), "record rejection of a pending expense.",
                FINAL, new JSONObject().put("expense_id", "E123").put("reason", "no receipt"), true),
        new Usage("escalate_expense", List.of("expense_id", "to"), List.of("reason"), "send a pending expense up to a role for decision. to is required: manager, director or it.",
                FINAL, new JSONObject().put("expense_id", "E123").put("to", "manager"), true));

    /** Names an agent plausibly uses for a parameter, taken from what agents actually did in the logs (id, claim_id, role). A wrong name is read, and said so. */
    private static final java.util.Map<String, List<String>> ALIASES = java.util.Map.of(
        "expense_id", List.of("id", "claim_id", "claim", "expense", "expenseid", "expense-id", "claimid"),
        "to", List.of("role", "target", "escalate_to", "to_role", "recipient"),
        "reason", List.of("note", "comment", "why", "justification"));

    private static final java.util.Map<String, String> PARAM_HELP = java.util.Map.of(
        "expense_id", "the id of the expense, as shown by list_pending(), for example E123",
        "to", "the role to escalate to: manager, director or it");

    static Usage usage(String op) { for (Usage u : USAGE) if (u.name.equals(op)) return u; return null; }

    private static final class Args { final JSONObject p = new JSONObject(); final List<String> notes = new ArrayList<>(); }

    /** Reads the call against the table: a wrong parameter name is accepted with a visible note; a missing one is a clear error that says nothing was recorded. */
    private static Args check(Usage u, JSONObject given) {
        Args a = new Args();
        java.util.Set<String> used = new java.util.HashSet<>();
        List<String> all = new ArrayList<>(u.required); all.addAll(u.optional);
        for (String name : all) {
            if (given.has(name)) { a.p.put(name, given.get(name)); used.add(name); continue; }
            for (String alias : ALIASES.getOrDefault(name, List.of())) {
                if (given.has(alias)) {
                    a.p.put(name, given.get(alias)); used.add(alias);
                    a.notes.add("note: the parameter '" + alias + "' was read as '" + name + "'; the parameter is called " + name + ".");
                    break;
                }
            }
            if (!a.p.has(name) && u.required.contains(name))
                throw new IllegalArgumentException("missing parameter '" + name + "' for " + u.signature() + ": " + PARAM_HELP.getOrDefault(name, name)
                        + ". You passed: " + (given.isEmpty() ? "no parameters" : given.keySet()) + (u.decision ? ". Nothing was recorded" : "")
                        + ". Example: " + u.exampleText + ".");
        }
        for (String k : given.keySet()) if (!used.contains(k)) a.notes.add("note: the parameter '" + k + "' is not used by " + u.name + " and was ignored.");
        return a;
    }

    private String unknownId(String id) {
        List<String> ids = new ArrayList<>(pending.keySet());
        return "no expense " + id + " is pending" + (ids.isEmpty() ? " (the queue is empty)"
                : ". Pending now: " + String.join(", ", ids.subList(0, Math.min(12, ids.size()))) + (ids.size() > 12 ? ", ..." : "") + ". list_pending() shows them");
    }

    private String alreadyDecided(String id) {
        Decision d = null; for (Decision x : decisions) if (x.expenseId.equals(id)) d = x;
        return "expense " + id + " was already decided (" + (d == null ? "?" : d.kind + (d.to != null ? ":" + d.to : "")) + ")";
    }

    @Override
    protected synchronized List<Object> doOperation(String op, JSONObject given) throws Exception {
        Usage u = usage(op);
        if (u == null) {
            StringBuilder sb = new StringBuilder("unknown operation '" + op + "'. The operations of this artifact are: ");
            for (int i = 0; i < USAGE.size(); i++) sb.append(i > 0 ? "; " : "").append(USAGE.get(i).signature());
            throw new IllegalArgumentException(sb.append(". get_manual shows what each does.").toString());
        }
        Args a = check(u, given);
        JSONObject p = a.p;
        String main;
        switch (op) {
            case "list_pending": {
                StringBuilder sb = new StringBuilder();
                for (ExpenseCase c : pending.values()) sb.append(c.summary()).append("\n");
                main = pending.isEmpty() ? "(no pending expenses)" : sb.toString().trim();
                break;
            }
            case "get_expense": {
                String id = String.valueOf(p.get("expense_id"));
                ExpenseCase c = pending.get(id);
                if (c != null) {
                    JSONObject j = c.toJson(); if (decidedVisible) j.put("status", "pending"); if (policyCue) j.put("policy_version", policy.name);
                    main = j.toString();
                    break;
                }
                ExpenseCase done = decidedCases.get(id);
                if (done == null) throw new IllegalArgumentException(unknownId(id));
                if (!decidedVisible) throw new IllegalArgumentException(alreadyDecided(id) + ", and decided expenses can no longer be retrieved or changed");
                Decision d = null; for (Decision x : decisions) if (x.expenseId.equals(id)) d = x;
                JSONObject dj = done.toJson().put("status", "decided: " + (d == null ? "?" : d.kind + (d.to != null ? ":" + d.to : "")));
                if (policyCue) dj.put("policy_version", policy.name);
                main = dj.toString();
                break;
            }
            case "approve_expense": main = decide(String.valueOf(p.get("expense_id")), "approve", null, reasonOf(p)); break;
            case "reject_expense": main = decide(String.valueOf(p.get("expense_id")), "reject", null, reasonOf(p)); break;
            default: {   // escalate_expense
                String to = String.valueOf(p.get("to")).trim().toLowerCase();
                if (!List.of("manager", "director", "it").contains(to))
                    throw new IllegalArgumentException("'" + p.get("to") + "' is not a role to escalate to. Use manager, director or it. Nothing was recorded. Example: " + u.exampleText + ".");
                main = decide(String.valueOf(p.get("expense_id")), "escalate", to, reasonOf(p));
            }
        }
        List<Object> out = new ArrayList<>(); out.add(main); out.addAll(a.notes);
        return out;
    }

    private static String reasonOf(JSONObject p) { return p.has("reason") ? String.valueOf(p.get("reason")) : null; }

    private String decide(String id, String kind, String to, String reason) {
        ExpenseCase c = pending.get(id);
        if (c == null) {
            if (decidedCases.containsKey(id)) throw new IllegalArgumentException(alreadyDecided(id) + "; a decision cannot be changed or withdrawn. Nothing was recorded.");
            throw new IllegalArgumentException(unknownId(id) + ". Nothing was recorded.");
        }
        ExpensePolicy.Verdict v = policy.decide(c);
        boolean correct = v.kind.equals(kind) && (!kind.equals("escalate") || v.to.equals(to));
        decisions.add(new Decision(id, kind, to, correct, v.label(), reason));
        decidedCases.put(id, c);
        pending.remove(id);
        notifyObsPropertyChanged("pending_count", pending.size() + 1, pending.size());
        if (pending.isEmpty() && onQueueEmpty != null) onQueueEmpty.run();
        String base = "recorded";
        if (feedback == Feedback.PRACTICE)
            return base + " — practice feedback: " + (correct ? "correct" : "incorrect. Policy: " + v.explanation);
        if (feedback == Feedback.AUDIT && !correct)
            return base + " — flagged by audit: " + v.explanation;
        return base;
    }

    public static Manual manual() { return manual(false); }

    /** decidedVisible must match setDecidedVisible: the manual tells the agent what the system will actually do. */
    public static Manual manual(boolean decidedVisible) { return manual(decidedVisible, false); }

    /** policyCue must match setPolicyCue: the manual lists the policy_version property only when the system really exposes it. */
    public static Manual manual(boolean decidedVisible, boolean policyCue) {
        List<Manual.Param> observables = new java.util.ArrayList<>();
        observables.add(new Manual.Param("pending_count", "how many expenses are still awaiting a decision"));
        if (policyCue) observables.add(new Manual.Param("policy_version", "the version of the approval policy currently in force (also shown on every expense)"));
        List<Manual.Operation> ops = new ArrayList<>();
        for (Usage u : USAGE) {
            String summary = u.summary.replace("%GET%", decidedVisible
                    ? "pending or already decided; a decided expense also shows the decision recorded for it"
                    : "pending expenses only: a decided expense can no longer be retrieved");
            ops.add(new Manual.Operation(u.signature(), summary + " " + u.effect + " Example: " + u.exampleText + ".",
                    u.decision ? List.of(new Manual.Param("status", "'recorded', and possibly feedback (see further information)"))
                    : u.name.equals("get_expense") ? List.of(new Manual.Param("expense", "the expense's fields as JSON"))
                    : List.of(new Manual.Param("summaries", "one line per pending expense"))));
        }
        return new Manual(type,
                "review the expenses employees have submitted and record a decision on each: approve, reject, or escalate to a role. "
                + "An expense is identified by its expense_id (for example E123).",
                null, null,
                observables,
                List.of(),
                ops,
                "The company's approval policy is not described here; decisions are judged against it. What a decision returns depends on the stage: "
                + "'recorded' always; at some stages also 'practice feedback', which says at once whether the decision matched the policy and quotes the rule; "
                + "at others 'flagged by audit', which appears after a decision was recorded if it violates the policy, quoting the rule. If the result is just "
                + "'recorded', no feedback is given at this stage; that does not mean the decision was right. Decided expenses cannot be revised. "
                + "A call with a missing or wrong parameter records nothing and returns an error saying what to fix.");
    }
}
