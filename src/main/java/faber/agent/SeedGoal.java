package faber.agent;

/**
 * What an agent is spawned with — the harness-injected goal that exists from before
 * cycle one, before any model output has happened at all. Every agent has exactly one
 * of these, unconditionally: an agent is never spawned without a goal.
 *
 * Deliberately minimal: goalId and description only. There is no assistantDefault()
 * and no way to pre-supply kind or plan — both are always left for the agent's own
 * first cycle to determine, the same ordinary <intention_changes> mechanism it would
 * use for any goal it introduces itself. This is a deliberate design commitment, not
 * a placeholder awaiting a richer type: the underlying position is that if a goal's
 * kind is ever genuinely ambiguous, the fault lies with whoever wrote the description,
 * not with something the harness should paper over by pre-deciding it on the model's
 * behalf — the same "who specified it, not what kind of detail it is" test already
 * governing the goal/plan split applies here too, just one level up, to the seed goal
 * itself rather than to an ordinary subgoal. A well-written description should make
 * the classification clear on its own; an unclear one should be rewritten, not
 * compensated for with an injected kind that quietly does the disambiguating work the
 * description itself should have done.
 *
 * goalId still cannot come from anywhere else: the wire protocol has no rename
 * operation, a goal's id is its own key once registered, and something has to exist
 * before cycle one even runs for G to never be BOTTOM — there is no later point at
 * which the model could supply one instead. description is, correspondingly, the one
 * piece that genuinely carries the full weight of this design: it is the whole of
 * what the assigner (whoever constructs this agent) gets to specify, and everything
 * else — kind, plan, and every subgoal that follows — is left entirely to the agent's
 * own reasoning from that text alone.
 */
public record SeedGoal(String goalId, String description) {}
