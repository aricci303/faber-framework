 package scenarios;

import java.util.concurrent.atomic.AtomicInteger;

import faber.agent.Agent;
import faber.agent.LlmClient;
import faber.environment.Workspace;

/**
 * Runs Stage 0 for real, updated for the operation_started refinement:
 * cycle 3 (WAIT) now genuinely perceives operation_started before the
 * operation resolves, rather than seeing an empty percept set — a
 * direct, visible consequence of closing the gap where op_id was
 * previously never communicated before its resolution.
 */
public final class Scenario01Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();
        
    	workspace.registerType(Counter.manual(), Counter.factory(workspace));
        Counter counter01 = new Counter("counter-01", workspace, 0);
        workspace.provision("counter-01", "Counter", counter01);

        Agent agent = new Agent("agent-0");
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();   
        agent.forceObserving(userConsole.id());

        userConsole.simulateIncomingMessage("Please increment counter-01 by 5 and tell me the new value. Then do the same with a "
                + "new counter created from scratch, to be called scratch-1: create it, increment it by "
                + "5, and tell me its value too.");
        
        agent.doYourJobAndSelfEvaluate(10);

        workspace.shutdown();

    }

    /** A scripted stand-in for a real model, exercising the full (now four-kind) Stage 0 action vocabulary. */
    static final class MockLlmClient implements LlmClient {
        private final AtomicInteger turn = new AtomicInteger(0);

        @Override
        public String plan(String systemPrompt, String context) {
            int t = turn.incrementAndGet();
            switch (t) {
                case 1:
                    return som("Task from the user: increment counter-01 by 5 and report the new value. "
                            + "I want to see the count change directly via its own observable property, "
                            + "not just through the operation's completion event, so focusing on it first.")
                            + act("{\"kind\":\"FOCUS\",\"artifact_id\":\"counter-01\"}");
                case 2:
                    return som("Now observing counter-01. Invoking inc(amount=5); I expect a new_count "
                            + "output back, and I'll reply with it once resolved.")
                            + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"counter-01\",\"operation_name\":\"inc\","
                            + "\"parameters\":{\"amount\":5},"
                            + "\"goal\":{\"id\":\"report_new_count\",\"content\":\"tell the user the counter's new value after incrementing by 5\"}}");
                case 3:
                    return som("Nothing new yet — the increment is still in flight. Waiting rather than "
                            + "replanning against no information.")
                            + act("{\"kind\":\"WAIT\",\"timeout_millis\":5000}");
                case 4:
                    return som("The operation resolved and the observed property updated to match — both "
                            + "confirm the count is now 5. Replying to the user via UserConsole now, since "
                            + "that's the only channel that reaches them and it's tracked like any other operation.")
                            + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"user-console-01\",\"operation_name\":\"send_msg_to_user\","
                            + "\"parameters\":{\"text\":\"counter-01 is now at 5.\"}}");
                case 5:
                    return som("The reply operation completed. Demonstrating dynamic artifact creation now "
                            + "that the user's request is handled: creating a scratch counter via WorkspaceArtifact.")
                            + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"workspace-01\",\"operation_name\":\"create_artifact\","
                            + "\"parameters\":{\"type\":\"Counter\",\"proposed_id\":\"scratch-1\",\"constructor_parameters\":{\"start\":100}}}");
                default:
                    return som("scratch-1 was created successfully; it served its demonstration purpose, "
                            + "so disposing of it now via WorkspaceArtifact rather than leaving it unused.")
                            + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"workspace-01\",\"operation_name\":\"dispose_artifact\","
                            + "\"parameters\":{\"artifact_id\":\"scratch-1\"}}");
            }
        }

        private static String som(String text) { return "<state_of_mind>" + text + "</state_of_mind>"; }
        private static String act(String json) { return "<action>" + json + "</action>"; }
    }
}

