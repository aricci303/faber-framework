package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * 
 * Scenario 01 - agent capabilities of interacting with artifacts and creating artifacts
 * 
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
        // agent.enableCycleDumpLogging(false);

        var userConsole = workspace.getUserConsole();   
        agent.forceObserving(userConsole.id());

        userConsole.simulateIncomingMessage("Please increment counter-01 by 5 and tell me the new value. Then do the same with a "
                + "new counter created from scratch, to be called scratch-1: create it, increment it by "
                + "5, and tell me its value too.");
        
        agent.doYourJobAndSelfEvaluate(10);

        workspace.shutdown();

    }
}

