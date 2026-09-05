package faber.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

/**
 * Models time perception as an ordinary artifact, rather than a
 * harness-level ad hoc mechanism (a bespoke wait_timeout percept). The
 * reasoning: there is no unmediated perception of time for any situated
 * cognitive system — biological or artificial — only perception
 * mediated by something (a clock, a heartbeat, an artifact). set_alarm
 * schedules asynchronously and returns immediately, confirming
 * acceptance via the ordinary operation_started/operation_completed
 * sequence; the actual firing happens later, on its own schedule.
 *
 * Firing follows the same dual pattern already used for
 * EmailArtifact.inbox_count: fired_alarms is a belief-mapped observable
 * property (the true current count, visible immediately to an agent
 * that starts observing late, exactly like an unread inbox) and
 * alarm_fired is a signal (for immediate reaction if actively
 * watching at the moment of firing). No always-observed exception is
 * needed — a missed firing is recoverable the moment the agent next
 * focuses on this artifact, the same way a missed email is.
 */
public final class AlarmArtifact extends Artifact {

    private final List<String> firedAlarmIds = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public AlarmArtifact(String id, Workspace workspace) {
        super(id, workspace);
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
                "Alarm",
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
