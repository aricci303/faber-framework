package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.AlarmArtifact;
import faber.environment.Workspace;

/**
 * Built specifically to resolve an ambiguity Scenario06's two runs
 * left genuinely open, not manufactured: "record a standing
 * preference" got tagged MAINTENANCE once and ACHIEVEMENT once, for
 * the identical user message, both readings internally defensible —
 * because writing a note is a single, natural setup action that could
 * plausibly BE the whole goal (Reading B), or could just as plausibly
 * be a first step toward a genuinely standing condition the goal
 * itself never terminates (Reading A). That ambiguity can't be
 * resolved by more runs of the same scenario; it needs a goal where
 * "Reading B" — a one-time task with a clean terminal action — isn't
 * available as an escape hatch at all.
 *
 * The repeating alarm-reset loop is built to remove that escape hatch
 * entirely: fire, notify, reset, fire again, indefinitely, with no
 * single action anywhere in the sequence that completes the
 * commitment the way writing one note could be read as completing
 * "record the preference." AlarmArtifact's own fired_alarms is a
 * cumulative counter across every alarm ever set (confirmed directly
 * against its source) — continuing the loop requires the agent to
 * genuinely re-invoke set_alarm each time, not something the harness
 * or the artifact does on its own. If a goal shaped exactly like this
 * still gets marked ACHIEVED after one fire-reset cycle, that is a
 * different, more informative finding than the seat-preference
 * ambiguity: not "which of two defensible readings applies here," but
 * "one iteration of an ongoing commitment is being conflated with the
 * commitment being complete," full stop.
 *
 * The standing seat preference is kept in the same scenario, on
 * purpose, as a secondary, functional check: does the loop's presence
 * disrupt or get displaced by an unrelated achievement task (booking
 * a flight) arriving in the middle of it, and does the preference
 * still apply correctly regardless of how it ends up classified.
 *
 * Deterministic throughout, not timed sleeps: the driver polls
 * AlarmArtifact's own fired_alarms count directly — a real, checkable
 * fact, not a guess at how long two iterations should take — before
 * injecting the flight request, the same discipline Scenario05's own
 * fix established and every scenario since has followed.
 */
public final class Scenario13Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        // Missing from the first version of this scenario — confirmed as the actual cause of the
        // "no flight-booking capability available" outcome in a real run, not a model failure: the
        // model correctly recognized a genuine capability gap and asked how to proceed, which was
        // the right behavior for what was, in fact, a real gap. Mirrors Scenario05/06's own
        // provisioning of flight-01 exactly.
        workspace.registerType(FlightBookingArtifact.manual(), null);
        FlightBookingArtifact flights = new FlightBookingArtifact("flight-01", workspace);
        workspace.provision("flight-01", "FlightBooking", flights);

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        AlarmArtifact alarm = (AlarmArtifact) workspace.instanceOf("alarm-01");

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(2000);
                userConsole.simulateIncomingMessage(
                        "Two standing things, please. First: I always want aisle seats on any "
                        + "flight you book for me — this applies to every future booking, not just "
                        + "the next one. Second: start a repeating reminder — set an alarm for 5 "
                        + "seconds, and every time it fires, tell me it fired and immediately set "
                        + "another one for 5 seconds. Keep this going indefinitely; don't stop "
                        + "unless I explicitly tell you to.");

                // Poll the artifact's own fired-alarm count directly — real, checkable ground
                // truth that the loop has genuinely iterated at least twice (fired, reset, fired
                // again), not a guess at how long that should take in wall-clock time.
                long deadline = System.currentTimeMillis() + 120_000;
                while (alarm.firedAlarmsCount() < 2 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(300);
                }
                userConsole.simulateIncomingMessage(
                        "Please book a flight from Rome to Paris for January 15th, 2027.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(35);
        workspace.shutdown();
    }
}
