package faber.devl;

/** What the agent decided about one item, as the framework scores it: the item, the label of the decision, whether it was right, and what was right. */
public final class Outcome {
    public final String itemId, label, expected; public final boolean correct;
    public Outcome(String itemId, String label, boolean correct, String expected) { this.itemId = itemId; this.label = label; this.correct = correct; this.expected = expected; }
    @Override public String toString() { return itemId + ":" + label + (correct ? " ok" : " WRONG(" + expected + ")"); }
}
