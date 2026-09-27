package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Category-1 (timing-abstracted) counterpart to ManualDelayBaseMain —
 * see that class's own doc comment for the shared setup and the
 * original question. That scenario deliberately raced Greg's reply
 * against the agent's own pace on a fixed, tight sleep; whether the
 * reply landed depended partly on real, variable LLM call latency, not
 * only on the agent's own reasoning. This scenario removes that
 * confound entirely: the scenario driver polls pager.isCurrentlyObserved()
 * — a genuine, thread-safe readiness condition, immune to how many
 * cycles the agent actually takes to reach it — and injects Greg's
 * reply only once that condition is true. Whatever the agent does with
 * the reply from this point on is then attributable to its own
 * reasoning, not to a race it never had a fair chance to win.
 *
 * A safety-net deadline still exists, mirroring Scenario05Main's own
 * pattern: if the condition is never met within it, that is itself a
 * separate, worthwhile finding (the agent never got around to
 * focusing pager-01 at all within a generous window) rather than the
 * scenario silently hanging forever.
 */
public final class ManualDelayFairTimingMain {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(PagerArtifact.manual(), null);
        PagerArtifact pager = new PagerArtifact("pager-01", workspace);
        workspace.provision("pager-01", "Pager", pager);

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "Please page Greg, who's on call, and ask if he can pick up the incident now. "
                        + "If he doesn't reply soon, let me know we haven't heard back and proceed without him.");

                // The actual injection condition — see class doc comment. The deadline below is a
                // safety net only, not the primary mechanism, exactly like Scenario05Main's own.
                long deadline = System.currentTimeMillis() + 90_000;
                while (!pager.isCurrentlyObserved() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                pager.simulateIncomingPage("Greg", "On it — I'll pick up the incident right away.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(10);
        workspace.shutdown();
    }
}
