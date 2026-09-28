package faber.agent.formal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import faber.agent.Percept;
import faber.agent.PlanResult;
import faber.agent.GoalLedger;
import faber.agent.GoalKind;
import faber.agent.IntentionLedger;

/**
 * Computes alpha: narrative/action -> formal tuple, externally and
 * non-generatively, never from the model's free narrative text.
 *
 * A cycle now commits to one or more actions at once (see PlanResult's
 * own doc comment on <actions> and when batching several genuinely
 * independent ones is safe) — extract() reflects that by returning one
 * tuple per action, not one tuple per cycle. Every tuple in the list
 * shares the same W (this cycle's perceived context is the same
 * regardless of which of this cycle's actions is being evaluated); G,
 * A, J, and W' are each computed independently per action, exactly as
 * a single action's tuple always was. Existing WF/Coherence checks
 * (WellFormedness.check, Coherence.check) are unchanged and unaware
 * anything batched at all — they simply run once per tuple in this
 * list, the same call they always made, just called from a loop now
 * instead of once.
 */
public interface TupleExtractor {

    List<CoreTuple> extract(List<Percept> perceptsThisCycle, PlanResult action, GoalLedger goalLedger, IntentionLedger intentionLedger);

    /**
     * A deliberately simple, deterministic extractor operating purely on
     * the harness's own structured state. For a single-action cycle
     * (still the ordinary case), W' relevance is keyed off having a
     * non-null correlationId in addition to a target match, which
     * naturally covers all three operation-lifecycle percepts (started,
     * completed, failed) uniformly. For a batch of more than one action,
     * that catch-all is deliberately narrowed to a pure target match per
     * action — seeded from a real run where the broader rule caused two
     * genuinely different, correctly-batched actions to end up with an
     * identical W' (an unrelated percept pulled into both, since neither
     * had anything else competing for that same broad "relevant"
     * bucket), silently erasing the one thing that would otherwise have
     * distinguished them and producing a false C4 flag.
     */
    final class HeuristicTupleExtractor implements TupleExtractor {
        @Override
        public List<CoreTuple> extract(List<Percept> perceptsThisCycle, PlanResult planResult,
                                        GoalLedger goalLedger, IntentionLedger intentionLedger) {
            Set<String> W = new LinkedHashSet<>();
            for (Percept p : perceptsThisCycle) {
            	W.add(p.toContextLine());
            }

            // Every intention in <intention_changes> is adopted/revised identically, whether or not
            // it's the one driving any of this cycle's actions — first-class, not an attachment to
            // whichever action happens to be taken this cycle. Goal and intention are kept in step
            // here, by this caller, from the same entry — neither ledger knows about the other on
            // its own. Runs exactly once per cycle, regardless of how many actions this cycle commits
            // to — intention adoption/revision is cycle-level, not per-action.
            for (PlanResult.IntentionEntry g : planResult.getIntentionChanges()) {
                if (g.goalId == null) continue;
                goalLedger.registerOrUpdate(g.goalId, g.goalDescription, g.parentGoalId, g.goalKind);
                intentionLedger.registerOrUpdate(g.goalId, g.plan);
                intentionLedger.registerTrigger(g.goalId, g.trigger);
                intentionLedger.resolveGoal(g.goalId, g.status);
            }

            List<CoreTuple> tuples = new java.util.ArrayList<>();
            // The correlationId catch-all below (any correlation-bearing percept counts as relevant,
            // regardless of whether it matches this action's own target) was written for a world
            // where every cycle has exactly one action — there, any correlation-bearing percept that
            // cycle could reasonably be assumed relevant to *the* action, since nothing else was
            // competing for that same broad "relevant" bucket. With more than one action sharing one
            // W now, that assumption breaks: the same unrelated percept gets pulled into every
            // action's W' regardless of which one it actually pertains to, silently erasing the one
            // thing (W') that would otherwise distinguish two same-goal, same-kind actions taken in
            // the same cycle — confirmed in a real run, where this produced a false C4 "identical
            // tuple" flag between two genuinely different, correctly-batched actions. Restricted to
            // the single-action case only, preserving today's exact, already-proven behavior there;
            // a batch of more than one falls back to pure targetId matching per action.
            boolean singleAction = planResult.getActions().size() == 1;
            for (PlanResult.ActionInfo actInfo : planResult.getActions()) {
                var goalId = actInfo.goalId();
                String G = goalId != null ? goalId : CoreTuple.BOTTOM;
                // The registration loop above runs first, so a goal introduced this very cycle already
                // has its kind on record by the time this lookup happens — no ordering gap. Relation
                // still needs a concrete value even when a cited goal's own kind is TO_BE_DECIDED (never
                // classified) — MAINTENANCE only when genuinely declared MAINTENANCE, ACHIEVEMENT
                // otherwise, the same fallback used before TO_BE_DECIDED existed. The difference is that
                // this fallback is no longer silent: WellFormedness's WF7 checks this exact case —
                // whether the goal actually cited this cycle was genuinely classified or is riding this
                // fallback — and flags it when it isn't, rather than letting an unclassified goal's
                // citation look identical to a deliberately-achievement one.
                Relation r = goalId != null
                        ? (goalLedger.kindOf(goalId) == GoalKind.MAINTENANCE
                                ? Relation.MAINTENANCE : Relation.ACHIEVEMENT)
                        // Relation.REACTIVE here means goalId itself was null — the model cited no goal
                        // at all. Computed the same way it always was, but no longer a silent, tolerated
                        // outcome: WellFormedness's WF8 flags this every time it happens now, the same
                        // way WF7 flags an unclassified kind riding the ACHIEVEMENT fallback above.
                        : Relation.REACTIVE;


                var content = actInfo.content();

                String targetId = null;
                if (content.has("artifact_id")){
                	targetId = content.getString("artifact_id");
                }
                Set<String> WPrime = new LinkedHashSet<>();
                for (Percept p : perceptsThisCycle) {
                    boolean relevant = (targetId != null && targetId.equals(p.artifactId))
                            || (singleAction && p.correlationId != null);
                    if (relevant) {
                    	WPrime.add(p.toContextLine());
                    }
                }
                if (WPrime.isEmpty()) {
                	WPrime.addAll(W);
                }
                tuples.add(new CoreTuple(W, G, actInfo.kind(), r, WPrime));
            }
            return tuples;
        }
    }
}
