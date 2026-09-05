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

    public final String stateOfMind;
    public final ActionKind kind;
    public final JSONObject action;
    public final String goalId;
    public final String goalContent;
    public final String pendingTriggerCondition;
    public final String pendingTriggerPlannedAction;

    private PlanResult(String stateOfMind, ActionKind kind, JSONObject action,
                        String goalId, String goalContent,
                        String pendingTriggerCondition, String pendingTriggerPlannedAction) {
        this.stateOfMind = stateOfMind;
        this.kind = kind;
        this.action = action;
        this.goalId = goalId;
        this.goalContent = goalContent;
        this.pendingTriggerCondition = pendingTriggerCondition;
        this.pendingTriggerPlannedAction = pendingTriggerPlannedAction;
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
        
        /* since it is supposed to be a JSON, some char are now allowed */
        /*
        if (act.indexOf("\r") > -1) {
        	act = act.replaceAll("\r", "");
        }
        if (act.indexOf("\n") > -1) {
        	act = act.replaceAll("\n", "");
        }*/
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

        String goalId = null, goalContent = null;
        String pendingTriggerCondition = null, pendingTriggerPlannedAction = null;
        if (parsed.has("goal")) {
	        JSONObject w = parsed.getJSONObject("goal"); 
	        goalId = (String) w.get("id");
	        if (w.has("content")) {
	        	goalContent = (String) w.get("content");
		    }
	        if (w.has("pending_trigger")) {
	        	JSONObject t = w.getJSONObject("pending_trigger");
	            pendingTriggerCondition = (String) t.get("condition");
	            pendingTriggerPlannedAction = (String) t.get("planned_action");
	        }
        }

        return new PlanResult(som, kind, parsed, goalId, goalContent, pendingTriggerCondition, pendingTriggerPlannedAction);
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
