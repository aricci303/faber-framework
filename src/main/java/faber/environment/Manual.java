package faber.environment;

import java.util.ArrayList;
import java.util.List;

/**
 * The manual for one artifact TYPE (not instance) — the sole
 * authoritative source for what an artifact of this type is for and how
 * to use it, matching the JSON schema specified in system-prompt-v2.md.
 * Hand-rolled JSON rendering here, deliberately, to keep this harness
 * dependency-free; a real deployment with richer manuals should use a
 * proper JSON library instead of extending this by hand.
 */
public final class Manual {

    public static final class Param {
        public final String name;
        public final String description;
        public Param(String name, String description) { this.name = name; this.description = description; }
    }

    public static final class Operation {
        public final String signature;
        public final String description;
        public final List<Param> outputs;
        public Operation(String signature, String description, List<Param> outputs) {
            this.signature = signature;
            this.description = description;
            this.outputs = outputs == null ? List.of() : outputs;
        }
    }

    public static final class Signal {
        public final String name;
        public final String description;
        public final List<Param> values;
        public Signal(String name, String description, List<Param> values) {
            this.name = name;
            this.description = description;
            this.values = values == null ? List.of() : values;
        }
    }

    public final String artifactType;
    public final String function;
    public final String constructorSignature; // null if not creatable
    public final String constructorDescription;
    public final List<Param> observableProperties;
    public final List<Signal> signals;
    public final List<Operation> operations;
    public final String furtherInfo;

    public Manual(String artifactType, String function, String constructorSignature,
                   String constructorDescription, List<Param> observableProperties,
                   List<Signal> signals, List<Operation> operations, String furtherInfo) {
        this.artifactType = artifactType;
        this.function = function;
        this.constructorSignature = constructorSignature;
        this.constructorDescription = constructorDescription;
        this.observableProperties = observableProperties == null ? new ArrayList<>() : observableProperties;
        this.signals = signals == null ? new ArrayList<>() : signals;
        this.operations = operations == null ? new ArrayList<>() : operations;
        this.furtherInfo = furtherInfo;
    }

    public boolean isCreatable() { return constructorSignature != null; }

    /** Renders the full JSON shape, exactly once per type, per the system prompt's caching rule. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"artifact-type\": \"").append(artifactType).append("\",\n");
        sb.append("  \"function\": \"").append(function).append("\"");
        if (isCreatable()) {
            sb.append(",\n  \"constructor\": { \"signature\": \"").append(constructorSignature)
              .append("\", \"description\": \"").append(constructorDescription).append("\" }");
        }
        sb.append(",\n  \"observable-properties\": [");
        for (int i = 0; i < observableProperties.size(); i++) {
            Param p = observableProperties.get(i);
            sb.append(i > 0 ? ", " : "").append("{ \"name\": \"").append(p.name)
              .append("\", \"description\": \"").append(p.description).append("\" }");
        }
        sb.append("],\n  \"signals\": [");
        for (int i = 0; i < signals.size(); i++) {
            Signal sig = signals.get(i);
            sb.append(i > 0 ? ", " : "").append("{ \"name\": \"").append(sig.name)
              .append("\", \"description\": \"").append(sig.description).append("\" }");
        }
        sb.append("],\n  \"operations\": [");
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            sb.append(i > 0 ? ", " : "").append("{ \"signature\": \"").append(op.signature)
              .append("\", \"description\": \"").append(op.description).append("\", \"outputs\": [");
            for (int j = 0; j < op.outputs.size(); j++) {
                Param o = op.outputs.get(j);
                sb.append(j > 0 ? ", " : "").append("{ \"name\": \"").append(o.name).append("\" }");
            }
            sb.append("] }");
        }
        sb.append("]");
        if (furtherInfo != null) {
            sb.append(",\n  \"further-info\": \"").append(furtherInfo).append("\"");
        }
        sb.append("\n}");
        return sb.toString();
    }
}
