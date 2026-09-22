package faber.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
 * Optionally holds one more thing about a goal's WHAT: which other
 * goal, if any, it exists in service of. A goal with no parent is a
 * standing, top-level one — either pre-registered by the harness
 * itself at startup, or (rarer) something the agent adopted with
 * nothing above it. A goal's parent, once declared, is never revised
 * — unlike description, there is no legitimate case for "the assigner
 * changed which larger goal this serves"; if that ever seems to be
 * happening, it is a new goal, not a reparenting of an old one.
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
    private final Map<String, String> parents = new LinkedHashMap<>();

    /**
     * Registers a goal if new, or revises its description if description is non-null for a goal that
     * already exists. Omitting description (null) on an already-registered goal leaves it untouched;
     * omitting it while registering a brand-new goal id simply registers the id with no description yet.
     * parentGoalId, if non-null, is recorded the first time this goal is registered and never revised
     * afterward, regardless of what a later call for the same goal id supplies.
     */
    public void registerOrUpdate(String goalId, String description, String parentGoalId) {
        if (description != null) {
            descriptions.put(goalId, description);
        } else {
            descriptions.putIfAbsent(goalId, null);
        }
        if (parentGoalId != null) {
            parents.putIfAbsent(goalId, parentGoalId);
        }
    }

    /** Convenience overload for a goal with no parent — a standing, top-level goal. */
    public void registerOrUpdate(String goalId, String description) {
        registerOrUpdate(goalId, description, null);
    }

    public boolean isRegistered(String goalId) { return descriptions.containsKey(goalId); }

    public String descriptionOf(String goalId) { return descriptions.get(goalId); }

    /** The goal this one exists in service of, or null if it's a standing, top-level goal. */
    public String parentOf(String goalId) { return parents.get(goalId); }

    /** Every registered goal id, in registration order. */
    public Set<String> allGoalIds() { return Collections.unmodifiableSet(descriptions.keySet()); }
}

