package scenarios;

import java.io.File;

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
public final class Scenario02Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();
        
        workspace.registerType(MessagingArtifact.manual(), null);
        workspace.registerType(EmailArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        EmailArtifact email = new EmailArtifact("email-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);
        workspace.provision("email-01", "EmailClientApp", email);

        Agent agent = new Agent("agent-0");
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();   
        agent.forceObserving(userConsole.id());

        userConsole.simulateIncomingMessage(
        		"Please forward any PDF that Greg sends you to John Doe (johndoe@example.com) as soon as you receive it.");

        // The scenario's real timeline, played independently of the agent's cycle pace —
        // an unrelated message arrives early (noise), the load-bearing email arrives later.
        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(10000);
            	System.out.println("*********SENDING JOHN MAIL******");
                messaging.simulateIncomingMessage("John Doe",
                        "Hey, did Greg ever send you that music list? No rush.");
                Thread.sleep(10000);
            	System.out.println("*********SENDING GREG MAIL******");
                email.simulateIncomingEmail("greg_email", "Greg", "List of music", 
                        "Attached is the music list PDF.", new File("List.pdf"));
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();
        
        
        agent.doYourJobAndSelfEvaluate(10);

        workspace.shutdown();

    }


    /**
     * A context-reactive stand-in for a real model: a small state machine
     * that inspects the actual assembled context each cycle rather than
     * following a fixed turn index, since real environment timing means
     * what is genuinely visible varies cycle to cycle (see EventQueue's
     * blocking awaitAtLeastOne — a WAIT cycle genuinely blocks until
     * something real arrives, so there is no need to guess timing here
     * either, only to react correctly to whatever shows up).
     */
    static final class ScenarioMockLlmClient implements LlmClient {
        private boolean focused = false;
        private boolean invoked = false;
        private boolean replied = false;

        @Override
        public String plan(String systemPrompt, String context) {
            if (!focused) {
                focused = true;
                return som("The task is to forward Greg's PDF to John as soon as it arrives, but I don't "
                        + "know when that will be. I need to be observing email-01 to catch its arrival "
                        + "signal at all, so focusing on it before anything else.")
                        + act("{\"kind\":\"FOCUS\",\"artifact_id\":\"email-01\","
                        + "\"goal\":{\"id\":\"deliver_pdf_to_john\",\"content\":\"forward Greg's pdf to John as soon as it arrives\","
                        + "\"pending_trigger\":{\"condition\":\"email_received on email-01 with sender containing greg\","
                        + "\"planned_action\":\"INVOKE email-01.forward_email with recipients=[johndoe@example.com]\"}}}");
            }
            if (context.contains("artifact_signal(email-01, email_received") && !invoked) {
                invoked = true;
                return som("email_received on email-01 confirms Greg's email (greg_email) has arrived — "
                        + "exactly the trigger I was waiting for. The message from John on messaging-01 "
                        + "earlier was unrelated small talk, not something this task needs. Forwarding "
                        + "the email to John now.")
                        + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"email-01\",\"operation_name\":\"forward_email\","
                        + "\"parameters\":{\"email_id\":\"greg_email\",\"recipients\":[\"johndoe@example.com\"]},"
                        + "\"goal\":{\"id\":\"deliver_pdf_to_john\",\"content\":\"forward Greg's pdf to John as soon as it arrives\"}}");
            }
            if (invoked && !replied && (context.contains("operation_completed") || context.contains("operation_failed"))) {
                replied = true;
                boolean failed = context.contains("operation_failed");
                String text = failed
                        ? "I tried to forward Greg's email to John but the operation failed."
                        : "Done — I've forwarded Greg's email to John.";
                return som("The forward_email operation resolved" + (failed ? ", with a failure" : " successfully")
                        + ". Replying via UserConsole now, since that's what the task actually asked for — "
                        + "and this reply is itself a tracked operation, so I won't send it twice.")
                        + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"user-console-01\",\"operation_name\":\"send_msg_to_user\","
                        + "\"parameters\":{\"text\":\"" + text + "\"}}");
            }
            return som("Nothing new relevant to the task yet — still waiting for Greg's email specifically, "
                    + "not just any activity in the workspace.")
                    + act("{\"kind\":\"WAIT\",\"timeout_millis\":5000}");
        }

        private static String som(String text) { return "<state_of_mind>" + text + "</state_of_mind>"; }
        private static String act(String json) { return "<action>" + json + "</action>"; }
    }    
    
}

