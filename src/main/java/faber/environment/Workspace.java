package faber.environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import faber.agent.Agent;

/**
 * 
 * Class representing the workspace containing artifacts.
 * 
 */
public final class Workspace {

    private final Map<String, String> availableArtifacts = new LinkedHashMap<>();
    private final Map<String, Manual> manualsByType = new LinkedHashMap<>();
    private final Map<String, ArtifactFactory> factoriesByType = new LinkedHashMap<>();
    private final Map<String, Artifact> instances = new LinkedHashMap<>();
    private WorkspaceArtifact workspaceArtifact; // null until registered, see class doc
    private UserConsoleArtifact userConsole;
    private ExecutorService executor = Executors.newFixedThreadPool(2);

    private List<Agent> joinedAgents;
    
    public Workspace() {
        joinedAgents = new ArrayList<Agent>();
    }

    public void initDefaultArtifacts() {
    	
        // WorkspaceArtifact first, then registered, so subsequent provisions are notified — see
        // Workspace's class doc for why this one specifically needs to go first.
        
    	/* workspace artifact, to manage artifacts inside the workspace */  
    	 
    	registerType(WorkspaceArtifact.manual(), null);
        workspaceArtifact = new WorkspaceArtifact("workspace-01", this);
        provision("workspace-01", "Workspace", workspaceArtifact);

        /* user-console artifact, to interact with the user */
        
        registerType(UserConsoleArtifact.manual(), null);
        userConsole = new UserConsoleArtifact("user-console-01", this);
        provision("user-console-01", "UserConsole", userConsole);    	

        /* alarm artifact, to manage time */
        
        registerType(AlarmArtifact.manual(), null);
        AlarmArtifact alarm = new AlarmArtifact("alarm-01", this);
        provision("alarm-01", "Alarm", alarm);
        
        /* notebook artifact, to hold standing beliefs independent of any goal or plan */

        registerType(NotebookArtifact.manual(), null);
        NotebookArtifact notebook = new NotebookArtifact("notebook-01", this);
        provision("notebook-01", "Notebook", notebook);
        
    }
        
    public UserConsoleArtifact getUserConsole() {
    	return this.userConsole;
    }

    public void join(Agent agent) {
    	joinedAgents.add(agent);
    }
    
    public void registerType(Manual manual, ArtifactFactory factory) {
        manualsByType.put(manual.artifactType, manual);
        if (factory != null) factoriesByType.put(manual.artifactType, factory);
    }

    public void provision(String id, String type, Artifact instance) {
        if (!manualsByType.containsKey(type)) {
            throw new IllegalStateException("No manual registered for type " + type);
        }
        availableArtifacts.put(id, type);
        instances.put(id, instance);
        if (workspaceArtifact != null) workspaceArtifact.notifyArtifactJoined(id, type);
    }


    public Artifact create(String type, String proposedId, JSONObject  ctorArgs) {
        Manual manual = manualsByType.get(type);
        if (manual == null || !manual.isCreatable() || !factoriesByType.containsKey(type)) {
            throw new IllegalStateException("Type " + type + " is not creatable");
        }
        if (availableArtifacts.containsKey(proposedId)) {
            throw new IllegalStateException("Artifact id already present: " + proposedId);
        }
        Artifact instance = factoriesByType.get(type).create(proposedId, ctorArgs);
        provision(proposedId, type, instance);
        return instance;
    }

    /** Cascades: fails any pending operations targeting this artifact, then removes it and notifies. */
    public void dispose(String artifactId) {
    	availableArtifacts.remove(artifactId);
        var artifact = instances.remove(artifactId);
        artifact.dispose();
        if (workspaceArtifact != null) {
        	workspaceArtifact.notifyArtifactLeft(artifactId);
        }
    	for (var ag: joinedAgents) {
    		ag.notifyFailureForDisposedArtifactPendingOps(artifactId);
    	}
    }

    public void startObserving(Agent who, String artifactId) {
        if (!availableArtifacts.containsKey(artifactId)) {
            throw new IllegalArgumentException("Cannot observe an artifact not in the workspace: " + artifactId);
        }
        var artifact = instances.get(artifactId);
        artifact.addObserverAgent(who);
        who.addObservedArtifact(artifact);
    }

    public void stopObserving(Agent who, String artifactId) {
       var artifact = instances.get(artifactId);
       if (artifact != null) {
    	   artifact.removeObserverAgent(who.getAgentId());
           who.removeObservedArtifact(artifact);
       }
    }

    public List<Artifact> getIAvailableArtifacts(){
    	var col = this.instances.values();
    	var list = new ArrayList<Artifact>();
    	for (var ar: col) {
    		list.add(ar);
    	}
    	return list;
    }
    
    public boolean contains(String id) { 
    	return availableArtifacts.containsKey(id); 
    }
    
    
    public String typeOf(String id) { return availableArtifacts.get(id); }
    
    public Manual manualFor(String type) { return manualsByType.get(type); }

    public Artifact instanceOf(String id) { return instances.get(id); }

    public void scheduleOpExecution(Runnable op) {
    	executor.submit(op);
    }
    
    public void shutdown() {
    	executor.shutdown();
    }
    
}
