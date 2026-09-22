package scenarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;

/**
 * A cloud VM provisioning service — structurally a direct mirror of
 * FlightBookingArtifact, deliberately, but in a domain sharing no
 * vocabulary with it at all. Built specifically to test whether the
 * goal/intention fixes developed against the travel-booking scenarios
 * this session (the goal/plan split, the "whose understanding" test,
 * wait-citation, parent_goal_id) generalize past that one domain, or
 * were narrower than they looked — every worked example illustrating
 * those fixes in the system prompt uses flights, hotels, and dates.
 *
 * provision_vm fails outright for one specific region (no capacity at
 * that spec) — not random or flaky, so the scenario is reproducible
 * and the failure is attributable to a real, checkable cause.
 * list_available_regions gives a legitimate, concrete path to replan
 * (try a different region) rather than only blind retry or giving up.
 */
public final class ProvisioningArtifact extends Artifact {

	public static final String type = "Provisioning";

    private static final String UNAVAILABLE_REGION = "eu-west-1";
    private static final List<String> ALTERNATIVE_REGIONS = List.of("eu-central-1", "us-east-1", "ap-southeast-1");

    private final Map<String, String> vms = new LinkedHashMap<>(); // vm_id -> "region-size_gb"
    private int idCounter = 0;
    private JSONObject lastRequest = null;

    public ProvisioningArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(400); // simulates real work
        switch (operationName) {
            case "provision_vm": {
                String region = params.getString("region");
                int sizeGb = params.getInt("size_gb");
                String networkTier = params.optString("network_tier", null);

                if (UNAVAILABLE_REGION.equals(region)) {
                    recordRequest(region, sizeGb, "failed", null, networkTier);
                    throw new IllegalStateException(
                            "no capacity for a " + sizeGb + "GB VM in " + region
                            + " — try list_available_regions for alternatives");
                }
                String vmId = "vm-" + (++idCounter);
                vms.put(vmId, region + "-" + sizeGb);
                recordRequest(region, sizeGb, "running", vmId, networkTier);
                return List.of(vmId, region,
                        networkTier != null ? networkTier : "no preference specified");
            }
            case "list_available_regions": {
                int sizeGb = params.getInt("size_gb");
                return List.of(sizeGb, new JSONArray(ALTERNATIVE_REGIONS));
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /**
     * Belief-mapped, exactly like FlightBookingArtifact.last_request — the retry after a
     * failure often happens many cycles later, once the user replies with an alternative
     * region, not in the very next cycle, so this can't just live in a single cycle's own
     * operation_started/failed percept.
     */
    private void recordRequest(String region, int sizeGb, String status, String vmId, String networkTier) {
        JSONObject newRequest = new JSONObject();
        newRequest.put("region", region);
        newRequest.put("size_gb", sizeGb);
        newRequest.put("status", status);
        if (vmId != null) newRequest.put("vm_id", vmId);
        if (networkTier != null) newRequest.put("network_tier", networkTier);
        notifyObsPropertyChanged("last_request", lastRequest, newRequest);
        lastRequest = newRequest;
    }

    public static Manual manual() {
        return new Manual(
        		ProvisioningArtifact.type,
                "provision and query cloud virtual machines",
                null, null,
                List.of(new Param("last_request",
                        "region, size_gb, and status (running or failed) of the most recently "
                        + "requested VM — check this before retrying rather than relying on your "
                        + "own memory of a prior request, especially after any delay")),
                List.of(),
                List.of(
                        new Operation("provision_vm(region, size_gb, network_tier)",
                                "provision a VM of the given size in the given region; fails if there is "
                                + "no capacity for that spec in that region. network_tier is optional "
                                + "(e.g. \"standard\", \"premium\") — if you hold a relevant standing "
                                + "preference, pass it explicitly rather than leaving it out",
                                List.of(new Param("vm_id", "id of the running VM"),
                                        new Param("confirmed_region", "the region actually provisioned in"),
                                        new Param("confirmed_network_tier", "the network tier actually applied, if any"))),
                        new Operation("list_available_regions(size_gb)",
                                "get regions with known capacity for a VM of the given size, useful after a provisioning failure",
                                List.of(new Param("regions", "list of regions with available capacity")))
                ),
                null
        );
    }
}
