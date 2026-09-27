package scenarios;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Workspace;

/**
 * Every prior scenario touching manual persistence (Scenario02,
 * Scenario04 and its siblings, Scenario07) only ever exercised the
 * write decision, and only within a single, short-lived task — never
 * a later, genuinely separate need for the same type's shape, and
 * never an unambiguous opportunity to retract a note once it's
 * actually done. retract_note has not been invoked in any scenario
 * this whole session; read_note has never been observed recovering
 * manual knowledge either, since nothing has yet created the gap that
 * would require it rather than a fresh get_manual call.
 *
 * This scenario deliberately creates that gap, mirroring Scenario06's
 * own device (a genuinely unrelated intervening task, plus a real time
 * gap) but applied to manual knowledge rather than a user preference:
 *
 *   1. A recurring watch on Pager (worth persisting, per Scenario07's
 *      own precedent) — requires get_manual, may or may not prompt a
 *      notebook write; this scenario observes rather than forces that
 *      choice.
 *   2. A genuinely unrelated distraction (Counter) occupying several
 *      real cycles.
 *   3. The user explicitly ends the Alice watch — if a note exists,
 *      this is where the interesting judgment call sits: is a note
 *      about Pager's shape tied to that one now-dropped goal, or does
 *      it outlive it as standing knowledge about the type itself? Left
 *      open deliberately, not engineered toward either answer.
 *   4. A second, separate need for Pager (paging Bob) — by this point
 *      watch-alice is no longer active in ONGOING INTENTIONS with its
 *      plan spelling out Pager's operations, so recalling send_page's
 *      signature here can only come from a retained note or a fresh
 *      get_manual call, not from a goal still sitting in context.
 *   5. An unambiguous signal that the pager system itself is done —
 *      the cleanest opportunity in this whole session for retract_note
 *      to actually fire, if a note was ever written.
 *
 * Injection timing: the transition from step 3 to step 4 is the one
 * whose validity actually depends on precise sequencing — if "page Bob"
 * arrived before watch-alice were genuinely dropped, that goal's own
 * plan (spelling out Pager's operations) would still be sitting in
 * ONGOING INTENTIONS, confounding the very question this scenario
 * exists to isolate. A fixed sleep can't guarantee this, since the
 * agent's own pace varies with real LLM latency, and there's no way to
 * poll for "watch-alice specifically has resolved" without already
 * knowing its exact goal id, which is always the agent's own choice,
 * never predictable in advance.
 *
 * Instead this chains two counts, agent.resolvedGoalsCount(), each
 * waiting for exactly one more resolution than the last, at a point in
 * the narrative where only one specific thing could plausibly cause
 * it: first, the Counter task (step 2) is the only goal that can
 * resolve before "stop watching Alice" is even sent — watch-alice is
 * recurring and never resolves on its own, and hasn't been told to
 * stop yet. Once that first resolution is confirmed, "stop watching
 * Alice" is sent, and the second wait's own count can now only advance
 * from watch-alice being dropped, since nothing else remains pending.
 * No specific goal id needs to be known at any point.
 */
public final class ManualRetentionMain {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(PagerArtifact.manual(), null);
        PagerArtifact pager = new PagerArtifact("pager-01", workspace);
        workspace.provision("pager-01", "Pager", pager);

        workspace.registerType(Counter.manual(), Counter.factory(workspace));

        Agent agent = new Agent("agent-0", new SeedGoal("serve-user",
                "Serve the user's requests as they arise, remaining available and responsive by default."));
        agent.init(workspace);

        var userConsole = workspace.getUserConsole();
        agent.forceObserving(userConsole.id());

        Thread scenarioDriver = new Thread(() -> {
            try {
                Thread.sleep(100);
                userConsole.simulateIncomingMessage(
                        "Please watch for any page from Alice and flag it to me as soon as it arrives.");

                // Not validity-critical (see class doc comment) — a generous, deliberate pacing gap,
                // mirroring Scenario06's own safe use of fixed delays for the same reason.
                Thread.sleep(5_000);
                userConsole.simulateIncomingMessage(
                        "By the way, can you create a new counter called 'ticket-count' and increment it by 3?");

                // Validity-critical from here on — see class doc comment for the full reasoning.
                // watch-alice is recurring and never resolves on its own, so the Counter task is the
                // only thing that can cause this first resolution.
                long deadline = System.currentTimeMillis() + 60_000;
                while (agent.resolvedGoalsCount() < 1 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                userConsole.simulateIncomingMessage(
                        "Actually, never mind about watching for Alice's pages — you can stop that now.");

                // Only watch-alice remains pending at this point, so this second resolution can only
                // be it being dropped (or, less likely, achieved) — never confused with the Counter
                // task, which already resolved above.
                deadline = System.currentTimeMillis() + 60_000;
                while (agent.resolvedGoalsCount() < 2 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                userConsole.simulateIncomingMessage(
                        "Please page Bob to ask if he can cover Alice's shift tomorrow.");

                // Not validity-critical — the retraction opportunity below doesn't depend on Bob's
                // task having resolved, since no reply is scripted at all for it either way.
                Thread.sleep(15_000);
                userConsole.simulateIncomingMessage(
                        "Thanks — we're completely done with the pager system now, you won't need it again.");
            } catch (InterruptedException ignored) {
            }
        });
        scenarioDriver.setDaemon(true);
        scenarioDriver.start();

        agent.doYourJobAndSelfEvaluate(32);
        workspace.shutdown();
    }
}
