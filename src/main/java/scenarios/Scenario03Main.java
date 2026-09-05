package scenarios;

import faber.agent.Agent;
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
}
