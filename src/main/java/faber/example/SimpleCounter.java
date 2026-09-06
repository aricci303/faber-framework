package faber.example;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;

import faber.agent.EventQueue;
import faber.environment.Artifact;
import faber.environment.ArtifactFactory;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;

/** Demo artifact matching the Counter example manual from system-prompt-v2.md. */
public final class SimpleCounter extends Artifact {

	public static final String type = "SimpleCounter";

	private int count;

    public SimpleCounter(String id, Workspace workspace, int start) {
        super(id, type, workspace);
        this.count = start;
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(400); // simulates real work, so WAIT has something meaningful to demonstrate
        switch (operationName) {
            case "inc": {
                int old = count;
                count += 1;
                notifyObsPropertyChanged("count", old, count);
                return List.of();
            }
            case "reset": {
                int old = count;
                count = 0;
                notifyObsPropertyChanged("count", old, count);
                return List.of();
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    public static Manual manual() {
        return new Manual(
                SimpleCounter.type,
                "to count, one by one",
                "SimpleCounter(start=0)",
                "creates a new counter, optionally with a starting value",
                List.of(new Manual.Param("count", "the current count value")),
                List.of(),
                List.of(
                        new Manual.Operation("inc()", "increments the count value by 1", List.of()),
                        new Manual.Operation("reset()", "resets the value to 0", List.of())
                ),
                "the initial value of count, when the artifact is created, is 0"
        );
    }

    public static ArtifactFactory factory(Workspace workspace) {
        return (proposedId, ctorArgs) -> {
            int start = ctorArgs.has("start") ? ((Number) ctorArgs.get("start")).intValue() : 0;
            return new SimpleCounter(proposedId, workspace, start);
        };
    }
}
