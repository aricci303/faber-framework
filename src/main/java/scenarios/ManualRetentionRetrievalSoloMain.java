package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Two attempts at an "uncontested" variant (removing Counter, then
 * discovering the single-step notebook-note task filled the same
 * blocking role) sharpened the working hypothesis further: the model
 * appears to treat "get the recurring watch functionally armed
 * (manual fetched, FOCUS in place)" as the standing priority, and
 * treats *whatever else remains open* — regardless of step count —
 * as deserving the very next cycle once that's done. Writing a note
 * about the watched artifact's manual has never once been what filled
 * that next cycle unless literally nothing else was pending at all.
 *
 * This scenario is the direct, minimal test of that sharpened claim:
 * the bundle contains only the Pager watch — no counter, no alarm, no
 * notebook fact, nothing else the user asks for at all. There is
 * provably nothing else that could ever compete for the cycle
 * immediately after FOCUS pager-01 resolves, removing every
 * confound the two prior attempts ran into. If the model still
 * doesn't write a note here, that's a clean, decisive result: the
 * mechanism isn't about contention at all, and Scenario07's own note
 * likely arose from something else entirely (its four-goal richness,
 * a genuinely different disposition, or simple run-to-run variance,
 * consistent with the real, independent variance already observed in
 * how differently each of these seven runs has handled the identical
 * undocumented Pager signal shape). If it does write one here, the
 * uncontested-cycle mechanism is confirmed precisely.
 *
 * From there, the same explicit watch-drop and second, separate need
 * for Pager as every sibling scenario. Injection timing: with no other
 * goal in this scenario at all, dropping watch-alice is necessarily
 * the very first resolution resolvedGoalsCount() could ever report —
 * an unusually clean, unambiguous polling target, needing no chained
 * stages the way every richer sibling scenario did.
 *
 * The gap before that drop was originally a plain, generous 20-second
 * pacing wait, reasoned as safe since nothing else exists in this
 * scenario to compete for attention. A first run showed that
 * reasoning was wrong in a specific, checkable way: with only one
 * simple goal to set up, the model reached and completed get_manual
 * well within that window, and the fixed sleep let "stop watching"
 * arrive before FOCUS pager-01 ever happened — cancelling the watch
 * before it reached the very moment this scenario exists to observe,
 * same root problem as the very first uncontested-variant run, just
 * from a different cause (there it was the ask-vs-guess derailment;
 * here it was simply an inflated-but-still-too-tight fixed guess).
 * Rather than inflate the number again, this now polls the actual
 * condition that matters directly: pager.isCurrentlyObserved(), the
 * same thread-safe accessor already proven for exactly this purpose
 * earlier this session — waiting for FOCUS to have genuinely
 * happened, then a further, deliberate pacing beat to give the model
 * real room to write a note if it's going to, before the watch is
 * ever cancelled.
 */
public final class ManualRetentionRetrievalSoloMain {

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
                        "If you ever see a page from Alice, flag it to me as high priority as soon as "
                        + "it arrives.");

                // Validity-critical: wait for FOCUS to have genuinely happened, rather than guessing
                // a fixed delay — see class doc comment for why a first attempt at this got it wrong.
                long deadline = System.currentTimeMillis() + 120_000;
                while (!pager.isCurrentlyObserved() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                System.out.println("[SCENARIO DRIVER] wait for pager-01 FOCUS exited via: "
                        + (pager.isCurrentlyObserved() ? "condition met" : "SAFETY-NET DEADLINE"));

                // A further, deliberate pacing beat once focused — real room for a note to be
                // written, if the model is going to, before the watch is ever cancelled.
                Thread.sleep(20_000);
                userConsole.simulateIncomingMessage(
                        "Actually, never mind about watching for Alice's pages — you can stop that now.");

                // Validity-critical: with no other goal anywhere in this scenario, this first
                // resolution can only ever be watch-alice being dropped.
                waitForResolvedCount(agent, 1, 240_000, "watch-alice being dropped");
                userConsole.simulateIncomingMessage(
                        "Please page Bob to ask if he can cover Alice's shift tomorrow.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(24);
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
