package faber.agent.formal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import faber.agent.Percept;
import faber.agent.PlanResult;
import faber.agent.GoalLedger;
import faber.agent.IntentionLedger;

/**
 * Computes alpha: narrative/action -> formal tuple, externally and
 * non-generatively, never from the model's free narrative text.
 */
public interface TupleExtractor {

    CoreTuple extract(List<Percept> perceptsThisCycle, PlanResult action, GoalLedger goalLedger, IntentionLedger intentionLedger);

    /**
     * A deliberately simple, deterministic extractor operating purely on
     * the harness's own structured state. W' relevance is keyed off
     * having a non-null correlationId, which now naturally covers all
     * three operation-lifecycle percepts (started, completed, failed)
     * uniformly, not just the two resolution ones — no change needed
     * here for the operation_started refinement, since this logic was
     * already generic over "anything correlation-bearing."
     */
    final class HeuristicTupleExtractor implements TupleExtractor {
        @Override
        public CoreTuple extract(List<Percept> perceptsThisCycle, PlanResult planResult,
                                  GoalLedger goalLedger, IntentionLedger intentionLedger) {
            Set<String> W = new LinkedHashSet<>();
            for (Percept p : perceptsThisCycle) {
            	W.add(p.toContextLine());
            }

            // Every intention in <intention_changes> is adopted/revised identically, whether or not
            // it's the one driving this cycle's action — first-class, not an attachment to whichever
            // action happens to be taken this cycle. Goal and intention are kept in step here, by this
            // caller, from the same entry — neither ledger knows about the other on its own.
            for (PlanResult.IntentionEntry g : planResult.getIntentionChanges()) {
                if (g.goalId == null) continue;
                goalLedger.registerOrUpdate(g.goalId, g.goalDescription);
                intentionLedger.registerOrUpdate(g.goalId, g.plan);
                intentionLedger.registerTrigger(g.goalId, g.trigger);
                intentionLedger.resolveGoal(g.goalId, g.status);
            }

            var goalId = planResult.getActInfo().goalId();
            String G = goalId != null ? goalId : CoreTuple.BOTTOM;
            Relation r = goalId != null ? Relation.MEANS_END : Relation.REACTIVE;

            
            var content = planResult.getActInfo().content();
            
            String targetId = null;
            if (content.has("artifact_id")){
            	targetId = content.getString("artifact_id");
            }
            Set<String> WPrime = new LinkedHashSet<>();
            for (Percept p : perceptsThisCycle) {
                boolean relevant = (targetId != null && targetId.equals(p.artifactId)) || (p.correlationId != null);
                if (relevant) {
                	WPrime.add(p.toContextLine());
                }
            }
            if (WPrime.isEmpty()) { 
            	WPrime.addAll(W);
            }
            return new CoreTuple(W, G, planResult.getActInfo().kind(), r, WPrime);
        }
    }
}
