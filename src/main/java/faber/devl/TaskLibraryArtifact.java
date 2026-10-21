package faber.devl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * The task library: how the work is done here, as the people who own the process keep it — a CURATED, VERSIONED, READ-ONLY artifact on the same discipline as the domain model
 * (KNOWLEDGE-LAYERS.md), part of the environment in every phase.
 *
 * It holds two kinds of given task knowledge, and nothing else:
 *   TASKS    what a kind of request is: its input, what counts as done and as failed, the quality asked of it, what the agent may decide alone (the mandate), and what is outside it (the scope);
 *   PLANS    practices the owners ask for, each with a STATUS — MANDATORY (followed; if it cannot be, say so and why), RECOMMENDED (a default the agent may adapt when it has a reason),
 *            EXAMPLE (an illustration of one way) — a trigger (when), a short body (steps), the reason (why) and what may be adapted. A plan is advice for the agent's own practical
 *            reasoning, never a procedure that runs for it: no operation here does the task, and no operation chooses among plans.
 *
 * It is assembled from two sources: the FRAMEWORK's base (faber/devl/devl-task-library-base.json: practices that are about doing any work in this process, with the case's slots filling the few
 * words that name its domain) and the CASE's own file (its tasks and its domain-specific plans). The agent sees one library. The version is the case file's.
 *
 * The agent's operations: tasks, describe_task, plans_for, describe_plan, version, changes_since, and propose (a suggestion for the owners that changes nothing). The designer side: the
 * proposals, the consultation counts by stage, a content hash, a Markdown export.
 */
public final class TaskLibraryArtifact extends Artifact {

    public static final String type = "TaskLibrary";
    public static final List<String> STATUSES = List.of("mandatory", "recommended", "example");

    /** What a status asks of the agent, in the words the manual and describe_plan use. */
    static String meaning(String status) {
        switch (status) {
            case "mandatory": return "mandatory — follow it; if you cannot, say so and why, and propose a change";
            case "recommended": return "recommended — a default: adapt it when you have a reason, and say so when it matters to your reader";
            default: return "example — an illustration of one way to do it, not a rule";
        }
    }

    public static final class Task {
        public final String id, name, summary, input, done, failed, mandate, scope; public final List<String> quality = new ArrayList<>();
        Task(JSONObject j) {
            id = j.getString("id"); name = j.getString("name"); summary = j.getString("summary"); input = j.getString("input"); done = j.getString("done"); failed = j.getString("failed");
            mandate = j.getString("mandate"); scope = j.getString("scope"); for (Object o : j.getJSONArray("quality")) quality.add((String) o);
        }
    }

    public static final class Plan {
        public final String id, name, status, when, why, adapt, source; public final int since; public final List<String> appliesTo = new ArrayList<>(), steps = new ArrayList<>(); public final boolean fromFramework;
        Plan(JSONObject j, boolean fromFramework) {
            id = j.getString("id"); name = j.getString("name"); status = j.getString("status"); when = j.getString("when"); why = j.getString("why"); adapt = j.optString("adapt", "");
            source = j.optString("source", "the process owners"); since = j.optInt("since", 1); this.fromFramework = fromFramework;
            for (Object o : j.getJSONArray("applies_to")) appliesTo.add((String) o); for (Object o : j.getJSONArray("steps")) steps.add((String) o);
        }
        boolean appliesTo(String task) { return appliesTo.contains("*") || appliesTo.contains(task); }
    }

    static final class Version {
        final int id; final String released; final List<String> changes = new ArrayList<>();
        Version(JSONObject j) { id = Integer.parseInt(j.getString("id")); released = j.optString("released", ""); for (Object c : j.optJSONArray("changes") == null ? new JSONArray() : j.getJSONArray("changes")) changes.add((String) c); }
    }

    /** The library as assembled from the framework's base and the case's file: parsed, with the slots filled, and its hash. */
    public static final class Parsed {
        public final String id, title, resourceName, sha256; public final Map<String, Task> tasks = new LinkedHashMap<>(); public final List<Plan> plans = new ArrayList<>(); final List<Version> versions = new ArrayList<>();
        Parsed(String id, String title, String resourceName, String sha256) { this.id = id; this.title = title; this.resourceName = resourceName; this.sha256 = sha256; }
    }

