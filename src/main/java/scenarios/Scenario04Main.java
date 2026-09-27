package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.AlarmArtifact;
import faber.environment.Workspace;

/**
 * Tests time perceived entirely through AlarmArtifact — no ad hoc
 * wait_timeout percept anywhere in this harness. The agent asks Greg a
 * question and sets a deadline; whichever resolves first (Greg's reply
 * or the alarm) drives the response. Two pending intentions are live
 * simultaneously, and this variant is built so the losing one's
 * condition is satisfied *after* the race is already decided — Greg's
 * reply genuinely arrives, just too late — to see what
 * checkTriggerFidelity actually does with a dangling trigger.
 *
 * "Greg out of time." One of three variants formerly toggled by
 * commenting/uncommenting a single hardcoded sleep in one file — split
 * apart because that hardcoded value was compared against an alarm
 * duration the scenario never actually controls: "soon" is deliberately
 * left for the agent's own interpretation, and that choice has varied
 * run to run (300s, 900s, 1800s, and others separately observed). A
 * fixed reply delay could easily land on the wrong side of the race —
 * arriving before a long alarm, silently testing "Greg on time" under a
 * scenario named for the opposite case. This variant reads back the
 * alarm's actual duration once it's genuinely set, and computes the
 * reply's timing relative to that real value, guaranteed to land after
 * the alarm fires regardless of what duration the agent chose.
 *
 * AlarmArtifact's timeScale compresses how long that actually takes in
 * real wall-clock time — a 900-second "soon" would otherwise take 15
 * real minutes to resolve. The scenario driver reads the identical
 * scale value back from the artifact itself (never a second, separately
 * hardcoded copy) and applies it only to the alarm-duration portion of
 * its own injection delay; the fixed buffer stays real, unscaled
 * milliseconds, since it's a safety margin, not part of the alarm's own
 * semantic duration. Nothing the agent itself perceives changes — it
 * only ever sees the seconds value it chose and the eventual
 * alarm_fired signal, never real elapsed time directly.
 *
 * See Scenario04MainGregOnTime and Scenario04MainGregUnresponsive for
 * the other two variants.
 */
public final class Scenario04Main {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(MessagingArtifact.manual(), null);
        MessagingArtifact messaging = new MessagingArtifact("messaging-01", workspace);
        workspace.provision("messaging-01", "MessagingApp", messaging);

        AlarmArtifact alarm = (AlarmArtifact) workspace.instanceOf("alarm-01");
        // 50x speedup — a real agent cycle (bound by real LLM latency) still comfortably fits inside
        // the compressed window; see AlarmArtifact.timeScale's own doc comment for why not more
        // aggressive than this.
        alarm.setTimeScale(0.02);

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "Please ask Greg, via messaging, if he can send the report today. If he doesn't "
                        + "reply soon, let me know we haven't heard back and proceed without it.");

                // Wait for the agent to have actually set its own alarm — see class doc comment for
                // why this can't be a fixed, hardcoded number. Safety-net deadline mirrors
                // Scenario05Main's own pattern.
                long deadline = System.currentTimeMillis() + 90_000;
                while (alarm.lastSetAlarmSeconds() < 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                long alarmSeconds = Math.max(alarm.lastSetAlarmSeconds(), 0);

                // Out of time: reply arrives after the alarm's own actual, scaled duration, plus a
                // small, unscaled buffer — guaranteeing it lands on the losing side of the race
                // regardless of what duration the agent chose.
                long scaledAlarmMillis = (long) (alarmSeconds * 1000 * alarm.timeScale());
                Thread.sleep(scaledAlarmMillis + 5_000);
                messaging.simulateIncomingMessage("Greg",
                        "Sorry for the delay — yes, I'll send the report by end of day.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(16);
        workspace.shutdown();
    }
}
