package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Both observed instances of the goal_id-outside-the-object
 * malformation (two separate Scenario07 runs) showed the exact same
 * structural shape: an actions array containing exactly one action
 * object, followed by a stray "goal_id" as a second, bare array
 * element. That's a sharper, more specific claim than "batching
 * causes this" — in both cases, the array held only a single action.
 * What both cases share instead is that the one action's own text
 * parameter required escaped, nested quotation marks (quoting an
 * incoming message's exact wording back to the user), and this
 * scenario tests that more precise claim directly: does array-wrapping
 * (now universal, even for a solo action, unlike the old singular
 * <action> tag) combined with nested quotes in that same action's
 * content correlate with this specific error, independent of whether
 * anything else is batched alongside it at all?
 *
 * Rather than leave quoting to the model's own spontaneous choice (as
 * happened, by chance, in both prior runs), the user's own request
 * here explicitly asks for exact-wording quotation — maximizing the
 * chance the model actually produces nested-quote text, giving the
 * hypothesis a real chance to be exercised rather than possibly never
 * arising at all. Three separate incoming messages, each requiring a
 * fresh quoted relay, give the hypothesis three independent trials
 * within one run rather than just one.
 *
 * Deliberately minimal and focused — no other bundled goals, unlike
 * Scenario07's own richness — since the aim here is isolating this one
 * specific variable, not re-testing batching or manual retention
 * again.
 *
 * Injection timing: the first message waits on messaging.isCurrentlyObserved()
 * (the same proven, condition-based accessor used for the earlier
 * solo-watch scenarios) rather than a fixed guess, since the watch must
 * genuinely be focused before the first message can ever be perceived
 * at all. The two messages after that use a generous, fixed pacing
 * gap — not validity-critical, since nothing downstream depends on
 * precise timing the way the focus step does.
 */
public final class QuoteEscapeStressMain {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(MessagingArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "If you ever see a message from Alice, flag it to me right away — please quote "
                        + "her exact words back to me so I know precisely what she said.");

                long deadline = System.currentTimeMillis() + 120_000;
                while (!messaging.isCurrentlyObserved() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                System.out.println("[SCENARIO DRIVER] wait for messaging-01 FOCUS exited via: "
                        + (messaging.isCurrentlyObserved() ? "condition met" : "SAFETY-NET DEADLINE"));

                Thread.sleep(3_000);
                messaging.simulateIncomingMessage("Alice", "Can we meet at 3pm today?");

                Thread.sleep(15_000);
                messaging.simulateIncomingMessage("Alice", "Actually, let's push it to 4pm instead.");

                Thread.sleep(15_000);
                messaging.simulateIncomingMessage("Alice", "Also, don't forget to bring the reports.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(20);
        workspace.shutdown();
    }
}
