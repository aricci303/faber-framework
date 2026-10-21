package faber.devl;

/** One unit of work in a case's queue, as the framework needs to know it: who it is, what group it belongs to, and the outcome that is right for it in a given world. */
public interface WorkItem {
    String id();
    /** A grouping the change analysis reports by (for the expense case, its category). */
    String group();
    /** The right outcome in the given world (1 = the world of learning, 2 = the changed world), as a label: the label an agent's decision is compared with. */
    String expected(int world);
}
