package faber.devl;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * When is a debrief, or a consolidation, over? When the agent has REPORTED and the playbook has been WRITTEN in this stage — or, if it reported without writing, after it has been given a few more cycles to write what
 * its report may have promised.
 *
 * The end rule used to be the first message the agent sent to the user. That was right while the playbook was a note written in the same batch as the report; with a playbook the agent has to read, rewrite and
 * confirm, an agent that sends a PROGRESS message ("next I'll write the new version…") was cut off before it wrote anything, and the playbook silently stayed as it was: in the first nine learning runs, 3 of 25
 * debriefs ended that way, all in the last two materials versions (FINDINGS.md, R1.11). Evaluated once per cycle; nothing the agent reads changes.
 *
 *   not reported yet                 -> not over
 *   reported, playbook revised       -> over
 *   reported, not revised            -> not over for `settle` further cycles, then over (the agent said what it said; it is not made to write)
 * Operations run asynchronously, so a short wait lets the batch that reported land its revision before the cycle is judged (landMillis; messageWaitMillis does the same for the report itself).
 */
public final class DebriefCompletion implements BooleanSupplier {
    /** Cycles an agent that has reported without writing the playbook is given. */
    public static final int SETTLE = 3;
    private final IntSupplier sent, revisions; private final int sentBefore, revisionsBefore, settle; private final long landMillis, messageWaitMillis;
    private int waited = 0; private boolean revised = false, reported = false;

    public DebriefCompletion(IntSupplier sent, int sentBefore, IntSupplier revisions, int revisionsBefore, int settle, long messageWaitMillis, long landMillis) {
        this.sent = sent; this.sentBefore = sentBefore; this.revisions = revisions; this.revisionsBefore = revisionsBefore; this.settle = settle; this.messageWaitMillis = messageWaitMillis; this.landMillis = landMillis;
    }

    private boolean until(BooleanSupplier cond, long millis) {
        if (cond.getAsBoolean()) return true;
        try { for (long t = 0; t < millis; t += 50) { Thread.sleep(50); if (cond.getAsBoolean()) return true; } } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return cond.getAsBoolean();
    }

    @Override public synchronized boolean getAsBoolean() {
        if (!until(() -> sent.getAsInt() > sentBefore, messageWaitMillis)) return false;                // the agent has not reported
        reported = true;
        if (until(() -> revisions.getAsInt() > revisionsBefore, landMillis)) { revised = true; return true; }
        return ++waited > settle;                                                                        // reported, nothing written: `settle` more cycles to write what the report may have promised
    }

    public synchronized boolean reported() { return reported; }
    public synchronized boolean revised() { return revised; }
    /** How many cycles the agent was given after reporting without having written the playbook. */
    public synchronized int cyclesAfterReportWithoutWriting() { return waited; }
    public synchronized String endedBy() {
        if (!reported) return "no report";
        return revised ? "agent reported and revised the playbook" : "agent reported; the playbook was not revised, after " + settle + " more cycles";
    }
}
