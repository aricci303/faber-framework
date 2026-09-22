package scenarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;

/**
 * A monitoring-agent installation service. Deliberately kept simple —
 * always succeeds — so this scenario has exactly one engineered point
 * of failure (ProvisioningArtifact.provision_vm), isolating its effect
 * rather than confounding it with a second, independent failure mode.
 * The dependent second step in this domain: install_agent should not
 * be called until a VM actually exists to install it on, mirroring how
 * HotelBookingArtifact's book_hotel is meant to wait on a confirmed
 * flight in the travel-booking scenarios.
 */
public final class MonitoringArtifact extends Artifact {

	public static final String type = "Monitoring";

    private final Map<String, String> installations = new LinkedHashMap<>();
    private int idCounter = 0;

    public MonitoringArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(400); // simulates real work
        switch (operationName) {
            case "install_agent": {
                String vmId = params.getString("vm_id");
                String region = params.getString("region");
                String installationId = "monitoring-" + (++idCounter);
                installations.put(installationId, vmId + " in " + region);
                return List.of(installationId);
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    public static Manual manual() {
        return new Manual(
        		MonitoringArtifact.type,
                "install monitoring agents on running VMs",
                null, null,
                List.of(),
                List.of(),
                List.of(new Operation("install_agent(vm_id, region)",
                        "install the monitoring agent on the given VM",
                        List.of(new Param("installation_id", "id of the confirmed installation")))),
                null
        );
    }
}
