package faber.devl;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import faber.agent.AgentArchitecture.CycleResult;
import faber.agent.PlanResult;

/**
 * Has the agent stalled? When, for several cycles in a row, nothing new reached it (no percept) and it did nothing (it only chose WAIT): it is waiting for something that will not come.
 *
 * The first run with a cheaper model showed it: after a dead end in stage 2 the agent wrote to the user — "where are the rules kept?" — and then chose WAIT for eight cycles, with nothing arriving, because
 * in a run the user does not answer (the tutor is the only voice, and it is scripted). Left alone, the stage would wait out its cap of 90 cycles, one model call each, and the run would learn nothing from it.
 * An agent that has stalled has failed the stage, which is a result; a harness that waits for it to stop is only spending the researcher's time and money.
 *
 * Read from outside the agent (the last cycle's percepts and the actions it chose), so nothing in the agent changes and nothing the agent reads changes. One detector per stage; evaluated once per cycle.
 * It is not used when the tutor is a person (--tutor=human): then waiting for an answer is what the run is for.
 */
public final class StallDetector implements BooleanSupplier {
    /** Cycles in a row with nothing new and nothing done. (In the first stalled run: seven, cycles 25 to 31.) */
    public static final int LIMIT = 6;
    private final Supplier<CycleResult> last; private final int limit;
    private long lastCycle = -1; private int idleRun = 0; private boolean stalled = false;

    public StallDetector(Supplier<CycleResult> lastCycle, int limit) { this.last = lastCycle; this.limit = limit; }

    /** An idle cycle: no new percept, and every action it chose is a WAIT. */
    static boolean idle(CycleResult r) {
        if (r == null || r.senseResult() == null || !r.senseResult().isEmpty() || r.planResult() == null) return false;
        var actions = r.planResult().getActions();
        return !actions.isEmpty() && actions.stream().allMatch(a -> a.kind() == PlanResult.ActionKind.WAIT);
    }

    @Override public synchronized boolean getAsBoolean() {
        CycleResult r = last.get();
        if (r == null || r.cycleNumber() == lastCycle) return stalled;                 // the same cycle seen twice counts once
        lastCycle = r.cycleNumber();
        idleRun = idle(r) ? idleRun + 1 : 0;
        if (idleRun >= limit) stalled = true;
        return stalled;
    }

    public synchronized boolean stalled() { return stalled; }
    public synchronized String endedBy() {
        return "stalled: " + limit + " cycles in a row with nothing new and nothing done (no percept, only WAIT): the agent was waiting for something this run does not give";
    }
}
