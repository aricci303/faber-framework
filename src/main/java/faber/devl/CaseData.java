package faber.devl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The data of a project (a case study), from project.json in the project's resource folder, faber/devl/projects/<project>/. The folder has STANDARD names, so that a phase's tool needs only the folder:
 *
 *   project.json         this file: id (the folder's name), title, devlCase, previousIds, itemIdPattern, tutorTopics, texts
 *   domain-model.json    the curated domain model (CuratedModelArtifact)
 *   rules-reference.json the rule probes (RuleReference) and what the change did to the rules
 *   task-library.json    the project's task library (TaskLibraryArtifact): its tasks and domain-specific plans (the framework adds its base)
 *   README.md            what the project is, how its files are used, how to run its phases (optional; recommended)
 *
 * and the fields of project.json:
 *   id, title            the project's name (the folder's name, and what a snapshot says it was learned for) and a description of the domain
 *   devlCase             the class of the project's code (its business system and workloads): a faber.devl.DevlCase with a constructor that takes this CaseData
 *   previousIds          other ids a playbook may say it was learned for (a project that was renamed): accepted, not refused
 *   itemIdPattern        a regular expression for the id of a work item as it appears in a report ("\\bE\\d{3}\\b")
 *   tutorTopics          an ordered ARRAY of {topic, keywords}: the topics the scripted tutor recognises in a question, in the order it lists them, each with the keywords that signal it
 *   texts                the SLOTS that complete the framework's message templates (REQUIRED_SLOTS): everything the agent reads that is about this domain and not about the process
 */
public final class CaseData {

    /** The slots every case provides. Each is a short phrase; the framework's templates (devl-messages.json) own the grammar around it. */
    public static final List<String> REQUIRED_SLOTS = List.of("welcome", "organization", "jobSummary", "company", "experts", "item", "items", "anItem", "itemParticulars", "rulesNoun", "standard", "tutorExample");

    private final String id, title, base, devlCase; private final List<String> previousIds = new ArrayList<>(); private final Pattern itemIdPattern;
    private final Map<String, String> slots = new LinkedHashMap<>(); private final Map<String, String[]> tutorTopics = new LinkedHashMap<>();

    private CaseData(JSONObject j, String base) {
        this.id = j.getString("id"); this.title = j.getString("title"); this.base = base;
        this.devlCase = j.optString("devlCase", ""); if (j.optJSONArray("previousIds") != null) for (Object o : j.getJSONArray("previousIds")) previousIds.add((String) o); this.itemIdPattern = Pattern.compile(j.getString("itemIdPattern"));
        JSONObject t = j.getJSONObject("texts"); for (String k : t.keySet()) slots.put(k, t.getString(k));
        // an ARRAY, not an object: the order of the topics is part of what the agent reads (the tutor lists the topics it recognises in a question), and a JSON object has no order
        JSONArray tt = j.optJSONArray("tutorTopics") == null ? new JSONArray() : j.getJSONArray("tutorTopics");
        for (Object o : tt) { JSONObject t1 = (JSONObject) o; List<String> kw = new ArrayList<>(); for (Object k : t1.getJSONArray("keywords")) kw.add((String) k); tutorTopics.put(t1.getString("topic"), kw.toArray(new String[0])); }
    }

    /** Where the projects are in the classpath. */
    public static final String ROOT = "/faber/devl/projects/";
    /** The standard names of a project folder's files. */
    public static final String PROJECT_FILE = "project.json", MODEL_FILE = "domain-model.json", RULES_FILE = "rules-reference.json", LIBRARY_FILE = "task-library.json", README_FILE = "README.md";

    /** A project's data from JSON already in hand, with the folder its files are in (for tests that build a deliberately broken project). */
    public static CaseData of(JSONObject json, String base) { return new CaseData(json, base); }

    /** The folder of a project and the id in its project.json must be the same: null if they are, else what is wrong. */
    public static String idProblem(String folder, String id) { return folder.equals(id) ? null : "the project in " + ROOT + folder + "/" + PROJECT_FILE + " has the id '" + id + "', and the folder is called '" + folder + "': they must be the same"; }

    /** Loads a project by the name of its folder in faber/devl/projects. The folder's name is the project's id: a mismatch is an error. */
    public static CaseData load(String project) {
        String base = ROOT + project + "/";
        try (InputStream in = CaseData.class.getResourceAsStream(base + PROJECT_FILE)) {
            if (in == null) throw new IllegalArgumentException("no project '" + project + "': " + base + PROJECT_FILE + " is not in the classpath (a project is a folder in resources/faber/devl/projects with a project.json, domain-model.json, rules-reference.json and task-library.json)");
            CaseData d = new CaseData(new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)), base);
            String bad = idProblem(project, d.id); if (bad != null) throw new IllegalArgumentException(bad);
            return d;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public String id() { return id; }
    public String title() { return title; }
    /** The classpath paths of the project's files, by their standard names. */
    public String base() { return base; }
    public String modelResource() { return base + MODEL_FILE; }
    public String rulesResource() { return base + RULES_FILE; }
    public String libraryResource() { return base + LIBRARY_FILE; }
    public String readmeResource() { return base + README_FILE; }
    /** The class of the project's code, and the ids a playbook may also say it was learned for. */
    public String devlCaseClass() { return devlCase; }
    public List<String> previousIds() { return previousIds; }
    public Pattern itemIdPattern() { return itemIdPattern; }
    public Map<String, String[]> tutorTopics() { return tutorTopics; }
    public Map<String, String> slots() { return slots; }
    /** A slot; a missing one is an error naming the case and the slot, never a silent blank in what the agent reads. */
    public String slot(String name) {
        String v = slots.get(name);
        if (v == null) throw new IllegalArgumentException("case '" + id + "' does not provide the text slot '" + name + "' (needed by the framework's message templates)");
        return v;
    }
}
