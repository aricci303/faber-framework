package scenarios;

import java.util.List;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;

/**
 * Deliberately mirrors MessagingArtifact's exact shape — one send
 * operation, one incoming signal, no observable properties, no
 * query-style recovery operation an agent could fall back on after
 * missing the signal — under a deliberately unfamiliar type name and
 * function description. Built specifically for the manual-delay
 * variant pair (base vs. pre-warmed via a seeded notebook-01 note): the
 * only variable that should differ between the two runs is whether the
 * agent already knows this type's shape, not anything about the type
 * itself. Structurally identical to MessagingArtifact on purpose.
 */
public final class PagerArtifact extends Artifact {

    public static final String type = "Pager";

    public PagerArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "send_page": {
                String pageId = "page-" + System.nanoTime();
                return List.of(pageId);
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /** Called by the scenario driver to simulate an incoming page arriving — environment-originated,
     *  not agent-caused, exactly like MessagingArtifact.simulateIncomingMessage. */
    public void simulateIncomingPage(String sender, String content) {
        System.out.println("***** NEW PAGE INCOMING from " + sender + " content: " + content);
        emitSignal("page_received", List.of(sender, content));
    }

    public static Manual manual() {
        return new Manual(
                PagerArtifact.type,
                "send and receive short pages to on-call contacts",
                null, null,
                List.of(),
                List.of(new Manual.Signal("page_received",
                        "a new page arrived from a contact",
                        List.of(new Manual.Param("sender", "who sent it"),
                                new Manual.Param("content", "page text")))),
                List.of(new Manual.Operation("send_page(recipient, content)",
                        "send a short page to a contact",
                        List.of(new Manual.Param("page_id", "id of the sent page")))),
                null
        );
    }
}
