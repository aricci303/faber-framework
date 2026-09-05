package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * Tests hierarchical subgoal planning with genuine failure recovery —
 * the open research question from this stage of the project: does the
 * current flat GoalLedger (a goal id maps to content, with no explicit
 * parent/child structure) suffice to track a dependency between
 * subgoals, or does tracking "subgoal 2 depends on subgoal 1
 * succeeding" expose a real need for genuine hierarchical goal
 * structure — the same way pending intentions exposed a gap a flat
 * wish model couldn't see?
 *
 * Three subgoals, genuinely dependent: (1) book a flight for a specific
 * date that is deliberately unavailable, (2) book a hotel for the same
 * dates, contingent on (1) succeeding, (3) report the final itinerary.
 * The task explicitly asks the agent to stop and report back rather
 * than autonomously pick a new date — so recovery requires a second,
 * later user message resolving the interruption, testing whether the
 * agent's own tracking survives across that gap and correctly resumes
 * the original three-subgoal plan rather than losing the thread.
 *
 * Deliberately not specified: whether the hotel dates should shift to
 * match a delayed flight date. Left open on purpose, to see whether the
 * agent reasons about that consistency on its own rather than being
 * told to.
 */
public final class Scenario05Main {

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
                userConsole.simulateIncomingMessage(
                        "Please book a flight from Bologna to Berlin for December 24th, 2026. Once the "
                        + "flight is confirmed, book a hotel in Berlin for the same dates (check-in "
                        + "December 24th, check-out December 27th). Once both are booked, let me know the "
                        + "full itinerary. If the flight can't be booked for December 24th, check what "
                        + "other dates are available and let me know before proceeding further — don't "
                        + "book the hotel until the flight situation is resolved.");

                // Gives the agent real cycles to hit the failure, check alternatives, and report
                // back before the user's follow-up arrives and resolves the interruption.
                Thread.sleep(25000);
                userConsole.simulateIncomingMessage("Let's do December 26th instead.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(20);
        workspace.shutdown();
    }
}
