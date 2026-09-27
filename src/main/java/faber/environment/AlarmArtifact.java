package faber.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

/**
 * 
 * Artifact providing functionalities to manage time 
 * - setting alarms
 * - listing fired alarms
 *  
 * By default, there is one instance of this kind of artifact in every workspace. 
 */
public final class AlarmArtifact extends Artifact {

	public static final String type = "Alarm";

    private final List<String> firedAlarmIds = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    // Set before a run starts, from the scenario's own main thread, well before
    // doYourJobAndSelfEvaluate begins — read later from the artifact's own scheduled-op thread inside
    // set_alarm. volatile makes this safe regardless, matching the explicit thread-safety discipline
    // used everywhere else in this class (AtomicInteger, AtomicLong). Default 1.0 — real time,
    // completely unaffected — for every scenario that never touches this at all.
    private volatile double timeScale = 1.0;

    /** Compresses real wall-clock time an alarm actually takes to fire, without changing anything the
     *  agent itself perceives — it only ever sees the seconds value it chose and the eventual
     *  alarm_fired signal, never real elapsed time directly, so this is invisible to it. Exists for
     *  scenarios where the agent's own "soon" interpretation could otherwise take an impractically
     *  long real time to actually resolve (see Scenario04Main and its siblings). A scenario driver
     *  computing its own event timing relative to the agent's chosen alarm duration must read
     *  timeScale() and apply the identical factor to its own computation — reading the same single
     *  value here, rather than hardcoding a second copy, is what keeps the two from silently
     *  drifting apart and landing on the wrong side of a deliberate race. Pushed too aggressively
     *  (very small values), an alarm could fire before the agent's own set_alarm invocation has even
     *  finished being processed, since the agent's own cycle-to-cycle pace is still bound by real,
     *  unaccelerated LLM latency — a moderate factor (tens, not thousands) keeps several real agent
     *  cycles comfortably inside the compressed window. */
    public void setTimeScale(double scale) { this.timeScale = scale; }
    public double timeScale() { return timeScale; }

    // Purely for external observability — e.g. a scenario driver polling for "the loop has
    // genuinely iterated N times" rather than guessing at a fixed sleep duration, the same reason
    // UserConsoleArtifact.sentMessageCount() exists. Doesn't change set_alarm's own behavior.
    public synchronized int firedAlarmsCount() { return firedAlarmIds.size(); }

    // The duration (in seconds) of the most recently set_alarm call, -1 until the first one — lets a
    // scenario driver compute an injection offset relative to what the model actually chose (its own
    // interpretation of "soon," never fixed by the scenario), rather than a hardcoded absolute number
    // that may or may not land on the intended side of a deliberate race, depending on a duration the
    // scenario has no way to predict in advance.
    private final java.util.concurrent.atomic.AtomicLong lastSetAlarmSeconds =
            new java.util.concurrent.atomic.AtomicLong(-1);
    public long lastSetAlarmSeconds() { return lastSetAlarmSeconds.get(); }

    public AlarmArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "set_alarm": {
                long seconds = (params.getNumber("seconds")).longValue();
                lastSetAlarmSeconds.set(seconds);
                String alarmId = "alarm-" + idCounter.incrementAndGet();
                // Scheduled independently of this operation's own completion — set_alarm confirms
                // acceptance quickly; firing happens later, on its own schedule, exactly like any
                // other environment-originated event this harness models.
                workspace.scheduleOpExecution(() -> {
                    try {
                        Thread.sleep((long) (seconds * 1000 * timeScale));
                        fireAlarm(alarmId);
                    } catch (InterruptedException ignored) {
                    }
                });
                return List.of(alarmId);
            }
            case "list_fired_alarms": {
                return List.of(List.copyOf(firedAlarmIds));
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    private synchronized void fireAlarm(String alarmId) {
        int oldCount = firedAlarmIds.size();
        firedAlarmIds.add(alarmId);
        notifyObsPropertyChanged("fired_alarms", oldCount, firedAlarmIds.size());
        emitSignal("alarm_fired", List.of(alarmId));
    }

    public static Manual manual() {
        return new Manual(
                AlarmArtifact.type,
                "perceive the passage of time by setting alarms that fire later",
                null, null,
                List.of(new Manual.Param("fired_alarms", "how many alarms have fired so far")),
                List.of(new Manual.Signal("alarm_fired",
                        "an alarm you set has fired",
                        List.of(new Manual.Param("alarm_id", "id returned by the set_alarm that scheduled it")))),
                List.of(
                        new Manual.Operation("set_alarm(seconds)",
                                "schedule an alarm to fire after the given number of seconds",
                                List.of(new Manual.Param("alarm_id", "id of the scheduled alarm"))),
                        new Manual.Operation("list_fired_alarms()",
                                "get the ids of every alarm that has fired so far",
                                List.of(new Manual.Param("alarm_ids", "list of fired alarm ids")))
                ),
                "invoking set_alarm does not, by itself, make you an observer of this artifact — "
                + "you still receive that call's own operation_completed regardless, but alarm_fired "
                + "is a separate, later signal that only reaches you if you are focused on alarm-01 "
                + "when it fires; if you intend to react to the alarm firing, focus alarm-01 "
                + "explicitly, the same as for any other artifact's signal"
        );
    }
}
