package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * A cleaner test of the objective/plan distinction than Scenario05
 * turned out to provide. Scenario05's own user message happens to
 * spell out the fallback contingency explicitly ("if the flight can't
 * be booked... check what other dates are available and let me know
 * before proceeding further") — meaning when that language shows up
 * in a registered goal's objective, it's genuinely correct: the
 * requester specified it, verbatim. That confound made an early
 * analysis of a real log wrongly flag correct behavior as a
 * conflation bug — the objective/plan test is "who specified it," and
 * relaying the requester's own explicit words is not agent invention.
 *
 * Here, the initial request is deliberately minimal — book a flight
 * for a specific date, then a hotel for the same length of stay, no
 * stated fallback at all. When the same engineered date failure
 * happens, whatever contingency-handling text the agent writes for
 * itself (check alternatives? ask the user? pick a nearby date on its
 * own?) is unambiguously agent-invented, since the requester never
 * said anything about it. If that text lands in "objective" here,
 * that is a genuine, unconfounded instance of the conflation the
 * objective/plan split exists to prevent — not an artifact of the
 * requester having already said it themselves.
 */
public final class Scenario08Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(FlightBookingArtifact.manual(), null);
        FlightBookingArtifact flights = new FlightBookingArtifact("flight-01", workspace);
        workspace.provision("flight-01", "FlightBooking", flights);

        workspace.registerType(HotelBookingArtifact.manual(), null);
        HotelBookingArtifact hotels = new HotelBookingArtifact("hotel-01", workspace);
        workspace.provision("hotel-01", "HotelBooking", hotels);

        Agent agent = new Agent("agent-0");
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                // Deliberately minimal — no fallback instruction, no mention of what to do if the
                // date fails, no instruction to check with the user first. Whatever the agent does
                // about the failure, and however it describes doing it, is entirely its own.
                userConsole.simulateIncomingMessage(
                        "Please book a flight from Bologna to Berlin for December 24th, 2026, and a "
                        + "hotel in Berlin for the same three nights. Let me know once it's all booked.");

                // Gives the agent real cycles to hit the failure and decide what to do about it
                // entirely on its own, before any further user input arrives.
                Thread.sleep(20000);
                userConsole.simulateIncomingMessage("Any update?");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(20);
        workspace.shutdown();
    }
}
