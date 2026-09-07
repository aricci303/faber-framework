package faber.example;

import faber.agent.Agent;
import faber.agent.LlmClient;
import faber.agent.PlanResult;
import faber.agent.PlanResult.ActionKind;
import faber.agent.formal.CoreTuple;
import faber.agent.formal.PredictiveSufficiencyProtocol;
import faber.agent.formal.Relation;
import faber.agent.formal.TupleExtractor;
import faber.agent.stages.StageProfile;
import faber.environment.Workspace;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * Simple example showing a faber agent working with a simple artifact (a simple counter).
 * 
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(SimpleCounter.manual(), SimpleCounter.factory(workspace));
        SimpleCounter counter01 = new SimpleCounter("counter-01", workspace, 0);
        workspace.provision("counter-01", "SimpleCounter", counter01);
        
        Agent agent = new Agent("agent-0");
        // agent.enableCycleDumpLogging(false);
        agent.init(workspace);
        
        var userConsole = workspace.getUserConsole();   
        agent.forceObserving(userConsole.id());
        
        userConsole.simulateIncomingMessage("Please increment counter-01 by 5 and tell me the new value.");
        
        agent.doYourJobAndSelfEvaluate(20);

        workspace.shutdown();
    }
}