    private static final Pattern SLOT = Pattern.compile("\\{([A-Za-z0-9]+)\\}");

    private static String fill(String text, Map<String, String> slots, String where) {
        Matcher m = SLOT.matcher(text); StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = slots.get(m.group(1));
            if (v == null) throw new IllegalArgumentException("the base task library (" + where + ") needs the text slot '" + m.group(1) + "', which the case does not provide");
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static JSONObject fillAll(JSONObject o, Map<String, String> slots, String where) {
        JSONObject r = new JSONObject();
        for (String k : o.keySet()) {
            Object v = o.get(k);
            if (v instanceof String s) r.put(k, fill(s, slots, where + "." + k));
            else if (v instanceof JSONArray a) { JSONArray b = new JSONArray(); for (Object x : a) b.put(x instanceof String s ? fill(s, slots, where + "." + k) : x); r.put(k, b); }
            else r.put(k, v);
        }
        return r;
    }

    private static JSONObject read(String resource) {
        try (InputStream in = TaskLibraryArtifact.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("resource not found: " + resource);
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    /** Assembles the library: the framework's base plans (slots filled from the case) first, then the case's tasks and plans. The hash is of the assembled content, in a fixed order. */
    public static Parsed parse(String baseResource, String caseResource, Map<String, String> slots) { return parse(read(baseResource), read(caseResource), CuratedModelArtifact.fileName(caseResource), slots); }

    /** The same from JSON in hand (for tests that build a deliberately broken library). */
    public static Parsed parse(JSONObject base, JSONObject mine, String file, Map<String, String> slots) {
        Parsed p = new Parsed(mine.getString("id"), mine.optString("title", mine.getString("id")), file, "");
        StringBuilder canon = new StringBuilder(); List<Plan> plans = new ArrayList<>(); Map<String, Task> tasks = new LinkedHashMap<>(); List<Version> versions = new ArrayList<>();
        for (Object o : mine.getJSONArray("versions")) versions.add(new Version((JSONObject) o));
        if (versions.isEmpty()) throw new IllegalArgumentException("a task library needs at least one version");
        for (Object o : mine.getJSONArray("tasks")) { Task t = new Task((JSONObject) o); tasks.put(t.id, t); canon.append("task|").append(t.id).append('|').append(t.name).append('|').append(t.summary).append('|').append(t.input).append('|').append(t.done).append('|').append(t.failed).append('|').append(String.join("¦", t.quality)).append('|').append(t.mandate).append('|').append(t.scope).append('\n'); }
        for (Object o : base.getJSONArray("plans")) plans.add(new Plan(fillAll((JSONObject) o, slots, "plan " + ((JSONObject) o).optString("id")), true));
        for (Object o : mine.getJSONArray("plans")) plans.add(new Plan((JSONObject) o, false));
        for (Plan pl : plans) canon.append("plan|").append(pl.id).append('|').append(pl.name).append('|').append(pl.status).append('|').append(String.join(",", pl.appliesTo)).append('|').append(pl.when).append('|').append(String.join("¦", pl.steps)).append('|').append(pl.why).append('|').append(pl.adapt).append('|').append(pl.source).append('|').append(pl.since).append('\n');
        for (Version v : versions) canon.append("version|").append(v.id).append('|').append(v.released).append('|').append(String.join("¦", v.changes)).append('\n');
        Parsed out = new Parsed(p.id, p.title, file, PlaybookSnapshot.sha256(canon.toString()));
        out.tasks.putAll(tasks); out.plans.addAll(plans); out.versions.addAll(versions);
        return out;
    }

    private final Parsed lib;
    private final List<String[]> proposals = new ArrayList<>();                      // {subject, change, evidence, version in force}
    private final Map<String, Map<String, Integer>> opsByStage = new TreeMap<>();
    private volatile int current = 1; private String stage = "";

    public TaskLibraryArtifact(String id, Workspace workspace, Parsed lib) {
        super(id, type, workspace); this.lib = lib;
        currentObsProperties().put("version", String.valueOf(current));
        JSONArray names = new JSONArray(); for (Task t : lib.tasks.values()) names.put(t.id);
        currentObsProperties().put("tasks", names); currentObsProperties().put("proposals", 0);
    }

    public static TaskLibraryArtifact load(String artifactId, Workspace workspace, String baseResource, String caseResource, Map<String, String> slots) { return new TaskLibraryArtifact(artifactId, workspace, parse(baseResource, caseResource, slots)); }

    /** The environment record a snapshot keeps: which library it was learned against. */
    public static JSONObject sourceRecord(String baseResource, String caseResource, Map<String, String> slots) {
        Parsed p = parse(baseResource, caseResource, slots); return new JSONObject().put("kind", "task_library").put("id", p.id).put("source", p.resourceName).put("sha256", p.sha256);
    }
    public JSONObject environmentRecord() { return new JSONObject().put("kind", "task_library").put("id", lib.id).put("source", lib.resourceName).put("sha256", lib.sha256); }

    public Parsed content() { return lib; }
    public String libraryId() { return lib.id; }
    public String resourceName() { return lib.resourceName; }
    public String sha256() { return lib.sha256; }
    public String currentVersion() { return String.valueOf(current); }
    public synchronized void setStage(String label) { this.stage = label; }
    public synchronized List<String[]> proposals() { List<String[]> l = new ArrayList<>(); for (String[] p : proposals) l.add(p.clone()); return l; }
    public synchronized Map<String, Map<String, Integer>> opCountsByStage() { Map<String, Map<String, Integer>> m = new TreeMap<>(); opsByStage.forEach((k, v) -> m.put(k, new TreeMap<>(v))); return m; }

    /** The plans of a task in the order they are shown: mandatory, then recommended, then examples; within a status, the framework's first, in the order of the files. */
    public List<Plan> plansFor(String taskId) {
        List<Plan> out = new ArrayList<>();
        for (String s : STATUSES) for (Plan p : lib.plans) if (p.status.equals(s) && p.appliesTo(taskId) && p.since <= current) out.add(p);
        return out;
    }

    public synchronized String toMarkdown() {
        StringBuilder sb = new StringBuilder("# ").append(lib.title).append("\n\nVersion in force: ").append(current).append(" (source ").append(lib.resourceName).append(", sha256 ").append(lib.sha256, 0, 12).append(")\n\n");
        for (Task t : lib.tasks.values()) {
            sb.append("## Task: ").append(t.name).append(" (").append(t.id).append(")\n\n").append(t.summary).append("\n\n- request: ").append(t.input).append("\n- done: ").append(t.done).append("\n- failed: ").append(t.failed).append("\n");
            for (String q : t.quality) sb.append("- quality: ").append(q).append("\n");
            sb.append("- mandate: ").append(t.mandate).append("\n- scope: ").append(t.scope).append("\n\n");
            for (Plan p : plansFor(t.id)) sb.append("### Plan [").append(p.status).append("] ").append(p.name).append(" (").append(p.id).append(")\n\n- when: ").append(p.when).append("\n").append(stepsText(p)).append("- why: ").append(p.why).append("\n- adapt: ").append(p.adapt).append("\n- source: ").append(p.source).append(p.fromFramework ? " (framework base)" : "").append("\n\n");
        }
        if (!proposals.isEmpty()) {
            sb.append("## Proposals from the agent, for the owners to decide\n\n"); int i = 1;
            for (String[] p : proposals) sb.append(i++).append(". ").append(p[0].isBlank() ? "(general)" : p[0]).append(": ").append(p[1]).append(" — evidence: ").append(p[2]).append(" (made at version ").append(p[3]).append(")\n");
        }
        return sb.toString();
    }

    private static String stepsText(Plan p) { StringBuilder sb = new StringBuilder(); int i = 1; for (String s : p.steps) sb.append("  ").append(i++).append(". ").append(s).append("\n"); return sb.toString(); }

    // ---- agent operations: knowledge about how the work is done, never the work itself ---------------------------------------------------------------------------------

    /** One practice in full, as describe_plan gives it. */
    private String planText(Plan pl) {
        StringBuilder sb = new StringBuilder(pl.id).append(" [").append(pl.status).append("] — ").append(pl.name);
        sb.append("\nApplies to: ").append(pl.appliesTo.contains("*") ? "every task" : String.join(", ", pl.appliesTo)).append("\nWhen: ").append(pl.when).append("\nSteps:");
        int i = 1; for (String s : pl.steps) sb.append("\n").append(i++).append(". ").append(s);
        sb.append("\nWhy: ").append(pl.why);
        if (!pl.adapt.isBlank()) sb.append("\nYou may adapt: ").append(pl.adapt);
        sb.append("\nStatus: ").append(meaning(pl.status)).append("\nSource: ").append(pl.source).append("; applies since version ").append(pl.since);
        return sb.toString();
    }

    private String req(JSONObject p, String op, String name) {
        String v = p.optString(name, "").trim();
        if (v.isEmpty()) throw new IllegalArgumentException("missing parameter '" + name + "': " + op + " needs it; nothing was done");
        return v;
    }
    private Task needTask(String id) {
        Task t = lib.tasks.get(id);
        if (t == null) throw new IllegalArgumentException("no such task: " + id + " (tasks() lists the tasks of this library)");
        return t;
    }
    private Plan needPlan(String id) {
        for (Plan p : lib.plans) if (p.id.equals(id) && p.since <= current) return p;
        throw new IllegalArgumentException("no such plan: " + id + " (plans_for(task) lists the plans of a task)");
    }

    @Override
    public synchronized List<Object> doOperation(String op, JSONObject p) throws Exception {
        opsByStage.computeIfAbsent(stage, k -> new TreeMap<>()).merge(op, 1, Integer::sum);       // consulting is what is measured: failed attempts included
        int v = current;
        switch (op) {
            case "tasks": {
                StringBuilder sb = new StringBuilder(lib.title).append(" — version ").append(v).append(". Tasks:\n");
                for (Task t : lib.tasks.values()) sb.append("- ").append(t.id).append(": ").append(t.name).append(" — ").append(t.summary).append(" (").append(plansFor(t.id).size()).append(" plans)\n");
                sb.append("describe_task(task) says what a task is; plans_for(task) lists its practices; describe_plan(plan) gives one in full.");
                return List.of(sb.toString());
            }
            case "describe_task": {
                Task t = needTask(req(p, op, "task")); StringBuilder sb = new StringBuilder(t.id).append(": ").append(t.name).append("\n").append(t.summary);
                sb.append("\nRequest: ").append(t.input).append("\nDone: ").append(t.done).append("\nFailed: ").append(t.failed).append("\nQuality asked of it, in this order:");
                for (String q : t.quality) sb.append("\n- ").append(q);
                sb.append("\nMandate: ").append(t.mandate).append("\nScope: ").append(t.scope);
                List<Plan> ps = plansFor(t.id); List<String> mand = new ArrayList<>(); for (Plan pl : ps) if (pl.status.equals("mandatory")) mand.add(pl.id);
                sb.append("\nPlans: ").append(ps.size()).append(" (plans_for(").append(t.id).append(") lists them; mandatory: ").append(mand.isEmpty() ? "none" : String.join(", ", mand)).append(")");
                for (Plan pl : ps) if (pl.status.equals("mandatory")) sb.append("\n\nMandatory — read it now; it applies whenever its trigger occurs:\n").append(planText(pl));       // what must be followed is shown wherever the task is, not only where a plan is asked for
                return List.of(sb.toString());
            }
            case "plans_for": {
                Task t = needTask(req(p, op, "task")); List<Plan> ps = plansFor(t.id);
                if (ps.isEmpty()) return List.of("No plans are recorded for " + t.id + " in version " + v + ".");
                StringBuilder sb = new StringBuilder("Plans for ").append(t.id).append(", version ").append(v).append(":");
                for (Plan pl : ps) {
                    sb.append("\n- ").append(pl.id).append(" [").append(pl.status).append("] ").append(pl.name).append(" — when: ").append(pl.when);
                    if (pl.status.equals("mandatory")) sb.append("\n  ").append(planText(pl).substring(planText(pl).indexOf("\nSteps:") + 1).replace("\n", "\n  "));       // a mandatory plan is shown in full wherever plans are listed
                }
                return List.of(sb.toString());
            }
            case "describe_plan": {
                return List.of(planText(needPlan(req(p, op, "plan"))));
            }
            case "version": {
                StringBuilder sb = new StringBuilder("Version ").append(v).append(" is in force. Published versions:");
                for (Version ve : lib.versions) if (ve.id <= v) sb.append(" ").append(ve.id).append(ve.released.isBlank() ? "" : " (" + ve.released + ")").append(";");
                return List.of(sb.toString());
            }
            case "changes_since": {
                int since; try { since = Integer.parseInt(req(p, op, "version")); } catch (NumberFormatException e) { throw new IllegalArgumentException("'" + p.getString("version") + "' is not a version number: versions are whole numbers (version() lists them)"); }
                if (since < 1) throw new IllegalArgumentException("versions start at 1");
                if (since >= v) return List.of("No changes: version " + since + (since == v ? " is the version in force." : " has not been published."));
                StringBuilder sb = new StringBuilder("Changes since version " + since + " (version " + v + " is in force):");
                for (Version ve : lib.versions) if (ve.id > since && ve.id <= v) { sb.append("\nVersion ").append(ve.id).append(ve.released.isBlank() ? "" : " (" + ve.released + ")").append(":"); for (String c : ve.changes) sb.append("\n- ").append(c); }
                return List.of(sb.toString());
            }
            case "propose": {
                String change = req(p, op, "change"), evidence = req(p, op, "evidence"), subject = p.optString("subject", "").trim();
                if (!subject.isEmpty() && !lib.tasks.containsKey(subject) && lib.plans.stream().noneMatch(pl -> pl.id.equals(subject))) throw new IllegalArgumentException("no such task or plan: " + subject + " (tasks() and plans_for(task) list them; subject is optional)");
                proposals.add(new String[] { subject, change, evidence, String.valueOf(v) });
                currentObsProperties().put("proposals", proposals.size());
                return List.of("Proposal " + proposals.size() + " recorded for the owners of the library. The library itself is unchanged: the owners decide, and a change they accept takes effect in a new version.");
            }
            default: throw new IllegalArgumentException("unknown operation: " + op + " (this library can be read — tasks, describe_task, plans_for, describe_plan, version, changes_since — and you can propose a change; you cannot change it)");
        }
    }

    public static Manual manual() {
        return new Manual(type,
                "how the work is done here, as the people who own the process keep it: what each kind of task is — its input, what counts as done, the quality asked of it, what you may decide alone and what is outside it — "
                + "and the practices they ask for. It is the process owners' statement of how they want the work done. You can read it; you cannot change it",
                null, null,
                List.of(new Manual.Param("version", "the version of the library in force"), new Manual.Param("tasks", "the ids of the tasks in the library"), new Manual.Param("proposals", "how many changes you have proposed so far")),
                List.of(),
                List.of(
                    new Manual.Operation("tasks()", "the catalogue: every task with a one-line summary and its number of plans", List.of(new Manual.Param("tasks", "one line per task"))),
                    new Manual.Operation("describe_task(task)", "what a task is: its request, what counts as done and as failed, the quality asked of it, your mandate and its scope — and the mandatory practices, in full", List.of(new Manual.Param("task", "the task in full"))),
                    new Manual.Operation("plans_for(task)", "the practices that apply to a task, each with its status and when it applies — mandatory first, and the mandatory ones in full", List.of(new Manual.Param("plans", "one line per plan; a mandatory plan with its steps, reason and what may be adapted"))),
                    new Manual.Operation("describe_plan(plan)", "one practice in full: when it applies, its steps, why it is asked, what you may adapt, its status and its source", List.of(new Manual.Param("plan", "the plan in full"))),
                    new Manual.Operation("version()", "the version in force, and the versions published so far", List.of(new Manual.Param("version", "the version in force"))),
                    new Manual.Operation("changes_since(version)", "what the owners changed in the library since a version you know", List.of(new Manual.Param("changes", "the changes, version by version"))),
                    new Manual.Operation("propose(subject, change, evidence)", "suggest a change to the owners, with what you observed that supports it; subject (a task or a plan) is optional. The library itself does not change: the owners decide, and an accepted change takes effect in a new version",
                            List.of(new Manual.Param("status", "confirmation with the proposal's number")))),
                "Each practice carries a status. A mandatory one is followed; if you cannot follow it, say so and why. A recommended one is a default: you may adapt it when you have a reason, and say so when it matters to your reader. "
                + "An example only illustrates one way. A practice is advice for your own judgment, not a procedure that runs for you: you decide how to do the work. The library is changed only by its owners, in new versions (see version and changes_since). "
                + "If what you observe contradicts it, say which, and propose a change; it is not yours to change.");
    }
}
