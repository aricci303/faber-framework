package faber.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The parsed result of one Plan (micro-loop) run: three top-level
 * blocks — <state_of_mind> (free narrative), <intention_changes> (a
 * structured, homogeneous array — every intention newly adopted or
 * revised this cycle, whether or not it's the one driving this
 * cycle's action), and <action> (kind, parameters, and a bare goal_id
 * string referencing one of the agent's own goals, or absent for a
 * reactive action with none).
 *
 * Aligned with Bratman's practical-reasoning vocabulary, and BDI more
 * generally: a goal is the state of affairs being pursued — the WHAT,
 * owned by whoever assigned it (a user, another agent, or the agent
 * itself decomposing a complex goal into subgoals) and only genuinely
 * revised when they change what they're asking for. An intention pairs
 * a goal with the plan devised to achieve it — the HOW, entirely the
 * agent's own, including any interpretive assumptions it had to make
 * about an underspecified goal. <intention_changes> is exactly that:
 * every entry is an intention (a goal_id plus a plan, and optionally a
 * revised goal_description when the goal itself has genuinely changed)
 * — never a plan-detail leaking into what's supposed to be a pure
 * record of what was asked for, and never the reverse.
 *
 * <intention_changes> is required every cycle, even as an empty array,
 * the same reasoning as <state_of_mind> and <action> already being
 * mandatory — a model with nothing new to adopt or revise says so
 * explicitly, rather than the harness having to guess whether omission
 * means "nothing new" or "forgot."
 */
public final class PlanResult {

    public enum ActionKind { INVOKE, WAIT, FOCUS, STOP_OBSERVING }

    /**
     * A trigger's structural matching fields, parsed alongside its free
     * text. signalArtifactId and signalName are checked against the
     * real Percept object's own fields — no text heuristics. See
     * GoalLedger's own doc for the history of why this replaced an
     * earlier, less reliable approach.
     */
    public static final class TriggerSpec {
        public final String condition;
        public final String plannedAction;
        public final String signalArtifactId;
        public final String signalName;
        public final java.util.List<String> signalNameAlternatives;
        public final String signalValueContains;
        public final String operationName;
        public final boolean recurring;

        TriggerSpec(String condition, String plannedAction, String signalArtifactId,
                    String signalName, java.util.List<String> signalNameAlternatives,
                    String signalValueContains, String operationName, boolean recurring) {
            this.condition = condition;
            this.plannedAction = plannedAction;
            this.signalArtifactId = signalArtifactId;
            this.signalName = signalName;
            this.signalNameAlternatives = signalNameAlternatives;
            this.signalValueContains = signalValueContains;
            this.operationName = operationName;
            this.recurring = recurring;
        }

        static TriggerSpec parse(JSONObject t) {
            if (t == null) return null;
            String condition = t.has("condition") ? (String) t.get("condition") : null;
            String plannedAction = t.has("planned_action") ? (String) t.get("planned_action") : null;
            String signalArtifactId = t.has("signal_artifact_id") ? (String) t.get("signal_artifact_id") : null;
            String signalName = t.has("signal_name") ? (String) t.get("signal_name") : null;
            java.util.List<String> signalNameAlternatives = new java.util.ArrayList<>();
            if (t.has("signal_name_alternatives")) {
                JSONArray alts = t.getJSONArray("signal_name_alternatives");
                for (int i = 0; i < alts.length(); i++) {
                    signalNameAlternatives.add(alts.getString(i));
                }
            }
            String signalValueContains = t.has("signal_value_contains") ? (String) t.get("signal_value_contains") : null;
            String operationName = t.has("operation_name") ? (String) t.get("operation_name") : null;
            boolean recurring = t.has("recurring") && t.getBoolean("recurring");
            return new TriggerSpec(condition, plannedAction, signalArtifactId, signalName,
                    signalNameAlternatives, signalValueContains, operationName, recurring);
        }
    }

    /**
     * One entry of the <intention_changes> array — homogeneous,
     * whether or not this particular intention is the one driving this
     * cycle's action. goalId identifies which goal this intention is
     * for; goalDescription is only ever supplied when the goal itself
     * is being introduced or has genuinely changed (the assigner's own
     * words, not the agent's approach to it); plan is the agent's own,
     * entirely its own to revise.
     */
    public static final class IntentionEntry {
        public final String goalId;
        public final String goalDescription;
        public final String plan;
        public final GoalStatus status;
        public final TriggerSpec trigger;

        IntentionEntry(String goalId, String goalDescription, String plan, GoalStatus status, TriggerSpec trigger) {
            this.goalId = goalId;
            this.goalDescription = goalDescription;
            this.plan = plan;
            this.status = status;
            this.trigger = trigger;
        }

