package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * Isolates the question Scenario05 could only suggest, not confirm:
 * does the discipline of "register a pending_trigger whenever
 * committing to wait on an operation's resolution" hold consistently
 * across several sequential steps within one long-lived intention, or
 * does it erode after the first one or two?
 *
 * Scenario05's own back half (cycles 7-11) showed trigger registration
 * apparently lapsing after the second step — but that stretch followed
 * a user interruption ("let's do December 26th instead") that also
 * forced a goal_description revision, and only then a failure-recovery
 * detour before any of the later, trigger-less waits happened. Three
 * different candidate causes (sheer step count, a goal revision having
 * just occurred, reacting to an external interruption) were tangled
 * together, with no way to tell which one actually mattered.
 *
 * This scenario removes all three confounds but one: a single request
 * up front, naming four sequential legs (three flights, one hotel),
 * every one of which succeeds on the first attempt — no failure
 * branch, no user message after the first, no externally-forced
 * goal_description revision. The only thing that changes across the
 * four invoke-then-wait steps is that the same intention's plan has
 * already been revised some number of times before. If trigger
 * registration still lapses partway through this run, sheer
 * repetition/habituation on the same intention is the remaining
 * explanation, not interruption or revision-in-response-to-new-
 * information — and if it lapses at a specific step number, that
 * number is itself the finding.
 */
public final class Scenario09Main {

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
                // One message, naming all four legs up front, every one of them booked
                // successfully on the model's first attempt — deliberately no failure branch and
                // no further user input, so nothing external ever prompts a plan revision.
                userConsole.simulateIncomingMessage(
                        "Please book this trip for me, one leg at a time, waiting for each booking "
                        + "to confirm before moving to the next: "
                        + "1) a flight from Bologna to Berlin on December 20th, 2026; "
                        + "2) a flight from Berlin to Munich on December 23rd, 2026; "
                        + "3) a flight from Munich to Bologna on December 26th, 2026; "
                        + "4) a hotel in Berlin, check-in December 20th, check-out December 23rd, "
                        + "to match the first two legs. "
                        + "Once all four are booked, send me the complete itinerary.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(24);
        workspace.shutdown();
    }
}
