package faber.agent;

import java.time.Instant;
import java.util.List;

/**
 * The unified percept model — down to six fixed types. USER_MESSAGE,
 * ARTIFACT_JOINED, and ARTIFACT_LEFT are retired as this turn's A&A
 * uniformity pass: "every action with an effect on the environment
 * should be an operation of an artifact" applies symmetrically to
 * perception too — incoming user messages are now a signal from a
 * UserConsole artifact (message_from_user), and workspace membership
 * changes are now signals from a WorkspaceArtifact (artifact_joined,
 * artifact_left), exactly like email_received or message_received
 * already were. No bespoke event types needed for any of them.
 */
public final class Percept {

    public enum Type {
        ARTIFACT_OBS_PROP_UPDATED,
        ARTIFACT_SIGNAL,
        OPERATION_STARTED,
        OPERATION_COMPLETED,
        OPERATION_FAILED,
        FOCUS_CHANGED
    }

    public final Type type;
    public final String artifactId;
    public final String correlationId;
    public final Instant timestamp;

    public final String text;
    public final String propName;
    public final Object oldValue, newValue;
    public final String signalName;
    public final List<Object> values;
    public final String reason;
    public final Boolean observing;

    private Percept(Type type, String artifactId, String correlationId, String text, String propName,
                     Object oldValue, Object newValue, String signalName, List<Object> values,
                     String reason, Boolean observing) {
        this.type = type;
        this.artifactId = artifactId;
        this.correlationId = correlationId;
        this.timestamp = Instant.now();
        this.text = text;
        this.propName = propName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.signalName = signalName;
        this.values = values;
        this.reason = reason;
        this.observing = observing;
    }

    public static Percept obsPropUpdated(String artifactId, String propName, Object oldValue, Object newValue) {
        return new Percept(Type.ARTIFACT_OBS_PROP_UPDATED, artifactId, null, null, propName, oldValue, newValue, null, null, null, null);
    }

    public static Percept signal(String artifactId, String signalName, List<Object> values) {
        return new Percept(Type.ARTIFACT_SIGNAL, artifactId, null, null, null, null, null, signalName, values, null, null);
    }

    public static Percept operationStarted(String correlationId, String operationSignature) {
        return new Percept(Type.OPERATION_STARTED, null, correlationId, operationSignature, null, null, null, null, null, null, null);
    }

    public static Percept operationCompleted(String correlationId, List<Object> outputValues) {
        return new Percept(Type.OPERATION_COMPLETED, null, correlationId, null, null, null, null, null, outputValues, null, null);
    }

    public static Percept operationFailed(String correlationId, String reason) {
        return new Percept(Type.OPERATION_FAILED, null, correlationId, null, null, null, null, null, null, reason, null);
    }

    public static Percept focusChanged(String artifactId, boolean observing) {
        return new Percept(Type.FOCUS_CHANGED, artifactId, null, null, null, null, null, null, null, null, observing);
    }

    public String toContextLine() {
        switch (type) {
            case ARTIFACT_OBS_PROP_UPDATED:
                return "artifact_obs_prop_updated(" + artifactId + ", " + propName + ", "
                        + render(oldValue) + ", " + render(newValue) + ")";
            case ARTIFACT_SIGNAL:
                return "artifact_signal(" + artifactId + ", " + signalName + ", " + values + ")";
            case OPERATION_STARTED:
                return "operation_started(" + correlationId + ", " + text + ")";
            case OPERATION_COMPLETED:
                return "operation_completed(" + correlationId + ", " + values + ")";
            case OPERATION_FAILED:
                return "operation_failed(" + correlationId + ", " + quote(reason) + ")";
            case FOCUS_CHANGED:
            default:
                return "focus_changed(" + artifactId + ", observing=" + observing + ")";
        }
    }

    private static String render(Object v) { return v == null ? "null" : v.toString(); }
    private static String quote(String s) { return "\"" + (s == null ? "" : s) + "\""; }
}
