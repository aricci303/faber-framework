package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.AlarmArtifact;
import faber.environment.Workspace;

/**
 * "Greg unresponsive." Third of three variants — see Scenario04Main's
 * own doc comment for the shared rationale. Unlike the other two, this
 * one has no reply timing to get right in the first place: Greg simply
 * never replies, so there's nothing to compute relative to the agent's
 * own alarm duration. AlarmArtifact's timeScale is still applied here,
 * though — purely so the alarm the agent itself sets actually fires
 * within a practical real-time window, letting this scenario reach its
 * own intended outcome (the alarm firing with no reply having ever
 * arrived) without waiting out a real, uncompressed "soon" that could
 * otherwise take many real minutes.
 *
 * Tests whether that outcome is handled correctly on its own — telling
 * the user we haven't heard back and proceeding without the report,
 * exactly as asked — without a genuine, merely-late reply ever entering
 * the picture at all.
 */
public final class Scenario04MainGregUnresponsive {

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
                // Deliberately nothing further — Greg never replies, at any point in the run.
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(16);
        workspace.shutdown();
    }
}
