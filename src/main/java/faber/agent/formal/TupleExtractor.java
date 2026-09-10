package faber.agent.formal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import faber.agent.Percept;
import faber.agent.PlanResult;
import faber.agent.GoalLedger;

/**
 * Computes alpha: narrative/action -> formal tuple, externally and
 * non-generatively, never from the model's free narrative text.
 */
public interface TupleExtractor {

    CoreTuple extract(List<Percept> perceptsThisCycle, PlanResult action, GoalLedger ledger);

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
        public CoreTuple extract(List<Percept> perceptsThisCycle, PlanResult planResult, GoalLedger ledger) {
            Set<String> W = new LinkedHashSet<>();
            for (Percept p : perceptsThisCycle) {
            	W.add(p.toContextLine());
            }

            // Every goal in <goals> is registered/updated identically, whether or not it's the one
            // driving this cycle's action — first-class, not an attachment to whichever action happens
            // to be taken this cycle.
            for (PlanResult.GoalEntry g : planResult.getGoals()) {
                if (g.id == null) continue;
                if (!ledger.isRegistered(g.id)) {
                    ledger.registerOrGet(g.id, g.content);
                }
                ledger.registerTrigger(g.id, g.trigger);
                ledger.resolveGoal(g.id, g.status);
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
