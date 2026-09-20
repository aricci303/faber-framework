package faber.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of goals, keyed by id — the WHAT half of the goal/intention
 * split (IntentionLedger holds the HOW). A goal is the state of
 * affairs being pursued, owned by whoever assigned it (a user, another
 * agent, or the agent itself decomposing a complex goal into
 * subgoals); its description is only ever revised when the assigner
 * has genuinely changed what they're asking for — never by the agent's
 * own reasoning about how to pursue it, which belongs to the intention
 * instead.
 *
 * Deliberately narrow and independent of IntentionLedger: this class
 * holds nothing about plans, status, or triggers, and has no reference
 * to the intention side at all. Earlier versions of this class held
 * both halves together — plan, status, and trigger were tracked here
 * too, in parallel maps keyed by the same goal id — but that fused the
 * WHAT and the HOW into one structure for no reason other than
 * convenience, exactly the conflation the goal/intention split (Part
 * III of the architecture reference) exists to avoid at the wire-
 * format level. Splitting it here too means the same discipline now
 * holds at the code level as well: keeping a goal and its intention in
 * step is the caller's job (TupleExtractor updates both from the same
 * <intention_changes> entry; AgentArchitecture combines both when
 * rendering ONGOING INTENTIONS), not something either ledger enforces
 * on the other's behalf.
 */
public final class GoalLedger {

    private final Map<String, String> descriptions = new LinkedHashMap<>();

    /**
     * Registers a goal if new, or revises its description if description is non-null for a goal that
     * already exists. Omitting description (null) on an already-registered goal leaves it untouched;
     * omitting it while registering a brand-new goal id simply registers the id with no description yet.
     */
    public void registerOrUpdate(String goalId, String description) {
        if (description != null) {
            descriptions.put(goalId, description);
        } else {
            descriptions.putIfAbsent(goalId, null);
        }
    }

    public boolean isRegistered(String goalId) { return descriptions.containsKey(goalId); }

    public String descriptionOf(String goalId) { return descriptions.get(goalId); }
}
