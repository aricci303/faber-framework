package faber.agent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

/**
 * The parsed result of one Plan (micro-loop) run: the updated narration
 * plus the committed action, parsed from a real structured JSON action
 * format 
 */
public final class PlanResult {

    public enum ActionKind { INVOKE, WAIT, FOCUS, STOP_OBSERVING }

    /**
     * One entry of "additional_goals" — a commitment recognized this
     * cycle but not the one driving this cycle's action. Deliberately
     * the same shape as "goal" itself, just plural: this exists because
     * a single cycle's perception can imply more than one distinct
     * goal (two user messages arriving together, each implying its own
     * commitment) while only one action can be taken this cycle. Left
     * in prose, the others are silently lost the moment narrative
     * moves on — the identical failure shape as manuals and origin
     * tracking eroding across many cycles, just triggered by several
     * simultaneous implications in one cycle instead.
     */
    public static final class AdditionalGoal {
        public final String id;
        public final String content;
        public final String status;
        public final String pendingTriggerCondition;
        public final String pendingTriggerPlannedAction;

        AdditionalGoal(String id, String content, String status,
                       String pendingTriggerCondition, String pendingTriggerPlannedAction) {
            this.id = id;
            this.content = content;
            this.status = status;
            this.pendingTriggerCondition = pendingTriggerCondition;
            this.pendingTriggerPlannedAction = pendingTriggerPlannedAction;
        }
    }

    public final String stateOfMind;
    public final ActionKind kind;
    public final JSONObject action;
    public final String goalId;
    public final String goalContent;
    public final String goalStatus;
    public final String pendingTriggerCondition;
    public final String pendingTriggerPlannedAction;
    public final java.util.List<AdditionalGoal> additionalGoals;

    private PlanResult(String stateOfMind, ActionKind kind, JSONObject action,
                        String goalId, String goalContent, String goalStatus,
                        String pendingTriggerCondition, String pendingTriggerPlannedAction,
                        java.util.List<AdditionalGoal> additionalGoals) {
        this.stateOfMind = stateOfMind;
        this.kind = kind;
        this.action = action;
        this.goalId = goalId;
        this.goalContent = goalContent;
        this.goalStatus = goalStatus;
        this.pendingTriggerCondition = pendingTriggerCondition;
        this.pendingTriggerPlannedAction = pendingTriggerPlannedAction;
        this.additionalGoals = additionalGoals;
    }

    private static final Pattern SOM = Pattern.compile("<state_of_mind>(.*?)</state_of_mind>", Pattern.DOTALL);
    private static final Pattern ACT = Pattern.compile("<action>(.*?)</action>", Pattern.DOTALL);

    @SuppressWarnings("unchecked")
    public static PlanResult parse(String rawModelOutput) {
        Matcher somMatcher = SOM.matcher(rawModelOutput);
        Matcher actMatcher = ACT.matcher(rawModelOutput);
        if (!somMatcher.find()) {
            throw new IllegalStateException("Model output missing <state_of_mind> — refusing to act without it.");
        }
        if (!actMatcher.find()) {
            throw new IllegalStateException("Model output missing <action>.");
        }
        String som = somMatcher.group(1).trim();

        JSONObject parsed = null;
        String act = actMatcher.group(1).trim();
        
        try {
        	parsed = new JSONObject(act);
        } catch (Exception ex) {
            throw new IllegalStateException("Model output malformed <act> (not a valid JSON object)");
        }
        
        Object kindRaw = parsed.get("kind");
        if (kindRaw == null) {
            throw new IllegalStateException("Action JSON missing required \"kind\" field: " + parsed);
        }
        ActionKind kind = ActionKind.valueOf(kindRaw.toString());

        String goalId = null, goalContent = null, goalStatus = null;
        String pendingTriggerCondition = null, pendingTriggerPlannedAction = null;
        if (parsed.has("goal")) {
	        JSONObject w = parsed.getJSONObject("goal"); 
	        goalId = (String) w.get("id");
	        if (w.has("content")) {
	        	goalContent = (String) w.get("content");
		    }
		    if (w.has("status")) {
		    	goalStatus = (String) w.get("status");
		    }
	        if (w.has("pending_trigger")) {
	        	JSONObject t = w.getJSONObject("pending_trigger");
	            pendingTriggerCondition = (String) t.get("condition");
	            pendingTriggerPlannedAction = (String) t.get("planned_action");
	        }
        }

        java.util.List<AdditionalGoal> additionalGoals = new java.util.ArrayList<>();
        if (parsed.has("additional_goals")) {
            org.json.JSONArray arr = parsed.getJSONArray("additional_goals");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject g = arr.getJSONObject(i);
                String id = (String) g.get("id");
                String content = g.has("content") ? (String) g.get("content") : null;
                String status = g.has("status") ? (String) g.get("status") : null;
                String cond = null, plannedAction = null;
                if (g.has("pending_trigger")) {
                    JSONObject t = g.getJSONObject("pending_trigger");
                    cond = (String) t.get("condition");
                    plannedAction = (String) t.get("planned_action");
                }
                additionalGoals.add(new AdditionalGoal(id, content, status, cond, plannedAction));
            }
        }

        return new PlanResult(som, kind, parsed, goalId, goalContent, goalStatus,
                pendingTriggerCondition, pendingTriggerPlannedAction, additionalGoals);
    }

    public String getString(String key) { 
    	if (action.has(key)) {
    		return action.getString(key); 
    	} else {
    		return null;
    	}
    }

    public JSONObject getJSONObject(String key) {
    	if (action.has(key)) {
    		return action.getJSONObject(key);
    	} else {
    		return new JSONObject();
    	}
    }

    public long getLong(String key, long defaultValue) {
        Object v = action.get(key);
        return v == null ? defaultValue : ((Number) v).longValue();
    }
}
