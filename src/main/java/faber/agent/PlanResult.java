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
     * A trigger's structural matching fields, parsed alongside its free
     * text. Replaces an earlier design that tried to infer what to
     * check for by heuristically parsing the "condition" sentence
     * itself (its first word, taken as a stand-in for the signal
     * name) — which broke in two different ways in real runs: once
     * when two unrelated percepts happened to share two incidental
     * keywords, and again when a condition's own first word happened
     * to be an ordinary word ("the") that matches almost any percept
     * carrying natural-language content at all. Both failures came
     * from trying to do reliable matching by parsing free text instead
     * of asking for the structural fact directly. signalArtifactId and
     * signalName are checked against the real Percept object's own
     * fields — no text heuristics at all. signalValueContains stays
     * optional, explicit, and scoped for the genuinely
     * value-conditional cases (e.g. "sender is Marco specifically"),
     * rather than being derived from the whole condition sentence.
     */
    public static final class TriggerSpec {
        public final String condition;
        public final String plannedAction;
        public final String signalArtifactId;
        public final String signalName;
        public final String signalValueContains;
        public final boolean recurring;

        TriggerSpec(String condition, String plannedAction, String signalArtifactId,
                    String signalName, String signalValueContains, boolean recurring) {
            this.condition = condition;
            this.plannedAction = plannedAction;
            this.signalArtifactId = signalArtifactId;
            this.signalName = signalName;
            this.signalValueContains = signalValueContains;
            this.recurring = recurring;
        }

        static TriggerSpec parse(JSONObject t) {
            if (t == null) return null;
            String condition = t.has("condition") ? (String) t.get("condition") : null;
            String plannedAction = t.has("planned_action") ? (String) t.get("planned_action") : null;
            String signalArtifactId = t.has("signal_artifact_id") ? (String) t.get("signal_artifact_id") : null;
            String signalName = t.has("signal_name") ? (String) t.get("signal_name") : null;
            String signalValueContains = t.has("signal_value_contains") ? (String) t.get("signal_value_contains") : null;
            boolean recurring = t.has("recurring") && t.getBoolean("recurring");
            return new TriggerSpec(condition, plannedAction, signalArtifactId, signalName, signalValueContains, recurring);
        }
    }

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
        public final TriggerSpec trigger;

        AdditionalGoal(String id, String content, String status, TriggerSpec trigger) {
            this.id = id;
            this.content = content;
            this.status = status;
            this.trigger = trigger;
        }
    }

    public final String stateOfMind;
    public final ActionKind kind;
    public final JSONObject action;
    public final String goalId;
    public final String goalContent;
    public final String goalStatus;
    public final TriggerSpec trigger;
    public final java.util.List<AdditionalGoal> additionalGoals;

    private PlanResult(String stateOfMind, ActionKind kind, JSONObject action,
                        String goalId, String goalContent, String goalStatus,
                        TriggerSpec trigger, java.util.List<AdditionalGoal> additionalGoals) {
        this.stateOfMind = stateOfMind;
        this.kind = kind;
        this.action = action;
        this.goalId = goalId;
        this.goalContent = goalContent;
        this.goalStatus = goalStatus;
        this.trigger = trigger;
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
        TriggerSpec trigger = null;
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
	        	trigger = TriggerSpec.parse(w.getJSONObject("pending_trigger"));
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
                TriggerSpec t = g.has("pending_trigger") ? TriggerSpec.parse(g.getJSONObject("pending_trigger")) : null;
                additionalGoals.add(new AdditionalGoal(id, content, status, t));
            }
        }

        return new PlanResult(som, kind, parsed, goalId, goalContent, goalStatus, trigger, additionalGoals);
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
