package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * A direct sibling of Scenario05MainNoInitialPlan, isolating exactly
 * one variable. That scenario's seed goal ("serve-user") had no real
 * ambiguity to get wrong — a pure standing disposition, no plausible
 * one-time-setup reading competing against it at all — and the model
 * correctly self-corrected the wrong ACHIEVEMENT default on its own
 * first cycle, unprompted, with explicit reasoning. That result was
 * good, but doesn't by itself say whether the same self-correction
 * instinct holds on a genuinely ambiguous classification, the kind
 * this project has already seen split 50/50 across otherwise-identical
 * runs (a recorded standing preference — MAINTENANCE in one run,
 * ACHIEVEMENT in another, both readings genuinely defensible).
 *
 * This scenario's own seed goal is deliberately built with that same
 * two-sided shape, at the seed level rather than as an ordinary
 * subgoal: "keep track of preferences and apply them going forward"
 * reads as ACHIEVEMENT if the goal is "get the tracking set up" (a
 * one-time task with a clean terminal action), or MAINTENANCE if the
 * goal is "the standing condition of preferences being honored" (no
 * terminal state at all). Both readings are as defensible here as they
 * were for the subgoal case — the deliberate point of this scenario,
 * not an oversight.
 *
 * goalId is deliberately not "serve-user" — that id alone would
 * signal the unambiguous, standing-disposition answer regardless of
 * how the description itself is phrased, which would undermine the
 * whole point of testing genuine ambiguity.
 *
 * January 15th, 2027 chosen specifically to avoid FlightBookingArtifact's
 * own hardcoded failure date (2026-12-24) — this scenario is about
 * kind self-classification, not booking-failure recovery, and a clean
 * success keeps that the only variable in play.
 */
public final class Scenario05MainAmbiguousSeedKind {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(FlightBookingArtifact.manual(), null);
        FlightBookingArtifact flights = new FlightBookingArtifact("flight-01", workspace);
        workspace.provision("flight-01", "FlightBooking", flights);

        Agent agent = new Agent("agent-0", new SeedGoal("track-preferences",
                "Keep track of any standing preferences this user mentions — for example, seat or "
                + "hotel preferences — and make sure every future booking made on their behalf "
                + "reflects them."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(2000);
                userConsole.simulateIncomingMessage(
                        "I always prefer aisle seats when flying, no matter what I book — please keep "
                        + "that in mind going forward. Also, could you book a flight from Bologna to "
                        + "Berlin for January 15th, 2027?");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(12);
        workspace.shutdown();
    }
}
