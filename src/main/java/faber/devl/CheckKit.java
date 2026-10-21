package faber.devl;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.agent.LlmClient;

/**
 * What the suites share: the PASS/FAIL counters, and the harness that drives the real runner with a scripted agent (no model calls), for any case. A suite of the framework and the suite of
 * each case use the same kit.
 */
public final class CheckKit {
    private CheckKit() {}

    public static int pass = 0, fail = 0;
    public static void check(String name, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + name); if (ok) pass++; else fail++; }

    /** Prints the summary and returns the process exit code (0 if nothing failed). */
    public static int summary(String suite) { System.out.println("\n" + suite + ": " + pass + " passed, " + fail + " failed"); return fail == 0 ? 0 : 1; }

    /** The version of the domain model as the agent sees it in a context (its own artifact line), or null: other artifacts have observable versions too (the task library), so a bare "version=1" proves nothing. */
    public static String modelVersion(String ctx) { var m = java.util.regex.Pattern.compile("id: \"domain-model-01\", current properties: \\{[^}]*?version=(\\d+)").matcher(ctx); return m.find() ? m.group(1) : null; }

    public static final String IDLE = "[{\"kind\":\"WAIT\",\"goal_id\":\"serve-user\"}]";
    public static JSONObject inv(String artifact, String op, JSONObject params) { return new JSONObject().put("kind", "INVOKE").put("artifact_id", artifact).put("operation_name", op).put("parameters", params).put("goal_id", "serve-user"); }
    public static JSONObject say(String text) { return inv("user-console-01", "send_msg_to_user", new JSONObject().put("text", text)); }
    /** The agent revises its playbook (the playbook artifact's operation; a note under a key until materials 8). */
    public static JSONObject revise(String text) { return inv("playbook-01", "revise", new JSONObject().put("text", text).put("reason", "test")); }
    public static JSONObject note(String key, String content) { return inv("notebook-01", "write_note", new JSONObject().put("key", key).put("content", content)); }

    /** The scripted agent: given the context it was shown and the case's system, the JSON array of actions of this cycle. */
    public interface Brain { String act(String ctx, CaseWorld world); }

    /** {log, result, playbook files (sorted), contexts} of one run of one of the three tools with a scripted agent. */
    public static Object[] run(String[] args, Brain brain) throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("devlrun");
        List<String> ctxs = new ArrayList<>(); java.util.concurrent.atomic.AtomicReference<CaseWorld> ref = new java.util.concurrent.atomic.AtomicReference<>();
        DevlRunner.worldHook = ref::set; DevlRunner.exitOnAbort = false;
        DevlRunner.llmOverride = new LlmClient() { public LlmCallResult plan(String sys, String ctx) {
            try { Thread.sleep(25); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }      // a model takes time between cycles: operations of two cycles must not overlap
            ctxs.add(ctx);
            return new LlmCallResult("<state_of_mind>s</state_of_mind>\n<intention_changes>\n[]\n</intention_changes>\n<actions>\n" + brain.act(ctx, ref.get()) + "\n</actions>", 1, 1, 0, 0); } };
        java.io.PrintStream keepOut = System.out, keepErr = System.err; System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream())); System.setErr(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        // the tools are one per phase; the suites name the phase in the arguments they pass and this kit strips it and runs the tool of that phase
        String phase = "learning"; List<String> all = new ArrayList<>(); for (String a : args) { if (a.startsWith("--phase=")) phase = a.substring(8); else all.add(a); }
        all.add("--log-dir=" + dir); all.add("--max-cycles=12"); all.add("--watchdog-seconds=0"); String[] argv = all.toArray(new String[0]);
        try { switch (phase) { case "learning": DevlLearningMain.main(argv); break; case "assessment": DevlAssessmentMain.main(argv); break; case "operation": DevlOperationMain.main(argv); break; default: throw new IllegalArgumentException("phase " + phase); } }
        finally { DevlRunner.llmOverride = null; DevlRunner.worldHook = null; DevlRunner.exitOnAbort = true; System.setOut(keepOut); System.setErr(keepErr); }
        String lg = "", rs = ""; List<java.nio.file.Path> pbs = new ArrayList<>();
        try (var st = java.nio.file.Files.list(dir)) { for (var f : st.sorted().toList()) { String n = f.getFileName().toString();
            if (n.startsWith("devl-run-")) lg = java.nio.file.Files.readString(f); else if (n.startsWith("devl-result-")) rs = java.nio.file.Files.readString(f); else if (n.startsWith("devl-playbook-")) pbs.add(f); } }
        return new Object[] { lg, rs, pbs, ctxs };
    }

    public static java.nio.file.Path writeJson(java.nio.file.Path p, JSONObject j) throws java.io.IOException { java.nio.file.Files.writeString(p, j.toString()); return p; }

    /** Approve-everything agents are written per case (the operations are the case's); this is the common shape: do the actions for what is pending, else send the report. */
    public static String actionsOrReport(JSONArray actions, String report) { return (actions.isEmpty() ? new JSONArray().put(say(report)) : actions).toString(); }
}
