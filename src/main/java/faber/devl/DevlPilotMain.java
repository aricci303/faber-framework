package faber.devl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

import faber.agent.Agent;
import faber.agent.SeedGoal;
import faber.environment.Artifact;
import faber.environment.LifePhase;
import faber.environment.NotebookArtifact;
import faber.environment.UserConsoleArtifact;
import faber.environment.Workspace;

/**
 * Developmental-learning pilot: ONE agent, ONE workspace, a sequence of stages. The agent instance
 * and the workspace persist across stages — what the agent knows at the start of stage N is exactly
 * what it kept (notes, stores it built, its own plan/goal ledger) from stages before. Between stages
 * a debrief message asks it to consolidate. Learning (stages 1-3) is followed by ASSESSMENT, in two
 * steps, without a tutor and without practice feedback: A1, a clean queue under the learned policy
 * (baseline competence, false alarms, cost of a routine run), then A2a, a queue under a changed policy
 * (does knowledge from the learning phase survive, and does the agent notice when it has gone stale?). The
 * assessments are simulated, and the agent is told so truthfully; the DEPLOYMENT phase exists in the kernel
 * for real use and is not used here.
 *
 *   usage: DevlPilotMain [--arm=notebook|stores|model-empty|model-seeded] [--tutor=scripted|human] [--max-cycles=90] [--log-dir=devl-logs] [--tutor-max-topics=0] [--watchdog-seconds=90] [--size=lean|full] [--decided-claims=hidden|visible] [--policy-cue=none|version] [--tutor-recall=off|on] [--from-knowledge=FILE [--assessments=a1,a2a | --write-primer]] [--letter=off|on] [--consolidate=off|on] [--assessment-grace=3] [--assessment-settle=0]
 *     notebook     : only the standard Notebook is available for memory (level 1: conventions over what exists)
 *     stores       : KeyValueStore and LogStore are also creatable (level 2: several typed stores of its own)
 *     model-empty  : a DomainModel artifact (domain-model-01), empty — stage 1's goal is to construct the domain model
 *     model-seeded : the same artifact seeded by the designer with the domain's vocabulary and groundings (no rules);
 *                    stage 1's goal is to make that model the agent's own: check, correct, complete
 *
 *   --size=lean (default): 28 claims across the four stages. --size=full: the 44-claim version used by the first complete run.
 */
public final class DevlPilotMain {

    /** Test seams: a scripted LLM instead of the real one, and whether an aborted run ends the JVM (it must, in real use). */
    static faber.agent.LlmClient llmOverride = null;
    static boolean exitOnAbort = true;
    static java.util.function.Consumer<ExpenseSystemArtifact> expensesHook = null;   // lets a scripted agent see the expense system

    static final class Stage {
        final String label; final LifePhase phase; final List<ExpenseCase> cases; final ExpensePolicy policy;
        final ExpenseSystemArtifact.Feedback feedback; final TutorArtifact.Availability tutor; final String message; final String debrief;
        Stage(String label, LifePhase phase, List<ExpenseCase> cases, ExpensePolicy policy, ExpenseSystemArtifact.Feedback fb,
              TutorArtifact.Availability tutor, String message, String debrief) {
            this.label = label; this.phase = phase; this.cases = cases; this.policy = policy; this.feedback = fb;
            this.tutor = tutor; this.message = message; this.debrief = debrief;
        }
    }

    static final String DEBRIEF = "Before the next stage, take a moment to consolidate: review what you did and what you learned about "
            + "the job and the system, and make sure whatever is worth keeping is stored where you will be able to find it later, when I "
            + "may not be around to help. Reorganize what you have if that would help. When you are done, tell me what you stored and where.";

    static final String WHAT_I_CAN_TELL_YOU = "What I can tell you for now: routine small meals (up to 40 EUR) with a receipt are approved, and anything "
            + "without a receipt is rejected. If an expense is anything else, ask the tutor. When the queue is empty, tell me you are done and how it went.";

    /**
     * The version of the curated materials: the manuals, the tutor's replies, the policy texts, the stage messages, the seed model. Logs record it, so that a
     * change in what the agent was GIVEN is never mistaken for a change in what the agent DID. 1 = everything up to the sixth complete run; 2 = the first
     * materials review (see MATERIALS-REVIEW.md): one vocabulary, teaching errors, per-operation notes on reversibility and feedback, a tutor that states its
     * mode, the order of the checks, verified worked examples; 3 = the first phase renamed from "development" to "learning" everywhere the agent reads
     * (the system prompt's phase paragraphs, the WORKSPACE line "life phase: learning", the instantiation message and the primer instruction), and the system prompt
     * stripped of a stale duplicate of its WORKSPACE-description section (an older two-phase "life phase" bullet and a second purpose bullet, present in some copies of
     * the prompt, which told the agent "your life has two coarse phases" next to the three-phase text). Nothing else changed.
     */
    static final String MATERIALS_VERSION = "3";

    /**
     * The instantiation protocol, version 1: what an instance made from a knowledge snapshot is told before its first assessment. FACTS ONLY — who it is, what it
     * carries, what it does not — and nothing about what to do with them or about what may have changed: the prompt already says how to treat what one holds, and
     * the challenge of A2a is not to be documented away (MATERIALS-REVIEW.md, the three kinds of content). A test holds the text to that.
     */
    static String instantiationMessage(KnowledgeSnapshot snap) {
        return "You have been instantiated, not continued. You learned your job at Halden & Co. — employee expense approvals in the expense system — and what you "
             + "learned then is in your notebook (notebook-01), loaded as it stood when your learning ended (knowledge snapshot " + snap.id() + "). "
             + "You carry no other memory of that time.";
    }

