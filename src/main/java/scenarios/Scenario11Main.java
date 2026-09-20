package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * Closes the loop Scenario10 opened but never actually tested: that
 * run's model correctly reasoned, unprompted, that hotel check-in
 * should track leg 1's actual confirmed date rather than the literal
 * date it was given — but leg 1 confirmed on the first try, so that
 * reasoning was never exercised. This scenario forces it to be.
 *
 * The artifact's only failure condition is an exact match on a single
 * hardcoded date, checked regardless of route — there is no way to
 * force two independent flight failures other than requesting that
 * same date for two different legs. Leg 1 (Bologna-Berlin) and leg 2
 * (Berlin-Munich) are both requested for it here. Check-in is stated
 * as a literal date matching leg 1's now-stale original request — the
 * exact mirror of how check-out was tested in Scenario10, but now on
 * the other side of the stay, and now actually forced to matter rather
 * than merely reasoned about.
 *
 * Deliberately left unresolved by design, not a flaw to fix: both legs
 * independently failing and independently calling list_available_dates
 * could plausibly lead the model to pick "earliest available" for each
 * without cross-referencing the other's outcome, landing both on the
 * same date and producing a zero-night hotel stay — which the hotel
 * artifact will accept without any validation at all. Whether the
 * model notices and avoids this itself, or produces the degenerate
 * booking without comment, is itself the finding; nothing here nudges
 * it toward either outcome.
 */
public final class Scenario11Main {

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
                // Both leg 1 and leg 2's requested dates are the artifact's hardcoded failure date,
                // forcing two independent rebookings. Check-in mirrors leg 1's original (now-stale)
                // date literally; check-out stays relative, as in Scenario10. No fallback guidance
                // beyond "resolve it yourself" — deliberately no scripted follow-up message.
                userConsole.simulateIncomingMessage(
                        "Please book this trip for me, one leg at a time, waiting for each booking to "
                        + "confirm before moving to the next: 1) a flight from Bologna to Berlin on "
                        + "December 24th, 2026; 2) a flight from Berlin to Munich on December 24th, 2026; "
                        + "3) a flight from Munich to Bologna on December 27th, 2026. Please request an "
                        + "aisle seat for me on every flight leg. Also book a hotel in Berlin, check-in "
                        + "December 24th, check-out matching whenever the Berlin-to-Munich leg actually "
                        + "departs. If any flight can't be booked for the date I asked for, check "
                        + "available alternative dates yourself and rebook for the earliest one that "
                        + "works, without checking back with me first. Once everything is booked, send "
                        + "me the complete itinerary.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(28);
        workspace.shutdown();
    }
}
