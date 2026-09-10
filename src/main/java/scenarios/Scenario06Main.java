package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * Tests whether Notebook is genuinely exploited in the agent's own
 * mental flow, not just mechanically correct in isolation. Three
 * things this scenario is built to observe, none of them told to the
 * agent directly — the task text never mentions Notebook at all,
 * since the point is whether the system prompt's general description
 * of what it's for is enough to prompt spontaneous, appropriate use:
 *
 *   1. Does the agent recognize a standing preference, mentioned with
 *      no current goal attached, as something worth writing down —
 *      rather than either ignoring it or trying to carry it forward in
 *      STATE OF MIND alone?
 *   2. Does that note survive a genuinely unrelated intervening task
 *      (a short alarm-based request) and a long real-time gap before
 *      it becomes relevant again?
 *   3. When a new, separate flight-booking task arrives — one that
 *      never mentions the preference — does the agent actually apply
 *      it, checkably, as the seat_preference parameter on its own
 *      book_flight invocation? Narration claiming to remember is not
 *      the test; the parameter on the real action is.
 */
public final class Scenario06Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(FlightBookingArtifact.manual(), null);
        FlightBookingArtifact flights = new FlightBookingArtifact("flight-01", workspace);
        workspace.provision("flight-01", "FlightBooking", flights);

        Agent agent = new Agent("agent-0");
        // agent.enableCycleDumpLogging(false);
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "Just so you know for the future: I always prefer aisle seats when flying, no "
                        + "matter what I book. This isn't about anything specific right now — just keep "
                        + "it in mind for later.");

                // A short, fully self-contained, unrelated task — deliberately intervening before the
                // preference becomes relevant again, to create genuine narrative distance from it.
                Thread.sleep(4000);
                userConsole.simulateIncomingMessage(
                        "Actually, first: set a reminder alarm for 5 seconds from now and let me know "
                        + "when it fires.");

                // A long real-time gap before the actually-interesting task arrives, deliberately
                // without repeating the seat preference — testing recall across the gap, not repetition.
                Thread.sleep(20000);
                userConsole.simulateIncomingMessage(
                        "Please book a flight from Rome to Paris for January 15th, 2027.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(28);
        workspace.shutdown();
    }
}
