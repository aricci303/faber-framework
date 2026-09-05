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
}
