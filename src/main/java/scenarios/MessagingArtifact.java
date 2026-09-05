package scenarios;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;

import faber.agent.EventQueue;
import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;
import faber.environment.Manual.Signal;

/**
 * Reproduces the relevant behavior of ARE's real MessagingApp
 * (are/simulation/apps/messaging.py), as exercised in scenario_tutorial:
 * sending a message is an agent-invoked operation; an incoming message
 * from a contact is environment-originated and surfaces as a signal,
 * not something the agent caused. Present in this workspace mainly as
 * environmental color the agent must sift as irrelevant to its actual
 * task — matching how real Gaia2 scenarios populate several apps, only
 * some of which are load-bearing for a given task.
 */
public final class MessagingArtifact extends Artifact {

    public MessagingArtifact(String id, Workspace workspace) {
        super(id, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "send_message": {
                String messageId = "msg-" + System.nanoTime();
                return List.of(messageId);
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /** Called by the scenario driver to simulate an incoming message arriving — environment-originated, not agent-caused. */
    public void simulateIncomingMessage(String sender, String content) {
    	System.out.println("***** NEW MESSAGE INCOMING from " + sender + " content: " + content);
        emitSignal("message_received", List.of(sender, content));
    }

    public static Manual manual() {
        return new Manual(
                "MessagingApp",
                "send and receive text messages with contacts",
                null, null,
                List.of(),
                List.of(new Manual.Signal("message_received",
                        "a new message arrived from a contact",
                        List.of(new Manual.Param("sender", "who sent it"),
                                new Manual.Param("content", "message text")))),
                List.of(new Manual.Operation("send_message(recipient, content)",
                        "send a text message to a contact",
                        List.of(new Manual.Param("message_id", "id of the sent message")))),
                null
        );
    }
}
