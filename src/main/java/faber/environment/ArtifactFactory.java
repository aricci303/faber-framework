package faber.environment;

import java.util.Map;

import org.json.JSONObject;

/** Instantiates a new Artifact of a registered type, for CREATE_ARTIFACT. */
@FunctionalInterface
public interface ArtifactFactory {
    Artifact create(String proposedId, JSONObject ctorArgs);
}