    static final String A1 = "a1-clean-assessment", A2A = "a2a-policy-change";

    static final String A1_MESSAGE = "This is an assessment: a simulated queue of expenses, nothing real. Work exactly as you would if it were real. "
            + "I am not available to answer questions. Tell me when you are done and anything I should know.";
    static final String A2A_MESSAGE = "This is the second assessment: another simulated queue of expenses, nothing real. Work exactly as you would if it "
            + "were real. I am not available to answer questions. Tell me when you are done and anything I should know.";

    static String stage1Message(String arm) {
        String welcome = "Welcome to Halden & Co. Starting today you handle our employee expense approvals, working in the expense system. ";
        switch (arm) {
            case "model-empty":
                return welcome + "This first stage is a guided introduction, and its goal is to build your understanding of the domain: work out what "
                    + "expense approval involves here — the concepts, how they relate, and how they map onto what you can do in the expense system — "
                    + "and record it in the domain model (domain-model-01), which starts out empty. Then process everything currently pending. "
                    + WHAT_I_CAN_TELL_YOU;
            case "model-seeded":
                return welcome + "This first stage is a guided introduction, and its goal is to make the domain your own. The finance team has prepared a "
                    + "first version of the domain model for you (domain-model-01): the main concepts and where they live in the expense system. "
                    + "Check it against what you find in the system, correct and complete it — the approval rules are not in it yet. Then process "
                    + "everything currently pending. " + WHAT_I_CAN_TELL_YOU;
            default:
                return welcome + "This first stage is a guided introduction: first learn how the system works, then process everything currently pending. "
                    + WHAT_I_CAN_TELL_YOU;
        }
    }

    static List<Stage> stages(String arm) { return stages(arm, ExpenseCases.Size.LEAN); }

    static List<Stage> stages(String arm, ExpenseCases.Size size) { return stages(arm, size, false); }

    /** letter: add to each debrief the request to start (first debrief) or revise (later ones) the letter to the agent's future self; the tutor is still full at the first two. */
    static List<Stage> stages(String arm, ExpenseCases.Size size, boolean letter) {
        boolean model = arm.startsWith("model-");
        String l1 = letter ? " " + PrimerPilot.learningRequest(true) : "", l2 = letter ? " " + PrimerPilot.learningRequest(false) : "";
        String stage2Extra = model ? " Record what you learn about the rules in the domain model — where each rule came from and how sure you are of it." : "";
        String debriefExtra = model ? " Make sure the domain model reflects what you now know: concepts, rules, and how each rule was learned." : "";
        return List.of(
            new Stage("stage1-introduction", LifePhase.LEARNING, ExpenseCases.stage1(size), ExpensePolicy.v1(),
                ExpenseSystemArtifact.Feedback.PRACTICE, TutorArtifact.Availability.FULL,
                stage1Message(arm),
                "Stage 1 is complete. " + DEBRIEF + debriefExtra + l1),
            new Stage("stage2-policy", LifePhase.LEARNING, ExpenseCases.stage2(size), ExpensePolicy.v1(),
                ExpenseSystemArtifact.Feedback.PRACTICE, TutorArtifact.Availability.FULL,
                "Stage 2: the full queue. You will now see every kind of expense we get, and nobody has handed you the company's approval "
                + "policy, so learn it. The tutor can explain our rules, and the system tells you whether each decision matched policy. "
                + "Process the whole queue and tell me when you are done." + stage2Extra,
                "Stage 2 is complete. " + DEBRIEF + debriefExtra + l2),
            new Stage("stage3-less-support", LifePhase.LEARNING, ExpenseCases.stage3(size), ExpensePolicy.v1(),
                ExpenseSystemArtifact.Feedback.NONE, TutorArtifact.Availability.HINTS,
                "Stage 3: another queue, with less support. The tutor will only give hints now, and the system no longer tells you whether a "
                + "decision was right. Process everything pending and tell me when you are done.",
                "Stage 3 is complete. " + DEBRIEF + debriefExtra + l2 + " After this your designers will assess what you can do, on new cases and without help."),
            // No debrief after A1: consolidating between the two assessments would be learning inside the assessment.
            new Stage(A1, LifePhase.ASSESSMENT, ExpenseCases.cleanAssessment(size), ExpensePolicy.v1(),
                ExpenseSystemArtifact.Feedback.NONE, TutorArtifact.Availability.NONE, A1_MESSAGE, null),
            new Stage(A2A, LifePhase.ASSESSMENT, ExpenseCases.policyChange(size), ExpensePolicy.v2(),
                ExpenseSystemArtifact.Feedback.AUDIT, TutorArtifact.Availability.NONE, A2A_MESSAGE, null));
    }

    /** Everything the agent kept, however it kept it, as concepts the reference model can assess: domain model, notebook, stores, logs. */
    static List<DomainModelArtifact.Concept> keptEverything(Workspace workspace, DomainModelArtifact model) {
        List<DomainModelArtifact.Concept> kept = new ArrayList<>();
        if (model != null) kept.addAll(model.snapshot());
        for (Artifact a : workspace.getIAvailableArtifacts()) {
            if (a instanceof NotebookArtifact n) kept.addAll(AssessmentMetrics.asConcepts(n.dump()));
            if (a instanceof KeyValueStoreArtifact k) kept.addAll(AssessmentMetrics.asConcepts(k.dump()));
            if (a instanceof LogStoreArtifact l) { var cs = new java.util.LinkedHashMap<String, String>(); int i = 0; for (String e : l.dump()) cs.put(a.id() + "#" + (++i), e); kept.addAll(AssessmentMetrics.asConcepts(cs)); }
        }
        return kept;
    }

