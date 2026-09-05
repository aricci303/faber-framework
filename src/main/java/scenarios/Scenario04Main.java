package scenarios;

import faber.agent.Agent;
import faber.agent.LlmClient;
import faber.environment.AlarmArtifact;
import faber.environment.Workspace;

/**
 * Tests time perceived entirely through AlarmArtifact — no ad hoc
 * wait_timeout percept anywhere in this harness. The agent asks Greg a
 * question and sets a deadline; whichever resolves first (Greg's reply
 * or the alarm) drives the response. Two pending intentions are live
 * simultaneously, and the scenario is built so the losing one's
 * condition is satisfied *after* the race is already decided — Greg's
 * reply genuinely arrives, just too late — to see what
 * checkTriggerFidelity actually does with a dangling trigger, rather
 * than guessing and building a cancellation mechanism preemptively.
 */
public final class Scenario04Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();
        
        workspace.registerType(MessagingArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);

        workspace.registerType(AlarmArtifact.manual(), null);
        AlarmArtifact alarm = new AlarmArtifact("alarm-01", workspace);
        workspace.provision("alarm-01", "Alarm", alarm);

        Agent agent = new Agent("agent-0");
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();   
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "Please ask Greg, via messaging, if he can send the report today. If he doesn't "
                        + "reply soon, let me know we haven't heard back and proceed without it.");
                // Greg's reply is genuine, not a red herring — it just arrives after the 1-second
                // alarm will already have fired, deliberately, to see what happens to the losing trigger.
                // Thread.sleep(30000); /* Greg on time */
                Thread.sleep(40000); /* Greg out of time */
                // Thread.sleep(80000); /* Greg unresponsive */
                messaging.simulateIncomingMessage("Greg",
                        "Sorry for the delay — yes, I'll send the report by end of day.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();
        
        agent.doYourJobAndSelfEvaluate(10);

        workspace.shutdown();
    }

    static final class DeadlineMockLlmClient implements LlmClient {
        private boolean focusedMessaging = false;
        private boolean sentAsk = false;
        private boolean setAlarm = false;
        private boolean focusedAlarm = false;
        private boolean resolved = false;

        @Override
        public String plan(String systemPrompt, String context) {
            boolean taskSeen = context.contains("artifact_signal(user-console-01, message_from_user");

            if (taskSeen && !focusedMessaging) {
                focusedMessaging = true;
                return som("Task: ask Greg if he can send the report today, and if he doesn't reply soon, "
                        + "tell the user and proceed without it. I need to be observing messaging-01 to "
                        + "catch Greg's reply at all, so focusing on it first, and committing now to what "
                        + "I'll do when it arrives.")
                        + act("{\"kind\":\"FOCUS\",\"artifact_id\":\"messaging-01\","
                        + "\"goal\":{\"id\":\"hear_from_greg\",\"content\":\"tell the user Greg's answer once he replies\","
                        + "\"pending_trigger\":{\"condition\":\"message_received on messaging-01 with sender containing Greg\","
                        + "\"planned_action\":\"INVOKE user-console-01.send_msg_to_user with Greg's answer\"}}}");
            }
            if (focusedMessaging && !sentAsk) {
                sentAsk = true;
                return som("Now observing messaging-01. Sending Greg the actual question before doing "
                        + "anything else, since there's nothing to wait for until he's actually been asked.")
                        + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"messaging-01\",\"operation_name\":\"send_message\","
                        + "\"parameters\":{\"recipient\":\"Greg\",\"content\":\"Can you send the report today?\"}}");
            }
            if (sentAsk && !setAlarm) {
                setAlarm = true;
                return som("Question sent. Setting a one-time-unit deadline now, as a fallback if Greg "
                        + "doesn't respond in time — committing to a second, independent pending intention "
                        + "for this, distinct from the one waiting on Greg's actual reply.")
                        + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"alarm-01\",\"operation_name\":\"set_alarm\","
                        + "\"parameters\":{\"seconds\":1},"
                        + "\"goal\":{\"id\":\"deadline_fallback\",\"content\":\"tell the user Greg hasn't responded and proceed without the report\","
                        + "\"pending_trigger\":{\"condition\":\"alarm_fired on alarm-01\","
                        + "\"planned_action\":\"INVOKE user-console-01.send_msg_to_user saying no response yet, proceeding without it\"}}}");
            }
            if (setAlarm && !focusedAlarm) {
                focusedAlarm = true;
                return som("Alarm set. Focusing on alarm-01 too, so I react the moment it fires rather "
                        + "than only discovering it next time I happen to check.")
                        + act("{\"kind\":\"FOCUS\",\"artifact_id\":\"alarm-01\"}");
            }

            if (focusedAlarm && !resolved) {
                if (context.contains("artifact_signal(alarm-01, alarm_fired")) {
                    resolved = true;
                    return som("The deadline passed with no reply from Greg — the alarm fired first. "
                            + "Addressing the deadline_fallback commitment now: telling the user and "
                            + "proceeding without the report.")
                            + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"user-console-01\",\"operation_name\":\"send_msg_to_user\","
                            + "\"parameters\":{\"text\":\"Haven't heard back from Greg in time, so proceeding without the report for now.\"},"
                            + "\"goal\":{\"id\":\"deadline_fallback\"}}");
                }
                if (context.contains("message_received, [Greg")) {
                    resolved = true;
                    return som("Greg replied before the deadline. Addressing the hear_from_greg commitment: "
                            + "telling the user his answer.")
                            + act("{\"kind\":\"INVOKE\",\"artifact_id\":\"user-console-01\",\"operation_name\":\"send_msg_to_user\","
                            + "\"parameters\":{\"text\":\"Greg replied: he'll send the report by end of day.\"},"
                            + "\"goal\":{\"id\":\"hear_from_greg\"}}");
                }
            }

            return som(resolved
                    ? "Already resolved this one way; nothing further required unless something new needs addressing."
                    : "Still waiting to see which happens first — Greg's reply or the deadline.")
                    + act("{\"kind\":\"WAIT\"}");
        }

        private static String som(String text) { return "<state_of_mind>" + text + "</state_of_mind>"; }
        private static String act(String json) { return "<action>" + json + "</action>"; }
    }
}
