package faber.environment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;

/**
 * 
 * Artifact providing functionalities to interact with the user (in chatbot like scenarios)
 * 
 * By default, there is one instance of this kind of artifact in every workspace. 
 */
public final class UserConsoleArtifact extends Artifact {

	public static final String type = "UserConsole";

	// Purely for external observability — e.g. a scenario driver polling for "the agent has
	// actually sent something" rather than guessing at a fixed sleep duration, which is a race
	// condition against real, variable LLM call latency, not a deterministic wait. Doesn't
	// change send_msg_to_user's own behavior at all, just makes it observable from outside.
	private final java.util.concurrent.atomic.AtomicInteger sentMessageCount =
			new java.util.concurrent.atomic.AtomicInteger(0);

	public int sentMessageCount() { return sentMessageCount.get(); }

	// What was sent, in order — read-only, for scenario drivers that assess an agent's final report. Same status as
	// sentMessageCount: observability from outside, no effect on send_msg_to_user itself.
	private final java.util.List<String> sentMessages = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

	public java.util.List<String> sentMessages() {
		synchronized (sentMessages) { return new java.util.ArrayList<>(sentMessages); }
	}

    public UserConsoleArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "send_msg_to_user": {
                String text = params.getString("text");
                System.out.println("[MSG TO USER] " + text); // stand-in for real delivery in this sketch
                sentMessages.add(text);
                sentMessageCount.incrementAndGet();
                return List.of();
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /** Called by the harness or scenario driver to simulate an incoming user message. */
    public void simulateIncomingMessage(String text) {
        System.out.println("[MSG FROM USER] " + text); // stand-in for real delivery in this sketch
        emitSignal("message_from_user", List.of(text));
    }

    public static Manual manual() {
        return new Manual(
                UserConsoleArtifact.type,
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
