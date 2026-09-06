package scenarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;

/**
 * A hotel booking service. Deliberately kept simple — always succeeds —
 * so this scenario has exactly one engineered point of failure
 * (FlightBookingArtifact.book_flight), isolating its effect rather than
 * confounding it with a second, independent failure mode.
 */
public final class HotelBookingArtifact extends Artifact {

	public static final String type = "HotelBooking";

    private final Map<String, String> bookings = new LinkedHashMap<>();
    private int idCounter = 0;

    public HotelBookingArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(400); // simulates real work
        switch (operationName) {
            case "book_hotel": {
                String destination = params.getString("destination");
                String checkIn = params.getString("check_in");
                String checkOut = params.getString("check_out");
                String bookingId = "hotel-" + (++idCounter);
                bookings.put(bookingId, destination + " " + checkIn + " to " + checkOut);
                return List.of(bookingId);
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    public static Manual manual() {
        return new Manual(
        		HotelBookingArtifact.type,
                "book hotel rooms",
                null, null,
                List.of(),
                List.of(),
                List.of(new Operation("book_hotel(destination, check_in, check_out)",
                        "book a hotel room for the given dates",
                        List.of(new Param("booking_id", "id of the confirmed booking")))),
                null
        );
    }
}
