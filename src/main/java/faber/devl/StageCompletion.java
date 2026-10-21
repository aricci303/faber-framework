package faber.devl;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * When is a stage over? When the queue has been emptied AND the agent has reported afterwards. "Afterwards"
 * must be measured against the exact moment the queue emptied (the baseline is captured by the case's
 * system's own hook), not against some earlier or later observation: an earlier version compared against a
 * baseline taken after the agent had already reported, so it never saw a new message and kept running idle
 * cycles. If the agent never reports, a short grace period ends the stage instead of waiting out the cap.
 * Evaluated once per cycle.
 *
 * Two knobs, both for the assessment stages only, both defaulting to the original behaviour. grace: how many cycles after the queue empties the stage waits for a report before
 * ending without one. settle: how many further cycles the agent gets AFTER it has reported — an agent that reports first and then corrects its notes, or confirms its note writes
 * before reporting, is otherwise cut off in the middle of work the assessment is meant to see (found in the first instance runs: one agent was stopped at its report with its notes
 * unwritten, another ended by grace three cycles after its last decision, having decided to confirm its note writes before reporting).
 */
public final class StageCompletion implements BooleanSupplier {
    private final IntSupplier sentMessages;
    private final int grace, settle;
    private int sentWhenEmptied = -1, cyclesSinceEmptied = 0, evaluationsSinceReport = 0;

    public StageCompletion(IntSupplier sentMessages, int graceCycles) { this(sentMessages, graceCycles, 0); }
    public StageCompletion(IntSupplier sentMessages, int graceCycles, int settleCycles) { this.sentMessages = sentMessages; this.grace = graceCycles; this.settle = settleCycles; }

    /** Call from the case system's queue-empty hook. Only the first call counts. */
    public synchronized void queueEmptied() { if (sentWhenEmptied < 0) sentWhenEmptied = sentMessages.getAsInt(); }

    @Override public synchronized boolean getAsBoolean() {
        if (sentWhenEmptied < 0) return false;
        if (sentMessages.getAsInt() > sentWhenEmptied) return evaluationsSinceReport++ >= settle;     // settle 0: over at once, as before; settle n: n more cycles first
        return ++cyclesSinceEmptied >= grace;
    }

    /** How many messages had been sent when the queue emptied (so messages from this index on were sent AFTER it), or -1 if it never did. */
    public synchronized int sentWhenEmptied() { return sentWhenEmptied; }

    public synchronized String endedBy() {
        if (sentWhenEmptied < 0) return "cap: the queue never emptied";
        return sentMessages.getAsInt() > sentWhenEmptied ? "agent reported" : "grace: queue empty, no report";
    }
}
