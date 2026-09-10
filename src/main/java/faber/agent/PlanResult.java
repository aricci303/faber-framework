package faber.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The parsed result of one Plan (micro-loop) run, now three top-level
 * blocks instead of two: <state_of_mind> (free narrative, unchanged),
 * <goals> (a structured, homogeneous array — every goal registered or
 * updated this cycle, whether or not it's the one driving this
 * cycle's action), and <action> (now genuinely minimal — kind,
 * parameters, and a bare goal_id string referencing one of this
 * cycle's own goals, or absent for a reactive action with none).
 *
 * This replaces the earlier design where a single "goal" object and a
 * separate, differently-shaped "additional_goals" array both lived
 * nested inside <action> — two shapes for what was conceptually the
 * same kind of thing. Goals are first-class here, not an attachment to
 * whichever action happens to be taken; <goals> is required every
 * cycle, even as an empty array, the same reasoning as <state_of_mind>
 * and <action> already being mandatory — a model with nothing new to
 * register says so explicitly, rather than the harness having to guess
 * whether omission means "nothing new" or "forgot."
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
     * One entry of the <goals> array — homogeneous, whether or not this
     * particular goal is the one driving this cycle's action.
     */
    public static final class GoalEntry {
        public final String id;
        public final String content;
        public final String status;
        public final TriggerSpec trigger;

        GoalEntry(String id, String content, String status, TriggerSpec trigger) {
            this.id = id;
            this.content = content;
            this.status = status;
            this.trigger = trigger;
        }

        static GoalEntry parse(JSONObject g) {
            String id = (String) g.get("id");
            String content = g.has("content") ? (String) g.get("content") : null;
            String status = g.has("status") ? (String) g.get("status") : null;
            TriggerSpec trigger = null;
            if (g.has("pending_trigger")) {
            	Object obj = g.get("pending_trigger");
            	if (obj instanceof JSONObject) {
            		trigger = TriggerSpec.parse(((JSONObject) obj));
            	} else {
            		System.err.println("ERROR: pending_trigger is not a JSONObjecy");
            	}
            }
            return new GoalEntry(id, content, status, trigger);
        }
    }
    
    public static final record ActionInfo(ActionKind kind, String goalId, JSONObject content) {}

    private final String stateOfMind;
    private final java.util.List<GoalEntry> goals;
    private final ActionInfo actInfo;
    // public final ActionKind kind;
    // public final JSONObject action;
    // public final String actionGoalId;

    private PlanResult(String stateOfMind, java.util.List<GoalEntry> goals, ActionInfo actInfo) {
                        // ActionKind kind, JSONObject action, String actionGoalId) {
        this.stateOfMind = stateOfMind;
        this.goals = goals;
        this.actInfo = actInfo;
        // this.kind = kind;
        // this.action = action;
        // this.actionGoalId = actionGoalId;
    }

    private static final Pattern SOM = Pattern.compile("<state_of_mind>(.*?)</state_of_mind>", Pattern.DOTALL);
    private static final Pattern GOALS = Pattern.compile("<goals>(.*?)</goals>", Pattern.DOTALL);
    private static final Pattern ACT = Pattern.compile("<action>(.*?)</action>", Pattern.DOTALL);

    public static PlanResult parse(String rawModelOutput) {
        Matcher somMatcher = SOM.matcher(rawModelOutput);
        Matcher goalsMatcher = GOALS.matcher(rawModelOutput);
        Matcher actMatcher = ACT.matcher(rawModelOutput);
        if (!somMatcher.find()) {
            throw new IllegalStateException("Model output missing <state_of_mind> — refusing to act without it.");
        }
        if (!goalsMatcher.find()) {
            throw new IllegalStateException("Model output missing <goals> — required every cycle, even as [].");
        }
        if (!actMatcher.find()) {
            throw new IllegalStateException("Model output missing <action>.");
        }
        String som = somMatcher.group(1).trim();

        java.util.List<GoalEntry> goals = new java.util.ArrayList<>();
        String goalsText = goalsMatcher.group(1).trim();
        JSONArray goalsArr;
        try {
            goalsArr = new JSONArray(goalsText);
        } catch (Exception ex) {
            throw new IllegalStateException("Model output malformed <goals> (not a valid JSON array): " + goalsText);
        }
        for (int i = 0; i < goalsArr.length(); i++) {
            goals.add(GoalEntry.parse(goalsArr.getJSONObject(i)));
        }

        JSONObject parsed;
        String act = actMatcher.group(1).trim();
        try {
            parsed = new JSONObject(act);
        } catch (Exception ex) {
            throw new IllegalStateException("Model output malformed <action> (not a valid JSON object)");
        }

        Object kindRaw = parsed.get("kind");
        if (kindRaw == null) {
            throw new IllegalStateException("Action JSON missing required \"kind\" field: " + parsed);
        }
        ActionKind kind = ActionKind.valueOf(kindRaw.toString());
        String actionGoalId = parsed.has("goal_id") ? parsed.getString("goal_id") : null;
        var actInfo = new ActionInfo(kind, actionGoalId, parsed);
        return new PlanResult(som, goals, actInfo);
    }

    public String getStateOfMind() {
    	return stateOfMind;
    }

    public java.util.List<GoalEntry> getGoals(){
    	return goals;
    }
    
    public ActionInfo getActInfo() {
    	return this.actInfo;
    }
    
}
