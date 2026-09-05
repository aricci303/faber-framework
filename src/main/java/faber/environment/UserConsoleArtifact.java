package faber.environment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;

/**
 * Replaces the ad hoc REPLY action and USER_MESSAGE percept type. The
 * gap this closes: REPLY had no correlation id and no operation_started/
 * completed/failed, so the agent had no way to tell, from context alone,
 * whether it had already replied — exactly the repeated-reply bug this
 * change was proposed to fix. As an ordinary operation on an artifact,
 * it now gets the same tracking every other operation already has.
 *
 * Must be provisioned via Workspace.provisionAlwaysObserved — see that
 * method's doc for why this one specifically can't use ordinary FOCUS.
 */
public final class UserConsoleArtifact extends Artifact {

    public UserConsoleArtifact(String id, Workspace workspace) {
        super(id, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "send_msg_to_user": {
                String text = params.getString("text");
                System.out.println("[USER SEES] " + text); // stand-in for real delivery in this sketch
                return List.of();
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /** Called by the harness or scenario driver to simulate an incoming user message. */
    public void simulateIncomingMessage(String text) {
        emitSignal("message_from_user", List.of(text));
    }

    public static Manual manual() {
        return new Manual(
                "UserConsole",
                "send messages to, and receive messages from, the user",
                null, null,
                List.of(),
                List.of(new Manual.Signal("message_from_user",
                        "the user sent a new message",
                        List.of(new Manual.Param("text", "message text")))),
                List.of(new Manual.Operation("send_msg_to_user(text)",
                        "send a message to the user.", List.of())),
                null
        );
    }
}
