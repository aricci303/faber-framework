package faber.devl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Artifact;
import faber.environment.LifePhase;
import faber.environment.NotebookArtifact;
import faber.environment.UserConsoleArtifact;
import faber.environment.Workspace;

/**
 * Developmental learning, in the regime of KNOWLEDGE-LAYERS.md — the FRAMEWORK: the process, independent of any case. A case (DevlCase: faber.devl.cases.&lt;name&gt;) supplies the domain — its
 * business system, its work, its rules, the phrases that complete the framework's texts; this class runs the phases over it.
 *
 * The domain is not the agent's to learn or to keep: a curated, versioned, read-only domain model (domain-model-01) is part of the environment in every phase. What the agent writes for its
 * future self is a PLAYBOOK — how to do the job, what it is unsure of — and the playbook is the release artifact. One run is one phase, and each phase starts from the artifact the one
 * before it produced:
 *
 *   --phase=learning     (default) one agent, three stages with the tutor fading and practice feedback going (FULL, FULL, HINTS), each closed by a debrief that asks for the playbook:
 *                        the first starts it, the later ones revise it. Ends by writing the playbook snapshot. Nothing is assessed here: what is assessed is a candidate, never a mind.
 *   --phase=assessment   --playbook=FILE   a FRESH instance made from the playbook runs A1 (a clean queue in the world of learning) and A2a (a queue in the changed world, audit flags only),
 *                        without a tutor and without help. --only=a1|a2a runs one of them. --consolidate sends the assessed instance back to learning for a moment to revise the playbook,
 *                        and writes the revision as a release candidate with its lineage, to be assessed in its turn.
 *   --phase=operation    --playbook=FILE   a fresh instance does the job itself, after the change: a queue in the changed world, some of whose items a reader of the first world decides wrongly.
 *
 *   --case=ID              the case (faber.devl.cases.*; registered in META-INF/services/faber.devl.DevlCase). May be left out when exactly one case is registered.
 *   --model=in-step|stale  (assessment and operation) how the curated model stands to the world once it has changed: published at version 2 with it (default), or left at version 1.
 *   --tutor=scripted|human   the scripted tutor points to the model and teaches no rule; a human at the console may teach what it cannot.
 *   --max-cycles=90   --log-dir=devl-logs   --watchdog-seconds=90
 *
 * The end of an assessment or operation stage: when the queue is empty and the agent has reported, plus two more cycles for what it does after reporting; if it has not reported, six
 * cycles after the queue empties. A learning stage waits three, and no more.
 */
public final class DevlRunner {

    // The Notebook of this pilot always keeps what it replaces (append_note, restore_note, a warning when a note shrinks): a playbook rewritten from memory has lost knowledge before.
    // Set here, before anything reads it: the system prompt includes the Notebook's manual and is built once.
    static { System.setProperty("faber.notebook.safe", "true"); }

    /** Test seams: a scripted LLM instead of the real one, whether an aborted run ends the JVM (it must, in real use), and a look at the case's system once it is provisioned. */
    public static faber.agent.LlmClient llmOverride = null;
    public static boolean exitOnAbort = true;
    /** The model of the agent in this run, as the run records it: the id the Anthropic client was built with, or "scripted (test)" in the suites. Read by the snapshot's options and the candidate's lineage. */
    public static String llmModel = "unknown";
    /** The profile and the prompt of the agent in this run, as the run records them (the profile's name; "core", or an overlay's id and hash). */
    public static String llmProfileName = "unknown", promptId = "unknown";
    public static java.util.function.Consumer<CaseWorld> worldHook = null;

    public static final String MODEL_ID = "domain-model-01", LIBRARY_ID = "task-library-01";
    /** The framework's base of the task library: practices for doing any work in this process; the case's file adds its tasks and domain-specific plans. */
    public static final String LIBRARY_BASE = "/faber/devl/devl-task-library-base.json";
    public static final String A1 = "a1-clean-assessment", A2A = "a2a-policy-change", OPERATION = "operation";
    public static final int LEARNING_GRACE = 3, ASSESSMENT_GRACE = 6, ASSESSMENT_SETTLE = 2;

