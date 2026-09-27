package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Four consecutive runs of ManualRetentionRetrievalMain and
 * ManualRetentionRetractionMain — all sharing the same four-way
 * bundle deliberately mirroring Scenario07's own richness — produced
 * zero notes about Pager's manual, despite each one containing a
 * genuine write_note call for the unrelated ticket-queue-id fact.
 * Raw commitment count, on its own, does not appear to be what
 * mattered in Scenario07.
 *
 * This variant tests a sharper, more specific hypothesis: in every
 * one of those four runs, the model consistently prioritized fetching
 * and setting up Pager (reasoning explicitly about the cost of
 * guessing its signal shape wrong) *before* ever touching notebook-01
 * for the first time that session — the notebook write always came
 * afterward, once Pager was already settled. Scenario07's own
 * ordering (recollected, not re-verified this session) was the
 * reverse: both of its notebook writes happened before its messaging
 * watch's own manual fetch and setup. This scenario forces that
 * reversed ordering deliberately, rather than leaving it to chance:
 * an isolated, uncontested notebook-write request is sent alone first,
 * and only once it has genuinely resolved does the rest of the bundle
 * — including the Pager watch — ever arrive. If the model is more
 * inclined to persist Pager's manual having just, recently used
 * notebook-01 for something else entirely, this ordering gives that
 * effect its fairest possible chance to show up; if it still doesn't,
 * that's a real answer too, not a failure of this design.
 *
 * From there, the narrative and injection discipline exactly mirror
 * ManualRetentionRetrievalMain: an explicit end to the Alice watch,
 * then a second, separate need for Pager afterward, with the same
 * chained resolvedGoalsCount() polling and exit-path logging. Only the
 * counts differ, since the notebook task now resolves in its own,
 * earlier stage rather than within the main bundle: 1 resolution
 * (the isolated notebook write) before the bundle is sent; a further
 * 2 (Counter and the alarm — the two remaining achievement tasks in
 * the bundle itself, for 3 total) before ending the watch; then a 4th
 * (the watch itself being dropped) before the second Pager need.
 */
public final class ManualRetentionRetrievalNotebookFirstMain {

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
                        "Please note down my ticket queue id, TQ-4471 — I'll need it eventually.");

                waitForResolvedCount(agent, 1, 240_000, "the isolated notebook write");
                userConsole.simulateIncomingMessage(
                        "I have a few more things for you to keep track of, all at once. First: if you "
                        + "ever see a page from Alice, flag it to me as high priority as soon as it "
                        + "arrives. Second: please create a new counter called 'ticket-count' and "
                        + "increment it by 3. Third: set a reminder alarm for 5 seconds from now and "
                        + "tell me when it fires.");

                waitForResolvedCount(agent, 3, 240_000, "the counter and alarm tasks, plus the earlier note");
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
