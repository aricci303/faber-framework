package faber.devl.cases.toy;

import java.util.List;

import faber.devl.CaseData;
import faber.devl.CaseWorld;
import faber.devl.DevlCase;
import faber.devl.StageKey;
import faber.environment.Workspace;

/**
 * The smallest case that satisfies everything the framework relies on — meeting-room booking requests at a small office, two room kinds, two worlds (after the change, focus rooms allow one
 * hour instead of two). It exists for two reasons: it is what the FRAMEWORK's own suite runs on, so that the suite does not depend on the expense case and any coupling would show; and it is
 * the template for a new case (ADDING-A-CASE.md). The agent never meets it unless a run says --project=toy-room-booking.
 */
public final class ToyDevlCase implements DevlCase {

    static final String SYSTEM_ID = "booking-system-01";
    private final CaseData data;

    /** Built by faber.devl.Projects from the project folder's data. */
    public ToyDevlCase(CaseData data) { this.data = data; }

    private static BookingRequest r(String id, String room, int hours) { return new BookingRequest(id, "Ada", room, hours); }

    @Override public CaseData data() { return data; }

    @Override public List<BookingRequest> workload(StageKey key) {
        switch (key) {
            case LEARNING_1: return List.of(r("B101", "focus", 1), r("B102", "workshop", 3));
            case LEARNING_2: return List.of(r("B201", "focus", 2), r("B202", "workshop", 4), r("B203", "focus", 3), r("B204", "workshop", 5));
            case LEARNING_3: return List.of(r("B301", "focus", 2), r("B302", "workshop", 4), r("B303", "focus", 3));
            case CLEAN:      return List.of(r("B501", "focus", 1), r("B502", "workshop", 2), r("B503", "workshop", 5));
            case CHANGE:     return List.of(r("B401", "focus", 2), r("B402", "focus", 1), r("B403", "workshop", 4));
            case OPERATION:  return List.of(r("B601", "focus", 2), r("B602", "workshop", 3));
            default: throw new IllegalArgumentException("no workload for " + key);
        }
    }

    @Override public CaseWorld provision(Workspace workspace) {
        workspace.registerType(BookingSystemArtifact.manual(), null);
        BookingSystemArtifact system = new BookingSystemArtifact(SYSTEM_ID, workspace);
        workspace.provision(SYSTEM_ID, BookingSystemArtifact.type, system);
        return system;
    }
}
