package faber.devl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

/**
 * Everything the framework says to the agent in its own words — the stage messages, the debriefs, the request for the playbook, the instantiation message, the scripted tutor's replies —
 * as TEMPLATES (devl-messages.json) that name the slots a case fills (CaseData.REQUIRED_SLOTS). What is the same in every case lives in the templates; what is about the domain lives in the
 * case's data. A slot a case does not provide is an error, never a blank; a template that names a slot nobody provides is found by the contract check, not by the agent.
 *
 * Besides the case's slots, a template may use {modelId} (the id of the domain model artifact), {libraryId} (the id of the task library artifact), {playbookArtifactId} (the id of the playbook artifact), {id} (a playbook's id, where one is given) and the key of another template
 * ({playbookContent}, {debrief}…), which is rendered in its place.
 */
public final class Messages {

    private static final Map<String, String> TEMPLATES = load();
    private static final Pattern SLOT = Pattern.compile("\\{([A-Za-z0-9]+)\\}");

    private final CaseData data; private final String modelId;

    public Messages(CaseData data, String modelId) { this.data = data; this.modelId = modelId; }

    private static Map<String, String> load() {
        try (InputStream in = Messages.class.getResourceAsStream("/faber/devl/devl-messages.json")) {
            if (in == null) throw new IllegalStateException("resource not found: faber/devl/devl-messages.json");
            JSONObject j = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)); Map<String, String> m = new LinkedHashMap<>();
            for (String k : j.keySet()) m.put(k, j.getString(k));
            return m;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    /** The keys of all templates, and the template text — for the contract check. */
    public static Map<String, String> templates() { return java.util.Collections.unmodifiableMap(TEMPLATES); }

    /** Every {name} a template uses, directly. */
    public static java.util.Set<String> placeholders(String template) { java.util.Set<String> s = new java.util.TreeSet<>(); Matcher m = SLOT.matcher(template); while (m.find()) s.add(m.group(1)); return s; }

    /** Renders a template: case slots, {modelId}, the extra values given, and other templates, recursively. */
    public String render(String key, Map<String, String> extra) {
        String t = TEMPLATES.get(key);
        if (t == null) throw new IllegalArgumentException("no message template '" + key + "'");
        Matcher m = SLOT.matcher(t); StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String n = m.group(1), v;
            if (extra != null && extra.containsKey(n)) v = extra.get(n);
            else if (n.equals("modelId")) v = modelId;
            else if (n.equals("libraryId")) v = DevlRunner.LIBRARY_ID;
            else if (n.equals("playbookArtifactId")) v = PlaybookArtifact.ID;
            else if (data.slots().containsKey(n)) v = data.slot(n);
            else if (TEMPLATES.containsKey(n)) v = render(n, extra);
            else throw new IllegalArgumentException("template '" + key + "' needs '" + n + "', which neither the case '" + data.id() + "' nor the framework provides");
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public String render(String key) { return render(key, null); }

    // ---- the texts, by name ---------------------------------------------------------------------------------------------------------------------------------------------
    public String stage1() { return render("stage1"); }
    public String stage2() { return render("stage2"); }
    public String stage3() { return render("stage3"); }
    public String a1() { return render("a1"); }
    public String a2a() { return render("a2a"); }
    public String operation() { return render("operation"); }
    public String debrief() { return render("debrief"); }
    /** The debrief that closes learning stage n (1 to 3). */
    public String debrief(int n) { return render("debrief" + n); }
    public String instantiation(String playbookId) { return render("instantiation", Map.of("id", playbookId)); }
    public String playbookContent() { return render("playbookContent"); }
    public String learningRequest(boolean first) { return render(first ? "playbookFirst" : "playbookLater"); }
    public String consolidationRequest() { return render("playbookConsolidation"); }
    public String tutorPointer(String topics, boolean hints) {
        String prefix = hints ? render("tutorHintsPrefix") : "";
        return prefix + (topics == null ? render("tutorPointerNothing") : render("tutorPointerTopics", Map.of("topics", topics)));
    }
}
