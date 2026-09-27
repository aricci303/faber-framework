package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Second of the two-scenario split replacing the original, single
 * ManualRetentionMain (retired) — see ManualRetentionRetrievalMain's
 * own doc comment for the shared rationale, including why the initial
 * bundle deliberately mirrors Scenario07's shape. This scenario is
 * shorter and more direct than its sibling: no second need for Pager,
 * no retrieval question at all — just the write opportunity, the
 * explicit end of the watch, and then the cleanest, most unambiguous
 * retract_note opportunity this whole session has offered, if a note
 * was ever written in the first place.
 *
 * Same chained resolvedGoalsCount() polling as the retrieval sibling,
 * for the same reason (see that class's own doc comment) — only the
 * message sent once both waits are satisfied differs. Deadlines are
 * set at roughly 4x an initially-observed requirement, after a first
 * run's shorter deadline (60s) fired at a genuine 0 resolutions for
 * this richer, four-goal bundle — see the retrieval sibling's own doc
 * comment for the full account of that miscalibration.
 */
public final class ManualRetentionRetractionMain {

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
                        "Thanks — we're completely done with the pager system now, you won't need it again.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(32);
        workspace.shutdown();
    }

    /** See ManualRetentionRetrievalMain's identical helper for the full rationale. */
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
