package scenarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;

/**
 * A flight booking service, deliberately built to test hierarchical
 * subgoal planning with failure recovery. book_flight fails outright
 * for one specific date (no seats available) — not a random or flaky
 * failure, so the scenario is reproducible and the failure is
 * attributable to a real, checkable cause rather than noise.
 * list_available_dates gives the agent a legitimate, concrete path to
 * replan (try a different date) rather than only the option of blind
 * retry or giving up.
 */
public final class FlightBookingArtifact extends Artifact {

    private static final String UNAVAILABLE_DATE = "2026-12-24";
    private static final List<String> ALTERNATIVE_DATES = List.of("2026-12-23", "2026-12-26", "2026-12-27");

    private final Map<String, String> bookings = new LinkedHashMap<>(); // booking_id -> "origin-destination-date"
    private int idCounter = 0;

    public FlightBookingArtifact(String id, Workspace workspace) {
        super(id, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(400); // simulates real work
        switch (operationName) {
            case "book_flight": {
                String origin = params.getString("origin");
                String destination = params.getString("destination");
                String date = params.getString("date");
                if (UNAVAILABLE_DATE.equals(date)) {
                    throw new IllegalStateException(
                            "no seats available on " + date + " for " + origin + "-" + destination
                            + " — try list_available_dates for alternatives");
                }
                String bookingId = "flight-" + (++idCounter);
                bookings.put(bookingId, origin + "-" + destination + "-" + date);
                return List.of(bookingId, date);
            }
            case "list_available_dates": {
                String origin = params.getString("origin");
                String destination = params.getString("destination");
                return List.of(origin, destination,new JSONArray(ALTERNATIVE_DATES));
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    public static Manual manual() {
        return new Manual(
                "FlightBooking",
                "search and book flights",
                null, null,
                List.of(),
                List.of(),
                List.of(
                        new Operation("book_flight(origin, destination, date)",
                                "book a flight for the given date; fails if no seats are available on that date",
                                List.of(new Param("booking_id", "id of the confirmed booking"),
                                        new Param("confirmed_date", "the date actually booked"))),
                        new Operation("list_available_dates(origin,destination)",
                                "get dates with known availability for from 'origin' to 'destination', useful after a booking failure",
                                List.of(new Param("dates", "list of available dates")))
                ),
                null
        );
    }
}