    /**
     * The version of the curated materials: the system prompt, the manuals, the tutor's replies, the stage messages, the requests for the playbook — everything the agent is GIVEN, the
     * framework's templates and the case's slots together. Logs and snapshots record it, so that a change in what the agent was given is never mistaken for a change in what the agent DID.
     * 1 to 3 belong to the first regime (the agent learned the domain itself; see the archive);
     * 4 = the regime of KNOWLEDGE-LAYERS.md: the curated model in every phase, the playbook as the release artifact, the third phase called OPERATION, one tutor that points to the model.
     * 5 = the request for the playbook says what "nothing from the particular items" covers and what it does not (a first playbook turned "no expense numbers" into a rule for its reports).
     * The separation of the framework from the case (texts as templates and slots) changed no text the agent reads, and so left the version alone: the expense case pins the hash of every text.
     * 6 = the task library: a second curated artifact in every phase (how the work is done: tasks, and practices with a status), named in the stage 1 message, the instantiation message and the request for
     *     the playbook, which now asks the playbook to refer to it and to record where it follows or departs from it.
     * 7 = what an agent does when a source and the system disagree is no longer left to be settled silently: the mandatory plan of the task library says "do not settle it silently … whichever you believe is right"
     *     (a drift probe showed an agent that read the plan, re-read the model, decided the audit was the one at fault and reported all its decisions correct), and the model's manual says that its authority is
     *     about the agent's assumptions and not about the world, and that feedback that contradicts it is to be reported, not resolved on one's own.
     * 8 = the same two messages, put where the agent reads them at the moment they matter (a repeat of the drift probe showed that neither wording had been in front of the agent: the manual's sentence for one cycle,
     *     four cycles before the conflict; the plan's steps and reason never): mandatory plans are shown IN FULL in describe_task and in plans_for, and the model's rules_for, describe and search end with a footer
     *     that says what to do if the system differs from the model.
     * 9 = the playbook as an ARTIFACT (playbook-01, with a manual, operations read, lineage, diff, revise(text, reason), restore, an observable state, and a phase policy: revised in learning and in the consolidation, frozen while
     *     the agent is assessed or at work). Until 8 it was a note under a key in the agent's notebook; the runs showed that an instance could overwrite it mid-assessment (9 of the first 28 assessment logs). The instantiation message,
     *     the four requests for the playbook and the three debriefs that carry them say where and how it is written and that it is the only thing carried; the notebook starts empty.
     */
    public static final String MATERIALS_VERSION = "9";

    public static final class Stage {
        public final String label; public final LifePhase phase; public final List<? extends WorkItem> items; public final int world;
        public final Feedback feedback; public final TutorArtifact.Availability tutor; public final String message; public final String debrief;
        Stage(String label, LifePhase phase, List<? extends WorkItem> items, int world, Feedback fb, TutorArtifact.Availability tutor, String message, String debrief) {
            this.label = label; this.phase = phase; this.items = items; this.world = world; this.feedback = fb; this.tutor = tutor; this.message = message; this.debrief = debrief;
        }
    }

    // ---- the stages: the framework decides how each is run, the case what is in it, the messages what is said ---------------------------------------------------------------------

    public static List<Stage> learningStages(DevlCase c, Messages m) {
        return List.of(
            new Stage("stage1-introduction", LifePhase.LEARNING, c.workload(StageKey.LEARNING_1), 1, Feedback.PRACTICE, TutorArtifact.Availability.FULL, m.stage1(), m.debrief(1)),
            new Stage("stage2-policy", LifePhase.LEARNING, c.workload(StageKey.LEARNING_2), 1, Feedback.PRACTICE, TutorArtifact.Availability.FULL, m.stage2(), m.debrief(2)),
            new Stage("stage3-less-support", LifePhase.LEARNING, c.workload(StageKey.LEARNING_3), 1, Feedback.NONE, TutorArtifact.Availability.HINTS, m.stage3(), m.debrief(3)));
    }

    /** No debrief after A1: consolidating between the two assessments would be learning inside the assessment. */
    public static List<Stage> assessmentStages(DevlCase c, Messages m, Set<String> which) {
        List<Stage> out = new ArrayList<>();
        if (which.contains("a1")) out.add(new Stage(A1, LifePhase.ASSESSMENT, c.workload(StageKey.CLEAN), 1, Feedback.NONE, TutorArtifact.Availability.NONE, m.a1(), null));
        if (which.contains("a2a")) out.add(new Stage(A2A, LifePhase.ASSESSMENT, c.workload(StageKey.CHANGE), 2, Feedback.AUDIT, TutorArtifact.Availability.NONE, m.a2a(), null));
        return out;
    }

    public static Stage operationStage(DevlCase c, Messages m) {
        return new Stage(OPERATION, LifePhase.OPERATION, c.workload(StageKey.OPERATION), 2, Feedback.NONE, TutorArtifact.Availability.NONE, m.operation(), null);
    }

