package faber.agent;

/**
 * A goal's status. Used in two related but distinct ways:
 *
 *   - As tracked persistently in GoalLedger, every registered goal
 *     always has exactly one of these three values at all times,
 *     defaulting to ONGOING the moment it's first registered — never
 *     absent, never implicit. This replaces an earlier design where
 *     "ongoing" was only ever represented by a goal id's absence from
 *     a separate resolutions map — a real value nowhere, only
 *     inferred from where a key wasn't.
 *
 *   - As reported in a single cycle's <intention_changes> entry (see
 *     PlanResult.IntentionEntry.status), only ACHIEVED or DROPPED is ever
 *     legitimately written by the model — there is no well-formed way
 *     to *say* "still ongoing" each cycle, and requiring the model to
 *     restate it as a JSON field would trade real, recurring output
 *     cost for information PENDING INTENTIONS already reliably shows.
 *     A null IntentionEntry.status therefore means "no status update this
 *     turn," which is a genuinely different thing from ONGOING itself
 *     — the latter is a persistent ledger fact, not a per-turn claim.
 */
public enum GoalStatus {
    ONGOING, ACHIEVED, DROPPED;

    /** Parses the model's own JSON value ("achieved" / "dropped"). Returns null for a null input,
     *  preserving "no status update this turn" as distinct from any real status value. */
    public static GoalStatus fromJsonValue(String raw) {
        if (raw == null) return null;
        switch (raw) {
            case "achieved": return ACHIEVED;
            case "dropped": return DROPPED;
            default: throw new IllegalArgumentException("Unknown goal status in model output: " + raw);
        }
    }

    public String toJsonValue() {
        return name().toLowerCase();
    }
}