    /**
     * The primer stage is over when the letter exists AND the agent has reported. Both are operations that run asynchronously: evaluated the instant a cycle returns they may still be in
     * flight, which would start one more (paid) cycle for nothing. So wait for the outcome — up to 2 s, against tens of seconds for a model call — before saying "not yet".
     */
    static boolean primerReady(UserConsoleArtifact console, Workspace workspace, int msgsAtStart) {
        try {
            for (int i = 0; i < 40; i++) {
                if (console.sentMessageCount() > msgsAtStart && hasNote(workspace, PrimerPilot.KEY)) return true;
                Thread.sleep(50);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return false;
    }

    /** The first 12 hex digits of the SHA-256 of a text: enough to tell two prompts apart in a log. */
    static String sha12(String text) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
            return sb.substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static boolean hasNote(Workspace workspace, String key) { return ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump().containsKey(key); }

    public static void main(String[] args) throws Exception {
        String arm = "notebook", tutorMode = "scripted", logDir = "devl-logs"; int maxCycles = 90, tutorMaxTopics = 0, watchdogSeconds = 90; ExpenseCases.Size size = ExpenseCases.Size.LEAN; boolean decidedVisible = false, policyCue = false, tutorRecall = false; String fromKnowledge = null, assessmentsOpt = null; boolean writePrimer = false, letterOn = false, consolidateOn = false; int assessGrace = 3, assessSettle = 0;
        for (String a : args) {
            if (a.startsWith("--arm=")) arm = a.substring(6);
            else if (a.startsWith("--tutor=")) tutorMode = a.substring(8);
            else if (a.startsWith("--max-cycles=")) maxCycles = Integer.parseInt(a.substring(13));
            else if (a.startsWith("--log-dir=")) logDir = a.substring(10);
            else if (a.startsWith("--tutor-max-topics=")) tutorMaxTopics = Integer.parseInt(a.substring(19));
            else if (a.startsWith("--watchdog-seconds=")) watchdogSeconds = Integer.parseInt(a.substring(19));
            else if (a.startsWith("--size=")) size = ExpenseCases.Size.valueOf(a.substring(7).toUpperCase());
            else if (a.startsWith("--decided-claims=")) decidedVisible = a.substring(17).equalsIgnoreCase("visible");
            else if (a.startsWith("--policy-cue=")) policyCue = a.substring(13).equalsIgnoreCase("version");
            else if (a.startsWith("--tutor-recall=")) tutorRecall = a.substring(15).equalsIgnoreCase("on");
            else if (a.startsWith("--from-knowledge=")) fromKnowledge = a.substring(17);
            else if (a.startsWith("--assessments=")) assessmentsOpt = a.substring(14);
            else if (a.equals("--write-primer")) writePrimer = true;
            else if (a.startsWith("--assessment-grace=")) {
                try { assessGrace = Integer.parseInt(a.substring(19)); } catch (NumberFormatException e) { throw new IllegalArgumentException("--assessment-grace takes a whole number of cycles (got '" + a.substring(19) + "')"); }
                if (assessGrace < 1 || assessGrace > 20) throw new IllegalArgumentException("--assessment-grace must be between 1 and 20 cycles (got " + assessGrace + ")");
            }
            else if (a.startsWith("--assessment-settle=")) {
                try { assessSettle = Integer.parseInt(a.substring(20)); } catch (NumberFormatException e) { throw new IllegalArgumentException("--assessment-settle takes a whole number of cycles (got '" + a.substring(20) + "')"); }
                if (assessSettle < 0 || assessSettle > 10) throw new IllegalArgumentException("--assessment-settle must be between 0 and 10 cycles (got " + assessSettle + ")");
            }
            else if (a.startsWith("--consolidate=")) {
                String v = a.substring(14).toLowerCase();
                if (!v.equals("on") && !v.equals("off")) throw new IllegalArgumentException("--consolidate takes on or off (got '" + a.substring(14) + "')");
                consolidateOn = v.equals("on");
            }
            else if (a.startsWith("--letter=")) {
                String v = a.substring(9).toLowerCase();
                if (!v.equals("on") && !v.equals("off")) throw new IllegalArgumentException("--letter takes on or off (got '" + a.substring(9) + "')");
                letterOn = v.equals("on");
            }
        }
        KnowledgeSnapshot snapshot = null; java.util.Set<String> assessmentsToRun = new java.util.LinkedHashSet<>(List.of("a1", "a2a"));   // an ordered set: Set.of iterates in an order that varies from one JVM run to the next
        if (assessmentsOpt != null && fromKnowledge == null) throw new IllegalArgumentException("--assessments is only meaningful with --from-knowledge");
        if (letterOn && fromKnowledge != null) throw new IllegalArgumentException("--letter=on is for a run WITH learning (the letter is written at each debrief); an instance of a snapshot has no debriefs");
        if (consolidateOn && writePrimer) throw new IllegalArgumentException("--consolidate=on and --write-primer exclude each other: the primer pilot runs no assessment, and consolidation follows one");
        if (writePrimer && fromKnowledge == null) throw new IllegalArgumentException("--write-primer needs --from-knowledge: the letter is written by an instance of a snapshot");
        if (writePrimer && assessmentsOpt != null) throw new IllegalArgumentException("--write-primer and --assessments exclude each other: the primer pilot runs no assessment (run the assessments afterwards, from the variant snapshots it writes)");
        if (fromKnowledge != null) {
            if (!arm.equals("notebook")) throw new IllegalArgumentException("--from-knowledge restores a Notebook: it needs --arm=notebook (got " + arm + ")");
            snapshot = KnowledgeSnapshot.read(java.nio.file.Path.of(fromKnowledge));
            if (!snapshot.errors().isEmpty()) throw new IllegalArgumentException("cannot instantiate from " + fromKnowledge + ": " + String.join("; ", snapshot.errors()));
            if (assessmentsOpt != null) {
                assessmentsToRun = new java.util.LinkedHashSet<>();
                for (String t : assessmentsOpt.toLowerCase().split("[,\\s]+")) {
                    if (t.isBlank()) continue;
                    if (!t.equals("a1") && !t.equals("a2a")) throw new IllegalArgumentException("unknown assessment '" + t + "' in --assessments (known: a1, a2a)");
                    assessmentsToRun.add(t);
                }
                if (assessmentsToRun.isEmpty()) throw new IllegalArgumentException("--assessments names no assessment (known: a1, a2a)");
            }
        }
        // the run writes its own complete log (console output + file), whatever launched it
        String stamp = RunLog.stamp();
        java.nio.file.Path logFile = RunLog.start(java.nio.file.Path.of(logDir), "devl-run-" + arm + "-" + stamp + ".log");
        System.out.println("[devl] arm=" + arm + " tutor=" + tutorMode + " max-cycles/stage=" + maxCycles + " size=" + size.name().toLowerCase() + " decided-claims=" + (decidedVisible ? "visible" : "hidden") + " policy-cue=" + (policyCue ? "version" : "none") + " tutor-recall=" + (tutorRecall ? "on" : "off")
                + " notebook-safe-mode=" + (NotebookArtifact.safeMode() ? "on" : "off") + " prompt-additions=" + faber.agent.PromptAdditions.specInForce() + " materials=" + MATERIALS_VERSION + " snapshot=" + (snapshot == null ? "none" : snapshot.id()) + " letter=" + (letterOn ? "on" : "off") + " consolidate=" + (consolidateOn ? "on" : "off")
                + " assessment-grace=" + assessGrace + " assessment-settle=" + assessSettle);
        if (snapshot != null) {
            System.out.println("[devl] INSTANCE of knowledge snapshot " + snapshot.provenanceLine());
            for (String w : snapshot.warnings(MATERIALS_VERSION, NotebookArtifact.safeMode())) System.out.println("[devl] snapshot WARNING: " + w);
            System.out.println(writePrimer ? "[devl] PRIMER PILOT (" + PrimerPilot.VERSION + "): this instance writes a letter to its future self; no assessment is run"
                                           : "[devl] learning is skipped; assessments to run: " + assessmentsToRun);
        }
        System.out.println("[devl] system prompt: " + faber.agent.SystemPrompt.systemPrompt.length() + " chars, sha256 " + sha12(faber.agent.SystemPrompt.systemPrompt)
                + " (loaded now, so that a prompt problem fails here and not mid-run; the hash identifies the exact text the agent reads, manuals and additions included)");
        System.out.println("[devl] logging to " + logFile.toAbsolutePath());
        System.out.println("[devl] llm: " + faber.agent.LlmClient.AnthropicLlmClient.settingsSummary() + "   (change with -Dfaber.llm.maxTokens=N and -Dfaber.llm.thinking=disabled)");
        System.out.println("[devl] tutor max topics per answer: " + (tutorMaxTopics == 0 ? "unlimited" : tutorMaxTopics) + "; watchdog notice after " + watchdogSeconds + "s of silence");
        if (watchdogSeconds > 0) RunLog.startWatchdog(5_000, watchdogSeconds * 1000L);

        Workspace workspace = new Workspace();
        workspace.initDefaultArtifacts();

        workspace.registerType(ExpenseSystemArtifact.manual(decidedVisible, policyCue), null);
        ExpenseSystemArtifact expenses = new ExpenseSystemArtifact("expense-system-01", workspace);
        if (expensesHook != null) expensesHook.accept(expenses);
        expenses.setDecidedVisible(decidedVisible);
        expenses.setPolicyCue(policyCue);
        workspace.provision("expense-system-01", ExpenseSystemArtifact.type, expenses);

        TutorArtifact.Responder responder = tutorMode.equals("human")
                ? TutorArtifact.humanAtConsole(new BufferedReader(new InputStreamReader(System.in)))
                : TutorArtifact.scriptedExpenseTutor(ExpensePolicy.v1(), tutorMaxTopics);
        workspace.registerType(TutorArtifact.manual(tutorRecall), null);
        TutorArtifact tutor = new TutorArtifact("tutor-01", workspace, responder);
        tutor.setRecallEnabled(tutorRecall);
        workspace.provision("tutor-01", TutorArtifact.type, tutor);

        if (!List.of("notebook", "stores", "model-empty", "model-seeded").contains(arm))
            throw new IllegalArgumentException("unknown arm: " + arm);
        DomainModelArtifact model = null;
        if (arm.startsWith("model-")) {
            workspace.registerType(DomainModelArtifact.manual(), null);
            model = new DomainModelArtifact("domain-model-01", workspace);
            if (arm.equals("model-seeded")) model.seed(DomainModelArtifact.loadJsonResource("expense-domain-seed.json"));
            workspace.provision("domain-model-01", DomainModelArtifact.type, model);
        }
        DomainReference reference = DomainReference.expense();   // used in every arm: the per-stage retention line, and the model arms' assessment
        if (arm.equals("stores")) {
            workspace.registerType(KeyValueStoreArtifact.manual(), (id, ctor) -> new KeyValueStoreArtifact(id, workspace));
            workspace.registerType(LogStoreArtifact.manual(), (id, ctor) -> new LogStoreArtifact(id, workspace));
        }

        SeedGoal seed = new SeedGoal("serve-user", "Serve the user's requests as they arise, remaining available and responsive by default.");
        Agent agent = llmOverride != null ? new Agent("agent-0", llmOverride, seed) : new Agent("agent-0", seed);
        agent.init(workspace);
        UserConsoleArtifact console = workspace.getUserConsole();
        agent.forceObserving(console.id());
        if (snapshot != null) {
            NotebookArtifact restored = (NotebookArtifact) workspace.instanceOf("notebook-01");
            restored.restoreNotes(snapshot.notes);                 // before observing it, so that the agent sees a notebook that was always like this
            agent.forceObserving(restored.id());                   // the agent that had learned observed its notebook; its note keys are its cue for what it holds
        }
        if (model != null) agent.forceObserving(model.id());   // its concept names are then shown every cycle, as a cue

        List<String> report = new ArrayList<>();
        report.add("arm=" + arm + " tutor=" + tutorMode);
        if (snapshot != null) {
            report.add("INSTANCE of knowledge snapshot " + snapshot.provenanceLine());
            for (String w : snapshot.warnings(MATERIALS_VERSION, NotebookArtifact.safeMode())) report.add("  snapshot warning: " + w);
            report.add(writePrimer ? "  PRIMER PILOT (" + PrimerPilot.VERSION + "): this instance wrote a letter to its future self; no assessment was run"
                                   : "  assessments run: " + assessmentsToRun + " (learning was not repeated)");
        }
        report.add(String.format("%-22s %7s %9s %8s %6s %7s", "stage", "cycles", "correct", "decided", "asks", "msgs"));

        Throwable aborted = null;
        Stage lastLoaded = null;
        Stage a2aStage = null; List<ExpenseSystemArtifact.Decision> a2aDecisions = null; List<DomainModelArtifact.Concept> a2aKept = null;     // what the A2a analysis needs, captured at the end of that stage
        List<ExpenseSystemArtifact.Decision> a1Decisions = null; List<ExpenseCase> a1Cases = null;
        List<String> a2aFinalMessages = new ArrayList<>(), a2aMidMessages = new ArrayList<>();
        try {
            List<Stage> stageList = stages(arm, size, letterOn);
            if (snapshot != null) {
                List<Stage> chosen = new ArrayList<>();
                if (writePrimer) chosen.add(new Stage(PrimerPilot.LABEL, LifePhase.LEARNING, List.of(), ExpensePolicy.v1(), ExpenseSystemArtifact.Feedback.NONE, TutorArtifact.Availability.NONE, PrimerPilot.INSTRUCTION, null));
                else for (Stage s0 : stageList) if ((s0.label.equals(A1) && assessmentsToRun.contains("a1")) || (s0.label.equals(A2A) && assessmentsToRun.contains("a2a"))) chosen.add(s0);
                Stage first = chosen.get(0);
                chosen.set(0, new Stage(first.label, first.phase, first.cases, first.policy, first.feedback, first.tutor, instantiationMessage(snapshot) + " " + first.message, first.debrief));
                stageList = chosen;
            }
            if (consolidateOn) {
                stageList = new ArrayList<>(stageList);
                boolean a2aIn = stageList.stream().anyMatch(s0 -> s0.label.equals(A2A));
                stageList.add(new Stage(Consolidation.LABEL, LifePhase.LEARNING, List.of(), a2aIn ? ExpensePolicy.v2() : ExpensePolicy.v1(), ExpenseSystemArtifact.Feedback.NONE, TutorArtifact.Availability.NONE, Consolidation.REQUEST, null));
            }
            int totalCycles = 0; JSONArray learningRows = new JSONArray(); String[] prevLetter = {null};
            KnowledgeSnapshot[] parentOfCandidate = { snapshot }; java.util.Map<String, String> assessedResults = new java.util.LinkedHashMap<>();
            for (int si = 0; si < stageList.size(); si++) {
                Stage st = stageList.get(si);
                workspace.setLifePhase(st.phase);
                expenses.loadStage(st.cases, st.policy, st.feedback);
            lastLoaded = st; if (st.label.equals(A2A)) a2aStage = st;
                tutor.setStage(st.label, st.tutor);
                if (model != null) model.setStage(st.label);
                int msgsBefore = console.sentMessageCount();
                long stageStart = System.currentTimeMillis();
                System.out.println("[devl " + RunLog.now() + "] >>> " + st.label + " starting (life phase: " + st.phase.toJsonValue() + ", " + st.cases.size() + " claims)");
                console.simulateIncomingMessage(st.message);

                StageCompletion done = st.phase == LifePhase.ASSESSMENT ? new StageCompletion(console::sentMessageCount, assessGrace, assessSettle) : new StageCompletion(console::sentMessageCount, 3);
                expenses.setOnQueueEmpty(done::queueEmptied);
                // The primer stage has no queue to empty: it ends when the letter exists and the agent has reported (capped at 15 cycles — a letter that is not coming should not burn 90).
                final boolean primerStage = st.label.equals(PrimerPilot.LABEL); final int msgsAtStart = msgsBefore;
                final boolean consolidationStage = st.label.equals(Consolidation.LABEL);
                final java.util.Map<String, String> notesAtStart = consolidationStage ? new java.util.LinkedHashMap<>(((NotebookArtifact) workspace.instanceOf("notebook-01")).dump()) : null;
                // The consolidation stage has no queue either: it ends when the agent has reported (capped at 15 cycles). Operations run asynchronously, so give the batch that reported 2 s to land.
                java.util.function.BooleanSupplier stop = primerStage ? () -> primerReady(console, workspace, msgsAtStart)
                        : consolidationStage ? () -> { try { for (int w9 = 0; w9 < 40; w9++) { if (console.sentMessageCount() > msgsAtStart) return true; Thread.sleep(50); } } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } return false; } : done;
                int cycles = agent.doYourJobAndSelfEvaluate(primerStage || consolidationStage ? Math.min(maxCycles, 15) : maxCycles, stop);
                if (consolidationStage) for (int w8 = 0; w8 < 20 && java.util.Objects.equals(notesAtStart.get(PrimerPilot.KEY) == null ? null : notesAtStart.get(PrimerPilot.KEY).strip(), hasNote(workspace, PrimerPilot.KEY) ? ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump().get(PrimerPilot.KEY).strip() : null); w8++) Thread.sleep(50);
                if (primerStage) for (int w8 = 0; w8 < 40 && !hasNote(workspace, PrimerPilot.KEY); w8++) Thread.sleep(50);      // a note written in the batch that reported has landed
                String endedByText = primerStage ? (hasNote(workspace, PrimerPilot.KEY) ? "primer written and reported" : "no primer after " + cycles + " cycles")
                        : consolidationStage ? (console.sentMessageCount() > msgsAtStart ? "agent reported" : "no report after " + cycles + " cycles") : done.endedBy();
                if (st.phase == LifePhase.ASSESSMENT) assessedResults.put(st.label, expenses.correctCount() + "/" + st.cases.size());
                totalCycles += cycles;
                learningRows.put(new JSONObject().put("stage", st.label).put("cycles", cycles).put("correct", expenses.correctCount() + "/" + st.cases.size()).put("tutor_asks", tutor.askCount(st.label)));
                report.add(String.format("%-22s %7d %4d/%-4d %8d %6d %7d   ended by: %s", st.label, cycles, expenses.correctCount(), st.cases.size(),
                        expenses.decidedCount(), tutor.askCount(st.label), console.sentMessageCount() - msgsBefore, endedByText));

                if (st.label.equals(A1)) { a1Decisions = expenses.decisions(); a1Cases = st.cases; }
                if (st.label.equals(A2A)) {
                    a2aDecisions = expenses.decisions(); a2aKept = keptEverything(workspace, model);       // before any later stage reloads the expense system and changes the notebook
                    // The report that matters is the one sent AFTER the queue emptied. An earlier message (a progress note, a "holding everything"
                    // message) describes a state of affairs the agent then moved on from; scoring it as the final report would be misleading.
                    var all = console.sentMessages(); int from = Math.min(msgsBefore, all.size());
                    int emptied = done.sentWhenEmptied() < 0 ? all.size() : Math.max(from, Math.min(done.sentWhenEmptied(), all.size()));
                    a2aMidMessages = new ArrayList<>(all.subList(from, emptied)); a2aFinalMessages = new ArrayList<>(all.subList(emptied, all.size()));
                }
                // retention, made visible after every stage in every arm: how much of the v1 policy does what the agent KEPT still say?
                var keptNow = keptEverything(workspace, model);
                var rulesNow = reference.assess("rules", keptNow);
                if (!st.label.equals(A2A))
                    report.add("  kept after " + st.label + " (v1 policy rules, across notebook/stores/model): " + rulesNow.matched.size() + "/" + rulesNow.total()
                            + (rulesNow.missing.isEmpty() ? "" : "  missing: " + String.join("; ", rulesNow.missing)));
                if (primerStage) {
                    PrimerPilot.Result pr = PrimerPilot.finish(snapshot, ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump(), java.nio.file.Path.of(logDir), stamp,
                            logFile.getFileName().toString(), cycles, reference);
                    report.addAll(pr.lines);
                    for (String line : pr.lines) { if (line.startsWith("--- the letter")) break; System.out.println("[devl " + RunLog.now() + "] " + line.strip()); }   // the letter itself: result file
                }
                if (consolidationStage && parentOfCandidate[0] != null) {
                    var nbEnd = ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump();
                    Consolidation.Result cr = Consolidation.finish(parentOfCandidate[0], snapshot == null, notesAtStart, nbEnd, assessedResults, assessedResults.containsKey(A2A), java.nio.file.Path.of(logDir), arm, stamp,
                            logFile.getFileName().toString(), cycles, reference);
                    report.addAll(cr.lines);
                    for (String line : cr.lines) System.out.println("[devl " + RunLog.now() + "] " + line.strip());
                } else if (consolidationStage) {
                    report.add("=== consolidation: NOT RUN — this run has no snapshot to derive a candidate from (a learning run needs --letter or at least its learning phase to have ended) ===");
                }
                System.out.println("[devl " + RunLog.now() + "] <<< " + st.label + " ended after " + cycles + " cycles, " + (System.currentTimeMillis() - stageStart) / 1000
                        + "s: " + expenses.correctCount() + "/" + st.cases.size() + " correct; ended by: " + endedByText);
                if (model != null) {
                    var snap = model.snapshot();
                    report.add("  domain model after " + st.label + ": " + reference.assess("structure", snap) + " | " + reference.assess("rules", snap)
                            + " | concepts=" + snap.size());
                }
                if (st.debrief != null) {
                    int before = console.sentMessageCount();
                    console.simulateIncomingMessage(st.debrief);
                    int dc = agent.doYourJobAndSelfEvaluate(14, () -> console.sentMessageCount() > before);
                    var keptAfter = reference.assess("rules", keptEverything(workspace, model));
                    report.add("  debrief after " + st.label + ": " + dc + " cycles; kept afterwards: " + keptAfter.matched.size() + "/" + keptAfter.total()
                            + (keptAfter.missing.isEmpty() ? "" : "  missing: " + String.join("; ", keptAfter.missing)));
                    System.out.println("[devl " + RunLog.now() + "] debrief after " + st.label + " took " + dc + " cycles");
                    if (letterOn) {
                        var nbNow = ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump();
                        for (int w8 = 0; w8 < 20 && (nbNow.get(PrimerPilot.KEY) == null || nbNow.get(PrimerPilot.KEY).strip().equals(prevLetter[0] == null ? "" : prevLetter[0].strip())); w8++) {
                            Thread.sleep(50); nbNow = ((NotebookArtifact) workspace.instanceOf("notebook-01")).dump();       // a letter written in the batch that reported has landed
                        }
                        for (String line : PrimerPilot.debriefLines(st.label, nbNow, prevLetter[0], reference)) { report.add(line); System.out.println("[devl " + RunLog.now() + "] " + line.strip()); }
                        prevLetter[0] = nbNow.get(PrimerPilot.KEY);
                    }
                    totalCycles += dc;
                    learningRows.put(new JSONObject().put("stage", "debrief:" + st.label).put("cycles", dc));
                    // The end of learning is the moment a knowledge snapshot is worth having: write it now, so that assessments can be repeated from it without repeating learning.
                    boolean lastLearning = snapshot == null && st.phase == LifePhase.LEARNING && si + 1 < stageList.size() && stageList.get(si + 1).phase == LifePhase.ASSESSMENT;
                    if (lastLearning) {
                        NotebookArtifact nbLive = (NotebookArtifact) workspace.instanceOf("notebook-01");
                        List<String> unsupported = new ArrayList<>();
                        if (!arm.equals("notebook")) unsupported.add("arm:" + arm);
                        for (Artifact a : workspace.getIAvailableArtifacts()) if (a instanceof KeyValueStoreArtifact || a instanceof LogStoreArtifact || a instanceof DomainModelArtifact) unsupported.add(a.id());
                        JSONObject opts = new JSONObject().put("arm", arm).put("size", size.name().toLowerCase()).put("decided_claims", decidedVisible ? "visible" : "hidden")
                                .put("policy_cue", policyCue ? "version" : "none").put("tutor_max_topics", tutorMaxTopics == 0 ? "unlimited" : String.valueOf(tutorMaxTopics))
                                .put("tutor_recall", tutorRecall ? "on" : "off").put("notebook_safe_mode", NotebookArtifact.safeMode() ? "on" : "off")
                                .put("prompt_additions", faber.agent.PromptAdditions.specInForce()).put("materials", MATERIALS_VERSION).put("letter", letterOn ? "on" : "off")
                                .put("assessment_grace", String.valueOf(assessGrace)).put("assessment_settle", String.valueOf(assessSettle));
                        String point = "debrief:" + st.label;
                        String label = st.label.matches("stage(\\d).*") ? "d" + st.label.replaceAll("stage(\\d).*", "$1") : "c" + totalCycles;
                        KnowledgeSnapshot live = KnowledgeSnapshot.ofLive("S-" + stamp + "-" + label, nbLive.dump(), opts,
                                new JSONObject().put("run", logFile.getFileName().toString()).put("snapshot_after", point).put("cycle", totalCycles).put("reconstructed_from_log", false),
                                learningRows, keptAfter.matched.size() + "/" + keptAfter.total(), unsupported);
                        parentOfCandidate[0] = live;
                        java.nio.file.Path snapFile = java.nio.file.Path.of(logDir).resolve("devl-snapshot-" + arm + "-" + stamp + ".json");
                        String letterEnd = letterOn ? nbLive.dump().get(PrimerPilot.KEY) : null;
                        if (letterOn) live.manifest.put("letter", new JSONObject().put("written_during_learning", true).put("present", letterEnd != null && !letterEnd.isBlank()).put("chars", letterEnd == null ? 0 : letterEnd.length()));
                        live.write(snapFile);
                        System.out.println("[devl " + RunLog.now() + "] knowledge snapshot written: " + snapFile.toAbsolutePath() + " (" + live.id() + ", " + live.notes.size() + " notes"
                                + (unsupported.isEmpty() ? "" : "; NOT loadable by --from-knowledge: " + unsupported) + ")");
                        report.add("  knowledge snapshot written: " + snapFile.getFileName() + " (" + live.id() + ")");
                        if (letterOn) {
                            if (letterEnd == null || letterEnd.isBlank()) {
                                System.out.println("[devl " + RunLog.now() + "] NO LETTER at the end of learning: only the full snapshot was written");
                                report.add("  NO LETTER at the end of learning: only the full snapshot was written, no letter-alone or notes-alone variant");
                            } else {
                                // the three conditions of a paired comparison, from ONE learning run: the letter with the notes (the snapshot above), the letter alone, the notes alone
                                java.util.LinkedHashMap<String, String> notesOnly = new java.util.LinkedHashMap<>(live.notes); notesOnly.remove(PrimerPilot.KEY);
                                java.util.LinkedHashMap<String, String> letterOnly = new java.util.LinkedHashMap<>(); letterOnly.put(PrimerPilot.KEY, letterEnd);
                                JSONObject how = new JSONObject().put("run", logFile.getFileName().toString()).put("cycle", totalCycles).put("instruction", PrimerPilot.LEARNING_VERSION).put("snapshot_after", "learning-end");
                                KnowledgeSnapshot pOnly = KnowledgeSnapshot.derived(live, "P", "letter-alone", letterOnly, how, reference.assess("rules", AssessmentMetrics.asConcepts(letterOnly)).matched.size() + "/8");
                                KnowledgeSnapshot rOnly = KnowledgeSnapshot.derived(live, "R", "notes-without-letter", notesOnly, how, reference.assess("rules", AssessmentMetrics.asConcepts(notesOnly)).matched.size() + "/8");
                                pOnly.manifest.put("letter", new JSONObject().put("written_during_learning", true).put("present", true).put("chars", letterEnd.length()));
                                rOnly.manifest.put("letter", new JSONObject().put("written_during_learning", true).put("present", false).put("chars", 0));
                                java.nio.file.Path pFile = java.nio.file.Path.of(logDir).resolve("devl-snapshot-" + arm + "-" + stamp + "-P.json"), rFile = java.nio.file.Path.of(logDir).resolve("devl-snapshot-" + arm + "-" + stamp + "-R.json");
                                pOnly.write(pFile); rOnly.write(rFile);
                                System.out.println("[devl " + RunLog.now() + "] letter variants written: " + pFile.getFileName() + " (letter alone, " + pOnly.id() + "), " + rFile.getFileName() + " (notes without the letter, " + rOnly.id() + ")");
                                report.add("  letter variants written: " + pFile.getFileName() + " (letter alone: " + pOnly.id() + "); " + rFile.getFileName() + " (notes without the letter: " + rOnly.id() + ")");
                            }
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

        if (a1Decisions != null) {
            report.add("");
            report.add("=== A1 decisions (clean assessment, policy v1) ===");
            for (var d : a1Decisions)
                report.add("  " + d.expenseId + ": " + d.kind + (d.to != null ? ":" + d.to : "") + (d.correct ? "  OK" : "  WRONG (expected " + d.expected + ")"));
        }
        if (a2aStage != null) {
            var a2aDec = a2aDecisions != null ? a2aDecisions : expenses.decisions();                              // a run aborted inside A2a has not captured them: use what the expense system holds
            report.add("");
            report.add("=== A2a decisions (policy-change assessment, policy v2) ===");
            for (var d : a2aDec)
                report.add("  " + d.expenseId + ": " + d.kind + (d.to != null ? ":" + d.to : "") + (d.correct ? "  OK" : "  WRONG (expected " + d.expected + ")"));
            report.add("");
            boolean a1Ran = a1Decisions != null;
            boolean baselineClean = !a1Ran || a1Decisions.stream().allMatch(d -> d.correct);     // not run in this instance (--assessments=a2a): the gate does not apply
            if (!a1Ran) report.add("  (A1 was not run in this instance, so the A1 gate does not apply; the policy-change analysis assumes a competent baseline)");
            report.addAll(PolicyChangeAnalysis.lines(a2aStage.cases, a2aDec, ExpensePolicy.v1(), a2aStage.policy, baselineClean));

            // everything the agent kept, however it kept it, assessed the same way in every arm
            List<DomainModelArtifact.Concept> kept = a2aKept != null ? a2aKept : keptEverything(workspace, model);
            report.add("  notes updated for the new policy (what the agent kept, however it kept it): " + AssessmentMetrics.notesUpdatedForV2(kept));
            if (a2aFinalMessages.isEmpty()) {
                report.add("  final report: NONE — the agent sent no message after the queue emptied" + (a2aMidMessages.isEmpty() ? "" : " (" + a2aMidMessages.size() + " earlier message(s) not counted as the final report)"));
                if (!a2aMidMessages.isEmpty()) {
                    var accMid = AssessmentMetrics.reportAccuracy(a2aMidMessages, a2aDec);
                    report.add("  for information, the earlier message(s), scored as if they were the report: " + accMid.line().trim());
                    report.addAll(accMid.evidenceLines());
                }
            } else {
                var acc = AssessmentMetrics.reportAccuracy(a2aFinalMessages, a2aDec);
                report.add(acc.line());
                report.addAll(acc.evidenceLines());
            }
        }
        if (model != null) report.add("  domain model operations by stage: " + model.opCountsByStage());
        report.add("");
        report.add("=== what the agent kept ===");
        if (model != null) { report.add("[" + model.id() + "] DomainModel (Markdown export, for review with domain experts):"); report.add(model.toMarkdown()); }
        for (Artifact a : workspace.getIAvailableArtifacts()) {
            String purpose = workspace.purposeOf(a.id());
            if (a instanceof NotebookArtifact n) { report.add("[" + a.id() + "] notebook"); n.dump().forEach((k, v) -> report.add("    " + k + " = " + v)); }
            if (a instanceof KeyValueStoreArtifact k) { report.add("[" + a.id() + "] KeyValueStore, purpose: " + purpose); k.dump().forEach((kk, v) -> report.add("    " + kk + " = " + v)); }
            if (a instanceof LogStoreArtifact l) { report.add("[" + a.id() + "] LogStore, purpose: " + purpose); l.dump().forEach(e -> report.add("    " + e)); }
        }
        report.add("");
        report.add("=== tutor exchanges ===");
        if (tutorRecall) report.add("  recall() calls by stage: " + tutor.recallCounts());
        for (var e : tutor.exchanges()) report.add("  [" + e.stage + "/" + e.availability + "] Q: " + e.question + "\n      A: " + e.answer);

        String out = String.join("\n", report);
        System.out.println("\n\n==================== PILOT RESULT ====================\n" + out);
        java.nio.file.Path resultFile = java.nio.file.Path.of(logDir, "devl-result-" + arm + "-" + stamp + ".txt");
        java.nio.file.Files.writeString(resultFile, out);
        System.out.println("[devl] result summary written to " + resultFile.toAbsolutePath());
        workspace.shutdown();
        RunLog.stop();
        if (aborted != null && exitOnAbort) System.exit(1);
    }
}
