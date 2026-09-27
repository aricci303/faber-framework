package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Quick scaling check on additional_goals, following directly from
 * Scenario06's confirmed success with exactly two simultaneous goals.
 * The open question this targets: does enumeration stay reliable as
 * the count and variety grows, or does completeness degrade past two?
 *
 * Deliberately not four copies of the same kind of commitment (which
 * would only test raw count) but four genuinely different kinds, in a
 * single message, forcing genuine breadth of recognition:
 *   1. An immediately actionable request (set an alarm) — this is
 *      almost certainly what gets acted on this cycle, the same role
 *      the alarm request played in Scenario06.
 *   2. A standing belief with no relation to (1) (a dietary
 *      preference) — deferred, no trigger, resolved only when it
 *      becomes relevant to something else later (never, in this
 *      scenario — the point is registration and persistence, not use).
 *   3. A second, independent standing belief (a frequent flyer number)
 *      — specifically to test whether the model registers BOTH
 *      deferred beliefs, not just the first one it notices.
 *   4. A genuine conditional commitment (flag a message from a named
 *      sender, whenever it arrives) — testing whether a
 *      pending_trigger-shaped implication gets correctly distinguished
 *      from the two plain standing beliefs, not collapsed into the
 *      same shape as them.
 *
 * The scenario driver sends a message from the named sender only once
 * messaging-01 is actually observed — a genuine readiness condition,
 * not a fixed delay (an earlier version used one, and a real run
 * showed Marco's message arriving before messaging-01 was ever
 * focused, an unintended timing confound on a scenario whose actual
 * purpose is enumeration breadth, not concurrency robustness). This
 * tests not just whether all four were recognized up front but
 * whether the one with a genuine trigger is still correctly tracked
 * and fires appropriately once it genuinely can — the same
 * cross-cycle persistence question this whole mechanism exists for,
 * now combined with the enumeration question, without timing fairness
 * itself being a confound.
 */
public final class Scenario07Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(MessagingArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        // agent.enableCycleDumpLogging(false);
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "I have a few things for you to keep track of, all at once. First: set a "
                        + "reminder alarm for 5 seconds from now and tell me when it fires. Second: "
                        + "remember for later that I'm vegetarian, in case that matters for any future "
                        + "booking. Third: also note down my frequent flyer number, ZX-88214 — I'll need "
                        + "it eventually. Fourth: if you ever see a message from someone named Marco, "
                        + "flag it to me as high priority as soon as it arrives.");

                // Injected only once messaging-01 is actually observed — see the class doc comment
                // for why this replaced a fixed sleep. The deadline is a safety net only, mirroring
                // Scenario05Main's own pattern: if it's ever hit, the agent never got around to
                // focusing messaging-01 at all within a very generous window, which is itself a
                // separate, worthwhile finding rather than the scenario silently hanging forever.
                long deadline = System.currentTimeMillis() + 120_000;
                while (!messaging.isCurrentlyObserved() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                messaging.simulateIncomingMessage("Marco", "Can we talk about the budget tomorrow?");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(30);
        workspace.shutdown();
    }
}
