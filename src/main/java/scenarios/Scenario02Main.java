package scenarios;

import java.io.File;

import faber.agent.Agent;
import faber.agent.LlmClient;
import faber.environment.Workspace;

/**
 * 
 * Scenario 02 - implementing ARE tutorial scenario
 * 
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
}

