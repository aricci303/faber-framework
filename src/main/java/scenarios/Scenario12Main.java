package scenarios;

import faber.agent.Agent;
import faber.environment.Workspace;

/**
 * A direct structural mirror of Scenario05, in a domain sharing no
 * vocabulary with it at all — cloud VM provisioning instead of travel
 * booking. Every worked example illustrating this session's
 * goal/intention fixes (the goal/plan split, the "whose understanding"
 * test, wait-citation, parent_goal_id, TriggerFidelity) uses flights,
 * hotels, and dates. That grounds each fix in a real observed failure,
 * which matters, but it leaves one thing genuinely untestable from
 * within that same domain: whether the model learned the general
 * principle behind a fix, or learned "when this smells like travel
 * booking, do X." Running Scenario05 again, however cleanly, can't
 * separate those two explanations — only a different domain can.
 *
 * The logical shape is kept deliberately identical to Scenario05, so
 * a different result here is attributable to domain generalization
 * specifically, not to a different test: a concrete, user-specified
 * detail that can fail (region, not date — date appears in every
 * existing worked example, so avoiding it too is a stronger test of
 * generalization than a differently-themed date problem would be); an
 * alternatives-lookup; an explicit "don't proceed with the dependent
 * step until this is resolved" instruction; a later user reply that
 * answers a question the agent itself posed, exercising the "whose
 * understanding" test in a shape that has nothing to do with dates at
 * all. ProvisioningArtifact and MonitoringArtifact mirror
 * FlightBookingArtifact and HotelBookingArtifact structurally, on
 * purpose — one hardcoded, reproducible failure point, one artifact
 * kept deliberately simple to isolate it as the scenario's only point
 * of failure.
 *
 * The follow-up message uses the same deterministic wait Scenario05
 * now uses — polling for the agent's own question actually being sent
 * — rather than a fixed sleep, for the identical reason: a race here
 * would confound "did the fix generalize" with "did the timing happen
 * to land favorably," which would defeat the entire point of this
 * scenario existing.
 */
public final class Scenario12Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(ProvisioningArtifact.manual(), null);
        ProvisioningArtifact provisioning = new ProvisioningArtifact("provisioning-01", workspace);
        workspace.provision("provisioning-01", "Provisioning", provisioning);

        workspace.registerType(MonitoringArtifact.manual(), null);
        MonitoringArtifact monitoring = new MonitoringArtifact("monitoring-01", workspace);
        workspace.provision("monitoring-01", "Monitoring", monitoring);

        Agent agent = new Agent("agent-0");
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(2000);
                userConsole.simulateIncomingMessage(
                        "Please provision a VM with 16GB RAM in the eu-west-1 region. Once it's "
                        + "running, install our monitoring agent on it. Once that's done, let me know "
                        + "the setup is complete. If eu-west-1 doesn't have capacity for that spec, "
                        + "check what other regions have capacity and let me know before proceeding "
                        + "further — don't install the agent until the provisioning is resolved.");

                // Same deterministic wait as Scenario05, for the same reason: a race here
                // would confound "did the fix generalize to a new domain" with "did the timing
                // happen to land favorably," undermining the entire point of this scenario.
                long deadline = System.currentTimeMillis() + 90_000;
                while (userConsole.sentMessageCount() == 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                userConsole.simulateIncomingMessage("Let's use eu-central-1 instead.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(20);
        workspace.shutdown();
    }
}