    // ---- options ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    public static final class Options {
        public String phase = "learning", tutor = "scripted", logDir = "devl-logs", playbook = null, model = "in-step", project = null, llmModel = null, llmProfile = null, promptOverlay = null;
        public int maxCycles = 90, watchdogSeconds = 90; public boolean consolidate = false;
        public Set<String> only = new LinkedHashSet<>(List.of("a1", "a2a"));      // an ordered set: Set.of iterates in an order that varies from one JVM run to the next
    }

    /** What earlier launches had and this one does not: said plainly, so that a launch configuration written for them fails with the way out and not with a mystery. */
    private static final Map<String, String> REMOVED = new LinkedHashMap<>();
    static {
        REMOVED.put("--phase", "each phase has its own tool: faber.devl.DevlLearningMain, faber.devl.DevlAssessmentMain, faber.devl.DevlOperationMain");
        REMOVED.put("--case", "name the project with --project=<folder> (a folder in resources/faber/devl/projects, e.g. --project=expense-approval)");
        REMOVED.put("--arm", "there is one regime now: the curated domain model is part of the environment in every phase");
        REMOVED.put("--letter", "the letter is the playbook, always written at each debrief");
        REMOVED.put("--write-primer", "the playbook is written during learning; there is no post-hoc primer");
        REMOVED.put("--from-knowledge", "use --playbook=FILE with DevlAssessmentMain or DevlOperationMain");
        REMOVED.put("--assessments", "use --only=a1|a2a with DevlAssessmentMain");
        REMOVED.put("--model-at-a2a", "use --model=in-step|stale");
        REMOVED.put("--size", "there is one size: the queues are the lean ones");
        REMOVED.put("--decided-claims", "decided claims are always retrievable");
        REMOVED.put("--policy-cue", "the model's observable version is the cue");
        REMOVED.put("--tutor-recall", "the tutor no longer teaches rules, so there is no transcript to recall");
        REMOVED.put("--tutor-max-topics", "the tutor no longer teaches rules");
        REMOVED.put("--assessment-grace", "the end rule of an assessment stage is fixed (six cycles of grace, two of settle)");
        REMOVED.put("--assessment-settle", "the end rule of an assessment stage is fixed (six cycles of grace, two of settle)");
        REMOVED.put("--consolidate=", "--consolidate is a flag: DevlAssessmentMain --consolidate");
    }

    private static int number(String opt, String v, int min, int max) {
        try { int n = Integer.parseInt(v); if (n < min || n > max) throw new IllegalArgumentException(opt + " must be between " + min + " and " + max + " (got " + n + ")"); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException(opt + " takes a whole number (got '" + v + "')"); }
    }

    /** The arguments every tool has: the project and the run's limits. Returns true if the argument was one of them. */
    public static boolean commonArgument(Options o, String a) {
        if (a.startsWith("--project=")) o.project = a.substring(10);
        else if (a.startsWith("--max-cycles=")) o.maxCycles = number("--max-cycles", a.substring(13), 1, 500);
        else if (a.startsWith("--log-dir=")) o.logDir = a.substring(10);
        else if (a.startsWith("--watchdog-seconds=")) o.watchdogSeconds = number("--watchdog-seconds", a.substring(19), 0, 3600);
        else if (a.startsWith("--llm-profile=")) { o.llmProfile = a.substring(14).trim(); if (o.llmProfile.isEmpty()) throw new IllegalArgumentException("--llm-profile takes the name of a profile of the catalog (faber/agent/llm-profiles.json)"); }
        else if (a.startsWith("--prompt-overlay=")) { o.promptOverlay = a.substring(17).trim(); if (o.promptOverlay.isEmpty()) throw new IllegalArgumentException("--prompt-overlay takes the id of a prompt overlay (faber/agent/prompt-overlays/<id>.md, or <id>.md in -Dfaber.prompt.overlays=DIR)"); }
        else if (a.startsWith("--llm-model=")) { o.llmModel = a.substring(12).trim(); if (o.llmModel.isEmpty()) throw new IllegalArgumentException("--llm-model takes the id of an Anthropic model (e.g. claude-haiku-5-5)"); }
        else return false;
        return true;
    }

    /** The error for an argument a tool does not accept: it names the way out if the argument was an earlier launch's. */
    public static IllegalArgumentException unknownArgument(String tool, String a) {
        String name = a.contains("=") ? a.substring(0, a.indexOf('=')) : a;
        String why = REMOVED.get(a.startsWith("--consolidate=") ? "--consolidate=" : name);
        return new IllegalArgumentException(why != null ? "option " + name + " is gone: " + why : tool + " does not take " + a + " (see its usage)");
    }

    public static void requireProject(Options o, String usage) { if (o.project == null || o.project.isBlank()) throw new IllegalArgumentException("--project=<folder> is required: the project this phase is run for\nusage: " + usage); }

    public static String tutorOf(String v) { v = v.toLowerCase(); if (!List.of("scripted", "human").contains(v)) throw new IllegalArgumentException("--tutor takes scripted or human (got '" + v + "')"); return v; }
    public static String modelOf(String v) { v = v.toLowerCase(); if (!List.of("in-step", "stale").contains(v)) throw new IllegalArgumentException("--model takes in-step or stale (got '" + v + "')"); return v; }

    /**
     * Runs the tool. A launch error — an argument the tool does not take, a project that does not exist, a playbook that was learned for another project — is a sentence and exit 2, not a stack trace:
     * the arguments are parsed by the tool, and the project and the playbook are opened before anything starts.
     */
    public static void launch(String tool, String usage, java.util.function.Function<String[], Options> parser, String[] args) throws Exception {
        Options o;
        try { o = parser.apply(args); }
        catch (IllegalArgumentException e) { System.err.println(tool + ": " + e.getMessage() + (e.getMessage().contains("usage:") ? "" : "\nusage: " + usage)); if (exitOnAbort) System.exit(2); throw e; }
        try { run(o); }
        catch (IllegalArgumentException e) { System.err.println(tool + ": " + e.getMessage()); if (exitOnAbort) System.exit(2); throw e; }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** The first 12 hex digits of the SHA-256 of a text: enough to tell two prompts apart in a log. */
    public static String sha12(String text) { return PlaybookSnapshot.sha256(text).substring(0, 12); }

    static Map<String, String> notebook(Workspace workspace) { return ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump(); }
    static PlaybookArtifact playbookArtifact(Workspace workspace) { return (PlaybookArtifact) workspace.instanceOf(PlaybookArtifact.ID); }
    /** The playbook as the agent holds it now: the text of its playbook artifact (a note under a key until materials 8). */
    static String playbookOf(Workspace workspace) { String t = playbookArtifact(workspace).text(); return t.isBlank() ? null : t; }

    /** The messages of a stage that came BEFORE the queue emptied (a progress note) and AFTER it (the report): only the second is the report. */
    static List<List<String>> splitMessages(UserConsoleArtifact console, int msgsBefore, StageCompletion done) {
        var all = console.sentMessages(); int from = Math.min(msgsBefore, all.size());
        int emptied = done.sentWhenEmptied() < 0 ? all.size() : Math.max(from, Math.min(done.sentWhenEmptied(), all.size()));
        return List.of(new ArrayList<>(all.subList(from, emptied)), new ArrayList<>(all.subList(emptied, all.size())));
    }

    static void decisionsSection(List<String> report, String title, List<Outcome> decisions) {
        report.add(""); report.add("=== " + title + " ===");
        var ordered = new ArrayList<>(decisions); ordered.sort(java.util.Comparator.comparing(d -> d.itemId));       // concurrent operations record decisions in the order they ran
        for (var d : ordered) report.add("  " + d.itemId + ": " + d.label + (d.correct ? "  OK" : "  WRONG (expected " + d.expected + ")"));
    }

    static void reportSection(List<String> report, List<String> mid, List<String> fin, List<Outcome> decisions, java.util.regex.Pattern itemId) {
        if (fin.isEmpty()) {
            report.add("  final report: NONE — the agent sent no message after the queue emptied" + (mid.isEmpty() ? "" : " (" + mid.size() + " earlier message(s) not counted as the final report)"));
            if (!mid.isEmpty()) { var acc = AssessmentMetrics.reportAccuracy(mid, decisions, itemId); report.add("  for information, the earlier message(s), scored as if they were the report: " + acc.line().trim()); report.addAll(acc.evidenceLines()); }
        } else { var acc = AssessmentMetrics.reportAccuracy(fin, decisions, itemId); report.add(acc.line()); report.addAll(acc.evidenceLines()); var dis = AssessmentMetrics.disclosure(fin, decisions, itemId); report.add(dis.line()); report.addAll(dis.evidenceLines()); }
    }

    // ---- the run ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    public static void run(Options o) throws Exception {
        final boolean learning = o.phase.equals("learning"), assessment = o.phase.equals("assessment"), operation = o.phase.equals("operation");
        DevlCase kase = Projects.open(o.project);
        final CaseData cd = kase.data(); final Messages msgs = new Messages(cd, MODEL_ID);
        PlaybookSnapshot playbook = null;
        PlaybookArtifact.Summary[] watchConsol = {null};                    // what the instance did to the playbook note, as the consolidation starts (null: no consolidation in this run)
        if (o.playbook != null) {
            playbook = PlaybookSnapshot.readChecked(java.nio.file.Path.of(o.playbook));
            if (!playbook.errors().isEmpty()) throw new IllegalArgumentException("cannot instantiate from " + o.playbook + ": " + String.join("; ", playbook.errors()));
            String mismatch = playbook.caseMismatch(cd.id(), cd.previousIds()); if (mismatch != null) throw new IllegalArgumentException(mismatch);
        }
        // the run writes its own complete log (console output + file), whatever launched it
        String stamp = RunLog.stamp();
        java.nio.file.Path logFile = RunLog.start(java.nio.file.Path.of(o.logDir), "devl-run-" + o.phase + "-" + stamp + ".log");
        JSONObject modelRecord = CuratedModelArtifact.sourceRecord(cd.modelResource()), libraryRecord = TaskLibraryArtifact.sourceRecord(LIBRARY_BASE, cd.libraryResource(), cd.slots());
        System.out.println("[devl] project=" + cd.id() + " phase=" + o.phase + " tutor=" + o.tutor + " max-cycles/stage=" + o.maxCycles + " materials=" + MATERIALS_VERSION + " playbook=" + (playbook == null ? "none" : playbook.id())
                + " model=" + (learning ? "n/a" : o.model) + " consolidate=" + (o.consolidate ? "on" : "off") + " assessments=" + (assessment ? String.join(",", o.only) : "n/a"));
        if (playbook != null) {
            System.out.println("[devl] INSTANCE of playbook " + playbook.provenanceLine());
            if (playbook.caseId() == null) System.out.println("[devl] playbook WARNING: the file does not say which project it was learned for (it predates the separation of projects from the framework); taken to be '" + cd.id() + "'");
            for (String w : playbook.warnings(MATERIALS_VERSION)) System.out.println("[devl] playbook WARNING: " + w);
            for (String w : playbook.environmentWarnings(modelRecord, libraryRecord)) System.out.println("[devl] playbook WARNING: " + w);
        }
        faber.agent.LlmProfile profile = faber.agent.LlmProfiles.resolve(o.llmProfile, o.llmModel, o.promptOverlay);       // an unknown profile or overlay fails here, as a launch error
        String promptText = profile.systemPrompt();
        System.out.println("[devl] system prompt: " + promptText.length() + " chars, sha256 " + sha12(promptText)
                + " (loaded now, so that a prompt problem fails here and not mid-run; the hash identifies the exact text the agent reads, manuals included)");
        System.out.println("[devl] llm profile: " + profile.name + " (" + profile.provider + ", model " + profile.modelId + "), prompt " + profile.promptId());
        System.out.println("[devl] logging to " + logFile.toAbsolutePath());
        System.out.println("[devl] llm: " + profile.describeSettings() + "   (change with -Dfaber.llm.maxTokens=N, -Dfaber.llm.maxTokensCap=N and -Dfaber.llm.thinking=disabled)");
        System.out.println("[devl] watchdog notice after " + o.watchdogSeconds + "s of silence");
        if (o.watchdogSeconds > 0) RunLog.startWatchdog(5_000, o.watchdogSeconds * 1000L);

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();
        CaseWorld world = kase.provision(workspace);                       // the case's business system: the framework drives it only through this handle
        if (worldHook != null) worldHook.accept(world);
        final String tutorExample = cd.slot("tutorExample");
        TutorArtifact.Responder responder = o.tutor.equals("human") ? TutorArtifact.humanAtConsole(new BufferedReader(new InputStreamReader(System.in))) : TutorArtifact.pointingToModel(msgs, cd.tutorTopics());
        workspace.registerType(TutorArtifact.manual(tutorExample), null);
        TutorArtifact tutor = new TutorArtifact("tutor-01", workspace, responder, tutorExample);
        workspace.provision("tutor-01", TutorArtifact.type, tutor);
        workspace.registerType(CuratedModelArtifact.manual(cd.slot("company")), null);
        CuratedModelArtifact model = CuratedModelArtifact.load(MODEL_ID, workspace, cd.modelResource());
        if (operation && o.model.equals("in-step")) model.publish("2");        // a fresh instance never saw version 1: it must not be shown a change it did not live
        workspace.provision(MODEL_ID, CuratedModelArtifact.type, model);
        workspace.registerType(TaskLibraryArtifact.manual(), null);
        TaskLibraryArtifact library = TaskLibraryArtifact.load(LIBRARY_ID, workspace, LIBRARY_BASE, cd.libraryResource(), cd.slots());
        workspace.provision(LIBRARY_ID, TaskLibraryArtifact.type, library);
        RuleReference reference = RuleReference.load(cd.rulesResource());

        SeedGoal seed = new SeedGoal("serve-user", "Serve the user's requests as they arise, remaining available and responsive by default.");
        Agent agent = llmOverride != null ? new Agent("agent-0", llmOverride, profile, seed) : new Agent("agent-0", profile, seed);
        llmModel = llmOverride != null ? "scripted (test)" : agent.llmModelId();
        llmProfileName = profile.name; promptId = profile.promptId();
        System.out.println("[devl] llm model: " + llmModel);
        agent.init(workspace);
        UserConsoleArtifact console = workspace.getUserConsole();
        agent.forceObserving(console.id());
        workspace.registerType(PlaybookArtifact.manual(), null);
        int parentRound = playbook == null || playbook.manifest.optJSONObject("lineage") == null ? 0 : playbook.manifest.getJSONObject("lineage").optInt("round", 0);
        PlaybookArtifact pbArt = new PlaybookArtifact(PlaybookArtifact.ID, workspace, playbook == null ? null : playbook.id(), parentRound, playbook == null ? "" : playbook.text());
        workspace.provision(PlaybookArtifact.ID, PlaybookArtifact.type, pbArt);
        agent.forceObserving(pbArt.id());                                         // its length, revisions and whether it is frozen are in view every cycle; the agent's notebook starts empty
        agent.forceObserving(model.id());                                         // its version and concept names are shown every cycle: the version is what an agent that looks sees change
        agent.forceObserving(library.id());                                       // the same for the task library: a trusted artifact among the agent's tools, its version in view

        List<String> report = new ArrayList<>();
        report.add("project=" + cd.id() + " phase=" + o.phase + " tutor=" + o.tutor + " llm=" + llmModel + " profile=" + llmProfileName + " prompt=" + promptId);
        if (playbook != null) {
            report.add("INSTANCE of playbook " + playbook.provenanceLine());
            if (playbook.caseId() == null) report.add("  playbook warning: the file does not say which project it was learned for; taken to be '" + cd.id() + "'");
            for (String w : playbook.warnings(MATERIALS_VERSION)) report.add("  playbook warning: " + w);
            for (String w : playbook.environmentWarnings(modelRecord, libraryRecord)) report.add("  playbook warning: " + w);
            report.add(assessment ? "  assessments run: " + o.only + (o.consolidate ? ", then a consolidation" : "") + " (learning was not repeated)" : "  operation (learning was not repeated)");
        }
        report.add(String.format("%-22s %7s %9s %8s %6s %7s", "stage", "cycles", "correct", "decided", "asks", "msgs"));

        Throwable aborted = null;
        List<Outcome> a1Decisions = null, a2aDecisions = null, opDecisions = null; Stage a2aStage = null, opStage = null;
        List<String> a2aMid = new ArrayList<>(), a2aFinal = new ArrayList<>(), opMid = new ArrayList<>(), opFinal = new ArrayList<>();
        try {
            List<Stage> stageList = new ArrayList<>(learning ? learningStages(kase, msgs) : assessment ? assessmentStages(kase, msgs, o.only) : List.of(operationStage(kase, msgs)));
            if (playbook != null) {
                Stage first = stageList.get(0);
                stageList.set(0, new Stage(first.label, first.phase, first.items, first.world, first.feedback, first.tutor, msgs.instantiation(playbook.id()) + " " + first.message, first.debrief));
            }
            if (assessment && o.consolidate)
                stageList.add(new Stage(Consolidation.LABEL, LifePhase.LEARNING, List.of(), o.only.contains("a2a") ? 2 : 1, Feedback.NONE, TutorArtifact.Availability.NONE, msgs.consolidationRequest(), null));
            int totalCycles = 0; JSONArray learningRows = new JSONArray(); String[] prevPlaybook = {null}; Map<String, String> assessedResults = new LinkedHashMap<>();
            for (int si = 0; si < stageList.size(); si++) {
                Stage st = stageList.get(si);
                workspace.setLifePhase(st.phase);
                world.loadStage(st.items, st.world, st.feedback);
                tutor.setStage(st.label, st.tutor);
                model.setStage(st.label); library.setStage(st.label);
                int msgsBefore = console.sentMessageCount();
                long stageStart = System.currentTimeMillis();
                System.out.println("[devl " + RunLog.now() + "] >>> " + st.label + " starting (life phase: " + st.phase.toJsonValue() + ", " + st.items.size() + " items)");
                if (st.label.equals(A2A)) {       // the experts' release, before the agent reads this stage's message: its first cycle shows the new version
                    String line = o.model.equals("in-step") ? "the curated domain model is published at version 2, in step with the change"
                                                            : "the curated domain model is left at version 1: the world changed and the model did not (--model=stale)";
                    if (o.model.equals("in-step")) model.publish("2");
                    System.out.println("[devl " + RunLog.now() + "] " + line); report.add("  " + line);
                }
                if (st.label.equals(OPERATION)) {
                    String line = o.model.equals("in-step") ? "the curated domain model stands at version 2 from the start, in step with the world in force"
                                                            : "the curated domain model stands at version 1 although the world in force is version 2 (--model=stale)";
                    System.out.println("[devl " + RunLog.now() + "] " + line); report.add("  " + line);
                }
                console.simulateIncomingMessage(st.message);

                final boolean consolidationStage = st.label.equals(Consolidation.LABEL), judged = st.phase != LifePhase.LEARNING;
                pbArt.setPolicy(judged && !consolidationStage, st.label);                       // the playbook is frozen while the agent is assessed or at work; open in learning and in the consolidation
                StageCompletion done = judged ? new StageCompletion(console::sentMessageCount, ASSESSMENT_GRACE, ASSESSMENT_SETTLE) : new StageCompletion(console::sentMessageCount, LEARNING_GRACE);
                world.setOnQueueEmpty(done::queueEmptied);
                final int msgsAtStart = msgsBefore; final String playbookAtStart = consolidationStage ? playbookOf(workspace) : null;
                if (consolidationStage && playbook != null) watchConsol[0] = pbArt.summary(true);
                // The consolidation stage has no queue: it ends when the agent has reported AND revised the playbook (capped at 15 cycles); an agent that reports without writing gets a few more cycles (DebriefCompletion).
                DebriefCompletion consolidationDone = new DebriefCompletion(console::sentMessageCount, msgsAtStart, pbArt::revisionsThisSession, pbArt.revisionsThisSession(), DebriefCompletion.SETTLE, 2000, 2000);
                java.util.function.BooleanSupplier base = consolidationStage ? consolidationDone : done;
                // an agent waiting for something that will not come (a reply from a user who does not answer) has failed the stage: it is not waited out to the cap. Off with a human tutor, who may well be answering.
                StallDetector stall = new StallDetector(agent::getLastCycleResult, StallDetector.LIMIT);
                boolean watchStall = !"human".equals(o.tutor) && !consolidationStage;
                java.util.function.BooleanSupplier stop = watchStall ? () -> { boolean over = base.getAsBoolean(), stuck = stall.getAsBoolean(); return over || stuck; } : base;
                int cycles = agent.doYourJobAndSelfEvaluate(consolidationStage ? Math.min(o.maxCycles, 15) : o.maxCycles, stop);
                if (consolidationStage) for (int w = 0; w < 20 && java.util.Objects.equals(playbookAtStart == null ? null : playbookAtStart.strip(), playbookOf(workspace) == null ? null : playbookOf(workspace).strip()); w++) Thread.sleep(50);
                String endedBy = consolidationStage ? (consolidationDone.reported() ? consolidationDone.endedBy() : "no report after " + cycles + " cycles") : (watchStall && stall.stalled() ? stall.endedBy() : done.endedBy());
                if (st.phase == LifePhase.ASSESSMENT) assessedResults.put(st.label, world.correctCount() + "/" + st.items.size());
                totalCycles += cycles;
                learningRows.put(new JSONObject().put("stage", st.label).put("cycles", cycles).put("correct", world.correctCount() + "/" + st.items.size()).put("tutor_asks", tutor.askCount(st.label)));
                report.add(String.format("%-22s %7d %4d/%-4d %8d %6d %7d   ended by: %s", st.label, cycles, world.correctCount(), st.items.size(), world.decidedCount(), tutor.askCount(st.label),
                        console.sentMessageCount() - msgsBefore, endedBy));

                if (st.label.equals(A1)) a1Decisions = world.outcomes();
                if (st.label.equals(A2A)) {
                    a2aStage = st; a2aDecisions = world.outcomes();                      // before a later stage reloads the system
                    var sp = splitMessages(console, msgsBefore, done); a2aMid = sp.get(0); a2aFinal = sp.get(1);
                    for (String line : Homes.lines("at the end of A2a", Homes.measure(reference, playbookOf(workspace), model.ruleTexts(), "2"))) { report.add(line); System.out.println("[devl " + RunLog.now() + "] " + line.strip()); }
                }
                if (st.label.equals(OPERATION)) {
                    opStage = st; opDecisions = world.outcomes();
                    var sp = splitMessages(console, msgsBefore, done); opMid = sp.get(0); opFinal = sp.get(1);
                }
                if (consolidationStage) {
                    Consolidation.Result cr = Consolidation.finish(playbook, playbookAtStart, playbookOf(workspace), assessedResults, assessedResults.containsKey(A2A), java.nio.file.Path.of(o.logDir), stamp,
                            logFile.getFileName().toString(), cycles, totalCycles, reference, model.ruleTexts(), kase.data().id(), watchConsol[0]);
                    report.addAll(cr.lines);
                    for (String line : cr.lines) System.out.println("[devl " + RunLog.now() + "] " + line.strip());
                }
                System.out.println("[devl " + RunLog.now() + "] <<< " + st.label + " ended after " + cycles + " cycles, " + (System.currentTimeMillis() - stageStart) / 1000 + "s: "
                        + world.correctCount() + "/" + st.items.size() + " correct; ended by: " + endedBy);

                if (st.debrief != null) {
                    int before = console.sentMessageCount(), revsBefore = pbArt.revisionsThisSession();
                    DebriefCompletion debriefDone = new DebriefCompletion(console::sentMessageCount, before, pbArt::revisionsThisSession, revsBefore, DebriefCompletion.SETTLE, 0, 2000);      // reported AND written, not merely reported
                    console.simulateIncomingMessage(st.debrief);
                    int dc = agent.doYourJobAndSelfEvaluate(14, debriefDone);
                    int revisedInDebrief = pbArt.revisionsThisSession() - revsBefore;
                    System.out.println("[devl " + RunLog.now() + "] debrief after " + st.label + " took " + dc + " cycles; ended by: " + debriefDone.endedBy());
                    String now = playbookOf(workspace);
                    for (int w = 0; w < 20 && (now == null || now.strip().equals(prevPlaybook[0] == null ? "" : prevPlaybook[0].strip())); w++) { Thread.sleep(50); now = playbookOf(workspace); }   // written in the batch that reported
                    report.add("  debrief after " + st.label + ": " + dc + " cycles; ended by: " + debriefDone.endedBy());
                    report.add("  debrief after " + st.label + ": the agent revised the playbook " + revisedInDebrief + " time(s) in this debrief" + (revisedInDebrief == 0 ? " — NOT revised: the playbook stands as it was; if the agent's last message promised a new version, it was not written" : ""));
                    for (String line : Playbook.debriefLines(st.label, now, prevPlaybook[0], reference)) { report.add(line); System.out.println("[devl " + RunLog.now() + "] " + line.strip()); }
                    for (String line : Homes.lines("after debrief " + st.label, Homes.measure(reference, now, model.ruleTexts(), "1"))) { report.add(line); System.out.println("[devl " + RunLog.now() + "] " + line.strip()); }
                    prevPlaybook[0] = now;
                    totalCycles += dc;
                    learningRows.put(new JSONObject().put("stage", "debrief:" + st.label).put("cycles", dc));
                    if (si == stageList.size() - 1) {         // the end of learning: the playbook is the release artifact; write it now
                        if (now == null || now.isBlank()) {
                            System.out.println("[devl " + RunLog.now() + "] NO PLAYBOOK at the end of learning: no snapshot written");
                            report.add("  NO PLAYBOOK at the end of learning: no snapshot written");
                        } else {
                            JSONObject opts = new JSONObject().put("phase", "learning").put("tutor", o.tutor).put("materials", MATERIALS_VERSION).put("playbook_request", Playbook.VERSION).put("llm_model", llmModel).put("llm_profile", llmProfileName).put("prompt", promptId)
                                    .put("end_rule", "learning " + LEARNING_GRACE + "; assessment " + ASSESSMENT_GRACE + "/" + ASSESSMENT_SETTLE);
                            PlaybookSnapshot snap = PlaybookSnapshot.ofLearning("PB-" + stamp, now, opts,
                                    new JSONObject().put("run", logFile.getFileName().toString()).put("snapshot_after", "debrief:" + st.label).put("cycle", totalCycles).put("reconstructed_from_log", false),
                                    learningRows, model.environmentRecord(), library.environmentRecord(), cd.id(), cd.title());
                            java.nio.file.Path snapFile = java.nio.file.Path.of(o.logDir).resolve("devl-playbook-" + stamp + ".json");
                            snap.write(snapFile);
                            System.out.println("[devl " + RunLog.now() + "] playbook snapshot written: " + snapFile.toAbsolutePath() + " (" + snap.id() + ", " + now.length() + " characters)");
                            report.add("  playbook snapshot written: " + snapFile.getFileName() + " (" + snap.id() + ")");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // A crash must not lose what the run already produced, nor leave the JVM hanging on the executor's threads.
            aborted = t;
            System.err.println("[devl " + RunLog.now() + "] RUN ABORTED: " + t);
            t.printStackTrace();
        }
        if (aborted != null) {
            report.add("");
            report.add("!!! RUN ABORTED at " + RunLog.now() + ": " + aborted);
            report.add("!!! everything below is PARTIAL — the stages listed above are the ones that completed");
        }

        if (a1Decisions != null) decisionsSection(report, "A1 decisions (clean assessment, world 1)", a1Decisions);
        boolean baselineClean = a1Decisions == null || a1Decisions.stream().allMatch(d -> d.correct);     // not run in this instance (--only=a2a): the gate does not apply
        if (a2aStage != null) {
            var dec = a2aDecisions != null ? a2aDecisions : world.outcomes();                              // a run aborted inside A2a has not captured them: use what the system holds
            decisionsSection(report, "A2a decisions (change assessment, world 2)", dec);
            report.add("");
            if (a1Decisions == null) report.add("  (A1 was not run in this instance, so the A1 gate does not apply; the analysis assumes a competent baseline)");
            report.addAll(ChangeAnalysis.lines("A2a", a2aStage.items, dec, 1, a2aStage.world, baselineClean));
            reportSection(report, a2aMid, a2aFinal, dec, cd.itemIdPattern());
        }
        if (opStage != null) {
            var dec = opDecisions != null ? opDecisions : world.outcomes();
            decisionsSection(report, "operation decisions (world 2)", dec);
            report.add("");
            report.addAll(ChangeAnalysis.lines("operation", opStage.items, dec, 1, opStage.world, true));
            reportSection(report, opMid, opFinal, dec, cd.itemIdPattern());
        }
        report.add("");
        report.add("  curated domain model " + model.modelId() + " (source " + model.resourceName() + ", sha256 " + model.sha256().substring(0, 12) + "), version in force at the end: " + model.currentVersion());
        report.add("  curated domain model consulted, by stage (operations, failed attempts included): " + model.opCountsByStage());
        var props = model.proposals();
        report.add("  proposals the agent made to the domain experts: " + props.size());
        int pi = 1; for (String[] pr : props) report.add("    " + pi++ + ". " + (pr[0].isBlank() ? "(general)" : pr[0]) + ": " + pr[1] + " — evidence: " + pr[2] + " (made at version " + pr[3] + ")");
        report.add("  task library " + library.libraryId() + " (source " + library.resourceName() + ", sha256 " + library.sha256().substring(0, 12) + "), version in force at the end: " + library.currentVersion());
        report.add("  task library consulted, by stage (operations, failed attempts included): " + library.opCountsByStage());
        var libProps = library.proposals();
        report.add("  proposals the agent made to the owners of the task library: " + libProps.size());
        int li = 1; for (String[] pr : libProps) report.add("    " + li++ + ". " + (pr[0].isBlank() ? "(general)" : pr[0]) + ": " + pr[1] + " — evidence: " + pr[2] + " (made at version " + pr[3] + ")");
        boolean worldChanged = a2aStage != null || opStage != null;
        boolean consolidated = watchConsol[0] != null;
        for (String line : Homes.lines("at the end of the run", Homes.measure(reference, playbookOf(workspace), model.ruleTexts(), worldChanged ? "2" : "1"))) report.add(line);
        if (playbook != null && !consolidated) report.addAll(pbArt.summary(false).lines(playbook.id()));      // a consolidation prints its own block, with where it started from
        report.add("");
        report.add("=== what the agent kept ===");
        report.add("[" + model.id() + "] curated domain model (Markdown export, for the domain experts; their proposals included):"); report.add(model.toMarkdown());
        report.add("[" + library.id() + "] task library (Markdown export, for its owners; their proposals included):"); report.add(library.toMarkdown());
        report.add("[" + pbArt.id() + "] playbook (" + pbArt.text().length() + " chars)"); report.add("    playbook = " + pbArt.text());
        for (Artifact a : workspace.getIAvailableArtifacts()) if (a instanceof NotebookArtifact n) { report.add("[" + a.id() + "] notebook"); n.dump().forEach((k, v) -> report.add("    " + k + " = " + v)); }
        report.add("");
        report.add("=== tutor exchanges ===");
        for (var e : tutor.exchanges()) report.add("  [" + e.stage + "/" + e.availability + "] Q: " + e.question + "\n      A: " + e.answer);

        String out = String.join("\n", report);
        System.out.println("\n\n==================== PILOT RESULT ====================\n" + out);
        java.nio.file.Path resultFile = java.nio.file.Path.of(o.logDir, "devl-result-" + o.phase + "-" + stamp + ".txt");
        java.nio.file.Files.writeString(resultFile, out);
        System.out.println("[devl] result summary written to " + resultFile.toAbsolutePath());
        workspace.shutdown();
        RunLog.stop();
        if (aborted != null && exitOnAbort) System.exit(1);
    }
}
