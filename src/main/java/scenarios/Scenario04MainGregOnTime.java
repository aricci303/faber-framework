package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.AlarmArtifact;
import faber.environment.Workspace;

/**
 * "Greg on time." Second of three variants — see Scenario04Main's own
 * doc comment for the shared rationale, including why a hardcoded
 * absolute delay can't reliably test this case and why AlarmArtifact's
 * timeScale is used to keep real wall-clock time practical without
 * changing anything the agent itself perceives. Here Greg's reply is
 * deliberately timed to land before the alarm fires — computed as half
 * the alarm's actual, observed (and scaled) duration, guaranteeing it
 * lands on the winning side of the race regardless of what duration the
 * agent chose.
 *
 * Tests the opposite resolution from Scenario04Main: the reply arrives
 * while the trigger is still genuinely pending, not after it's already
 * lost the race — a real, live intention resolving normally, rather
 * than what checkTriggerFidelity does with one that's already decided.
 */
public final class Scenario04MainGregOnTime {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(MessagingArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);

        AlarmArtifact alarm = (AlarmArtifact) workspace.instanceOf("alarm-01");
        alarm.setTimeScale(0.02);

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "Please ask Greg, via messaging, if he can send the report today. If he doesn't "
                        + "reply soon, let me know we haven't heard back and proceed without it.");

                long deadline = System.currentTimeMillis() + 90_000;
                while (alarm.lastSetAlarmSeconds() < 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                long alarmSeconds = Math.max(alarm.lastSetAlarmSeconds(), 0);

                // On time: reply arrives at half the alarm's own actual, scaled duration —
                // guaranteed before it fires, whatever that duration turns out to be.
                long scaledAlarmMillis = (long) (alarmSeconds * 1000 * alarm.timeScale());
                Thread.sleep(scaledAlarmMillis / 2);
                messaging.simulateIncomingMessage("Greg",
                        "Sorry for the delay — yes, I'll send the report by end of day.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(16);
        workspace.shutdown();
    }
}
