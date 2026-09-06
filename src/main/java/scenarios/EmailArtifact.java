package scenarios;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.agent.EventQueue;
import faber.environment.Artifact;
import faber.environment.Manual;
import faber.environment.Workspace;
import faber.environment.Manual.Operation;
import faber.environment.Manual.Param;
import faber.environment.Manual.Signal;

/**
 * Reproduces the relevant behavior of ARE's real EmailClientApp
 * (are/simulation/apps/email_client.py), as exercised in
 * scenario_tutorial: an incoming email is environment-originated
 * (a signal, exactly like MessagingArtifact's incoming message) and
 * forward_email is the operation the scenario's oracle actually
 * targets — matching EmailClientApp.forward_email in the real scenario.
 */
public final class EmailArtifact extends Artifact {

	public static final String type = "EmailClientApp";

    private static final class EmailRecord {
        final String sender, subject, content;
        final File attachment; 
        EmailRecord(String sender, String subject, String content, File attachment) {
            this.sender = sender; this.subject = subject; this.content = content; this.attachment = attachment;
        }
    }

    private final Map<String, EmailRecord> received = new LinkedHashMap<>();

    public EmailArtifact(String id, Workspace workspace) {
        super(id, type, workspace);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Object> doOperation(String operationName, JSONObject params) throws Exception {
        Thread.sleep(200); // simulates real work, same convention as Counter
        switch (operationName) {
            case "forward_email": {
                String emailId = params.getString("email_id");
                if (!received.containsKey(emailId)) {
                    throw new IllegalArgumentException("no such email: " + emailId);
                }
                JSONArray recipients = params.getJSONArray("recipients");
                if (recipients == null || recipients.isEmpty()) {
                    throw new IllegalArgumentException("forward_email requires at least one recipient");
                }
                return List.of(); // matches the manual's declared empty outputs
            }
            case "list_emails": {
                List<Object> summaries = new ArrayList<>();
                for (Map.Entry<String, EmailRecord> e : received.entrySet()) {
                    summaries.add(e.getKey() + ": " + e.getValue().sender + " - " + e.getValue().subject + " - attachments: " + e.getValue().attachment);
                }
                return List.of(summaries);
            }
            default:
                throw new IllegalArgumentException("unknown operation: " + operationName);
        }
    }

    /** Called by the scenario driver to simulate an email arriving — environment-originated. */
    public void simulateIncomingEmail(String emailId, String sender, String subject, String content, File attachment) {
        System.out.println("NEW INCOMING EMAIL: " + emailId + " from " + sender + " subject: " + subject);
    	int oldCount = received.size();
        received.put(emailId, new EmailRecord(sender, subject, content, attachment));
        notifyObsPropertyChanged("inbox_count", oldCount, received.size());
        if (attachment != null) {
        	emitSignal("email_received", List.of(emailId, sender, subject, content, attachment));
        } else {
        	emitSignal("email_received", List.of(emailId, sender, subject, content, "no-attachments"));
        }
    }

    public static Manual manual() {
        return new Manual(
                EmailArtifact.type,
                "send, receive, and forward email",
                null, null,
                List.of(new Manual.Param("inbox_count", "how many emails have been received so far")),
                List.of(new Manual.Signal("email_received",
                        "a new email arrived",
                        List.of(new Manual.Param("email_id", "id to use with forward_email"),
                                new Manual.Param("sender", "who sent it"),
                                new Manual.Param("subject", "email subject"),
                                new Manual.Param("content", "email content"),
                                new Manual.Param("attachments", "email attachments")))),
                List.of(
                    new Manual.Operation("forward_email(email_id, recipients)",
                        "forward a previously received email to new recipients", List.of()),
                    new Manual.Operation("list_emails()",
                        "get sender/subject summaries of every email received so far",
                        List.of(new Manual.Param("summaries", "list of \"id: sender - subject - attachments\" strings")))

                ),
                null
        );
    }
}
