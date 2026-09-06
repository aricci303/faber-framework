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

    public AlarmArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        switch (operationName) {
            case "set_alarm": {
                long seconds = (params.getNumber("seconds")).longValue();
                String alarmId = "alarm-" + idCounter.incrementAndGet();
                // Scheduled independently of this operation's own completion — set_alarm confirms
                // acceptance quickly; firing happens later, on its own schedule, exactly like any
                // other environment-originated event this harness models.
                workspace.scheduleOpExecution(() -> {
                    try {
                        Thread.sleep(seconds * 1000);
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
                null
        );
    }
}