        static IntentionEntry parse(JSONObject g) {
            String goalId = (String) g.get("goal_id");
            String goalDescription = g.has("goal_description") ? (String) g.get("goal_description") : null;
            String plan = g.has("plan") ? (String) g.get("plan") : null;
            GoalStatus status = g.has("status") ? GoalStatus.fromJsonValue((String) g.get("status")) : null;
            TriggerSpec trigger = null;
            if (g.has("pending_trigger")) {
            	Object obj = g.get("pending_trigger");
            	if (obj instanceof JSONObject) {
            		trigger = TriggerSpec.parse(((JSONObject) obj));
            	} else {
            		System.err.println("ERROR: pending_trigger is not a JSONObjecy");
            	}
            }
            return new IntentionEntry(goalId, goalDescription, plan, status, trigger);
        }
    }
    
    public static final record ActionInfo(ActionKind kind, String goalId, JSONObject content) {}

    private final String stateOfMind;
    private final java.util.List<IntentionEntry> intentionChanges;
    private final ActionInfo actInfo;
    
    private PlanResult(String stateOfMind, java.util.List<IntentionEntry> intentionChanges, ActionInfo actInfo) {
        this.stateOfMind = stateOfMind;
        this.intentionChanges = intentionChanges;
        this.actInfo = actInfo;
    }

    // Anchored to the start of a line (optionally after leading whitespace), not just any
    // occurrence anywhere in the text. Found necessary in a real run: an earlier exception
    // message literally spelled out the tag name (since fixed to avoid that), the model
    // reasonably echoed that phrasing back while acknowledging a retry-feedback note, and a
    // naive first-match regex latched onto that incidental mid-prose mention as if it were the
    // real opening tag — capturing everything through to the model's actual, well-formed block
    // as one garbled blob. A genuine structural tag, per the format this class asks for, always
    // starts its own line; an incidental mention within a sentence virtually never does. This is
    // deliberately defense in depth — the root cause (literal tag syntax in text fed back into
    // context) is also fixed at its source, but this holds regardless of what caused a stray
    // mention.
    private static final Pattern SOM = Pattern.compile(
            "^\\s*<state_of_mind>(.*?)</state_of_mind>", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern INTENTION_CHANGES = Pattern.compile(
            "^\\s*<intention_changes>(.*?)</intention_changes>", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern ACT = Pattern.compile(
            "^\\s*<action>(.*?)</action>", Pattern.DOTALL | Pattern.MULTILINE);

    public static PlanResult parse(String rawModelOutput) {
    	var som = parseStateOfMindBlock(rawModelOutput);
        var intentionChanges = parseIntentionChangesBlock(rawModelOutput);
        var actionInfo = parseActionBlock(rawModelOutput);
        return new PlanResult(som, intentionChanges, actionInfo);
    }

    private static String parseStateOfMindBlock(String rawModelOutput){
        Matcher somMatcher = SOM.matcher(rawModelOutput);
        if (!somMatcher.find()) {
            throw new IllegalStateException("Model output missing the state-of-mind section — refusing to act without it.");
        }
        return somMatcher.group(1).trim();
    }
    
    private static ActionInfo parseActionBlock(String rawModelOutput){
        Matcher actMatcher = ACT.matcher(rawModelOutput);
        if (!actMatcher.find()) {
            throw new IllegalStateException("Model output missing the action section.");
        }
        JSONObject parsed;
        String act = actMatcher.group(1).trim();
        try {
            parsed = new JSONObject(act);
        } catch (Exception ex) {
            throw new IllegalStateException("Model output's action section is malformed (not a valid JSON object)");
        }

        Object kindRaw = parsed.get("kind");
        if (kindRaw == null) {
            throw new IllegalStateException("Action JSON missing required \"kind\" field: " + parsed);
        }
        ActionKind kind = ActionKind.valueOf(kindRaw.toString());
        String actionGoalId = parsed.has("goal_id") ? parsed.getString("goal_id") : null;
        return  new ActionInfo(kind, actionGoalId, parsed);
    	
    }

    private static java.util.List<IntentionEntry> parseIntentionChangesBlock(String rawModelOutput){
        Matcher intentionChangesMatcher = INTENTION_CHANGES.matcher(rawModelOutput);
        if (!intentionChangesMatcher.find()) {
            throw new IllegalStateException("Model output missing the intention_changes section — required every cycle, even as an empty list.");
        }
        java.util.List<IntentionEntry> intentionChanges = new java.util.ArrayList<>();
        String intentionChangesText = intentionChangesMatcher.group(1).trim();
        JSONArray intentionChangesArr;
        try {
            intentionChangesArr = new JSONArray(intentionChangesText);
        } catch (Exception ex) {
            throw new IllegalStateException("Model output's intention_changes section is malformed (not a valid JSON array): " + intentionChangesText);
        }
        for (int i = 0; i < intentionChangesArr.length(); i++) {
            intentionChanges.add(IntentionEntry.parse(intentionChangesArr.getJSONObject(i)));
        }
        return intentionChanges;
    }

    public String getStateOfMind() {
    	return stateOfMind;
    }

    public java.util.List<IntentionEntry> getIntentionChanges(){
    	return intentionChanges;
    }
    
    public ActionInfo getActInfo() {
    	return this.actInfo;
    }
    
}
