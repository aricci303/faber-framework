package scenarios;

import faber.agent.Agent;
import faber.agent.LlmClient;
import faber.environment.Workspace;

/**
 * Reproduces ARE's real scenario_events_tutorial (are/simulation/
 * scenarios/scenario_events_tutorial/scenario.py): several emails
 * arrive over time, one of them triggered by an environment-side
 * condition (2+ emails received) rather than a fixed delay, and the
 * user only asks for a count and summary once things have settled —
 * with no prior instruction to monitor email at all.
 *
 * This is specifically built to test the new belief-mapping mechanism
 * (Artifact.currentObsProperties / Workspace rendering current values
 * for observed artifacts): the agent has no reason to focus on email-01
 * until it is asked, by which point four emails have already arrived
 * while completely unobserved. The question is whether focusing at
 * that late point immediately surfaces the true current inbox_count,
 * or whether — as the old percept-only mechanism would have done — it
 * only starts learning about changes from that point forward, missing
 * everything that already happened.
 */
public final class Scenario03Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();
        
        workspace.registerType(EmailArtifact.manual(), null);
        EmailArtifact email = new EmailArtifact("email-01", workspace);
        workspace.provision("email-01", "EmailClientApp", email);

        Agent agent = new Agent("agent-0");
        agent.init(workspace);
        
        var userConsole = workspace.getUserConsole();   
        agent.forceObserving(userConsole.id());
        
        // The real scenario's timeline: scheduled emails, one condition-triggered (>=2 received),
        // and the user's question arriving only after things have settled — no prior instruction
        // to watch email-01 at all, so the agent accumulates four emails entirely unobserved.
        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(2000);
                email.simulateIncomingEmail("email_1", "tutorial@example.com",
                        "Welcome to Events Tutorial", "Scheduled email one.", null);
                Thread.sleep(150);
                email.simulateIncomingEmail("email_2", "tutorial@example.com",
                        "Second Scheduled Email", "Scheduled email two — inbox now at 2.", null);
                // Environment-side condition (>=2 emails received) fires here, matching ARE's
                // ConditionCheckEvent — not the agent's own pending_trigger mechanism, deliberately:
                // this is the environment reasoning about its own state, not a standing agent commitment.
                Thread.sleep(2000);
                email.simulateIncomingEmail("email_3", "system@example.com",
                        "Conditional Event Triggered!", "Sent because 2+ emails had arrived.", null);
                Thread.sleep(120);
                email.simulateIncomingEmail("email_4", "tutorial@example.com",
                        "Absolutely Timed Email", "A fourth, later scheduled email.", null);
                Thread.sleep(2000);
                userConsole.simulateIncomingMessage(
                        "How many emails have you received, and can you tell me about them?");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(10);

        workspace.shutdown();

    }

    static final class EventsMockLlmClient implements LlmClient {
        private boolean focused = false;
        private boolean queried = false;
        private boolean replied = false;

        @Override
        public String plan(String systemPrompt, String context) {
            boolean taskSeen = context.contains("artifact_signal(user-console-01, message_from_user");

            if (taskSeen && !focused) {
                focused = true;
                return som("The user is asking how many emails I've received and for details about "
                        + "them. I've never been observing email-01, so I have no idea what's in there "
                        + "yet — focusing on it now should immediately show me its true current state, "
                        + "not just future changes, since I'm told observable properties are mapped as "
                        + "soon as observation starts.")
                        + act("{\"kind\":\"FOCUS\",\"artifact_id\":\"email-01\"}");
            }

            if (focused && context.contains("current properties: {inbox_count=") && !queried) {
                queried = true;
                return som("WORKSPACE now shows email-01's current inbox_count directly — I can see how "
                        + "many without needing to wait for a fresh change notification. That answers "
                        + "'how many' already. For 'tell me about them' I still need the actual "
                        + "sender/subject details, which aren't in the observable property — invoking "
                        + "list_emails for that.")
                        + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"email-01\",\"operation_name\":\"list_emails\",\"parameters\":{}}");
            }

            if (queried && !replied && (context.contains("operation_completed") || context.contains("operation_failed"))) {
                replied = true;
                boolean failed = context.contains("operation_failed");
                String text = failed
                        ? "I tried to list the emails but the operation failed."
                        : "You've received 4 emails: from tutorial@example.com (Welcome to Events "
                        + "Tutorial, Second Scheduled Email, Absolutely Timed Email) and one from "
                        + "system@example.com (Conditional Event Triggered!, sent once 2 or more had arrived).";
                return som("list_emails resolved" + (failed ? " with a failure" : "") + ". Replying to "
                        + "the user now with the count and details, since that's exactly what was asked.")
                        + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"user-console-01\",\"operation_name\":\"send_msg_to_user\","
                        + "\"parameters\":{\"text\":\"" + text + "\"}}");
            }

            return som(taskSeen
                    ? "Working through the request — nothing further to do yet this cycle."
                    : "No task yet — nothing to do until the user asks something.")
                    + act("{\"kind\":\"WAIT\",\"timeout_millis\":5000}");
        }

        private static String som(String text) { return "<state_of_mind>" + text + "</state_of_mind>"; }
        private static String act(String json) { return "<action>" + json + "</action>"; }
    }
}
