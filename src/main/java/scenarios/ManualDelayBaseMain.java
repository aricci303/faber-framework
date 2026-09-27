package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * First of a two-variant pair isolating one specific question: does the
 * genuinely necessary get_manual round-trip for an unfamiliar,
 * task-specific type delay FOCUSing it past the moment its reply
 * arrives — a real, one-off signal, with no query-style recovery
 * operation to fall back on (see PagerArtifact's own doc comment on why
 * it deliberately mirrors MessagingArtifact's shape)?
 *
 * The reply is scripted to fire on a very short, fixed real-time delay
 * after the user's own request — short enough that it will have
 * already fired well before a real LLM call for cycle 2 or 3 returns,
 * leaving no natural slack for any sequencing choice to absorb. This is
 * the base case: the agent has no prior knowledge of Pager at all, so
 * whatever get_manual costs here is paid in full. Compare directly
 * against ManualDelayPrewarmedMain, identical in every other respect,
 * where notebook-01 is seeded before cycle one with a note describing
 * Pager's shape, simulating "the agent already learned this earlier" —
 * removing the need for get_manual entirely.
 */
public final class ManualDelayBaseMain {

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
                // Scripted to fire well before a real LLM call for cycle 2 or 3 would return — no
                // natural slack for a sequencing choice (get_manual before FOCUS, or the reverse) to
                // absorb. If the reply is missed, that is the point being tested, not a scenario bug.
                Thread.sleep(300);
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
