package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * First of the two-scenario split replacing the original, single
 * ManualRetentionMain (retired) — see that scenario's own history for
 * why bundling retrieval and retraction into one run made either
 * finding uninterpretable whenever the initiating note simply didn't
 * get written, which happened in both runs attempted. This scenario
 * isolates retrieval alone.
 *
 * The initial bundle deliberately mirrors Scenario07's own shape
 * closely (one recurring watch, several achievement tasks of varied
 * kinds, all in a single message) rather than the two-goal bundle the
 * original design used — Scenario07 is the one confirmed instance
 * this whole session of the model actually writing a manual note
 * unprompted, and its own stated reason was specifically "since this
 * is a recurring/standing watch," suggesting the pressure of several
 * simultaneous, varied commitments may matter to that decision, not
 * just the watch's own nature in isolation. Untested hypothesis, not
 * a guarantee — this scenario is built to give it a fairer chance,
 * not to force the outcome.
 *
 * Narrative: (1) a rich bundle — watch for pages from Alice (Pager,
 * maintenance), create and increment a counter (Counter, achievement),
 * note a simple fact (Notebook, achievement), set a reminder alarm
 * (Alarm, achievement) — four distinct commitments in one message; (2)
 * once the three achievement tasks have resolved, the user explicitly
 * ends the Alice watch; (3) once that drop is also confirmed, a
 * second, separate need for Pager arises (paging Bob) — by this point
 * watch-alice's own plan, which would have spelled out Pager's
 * operations, is no longer active in ONGOING INTENTIONS, so recalling
 * send_page's signature here can only come from a retained note or a
 * fresh get_manual call, not from a goal still sitting in context.
 *
 * Injection timing: chains agent.resolvedGoalsCount() the same way the
 * original scenario did — first waiting for exactly 3 resolutions (the
 * three achievement tasks; watch-alice is recurring and never resolves
 * on its own, so it cannot be confused with them), then for a 4th
 * (which, with nothing else pending, can only be watch-alice being
 * dropped). This assumes the model registers exactly one goal per
 * distinct request, the consistent pattern observed all session — a
 * reasonable, not absolute, assumption. Each wait now logs which exit
 * path it actually took (condition met vs. the safety-net deadline),
 * closing an observability gap the original scenario's own logs left
 * unresolved — and that logging caught a real miscalibration on its
 * very first use: a first run's deadline (60s) was set too low for
 * this richer, four-goal bundle, which reasons through noticeably more
 * per cycle (roughly double the output tokens of the simpler two-goal
 * version) — the deadline fired at a genuine 0 resolutions, before the
 * watch had even been fetched or focused, voiding that run's premise
 * entirely rather than just costing it some safety margin. Deadlines
 * below are set at roughly 4x that observed requirement.
 */
public final class ManualRetentionRetrievalMain {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(PagerArtifact.manual(), null);
        PagerArtifact pager = new PagerArtifact("pager-01", workspace);
        workspace.provision("pager-01", "Pager", pager);

        workspace.registerType(Counter.manual(), Counter.factory(workspace));

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "I have a few things for you to keep track of, all at once. First: if you ever "
                        + "see a page from Alice, flag it to me as high priority as soon as it arrives. "
                        + "Second: please create a new counter called 'ticket-count' and increment it by "
                        + "3. Third: also note down my ticket queue id, TQ-4471 — I'll need it "
                        + "eventually. Fourth: set a reminder alarm for 5 seconds from now and tell me "
                        + "when it fires.");

                waitForResolvedCount(agent, 3, 240_000, "the three achievement tasks");
                userConsole.simulateIncomingMessage(
                        "Actually, never mind about watching for Alice's pages — you can stop that now.");

                waitForResolvedCount(agent, 4, 240_000, "watch-alice also being dropped");
                userConsole.simulateIncomingMessage(
                        "Please page Bob to ask if he can cover Alice's shift tomorrow.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(32);
        workspace.shutdown();
    }

    /** Logs which exit path was actually taken (condition met vs. safety-net deadline) — closes the
     *  observability gap left by the original ManualRetentionMain, where this could only be inferred
     *  after the fact from cycle timestamps, not confirmed directly. */
    private static void waitForResolvedCount(Agent agent, int threshold, long deadlineMillis, String label)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + deadlineMillis;
        while (agent.resolvedGoalsCount() < threshold && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        boolean conditionMet = agent.resolvedGoalsCount() >= threshold;
        System.out.println("[SCENARIO DRIVER] wait for resolvedGoalsCount>=" + threshold + " (" + label
                + ") exited via: " + (conditionMet ? "condition met" : "SAFETY-NET DEADLINE")
                + " (actual count: " + agent.resolvedGoalsCount() + ")");
    }
}
