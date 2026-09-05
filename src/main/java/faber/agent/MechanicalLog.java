package faber.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Low-salience mechanical bookkeeping: correlation ids and raw facts,
 * kept terse on purpose — never narrated directly. Unchanged in spirit
 * from the first version; added resolveAllFor(artifactId) to support
 * DISPOSE_ARTIFACT's cascade (a pending operation targeting a disposed
 * artifact must resolve as action_failed, not vanish).
 */
public final class MechanicalLog {

    public static final class PendingOp {
        public final String correlationId;
        public final String artifactId;
        public final String operationName;
        public final long invokedAtEpochMillis;

        PendingOp(String correlationId, String artifactId, String operationName) {
            this.correlationId = correlationId;
            this.artifactId = artifactId;
            this.operationName = operationName;
            this.invokedAtEpochMillis = System.currentTimeMillis();
        }
    }

    private final Map<String, PendingOp> pending = new LinkedHashMap<>();

    public void recordInvocation(String correlationId, String artifactId, String operationName) {
        pending.put(correlationId, new PendingOp(correlationId, artifactId, operationName));
    }

    public PendingOp resolve(String correlationId) {
        return pending.remove(correlationId);
    }

    /** Correlation ids of every pending op targeting a given artifact — used by DISPOSE_ARTIFACT's cascade. */
    public java.util.List<String> pendingOpsFor(String artifactId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (PendingOp op : pending.values()) {
            if (op.artifactId.equals(artifactId)) ids.add(op.correlationId);
        }
        return ids;
    }

    public String toContextBlock() {
        if (pending.isEmpty()) return "pending_ops: none";
        StringBuilder sb = new StringBuilder("pending_ops:\n");
        for (PendingOp op : pending.values()) {
            sb.append("  - id: ").append(op.correlationId)
              .append(", target: ").append(op.artifactId).append('.').append(op.operationName)
              .append(", invoked_at: ").append(op.invokedAtEpochMillis).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
