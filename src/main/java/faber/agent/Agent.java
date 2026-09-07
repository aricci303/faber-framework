package faber.agent;

import java.util.ArrayList;
import java.util.List;

import faber.agent.formal.PredictiveSufficiencyProtocol;
import faber.agent.formal.Relation;
import faber.environment.Artifact;
import faber.environment.Workspace;

public class Agent {

	private String agentId;
	private AgentArchitecture agentArch;
	private EventQueue eventQueue;
	private String apiKey, llmModel;
	private Workspace workspace;
	private ArrayList<Artifact> observedArtifacts;
	private boolean logCycle;

	protected Agent() {
		eventQueue = new EventQueue();
		observedArtifacts = new ArrayList<>();		
		logCycle = true;
	}
	
	public Agent(String agentId) {
		this();
		this.agentId = agentId;
		agentArch = new AgentArchitecture(this, eventQueue); 	
	}

	public Agent(String agentId, LlmClient client) {
		this();
		this.agentId = agentId;
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
	
    public void addObservedArtifact(Artifact artifact) {
    	observedArtifacts.add(artifact);
    	notifyNewPercept(Percept.focusChanged(artifact.id(), true));
    }
    
    public List<Artifact> getObservedArtifacts(){
    	return observedArtifacts;
    }

    public void removeObservedArtifact(Artifact artifact) {
    	var it = observedArtifacts.iterator();
    	while (it.hasNext()) {
    		var ar = it.next();
    		if (ar.id().equals(artifact.id())) {
    			it.remove();
    	    	notifyNewPercept(Percept.focusChanged(ar.id(), false));
    			break;
    		}
    	}
    }
	
    public void enableCycleDumpLogging(boolean enable) {
    	logCycle = enable;
    }
	
	public void doYourJobAndSelfEvaluate(int cyclesBudget) throws Exception {
		long totalInputTokens = 0, totalOutputTokens = 0;
		for (int i = 0; i < cyclesBudget; i++) {
         	if (logCycle) {
         		System.out.println("=== cycle " + agentArch.getNextCycleToRun() + " ===");            
         	}
            var cycleResult = agentArch.runOneCycle();
            totalInputTokens += cycleResult.llmCallResult().totalInputTokens();
            totalOutputTokens += cycleResult.llmCallResult().numOutputTokens();
         	if (logCycle) {
	            System.out.print("*** ENVIRONMENT   ***\n" + agentArch.dumpLightContext());
	            System.out.print("*** STATE OF MIND ***\n" + cycleResult.stateOfMind());    		//
	            System.out.println("*** ACTION COMMITTED ***\n" + cycleResult.actResult().act());
	            System.out.println("*** GOALS IN PLAN RESULT ***");
	            System.out.print(agentArch.dumpLastCyclePlanResultGoals());
	            System.out.println("*** LLM CALL ***");
	            System.out.println("- input tokens (non-cached): " + cycleResult.llmCallResult().numInputTokens());
	            System.out.println("- input tokens (cache creation): " + cycleResult.llmCallResult().cacheCreationInputTokens());
	            System.out.println("- input tokens (cache read): " + cycleResult.llmCallResult().cacheReadInputTokens());
	            System.out.println("- input tokens (total): " + cycleResult.llmCallResult().totalInputTokens());
	            System.out.println("- output tokens: " + cycleResult.llmCallResult().numOutputTokens());
	            System.out.println("- Total tokens across " + i + " cycles — input: " + totalInputTokens
	                    + ", output: " + totalOutputTokens
	                    + " (input total includes cache creation + cache read, comparable to the Claude console's own reporting)");
	            System.out.println("*** VALIDATION ***");
	            System.out.println("[tuple] " + cycleResult.coreTuple());
	            System.out.println("[WF] pass=" + cycleResult.wf().pass + " " + cycleResult.wf().violations);
	            System.out.println("[Coherence] pass=" + cycleResult.coherence().pass + " " + cycleResult.coherence().notes);
         	}
		}
		
        PredictiveSufficiencyProtocol.Score score = PredictiveSufficiencyProtocol.run(
                agentArch.trace(), Agent::trivialPredictor);
        System.out.println("Predictive sufficiency: " + score.correct + "/" + score.total
                + " = " + score.accuracy());
        System.out.println("Total tokens across " + cyclesBudget + " cycles — input: " + totalInputTokens
                + ", output: " + totalOutputTokens
                + " (input total includes cache creation + cache read, comparable to the Claude console's own reporting)");
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
