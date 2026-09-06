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

	public static final String type = "FlightBooking";

    private static final String UNAVAILABLE_DATE = "2026-12-24";
    private static final List<String> ALTERNATIVE_DATES = List.of("2026-12-23", "2026-12-26", "2026-12-27");

    private final Map<String, String> bookings = new LinkedHashMap<>(); // booking_id -> "origin-destination-date"
    private int idCounter = 0;
    private JSONObject lastRequest = null;

    public FlightBookingArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(400); // simulates real work
        switch (operationName) {
            case "book_flight": {
                String origin = params.getString("origin");
                String destination = params.getString("destination");
                String date = params.getString("date");
                String seatPreference = params.optString("seat_preference", null);

                if (UNAVAILABLE_DATE.equals(date)) {
                    recordRequest(origin, destination, date, "failed", null, seatPreference);
                    throw new IllegalStateException(
                            "no seats available on " + date + " for " + origin + "-" + destination
                            + " — try list_available_dates for alternatives");
                }
                String bookingId = "flight-" + (++idCounter);
                bookings.put(bookingId, origin + "-" + destination + "-" + date);
                recordRequest(origin, destination, date, "confirmed", bookingId, seatPreference);
                return List.of(bookingId, date,
                        seatPreference != null ? seatPreference : "no preference specified");
            }
            case "list_available_dates": {
                String origin = params.getString("origin");
                String destination = params.getString("destination");
                return List.of(origin, destination, new JSONArray(ALTERNATIVE_DATES));
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /**
     * Belief-mapped, exactly like EmailArtifact.inbox_count and
     * AlarmArtifact.fired_alarms — the true current value maintained
     * unconditionally, visible in WORKSPACE for as long as the agent is
     * observing this artifact. This exists specifically because
     * origin/destination were previously only ever visible as part of a
     * single cycle's own operation_started/failed percept — gone from
     * context the moment that cycle passed, which is exactly the gap
     * that matters here: the retry after a failure often happens many
     * cycles later, once the user replies with an alternative date, not
     * in the very next cycle.
     */
    private void recordRequest(String origin, String destination, String date, String status,
                                String bookingId, String seatPreference) {
        JSONObject newRequest = new JSONObject();
        newRequest.put("origin", origin);
        newRequest.put("destination", destination);
        newRequest.put("date", date);
        newRequest.put("status", status);
        if (bookingId != null) newRequest.put("booking_id", bookingId);
        if (seatPreference != null) newRequest.put("seat_preference", seatPreference);
        notifyObsPropertyChanged("last_request", lastRequest, newRequest);
        lastRequest = newRequest;
    }

    public static Manual manual() {
        return new Manual(
        		FlightBookingArtifact.type,
                "search and book flights",
                null, null,
                List.of(new Param("last_request",
                        "origin, destination, date, and status (confirmed or failed) of the most "
                        + "recently requested booking — check this before retrying rather than relying "
                        + "on your own memory of a prior request, especially after any delay")),
                List.of(),
                List.of(
                        new Operation("book_flight(origin, destination, date, seat_preference)",
                                "book a flight for the given date; fails if no seats are available on that "
                                + "date. seat_preference is optional (e.g. \"aisle\", \"window\") — if you "
                                + "hold a relevant standing preference, pass it explicitly rather than "
                                + "leaving it out",
                                List.of(new Param("booking_id", "id of the confirmed booking"),
                                        new Param("confirmed_date", "the date actually booked"),
                                        new Param("confirmed_seat_preference", "the seat preference actually applied, if any"))),
                        new Operation("list_available_dates(origin,destination)",
                                "get dates with known availability for from 'origin' to 'destination', useful after a booking failure",
                                List.of(new Param("dates", "list of available dates")))
                ),
                null
        );
    }
}
