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
        public CoreTuple extract(List<Percept> perceptsThisCycle, PlanResult action, GoalLedger ledger) {
            Set<String> W = new LinkedHashSet<>();
            for (Percept p : perceptsThisCycle) W.add(p.toContextLine());

            String G = action.goalId != null ? action.goalId : CoreTuple.BOTTOM;
            if (action.goalId != null && !ledger.isRegistered(action.goalId)) {
                ledger.registerOrGet(action.goalId, action.goalContent);
            }
            if (action.goalId != null) {
                ledger.registerTrigger(action.goalId, action.pendingTriggerCondition, action.pendingTriggerPlannedAction);
                ledger.resolveGoal(action.goalId, action.goalStatus);
            }

            // Goals recognized this cycle but not driving this cycle's action — registered exactly
            // like the main goal, but deliberately left out of G/Relation below: only the one goal
            // actually justifying this cycle's action counts as this cycle's means-end relation.
            for (PlanResult.AdditionalGoal ag : action.additionalGoals) {
                if (ag.id == null) continue;
                if (!ledger.isRegistered(ag.id)) {
                    ledger.registerOrGet(ag.id, ag.content);
                }
                ledger.registerTrigger(ag.id, ag.pendingTriggerCondition, ag.pendingTriggerPlannedAction);
                ledger.resolveGoal(ag.id, ag.status);
            }

            Relation r = action.goalId != null ? Relation.MEANS_END : Relation.REACTIVE;

            
            String targetId = action.getString("artifact_id");
            Set<String> WPrime = new LinkedHashSet<>();
            for (Percept p : perceptsThisCycle) {
                boolean relevant = (targetId != null && targetId.equals(p.artifactId)) || (p.correlationId != null);
                if (relevant) WPrime.add(p.toContextLine());
            }
            if (WPrime.isEmpty()) WPrime.addAll(W);

            return new CoreTuple(W, G, action.kind, r, WPrime);
        }
    }
}
