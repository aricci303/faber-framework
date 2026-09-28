package faber.environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import faber.agent.Agent;
import faber.agent.Percept;

/**
 * Anything an agent can act on, modeled per A&A: operations are invoked
 * non-blocking, execute, and their outcome re-enters the environment as
 * an ordinary Percept.
 *
 * This version adds A&A/CArtAgO's real belief-mapping semantics for
 * observable properties: an artifact's true current property values
 * are maintained here unconditionally, not only while observed — the
 * percept mechanism (artifact_obs_prop_updated) only ever announced a
 * *change*, while observed, which meant an agent that started observing
 * late had no way to learn the true current value, only future changes
 * from that point on. Workspace now renders the current value of every
 * observable property for every currently-observed artifact, always,
 * exempt from delta-only compression — the same guaranteed-survival
 * treatment already given to manuals, pending intentions, and
 * mechanical-log entries. The percept still fires on change, for
 * anything that specifically needs to react to the moment of change;
 * the two mechanisms serve different purposes and both stay.
 */
public abstract class Artifact {

    protected final String id;
    protected final String type;
    protected final Workspace workspace;
    private final Map<String, Object> obsProperties = new LinkedHashMap<>();
    private ArrayList<Agent> observerAgents;
    // Thread-safe mirror of "at least one agent is currently observing this artifact" — observerAgents
    // itself is a plain ArrayList, mutated only by the agent's own thread, unsafe to poll from a
    // separate scenario-driver thread. This is what a driver should actually condition event
    // injection on: e.g. "wait until pager-01 is observed, then fire" is a genuine readiness
    // condition, immune to how many cycles the agent happens to take to reach it — unlike a fixed
    // cycle count or a fixed sleep duration, both of which race against real, variable LLM latency.
    private final java.util.concurrent.atomic.AtomicBoolean currentlyObserved =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Safe to poll from any thread — see the field's own doc comment above. */
    public boolean isCurrentlyObserved() { return currentlyObserved.get(); }

    protected Artifact(String id, String type, Workspace workspace) {
        this.id = id;
        this.type = type;
        this.workspace = workspace;
        // this.eventQueue = eventQueue;
        // this.executor = executor;
        this.observerAgents = new ArrayList<Agent>();
    }

    public String id() { return id; }
    
    public String type() { return type; }

    /** Non-blocking: publishes operation_started immediately, then schedules the operation and returns.
     *  Execution itself is serialized per artifact instance — the actual doOperation() call is
     *  synchronized on this artifact, so only one operation body ever runs at a time for a given
     *  instance, exactly matching A&A/CArtAgO's own artifact-as-monitor semantics. This was always
     *  the right model conceptually — an artifact's internal state is its own, and two operations on
     *  the same instance were never meant to interleave, whether the requestor is a single agent
     *  batching several actions in one cycle or several distinct agents acting concurrently on a
     *  shared coordination artifact. It became a real, reachable hazard rather than a theoretical one
     *  the moment batching let a single cycle submit more than one action, since two operations on the
     *  same instance can now genuinely be scheduled close enough together to land on separate worker
     *  threads at once — confirmed by a real run producing a corrupted-state exception in
     *  NotebookArtifact from exactly this. The fix belongs here, once, rather than as ad-hoc internal
     *  locking repeated in every individual artifact that happens to get hit by it: any artifact whose
     *  doOperation() touches its own mutable state gets the guarantee for free, uniformly. Operations
     *  on two different artifact instances are completely unaffected — each instance has its own
     *  monitor, so genuine parallelism across artifacts is preserved; this only ever serializes
     *  concurrent attempts to execute against the very same instance. */
    public final void invoke(Agent requestor, String operationName, JSONObject params, String correlationId) {
        String signature = id + "." + operationName + "(" + params + ")";      
        requestor.notifyNewPercept(Percept.operationStarted(correlationId, signature));        
        workspace.scheduleOpExecution(() -> {
            try {
                List<Object> outputs;
                synchronized (this) {
                    outputs = doOperation(operationName, params);
                }
                requestor.notifyNewPercept(Percept.operationCompleted(correlationId, outputs));
            } catch (Exception e) {
            	requestor.notifyNewPercept(Percept.operationFailed(correlationId, e.getMessage()));
            }
        });
    }
    
    public void dispose() {
    	this.notifyNewPerceptToObserverAgents(Percept.focusChanged(id, false));
    }

    public void addObserverAgent(Agent agent) {
    	observerAgents.add(agent);
    	currentlyObserved.set(true);
    	// agent.notifyNewPercept(Percept.focusChanged(id, true));
    }

    public void removeObserverAgent(String agentId) {
    	var it = observerAgents.iterator();
    	while (it.hasNext()) {
    		var ag = it.next();
    		if (ag.getAgentId().equals(agentId)) {
    			it.remove();
    	    	// ag.notifyNewPercept(Percept.focusChanged(id, false));
    			break;
    		}
    	}
    	currentlyObserved.set(!observerAgents.isEmpty());
    }
    
    public void notifyNewPerceptToObserverAgents(Percept p) {
    	for (var ag: observerAgents) {
    		ag.notifyNewPercept(p);
    	}
    }
    
    /** The actual operation logic. Returns declared outputs (empty list if none). May throw to signal failure. */
    protected abstract List<Object> doOperation(String operationName, JSONObject params) throws Exception;

    /**
     * Call when an observable property changes. Unconditionally updates
     * the true current value (the "belief" store), then publishes the
     * change percept only to agents currently observing — matching
     * CArtAgO exactly: the artifact's real state always advances; only
     * the notification of a specific change is gated on observation.
     */
    protected void notifyObsPropertyChanged(String propName, Object oldValue, Object newValue) {
        obsProperties.put(propName, newValue);
        notifyNewPerceptToObserverAgents(Percept.obsPropUpdated(id, propName, oldValue, newValue));
        /*
        if (workspace.isObserving(id)) {
            eventQueue.publish(Percept.obsPropUpdated(id, propName, oldValue, newValue));
        }*/
    }

    /** Call to emit a one-off signal (something that happened, as opposed to a persisting property). */
    protected void emitSignal(String signalName, List<Object> values) {
        notifyNewPerceptToObserverAgents(Percept.signal(id, signalName, values));
        /*
        if (workspace.isObserving(id)) {
            eventQueue.publish(Percept.signal(id, signalName, values));
        }*/
    }

    /** The true current value of every observable property set so far — queried by Workspace for observed artifacts. */
    public Map<String, Object> currentObsProperties() {
        return obsProperties;
    }
}
