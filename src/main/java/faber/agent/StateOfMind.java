package faber.agent;

/**
 * The agent's own self-narration, carried forward verbatim from one Plan
 * run to the next — the dominant tier of the two-tier context. Unchanged
 * from the first version of this harness.
 */
public final class StateOfMind {

    private String narration = "(no prior state — this is the first cycle)";

    public String current() { return narration; }
    public void update(String newNarration) { this.narration = newNarration; }
}
