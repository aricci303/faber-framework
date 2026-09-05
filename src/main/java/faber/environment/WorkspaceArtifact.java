package faber.environment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 
 * Artifact providing functionalities to manage the workspace
 * - creating new artifacts
 * - disposing existing artifacts
 * 
 * By default, there is one instance of this kind of artifact in every workspace. 
 */
public final class WorkspaceArtifact extends Artifact {

    public WorkspaceArtifact(String id, Workspace workspace) {
        super(id, workspace);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "create_artifact": {
                String type = (String) params.get("type");
                String proposedId = (String) params.get("proposed_id");
                JSONObject ctorArgs = params.getJSONObject("constructor_parameters");
                workspace.create(type, proposedId, ctorArgs); // provision() inside triggers notifyArtifactJoined
                return List.of(proposedId);
            }
            case "dispose_artifact": {
                String artifactId = (String) params.get("artifact_id");
                workspace.dispose(artifactId); // triggers the pending-op cascade + notifyArtifactLeft
                return List.of();
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    // Package-private: called by Workspace only, on provision()/dispose(). Not part of the public
    // artifact interface — an agent never invokes these directly, they're purely notification plumbing.
    void notifyArtifactJoined(String artifactId, String artifactType) {
        emitSignal("artifact_joined", List.of(artifactId, artifactType));
    }

    void notifyArtifactLeft(String artifactId) {
        emitSignal("artifact_left", List.of(artifactId));
    }

    public static Manual manual() {
        return new Manual(
                "Workspace",
                "create and dispose artifacts in this workspace, and observe membership changes",
                null, null,
                List.of(),
                List.of(
                        new Manual.Signal("artifact_joined", "a new artifact entered the workspace",
                                List.of(new Manual.Param("artifact_id", "id of the joined artifact"),
                                        new Manual.Param("artifact_type", "its type"))),
                        new Manual.Signal("artifact_left", "an artifact left the workspace",
                                List.of(new Manual.Param("artifact_id", "id of the artifact that left")))
                ),
                List.of(
                        new Manual.Operation("create_artifact(type, proposed_id, constructor_parameters)",
                                "instantiate a new artifact of a creatable type",
                                List.of(new Manual.Param("artifact_id", "the id actually assigned"))),
                        new Manual.Operation("dispose_artifact(artifact_id)",
                                "remove an artifact from the workspace", List.of())
                ),
                null
        );
    }
}
