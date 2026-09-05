package faber.agent.formal;

import java.util.List;
import java.util.Set;

import faber.agent.PlanResult;
import faber.agent.PlanResult.ActionKind;

/**
 * Section 5 of the Core semantics note, as runnable code: hide A,
 * present only (W, G, J), ask an evaluator to guess A from the closed
 * action vocabulary, score accuracy over a trace. A real deployment
 * would plug in an evaluator backed by a second, independent model
 * call; the trivial predictor in this harness's Main demo exists only
 * to prove the protocol runs end to end, not as a serious baseline.
 */
public final class PredictiveSufficiencyProtocol {

    @FunctionalInterface
    public interface ActionPredictor {
        PlanResult.ActionKind predict(Set<String> W, String G, Relation r, Set<String> WPrime);
    }

    public static final class Score {
        public final int total;
        public final int correct;
        Score(int total, int correct) { this.total = total; this.correct = correct; }
        public double accuracy() { return total == 0 ? 0.0 : (double) correct / total; }
    }

    public static Score run(List<CoreTuple> trace, ActionPredictor predictor) {
        int correct = 0;
        for (CoreTuple t : trace) {
            if (predictor.predict(t.W, t.G, t.r, t.WPrime) == t.A) correct++;
        }
        return new Score(trace.size(), correct);
    }
}
