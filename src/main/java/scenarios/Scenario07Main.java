package scenarios;

import faber.agent.Agent;
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
 * The scenario driver later sends a message from the named sender,
 * well after everything else has settled, testing not just whether
 * all four were recognized up front but whether the one with a
 * genuine trigger is still correctly tracked and fires appropriately
 * much later — the same cross-cycle persistence question this whole
 * mechanism exists for, now combined with the enumeration question.
 */
public final class Scenario07Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(MessagingArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);

        Agent agent = new Agent("agent-0");
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

                // A long gap, well after the alarm and any note-writing should have settled, before
                // the conditional commitment's actual trigger arrives — testing whether it survived
                // both the initial multi-goal registration and everything that happened afterward.
                Thread.sleep(80000);
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
