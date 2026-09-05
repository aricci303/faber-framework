package faber.agent;

import faber.agent.formal.PredictiveSufficiencyProtocol;
import faber.agent.formal.Relation;
import faber.environment.Workspace;

public class Agent {

	private String agentId;
	private AgentArchitecture agentArch;
	private EventQueue eventQueue;
	private String apiKey, llmModel;
	private Workspace workspace;

	public Agent(String agentId) {
		this.agentId = agentId;
		eventQueue = new EventQueue();
		agentArch = new AgentArchitecture(this, eventQueue); 	
	}

	public Agent(String agentId, LlmClient client) {
		this.agentId = agentId;
		eventQueue = new EventQueue();
		agentArch = new AgentArchitecture(this, eventQueue, client); 	
	}
	
	
	public String getAgentId() {
		return agentId;
	}
	
	public void init(Workspace workspace) {
		this.workspace = workspace;
		agentArch.init(workspace);
		workspace.join(this);
	}
	
	public void forceObserving(String artifactId) {
		workspace.startObserving(this, artifactId);
	}
	
	public void doYourJobAndSelfEvaluate(int cyclesBudget) throws Exception {
		for (int i = 0; i < cyclesBudget; i++) {
         	System.out.println("=== cycle " + agentArch.getNextCycleToRun() + " ===");            
            agentArch.runOneCycle();            
            var result = agentArch.getLastCycleResult();
            System.out.println("[state_of_mind] " + result.plan().stateOfMind);
            System.out.println("[tuple] " + result.tuple());
            System.out.println("[WF] pass=" + result.wf().pass + " " + result.wf().violations);
            System.out.println("[Coherence] pass=" + result.coherence().pass + " " + result.coherence().notes);
            System.out.println();
		}
		
        PredictiveSufficiencyProtocol.Score score = PredictiveSufficiencyProtocol.run(
                agentArch.trace(), Agent::trivialPredictor);
        System.out.println("Predictive sufficiency: " + score.correct + "/" + score.total
                + " = " + score.accuracy());
	}
	
    private static PlanResult.ActionKind trivialPredictor(
            java.util.Set<String> W, String G, Relation r, java.util.Set<String> WPrime) {
        boolean anyResolved = W.stream().anyMatch(s -> s.startsWith("operation_completed") || s.startsWith("operation_failed"));
        return anyResolved ? PlanResult.ActionKind.INVOKE : PlanResult.ActionKind.WAIT;
    }
	
	
	
	
	public void notifyNewPercept(Percept p) {
		eventQueue.publish(p);
	}

	public void notifyFailureForDisposedArtifactPendingOps(String artifactId) {
		agentArch.notifyFailureForDisposedArtifactPendingOps(artifactId);
	}

}
