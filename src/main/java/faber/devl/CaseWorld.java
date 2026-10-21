package faber.devl;

import java.util.List;

/**
 * The handle the framework holds on a case's business system (the artifact the agent works with): it loads a stage's items under a world and a feedback mode, signals when the queue
 * has emptied, and reports what the agent decided. Everything else about the system — its operations, its manual, how it presents an item — is the case's.
 */
public interface CaseWorld {
    /** Replaces the queue with the items, to be judged in the world (1 or 2), with the feedback mode. */
    void loadStage(List<? extends WorkItem> items, int world, Feedback feedback);
    /** Called once, the moment the last pending item is decided. */
    void setOnQueueEmpty(Runnable r);
    int pendingCount();
    int decidedCount();
    int correctCount();
    /** Every decision so far, in no particular order (operations run concurrently). */
    List<Outcome> outcomes();
}
