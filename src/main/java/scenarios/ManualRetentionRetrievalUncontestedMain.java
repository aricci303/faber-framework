package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Direct comparison of Scenario07's real transcript against five
 * ManualRetentionRetrieval/Retraction/NotebookFirst runs (raw
 * commitment count and notebook-use recency both tested and both
 * ruled out — neither moved the outcome at all) surfaced something
 * more precise: in Scenario07, by the cycle immediately after FOCUS
 * messaging-01, every other bundled goal (alarm, two notebook facts)
 * had already fully resolved — nothing else remained open, and the
 * model used that specific, uncontested next cycle to write a note
 * about the manual. In every ManualRetentionRetrieval-family run,
 * Counter — the only genuinely multi-step task in the bundle
 * (get_manual, then create_artifact, then inc: three real actions
 * across separate cycles) — was still open exactly when Pager's own
 * FOCUS resolved, and the model used that cycle to advance Counter
 * instead. The note-writing opportunity was never returned to
 * afterward in any of those five runs.
 *
 * This variant tests that specific, narrower claim directly by
 * removing Counter — the one identified structural difference —
 * rather than adjusting bundle richness or message ordering generally,
 * both of which were already tried and made no difference. The watch
 * on Pager, the notebook note, and the alarm are all kept exactly as
 * before, since none of those three were ever implicated as the
 * blocker; only the multi-step distractor is gone. If the note now
 * gets written, that's a real, specific confirmation of the
 * uncontested-cycle mechanism; if it still doesn't, the mechanism
 * needs rethinking again rather than further tuning of this same
 * axis.
 *
 * Same downstream narrative and chained resolvedGoalsCount() polling
 * as ManualRetentionRetrievalMain, with counts adjusted for the
 * smaller, two-achievement-task bundle (notebook note + alarm, no
 * Counter): 2 resolutions before ending the watch, then a 3rd (the
 * watch itself being dropped) before the second Pager need.
 */
public final class ManualRetentionRetrievalUncontestedMain {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(PagerArtifact.manual(), null);
        PagerArtifact pager = new PagerArtifact("pager-01", workspace);
        workspace.provision("pager-01", "Pager", pager);

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
                        + "Second: note down my ticket queue id, TQ-4471 — I'll need it eventually. "
                        + "Third: set a reminder alarm for 5 seconds from now and tell me when it fires.");

                waitForResolvedCount(agent, 2, 240_000, "the notebook note and the alarm");
                userConsole.simulateIncomingMessage(
                        "Actually, never mind about watching for Alice's pages — you can stop that now.");

                waitForResolvedCount(agent, 3, 240_000, "watch-alice also being dropped");
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
