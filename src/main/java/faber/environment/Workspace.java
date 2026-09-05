package faber.environment;

import java.util.ArrayList;
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
 * The WORKSPACE context block: available artifacts, the subset
 * currently observed, and one manual per distinct type — given in full
 * only the first time that type appears. This class is the harness's
 * ground truth; it is never narrated by the model, only referenced.
 *
 * Two mechanisms added this turn, both applying "every action with an
 * effect on the environment is an operation of an artifact" as far as
 * it will go without breaking:
 *
 *   - Always-observed artifacts (see provisionAlwaysObserved) bypass
 *     FOCUS entirely and cannot be targeted by STOP_OBSERVING. This is
 *     specifically for UserConsole: unlike ordinary artifacts, where
 *     missing a signal costs nothing (the WORKSPACE block always shows
 *     current ground truth regardless), the user's first instruction
 *     has no such fallback — it exists only as that one signal. FOCUS
 *     is the right default everywhere else; here it would create a
 *     chicken-and-egg problem (deciding to listen requires already
 *     having heard something).
 *
 *   - A registered WorkspaceArtifact (see registerWorkspaceArtifact) is
 *     notified on every provision()/dispose(), turning artifact_joined
 *     and artifact_left into ordinary FOCUS-gated signals rather than
 *     bespoke, unconditionally-published event types — this one has no
 *     analogous chicken-and-egg problem, since membership itself is
 *     always visible in WORKSPACE regardless of whether the agent is
 *     observing WorkspaceArtifact's own signal stream.
 */
public final class Workspace {

    private final Map<String, String> availableArtifacts = new LinkedHashMap<>();
    private final Set<String> observed = new LinkedHashSet<>();
    // private final Set<String> alwaysObserved = new LinkedHashSet<>();
    private final Map<String, Manual> manualsByType = new LinkedHashMap<>();
    private final Map<String, ArtifactFactory> factoriesByType = new LinkedHashMap<>();
    private final Map<String, Artifact> instances = new LinkedHashMap<>();
    private final Set<String> manualsAlreadyShown = new LinkedHashSet<>();
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

    /** Like provision(), but the artifact is observed from the moment it exists — see class doc. */
    /*
    public void provisionAlwaysObserved(String id, String type, Artifact instance) {
        provision(id, type, instance);
        observed.add(id);
        alwaysObserved.add(id);
    }*/

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
    	for (var ag: joinedAgents) {
    		ag.notifyFailureForDisposedArtifactPendingOps(artifactId);
        if (workspaceArtifact != null) workspaceArtifact.notifyArtifactLeft(artifactId);
    	}
    }

    public void startObserving(Agent who, String artifactId) {
        if (!availableArtifacts.containsKey(artifactId)) {
            throw new IllegalArgumentException("Cannot observe an artifact not in the workspace: " + artifactId);
        }
        if (observed.add(artifactId)) {
        	var artifact = instances.get(artifactId);
        	artifact.addObserverAgent(who);
        }
    }

    public void stopObserving(Agent who, String artifactId) {
        /*
    	if (alwaysObserved.contains(artifactId)) {
            throw new IllegalArgumentException("cannot stop observing an always-observed artifact: " + artifactId);
        }*/
        if (observed.remove(artifactId)) {
        	var artifact = instances.get(artifactId);
        	artifact.removeObserverAgent(who.getAgentId());
        }
    }

    public boolean isObserving(String id) { return observed.contains(id); }
    public boolean contains(String id) { return availableArtifacts.containsKey(id); }
    public String typeOf(String id) { return availableArtifacts.get(id); }
    public Manual manualFor(String type) { return manualsByType.get(type); }
    public Artifact instanceOf(String id) { return instances.get(id); }

    public void scheduleOpExecution(Runnable op) {
    	executor.submit(op);
    }
    
    public void shutdown() {
    	executor.shutdown();
    }
    
    public String toContextBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("available artifacts:\n");
        if (availableArtifacts.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<String, String> e : availableArtifacts.entrySet()) {
                sb.append("  - id: \"").append(e.getKey()).append("\", type: \"").append(e.getValue()).append("\"\n");
            }
        }
        sb.append("observed artifacts:\n");
        if (observed.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String id : observed) {
                Artifact instance = instances.get(id);
                Map<String, Object> props = instance == null ? Map.of() : instance.currentObsProperties();
                sb.append("  - id: \"").append(id).append("\"");
                if (props.isEmpty()) {
                    sb.append("\n");
                } else {
                    sb.append(", current properties: ").append(props).append("\n");
                }
            }
        }
        sb.append("manuals:\n");
        Set<String> typesPresent = new LinkedHashSet<>(availableArtifacts.values());
        boolean anyShown = false;
        for (String type : typesPresent) {
            if (!manualsAlreadyShown.contains(type)) {
                sb.append(manualsByType.get(type).toJson()).append("\n");
                manualsAlreadyShown.add(type);
                anyShown = true;
            }
        }
        if (!anyShown) {
            sb.append("  (all manuals for present types already shown in a prior cycle)\n");
        }
        return sb.toString();
    }
}
