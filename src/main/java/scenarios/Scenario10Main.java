package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * Stress-tests the consistency self-check instruction (added after a
 * real Scenario05 run produced a hotel check-in two days before the
 * flight it was meant to follow) along two axes the original fix was
 * never actually tested against:
 *
 *   1. A repeated instance of the SAME pattern (a downstream date that
 *      must track an upstream one that gets rebooked), but phrased
 *      relatively — "check-out matching whenever the Berlin-to-Munich
 *      leg actually departs" — rather than as a literal date the model
 *      can infer once and forget. This checks whether the fix
 *      generalizes past the single two-item case (one flight, one
 *      hotel) it was written from to a three-leg chain where the
 *      dependent item sits in the MIDDLE of the chain, not at the end.
 *
 *   2. A genuinely different dimension: an aisle-seat preference,
 *      stated once, up front, for "every flight leg" — including one
 *      that fails and must be rebooked. The rebooking is a new
 *      book_flight call the model itself generates in response to a
 *      failure; nothing in the user's own message re-states the
 *      preference for that specific, not-yet-anticipated call. This
 *      tests whether an established commitment survives into an
 *      action the model authors on its own, not just whether an
 *      inferred value updates correctly — a different failure mode
 *      than a stale date, even though both are downstream-consistency
 *      questions in the same broad sense.
 *
 * Deliberately no scripted user interruption: the failure-and-rebook
 * must be resolved autonomously ("without checking back with me
 * first"), so nothing external cues the model to reconsider — if the
 * self-check holds here, it is being applied as a standing discipline,
 * not triggered by noticing a salient new message the way the original
 * Scenario05 fix might have been.
 */
public final class Scenario10Main {

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
                // Leg 2's date (Dec 24) is the artifact's hardcoded failure date, forcing exactly one
                // rebooking. No fallback instruction is given beyond "resolve it yourself" — deliberately
                // no scripted follow-up message, so nothing external prompts reconsideration.
                userConsole.simulateIncomingMessage(
                        "Please book this trip for me, one leg at a time, waiting for each booking to "
                        + "confirm before moving to the next: 1) a flight from Bologna to Berlin on "
                        + "December 20th, 2026; 2) a flight from Berlin to Munich on December 24th, 2026; "
                        + "3) a flight from Munich to Bologna on December 27th, 2026. Please request an "
                        + "aisle seat for me on every flight leg. Also book a hotel in Berlin, check-in "
                        + "December 20th, check-out matching whenever the Berlin-to-Munich leg actually "
                        + "departs. If any flight can't be booked for the date I asked for, check "
                        + "available alternative dates yourself and rebook for the earliest one that "
                        + "works, without checking back with me first. Once everything is booked, send "
                        + "me the complete itinerary.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(24);
        workspace.shutdown();
    }
}
