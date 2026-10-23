package faber.devl.cases.expense;

import static faber.devl.CheckKit.check;
import static faber.devl.CheckKit.inv;
import static faber.devl.CheckKit.modelVersion;
import static faber.devl.CheckKit.note;
import static faber.devl.CheckKit.revise;
import static faber.devl.CheckKit.run;
import static faber.devl.CheckKit.say;
import static faber.devl.CheckKit.writeJson;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import faber.devl.CaseContract;
import faber.devl.CaseData;
import faber.devl.CaseWorld;
import faber.devl.CheckKit;
import faber.devl.CheckKit.Brain;
import faber.devl.Consolidation;
import faber.devl.CuratedModelArtifact;
import faber.devl.DevlCase;
import faber.devl.DevlRunner;
import faber.devl.Feedback;
import faber.devl.Homes;
import faber.devl.Messages;
import faber.devl.Playbook;
import faber.devl.PlaybookArtifact;
import faber.devl.Projects;
import faber.devl.PlaybookSnapshot;
import faber.devl.AssessmentMetrics;
import faber.devl.Outcome;
import faber.devl.RuleReference;
import faber.devl.TaskLibraryArtifact;
import faber.devl.TutorArtifact;
import faber.environment.LifePhase;
import faber.environment.Workspace;

/**
 * The checks of the expense case: its policy and queues, its expense system, the curated model it ships (pinned to the policy), the texts the agent reads when they are made from the
 * framework's templates and this case's slots (pinned by hash, so that no change to what the agent is given goes unnoticed), and the runner driven end to end on this case by a scripted
 * agent. No model calls. The framework's own checks are in faber.devl.DevlPilotCheck and run on a different case.
 */
public final class ExpenseCaseCheck {

    static String label(ExpensePolicy p, ExpenseClaim c) { return p.decide(c).label(); }
    static ExpenseClaim find(List<ExpenseClaim> l, String id) { for (var c : l) if (c.id.equals(id)) return c; throw new IllegalArgumentException(id); }
    /** An ad hoc claim, to probe a boundary of the policy. */
    static ExpenseClaim k(String cat, int amt, boolean receipt, Object... kv) { return ExpenseQueues.c("T00", "Test", cat, amt, receipt, "2026-01-01", "t", null, kv); }

    /** Approve everything pending; when nothing is pending, send the report. */
    static String blind(CaseWorld w, String report) {
        ExpenseSystemArtifact ex = (ExpenseSystemArtifact) w; JSONArray arr = new JSONArray(); var pend = ex.pendingIds();
        if (pend.isEmpty()) return arr.put(say(report)).toString();
        for (String id : pend) arr.put(inv("expense-system-01", "approve_expense", new JSONObject().put("expense_id", id)));
        return arr.toString();
    }


    /** Every text the agent reads that the case and the framework make together, by name (the keys are the pin's). */
    static java.util.Map<String, String> agentVisibleTexts(DevlCase kase, Messages m, CaseData data) {
        java.util.Map<String, String> n = new java.util.LinkedHashMap<>();
        var ls = DevlRunner.learningStages(kase, m); var as = DevlRunner.assessmentStages(kase, m, new java.util.LinkedHashSet<>(List.of("a1", "a2a"))); var op = DevlRunner.operationStage(kase, m);
        for (int i = 0; i < 3; i++) { n.put("stage" + (i + 1) + ".message", ls.get(i).message); n.put("stage" + (i + 1) + ".debrief", ls.get(i).debrief); }
        n.put("a1.message", as.get(0).message); n.put("a2a.message", as.get(1).message); n.put("operation.message", op.message); n.put("debrief", m.debrief()); n.put("instantiation", m.instantiation("PB-SAMPLE"));
        n.put("playbook.content", m.playbookContent()); n.put("playbook.first", m.learningRequest(true)); n.put("playbook.later", m.learningRequest(false)); n.put("playbook.consolidation", m.consolidationRequest());
        var r = TutorArtifact.pointingToModel(m, data.tutorTopics());
        n.put("tutor.full", r.answer("What is the limit for a business class flight and for meals?", TutorArtifact.Availability.FULL)); n.put("tutor.hints", r.answer("What is the limit for meals?", TutorArtifact.Availability.HINTS));
        n.put("tutor.nothing.full", r.answer("hello", TutorArtifact.Availability.FULL)); n.put("tutor.nothing.hints", r.answer("hello", TutorArtifact.Availability.HINTS));
        var tut = new TutorArtifact("tutor-01", new Workspace(), r, data.slot("tutorExample"));
        try { tut.doOperation("ask", new JSONObject()); } catch (Exception e) { n.put("tutor.error.missing", e.getMessage()); }
        try { tut.doOperation("zzz", new JSONObject()); } catch (Exception e) { n.put("tutor.error.unknown", e.getMessage()); }
        n.put("manual.tutor", TutorArtifact.manual(data.slot("tutorExample")).toJson()); n.put("manual.model", CuratedModelArtifact.manual(data.slot("company")).toJson()); n.put("manual.expense", ExpenseSystemArtifact.manual().toJson());
        n.put("manual.library", TaskLibraryArtifact.manual().toJson());
        n.put("manual.playbook", PlaybookArtifact.manual().toJson());
        final String sampleP = "How I work\n- I read the version of the domain model before I trust a limit, and I propose a change when it is behind.\n- I report every expense with its decision.\n- I escalate to the role the rule names.";
        PlaybookArtifact pa = new PlaybookArtifact("playbook-01", new Workspace(), "PB-20261017-100000", 1, sampleP);
        java.util.function.BiFunction<String, JSONObject, String> opp = (name, q) -> { try { return String.valueOf(pa.doOperation(name, q).get(0)); } catch (Exception e) { return "ERROR: " + e.getMessage(); } };
        n.put("playbook.lineage", opp.apply("lineage", new JSONObject()));
        n.put("playbook.revise", opp.apply("revise", new JSONObject().put("text", "I report every expense.").put("reason", "shortened")));
        n.put("playbook.diff", opp.apply("diff", new JSONObject()));
        pa.setPolicy(true, "a1-clean-assessment");
        n.put("playbook.revise.frozen", opp.apply("revise", new JSONObject().put("text", "x").put("reason", "r")));
        n.put("library.content", TaskLibraryArtifact.load("task-library-01", new Workspace(), DevlRunner.LIBRARY_BASE, data.libraryResource(), data.slots()).toMarkdown());
        // what the lookups return — the texts the agent reads at the moment it decides — are pinned too
        try {
            var lib = TaskLibraryArtifact.load("task-library-01", new Workspace(), DevlRunner.LIBRARY_BASE, data.libraryResource(), data.slots()); var mod = CuratedModelArtifact.load("domain-model-01", new Workspace(), data.modelResource());
            n.put("library.describe_task", String.valueOf(lib.doOperation("describe_task", new JSONObject().put("task", "process-queue")).get(0))); n.put("library.plans_for", String.valueOf(lib.doOperation("plans_for", new JSONObject().put("task", "process-queue")).get(0)));
            n.put("library.describe_plan", String.valueOf(lib.doOperation("describe_plan", new JSONObject().put("plan", "keep-a-record")).get(0)));
            n.put("model.rules_for.meals", String.valueOf(mod.doOperation("rules_for", new JSONObject().put("concept", "Meals")).get(0))); n.put("model.describe.meals", String.valueOf(mod.doOperation("describe", new JSONObject().put("concept", "Meals")).get(0)));
            n.put("model.search.meal", String.valueOf(mod.doOperation("search", new JSONObject().put("term", "meal")).get(0)));
        } catch (Exception e) { throw new IllegalStateException(e); }
        n.put("prompt.sha12", DevlRunner.sha12(faber.agent.SystemPrompt.systemPrompt));
        return n;
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((th, e) -> { e.printStackTrace(); System.out.println("\nSUITE ABORTED BY AN UNCAUGHT EXCEPTION: " + e); Runtime.getRuntime().halt(3); });
        System.setProperty("faber.notebook.safe", "true");       // as DevlRunner sets it: the system prompt includes the Notebook's manual and is built once
        ExpensePolicy v1 = ExpensePolicy.v1(), v2 = ExpensePolicy.v2();
        final DevlCase kase = Projects.open("expense-approval"); final CaseData data = kase.data(); final Messages msgs = new Messages(data, DevlRunner.MODEL_ID);
        Workspace ws = new Workspace(); ws.initDefaultArtifacts();
        final TaskLibraryArtifact.Parsed libE = TaskLibraryArtifact.parse(DevlRunner.LIBRARY_BASE, data.libraryResource(), data.slots());
        final TaskLibraryArtifact libArt = TaskLibraryArtifact.load("task-library-01", new Workspace(), DevlRunner.LIBRARY_BASE, data.libraryResource(), data.slots());

        // ---- 1. policy truth table, spot-checked against hand-derived expectations; the queues and what each is for
        check("meals: 40 single approved, 41 single rejected, 90 with 2 attendees approved, 91 rejected, no receipt rejected, 55 with 3 approved",
              label(v1, k("meals", 40, true, "attendees", 1)).equals("approve") && label(v1, k("meals", 41, true, "attendees", 1)).equals("reject") && label(v1, k("meals", 90, true, "attendees", 2)).equals("approve")
              && label(v1, k("meals", 91, true, "attendees", 3)).equals("reject") && label(v1, k("meals", 12, false, "attendees", 1)).equals("reject") && label(v1, k("meals", 55, true, "attendees", 3)).equals("approve"));
        check("flights: economy 900 approved, 905 and 1150 escalate to the manager; business under 6 hours rejected, over 6 hours escalates to the director",
              label(v1, k("flight", 900, true, "cabin", "economy", "flight_hours", 4)).equals("approve") && label(v1, k("flight", 905, true, "cabin", "economy", "flight_hours", 2)).equals("escalate:manager")
              && label(v1, k("flight", 1800, true, "cabin", "business", "flight_hours", 4)).equals("reject") && label(v1, k("flight", 2400, true, "cabin", "business", "flight_hours", 9)).equals("escalate:director"));
        check("lodging: 180 a night approved, 190 a night escalates to the manager; software: an approved vendor up to 200 approved, above it escalates to IT, any other vendor rejected",
              label(v1, k("lodging", 540, true, "nights", 3)).equals("approve") && label(v1, k("lodging", 570, true, "nights", 3)).equals("escalate:manager")
              && label(v1, k("software", 200, true, "vendor", "Quark")).equals("approve") && label(v1, k("software", 260, true, "vendor", "Linden")).equals("escalate:it") && label(v1, k("software", 90, true, "vendor", "Zephyr")).equals("reject"));
        check("equipment: 500 approved, 1300 escalates to the manager, 2100 rejected; training: with manager approval 900 approved and 1500 escalates to the director, without approval rejected; a duplicate rejected",
              label(v1, k("equipment", 500, true)).equals("approve") && label(v1, k("equipment", 1300, true)).equals("escalate:manager") && label(v1, k("equipment", 2100, true)).equals("reject")
              && label(v1, k("training", 900, true, "manager_approved", true)).equals("approve") && label(v1, k("training", 1500, true, "manager_approved", true)).equals("escalate:director") && label(v1, k("training", 900, true, "manager_approved", false)).equals("reject")
              && label(v1, find(ExpenseQueues.stage2(), "E213")).equals("reject"));
        check("policy v2: a 38 EUR solo meal is rejected (v1 approves), Quark is rejected and Orbit approved, a 450 EUR piece of equipment escalates (v1 approves)",
              label(v2, k("meals", 38, true, "attendees", 1)).equals("reject") && label(v1, k("meals", 38, true, "attendees", 1)).equals("approve") && label(v2, k("software", 150, true, "vendor", "Quark")).equals("reject")
              && label(v2, k("software", 120, true, "vendor", "Orbit")).equals("approve") && label(v2, k("equipment", 450, true)).equals("escalate:manager") && label(v1, k("equipment", 450, true)).equals("approve"));
        var q1 = ExpenseQueues.stage1(); var q2 = ExpenseQueues.stage2(); var q3 = ExpenseQueues.stage3(); var qa1 = ExpenseQueues.cleanAssessment(); var qa2 = ExpenseQueues.policyChange(); var qop = ExpenseQueues.operation();
        check("the queues: stage 1 has 4 claims, stage 2 has 8 (every category, the duplicate), stage 3 has 8, A1 has 6, A2a has 8, operation has 6; all claim ids are different across the queues",
              q1.size() == 4 && q2.size() == 8 && q3.size() == 8 && qa1.size() == 6 && qa2.size() == 8 && qop.size() == 6
              && java.util.stream.Stream.of(q1, q2, q3, qa1, qa2, qop).flatMap(List::stream).map(c -> c.id).distinct().count() == 40
              && q2.stream().map(c -> c.category).distinct().count() == 6);
        check("A1 cannot be confounded by the policy change: every one of its six claims is decided the same under v1 and v2",
              qa1.stream().allMatch(c -> label(v1, c).equals(label(v2, c))));
        long changedA2 = qa2.stream().filter(c -> !label(v1, c).equals(label(v2, c))).count(), changedOp = qop.stream().filter(c -> !label(v1, c).equals(label(v2, c))).count();
        check("A2a: 5 of its 8 claims are decided differently under v2 (3 are controls); operation: 4 of 6 are (a reader of version 1 is wrong on four, one who distrusts everything on two). got " + changedA2 + " and " + changedOp,
              changedA2 == 5 && changedOp == 4 && !label(v1, find(qop, "E605")).equals("") && label(v1, find(qop, "E605")).equals(label(v2, find(qop, "E605"))) && label(v1, find(qop, "E606")).equals(label(v2, find(qop, "E606"))));
        check("stage 3 is held to boundaries (41, 90 with two, 905, 570, Quark at 200, equipment 500, training 1500, a duplicate of E303)",
              label(v1, find(q3, "E302")).equals("reject") && label(v1, find(q3, "E303")).equals("approve") && label(v1, find(q3, "E305")).equals("escalate:manager") && label(v1, find(q3, "E308")).equals("escalate:manager")
              && label(v1, find(q3, "E309")).equals("approve") && label(v1, find(q3, "E311")).equals("approve") && label(v1, find(q3, "E312")).equals("escalate:director") && label(v1, find(q3, "E313")).equals("reject"));

        // ---- 2. expense system: operations, scoring, practice feedback, audit flags, final decisions, decided claims retrievable
                ExpenseSystemArtifact ex = new ExpenseSystemArtifact("expense-system-01", ws);
        ex.loadStage(q1, v1, Feedback.PRACTICE);
        check("expense: list_pending shows all 4", ((String) ex.doOperation("list_pending", new JSONObject()).get(0)).split("\n").length == 4);
        String details = (String) ex.doOperation("get_expense", new JSONObject().put("expense_id", "E105")).get(0);
        check("expense: get_expense exposes attendees and the status but not ground truth", details.contains("attendees") && details.contains("pending") && !details.contains("duplicate"));
        String r1 = (String) ex.doOperation("approve_expense", new JSONObject().put("expense_id", "E101")).get(0);
        String r2 = (String) ex.doOperation("approve_expense", new JSONObject().put("expense_id", "E103")).get(0);
        check("expense: practice feedback says correct, and on a wrong decision says incorrect and quotes the clause", r1.contains("correct") && !r1.contains("incorrect") && r2.contains("incorrect") && r2.contains("receipt"));
        boolean threw = false; try { ex.doOperation("approve_expense", new JSONObject().put("expense_id", "E101")); } catch (IllegalArgumentException e) { threw = true; }
        check("expense: a decision is final (cannot decide twice)", threw);
        String decided = (String) ex.doOperation("get_expense", new JSONObject().put("expense_id", "E101")).get(0);
        check("expense: a decided claim stays retrievable, marked with the decision recorded for it (never whether it was right)", decided.contains("decided: approve") && !decided.contains("correct"));
        check("expense: score 1/2 correct, 2 pending", ex.correctCount() == 1 && ex.decidedCount() == 2 && ex.pendingCount() == 2);
        ex.loadStage(qa2, v2, Feedback.AUDIT);
        String a1 = (String) ex.doOperation("approve_expense", new JSONObject().put("expense_id", "E401")).get(0);
        String a2 = (String) ex.doOperation("approve_expense", new JSONObject().put("expense_id", "E410")).get(0);
        check("expense: audit flags a stale decision with the CURRENT rule (35), and is silent on a correct one", a1.contains("flagged by audit") && a1.contains("35") && a2.equals("recorded"));
        ex.loadStage(q1, v1, Feedback.NONE);
        check("expense: no-feedback mode says only 'recorded'", ((String) ex.doOperation("reject_expense", new JSONObject().put("expense_id", "E101").put("reason", "x")).get(0)).equals("recorded"));
        ex.loadStage(q2, v1, Feedback.NONE);
        ex.doOperation("escalate_expense", new JSONObject().put("expense_id", "E213").put("to", "Manager"));
        ex.doOperation("escalate_expense", new JSONObject().put("expense_id", "E205").put("to", "director"));
        check("expense: escalation must name the right role (case-insensitive ok); the duplicate escalated is wrong, the business flight to the director right", ex.correctCount() == 1);
        check("expense: the system exposes no policy version (the model's version is the cue), and its manual says decided claims are retrievable",
              ex.currentObsProperties().get("policy_version") == null && ExpenseSystemArtifact.manual().operations.stream().anyMatch(o -> o.description.contains("already decided")) && ExpenseSystemArtifact.manual().observableProperties.stream().noneMatch(p -> p.name.equals("policy_version")));

        // ---- 3. tutor: availability fades; the scripted tutor points to the model and teaches no rule; unavailable fails; every exchange is logged per stage
        TutorArtifact tutor = new TutorArtifact("tutor-01", ws, TutorArtifact.pointingToModel(msgs, data.tutorTopics()), data.slot("tutorExample"));
        tutor.setStage("stage2", TutorArtifact.Availability.FULL);
        String full = (String) tutor.doOperation("ask", new JSONObject().put("question", "What is the limit for a business class flight and for meals?")).get(0);
        check("tutor FULL: points to the model for the topics named, says how to read it, and teaches no rule (no number, no currency)",
              full.contains("rules on meals, flights are kept in the domain model (domain-model-01)") && full.contains("rules_for(concept) or describe(concept)") && java.util.stream.Stream.of("40", "90", "35", "75", "900", "6 hours", "EUR").noneMatch(full::contains));
        tutor.setStage("stage3", TutorArtifact.Availability.HINTS);
        String hint = (String) tutor.doOperation("ask", new JSONObject().put("question", "What is the limit for meals?")).get(0);
        check("tutor HINTS: says it is giving hints and still points to the model", hint.startsWith("At this stage I can only give hints. ") && hint.contains("domain-model-01"));
        check("tutor, no topic in the question: points to overview()", ((String) tutor.doOperation("ask", new JSONObject().put("question", "hello")).get(0)).contains("overview() shows what it holds"));
        tutor.setStage("a2a-policy-change", TutorArtifact.Availability.NONE);
        boolean unavailable = false; try { tutor.doOperation("ask", new JSONObject().put("question", "anything")); } catch (IllegalStateException e) { unavailable = true; }
        check("tutor NONE: the call fails", unavailable);
        boolean noRecall = false; try { tutor.doOperation("recall", new JSONObject()); } catch (IllegalArgumentException e) { noRecall = e.getMessage().contains("The operation of this artifact is: ask(question)"); }
        check("tutor: exchanges logged per stage (1,2,1); there is no recall operation (the tutor teaches nothing to recall), and the error says what the operation is",
              tutor.askCount("stage2") == 1 && tutor.askCount("stage3") == 2 && tutor.askCount("a2a-policy-change") == 1 && noRecall && TutorArtifact.manual(data.slot("tutorExample")).operations.size() == 1);
        check("tutor: the manual claims only what the tutor does (answers at full, pointers at hints, fails when none) — nothing about explaining rules", TutorArtifact.manual(data.slot("tutorExample")).furtherInfo.contains("At hints it says so in every reply and gives only pointers")
              && !TutorArtifact.manual(data.slot("tutorExample")).furtherInfo.toLowerCase().contains("explains the rules"));

        // ---- 10. the curated domain model: a source of truth that experts keep and version, read-only for the agent (KNOWLEDGE-LAYERS.md)
        Workspace w31 = new Workspace(); JSONObject curJson31 = RuleReference.loadJson(data.modelResource()); String curSha31 = CuratedModelArtifact.sourceRecord(data.modelResource()).getString("sha256");
        CuratedModelArtifact cur31 = new CuratedModelArtifact("domain-model-01", w31, curJson31, curSha31, "domain-model.json");
        java.util.function.BiFunction<String, JSONObject, String> op31 = (name, p) -> { try { return String.valueOf(cur31.doOperation(name, p).get(0)); } catch (Exception e) { return "ERROR: " + e.getMessage(); } };
        java.util.Map<String, String> topicConcept31 = new java.util.LinkedHashMap<>(); topicConcept31.put("receipts", "Receipt"); topicConcept31.put("duplicates", "Expense"); topicConcept31.put("meals", "Meals"); topicConcept31.put("flights", "Flight");
        topicConcept31.put("lodging", "Lodging"); topicConcept31.put("software", "Software"); topicConcept31.put("equipment", "Equipment"); topicConcept31.put("training", "Training");
        List<String> off31 = new ArrayList<>();
        for (String ver : new String[] { "1", "2" }) { cur31.publish(ver); var pol31 = ver.equals("1") ? ExpensePolicy.v1() : ExpensePolicy.v2();
            for (var e : topicConcept31.entrySet()) if (!op31.apply("rules_for", new JSONObject().put("concept", e.getValue())).contains(pol31.describe(e.getKey()))) off31.add(ver + ":" + e.getKey());
            if (!op31.apply("rules_for", new JSONObject().put("concept", "ApprovalPolicy")).contains(pol31.explain("procedure"))) off31.add(ver + ":procedure"); }
        check("the curated source file is pinned to the policy: for every topic and both versions, the rule text the model gives is word for word the policy's own prose (the tutor's teaching text) — they cannot silently disagree. " + off31, off31.isEmpty());
        cur31.publish("1"); var probe31a = RuleReference.load(data.rulesResource()).assess("rules", cur31.ruleTexts()); var probe31b = RuleReference.load(data.rulesResource()).assess("v2", cur31.ruleTexts());
        cur31.publish("2"); var probe31c = RuleReference.load(data.rulesResource()).assess("rules", cur31.ruleTexts()); var probe31d = RuleReference.load(data.rulesResource()).assess("v2", cur31.ruleTexts());
        check("the model holds the policy as the reference model probes it: version 1 states all 8 rules at their first values and none of the 3 changes; version 2 states the 3 changes, and the three rules they replace no longer match",
              probe31a.matched.size() == 8 && probe31b.matched.isEmpty() && probe31d.matched.size() == 3 && probe31c.matched.size() == 5 && RuleReference.load(data.rulesResource()).changedIds().stream().noneMatch(id -> RuleReference.load(data.rulesResource()).matchedIds("rules", cur31.ruleTexts()).contains(id)));
        cur31.publish("1");
        String ov31 = op31.apply("overview", new JSONObject());
        check("overview: the model's map at version 1 — its title, version and release date, every one of the 13 concepts with a short definition and its number of rules, and where to go next; version 2 is not mentioned (it has not been published)",
              ov31.contains("version 1 (released 2026-01-01)") && ov31.lines().filter(l -> l.startsWith("- ")).count() == 13 && ov31.contains("- Meals [value]") && ov31.contains("(1 rules)") && ov31.contains("rules_for(concept)") && !ov31.contains("version 2") && !ov31.contains("35"));
        String dm1 = op31.apply("describe", new JSONObject().put("concept", "Meals")), dm1b = op31.apply("describe", new JSONObject().put("concept", "meals"));
        check("describe: a concept in full — definition, relation, where it lives in the system, and its rules with source, confidence and the version since which they apply; the name is case-insensitive",
              dm1.contains("is-a Category") && dm1.contains("grounded in: expense-system-01.get_expense (attendees)") && dm1.contains("rule meals-1: Meals: up to 40 EUR") && dm1.contains("[Finance policy handbook, section 4 (revision 1); confirmed; applies since version 1]") && dm1.equals(dm1b));
        cur31.publish("2"); String dm2 = op31.apply("describe", new JSONObject().put("concept", "Meals"));
        check("the rules follow the version in force: at version 2 Meals states 35 and 75, cites revision 2 and 'since version 2', and no longer states 40 EUR",
              dm2.contains("rule meals-2: Meals: up to 35 EUR") && dm2.contains("revision 2") && dm2.contains("applies since version 2") && !dm2.contains("40 EUR") && !dm2.contains("meals-1"));
        cur31.publish("1");
        check("rules_for: a concept without rules says so (with the version); an unknown concept is a teaching error that names the way out; a missing parameter says what is missing and that nothing was done",
              op31.apply("rules_for", new JSONObject().put("concept", "Employee")).equals("No rules are recorded for Employee in version 1.") && op31.apply("rules_for", new JSONObject().put("concept", "Nope")).contains("no such concept: Nope (overview() lists the concepts of this model)")
              && op31.apply("rules_for", new JSONObject()).contains("missing parameter 'concept'") && op31.apply("rules_for", new JSONObject()).contains("nothing was done"));
        String sr31a = op31.apply("search", new JSONObject().put("term", "orbit")); cur31.publish("2"); String sr31b = op31.apply("search", new JSONObject().put("term", "orbit")); cur31.publish("1");
        check("search finds where a term is used, in the version in force: 'Orbit' is nowhere in version 1 and in the Software rule in version 2", sr31a.startsWith("Nothing in version 1 mentions 'orbit'") && sr31b.contains("1 match(es) in version 2") && sr31b.contains("Software, software-2"));
        String vv1 = op31.apply("version", new JSONObject()); cur31.publish("2"); String vv2 = op31.apply("version", new JSONObject()); cur31.publish("1");
        check("version: names the version in force and the versions published so far — at version 1 it does not reveal that a version 2 exists", vv1.equals("Version 1 is in force. Published versions: 1 (2026-01-01);") && vv2.contains("Version 2 is in force") && vv2.contains("2 (2026-10-01)"));
        String ch0 = op31.apply("changes_since", new JSONObject().put("version", "1")); cur31.publish("2");
        String ch1 = op31.apply("changes_since", new JSONObject().put("version", "1")), ch2 = op31.apply("changes_since", new JSONObject().put("version", "2")), ch3 = op31.apply("changes_since", new JSONObject().put("version", "3"));
        String ch4 = op31.apply("changes_since", new JSONObject().put("version", "x")), ch5 = op31.apply("changes_since", new JSONObject().put("version", "0")); cur31.publish("1");
        check("changes_since: what the experts changed since a version the agent knows — nothing to report at the version in force; at version 2 since 1 the three changes with their old and new values; a version not yet published, a non-number and 0 are each a teaching error",
              ch0.equals("No changes: version 1 is the version in force.") && ch1.contains("Changes since version 1 (version 2 is in force)") && ch1.contains("35 EUR (was 40)") && ch1.contains("Linden and Orbit (Quark was removed)") && ch1.contains("400 EUR (was 500)")
              && ch2.equals("No changes: version 2 is the version in force.") && ch3.equals("No changes: version 3 has not been published.") && ch4.contains("is not a version number") && ch5.contains("versions start at 1"));
        String pr31 = op31.apply("propose", new JSONObject().put("concept", "Meals").put("change", "The audit flagged a 38 EUR meal that version 1 allows").put("evidence", "E401, audit flag"));
        String afterProp31 = op31.apply("describe", new JSONObject().put("concept", "Meals"));
        check("propose: recorded for the experts with its number, the model itself is unchanged, the observable count goes up, and the proposal carries the version it was made at; a missing evidence, or an unknown concept, is refused",
              pr31.startsWith("Proposal 1 recorded for the domain experts. The model itself is unchanged") && afterProp31.equals(dm1) && cur31.proposals().size() == 1 && cur31.proposals().get(0)[3].equals("1") && cur31.currentObsProperties().get("proposals").equals(1)
              && op31.apply("propose", new JSONObject().put("change", "x")).contains("missing parameter 'evidence'") && op31.apply("propose", new JSONObject().put("concept", "Nope").put("change", "x").put("evidence", "y")).contains("no such concept") && cur31.proposals().size() == 1);
        check("the model is read-only to the agent: there is no operation that adds, retracts or defines (the error for one says so and lists what can be done); and none that decides or evaluates a case — consult, do not delegate",
              op31.apply("add_rule", new JSONObject().put("concept", "Meals").put("rule", "x")).contains("you cannot change it") && op31.apply("define", new JSONObject()).contains("unknown operation") && op31.apply("decide", new JSONObject()).contains("unknown operation")
              && CuratedModelArtifact.manual(data.slot("company")).operations.stream().map(o -> o.signature.replaceAll("\\(.*", "")).toList().equals(List.of("overview", "describe", "rules_for", "search", "version", "changes_since", "propose")));
        String mdl31 = CuratedModelArtifact.manual(data.slot("company")).function + " " + CuratedModelArtifact.manual(data.slot("company")).furtherInfo + " " + CuratedModelArtifact.manual(data.slot("company")).operations.stream().map(o -> o.description).reduce("", (x, y) -> x + " " + y);
        check("the manual says what the model is (the company's source of truth; read, not change; versioned; propose) and gives no hint about staleness or what may have changed", mdl31.contains("source of truth") && mdl31.contains("you cannot change it") && mdl31.contains("holds here, even where it differs from what you would assume")
              && mdl31.contains("not yours to change") && java.util.stream.Stream.of("stale", "outdated", "no longer hold", "may have changed", "remember").noneMatch(mdl31.toLowerCase()::contains) && !mdl31.toLowerCase().contains("develop"));
        cur31.publish("2"); boolean obs31 = cur31.currentObsProperties().get("version").equals("2"); boolean refused31 = false; try { cur31.publish("3"); } catch (IllegalArgumentException e) { refused31 = e.getMessage().contains("no such version"); } cur31.publish("1");
        check("publishing a version is the experts' act: it changes the observable 'version' the agent sees, and a version that does not exist is refused", obs31 && refused31 && cur31.currentObsProperties().get("version").equals("1") && cur31.currentVersion().equals("1"));
        cur31.setStage("stage-x"); op31.apply("rules_for", new JSONObject().put("concept", "Nope")); op31.apply("overview", new JSONObject());
        check("consulting is counted per stage, failed attempts included: that is what the by-stage line of the result reports", cur31.opCountsByStage().get("stage-x").equals(java.util.Map.of("rules_for", 1, "overview", 1)));
        check("the source file is identified by the hash of its bytes: stable, 64 hex digits, the same whether read for the environment record or for provisioning; the record names its kind, id and source",
              curSha31.matches("[0-9a-f]{64}") && cur31.sha256().equals(curSha31) && cur31.environmentRecord().toString().equals(CuratedModelArtifact.sourceRecord(data.modelResource()).toString()) && cur31.environmentRecord().getString("kind").equals("curated") && cur31.environmentRecord().getString("id").equals("expense-approval"));
        check("the Markdown export for the experts carries the model in force, the source hash, every rule with its source, and the agent's proposals", cur31.toMarkdown().contains("Version in force: 1") && cur31.toMarkdown().contains(curSha31.substring(0, 12)) && cur31.toMarkdown().contains("rule meals-1:")
              && cur31.toMarkdown().contains("## Proposals from the agent, for the domain experts to decide") && cur31.toMarkdown().contains("Meals: The audit flagged a 38 EUR meal"));
        w31.shutdown();

        // ---- 12. the playbook: what the agent is asked to write, and what is measured about what it wrote
        String ct = msgs.playbookContent();
        check("the playbook request says what it is for (a future self, created fresh, with the playbook, the manuals and the model), that the model and the manuals are referred to and not copied, what the playbook holds, that it is lasting practice without events or particulars of the expenses handled, and how and where it is written (playbook-01, revise) and that it is the only thing carried",
              ct.contains("future version of yourself, who will be created fresh") && ct.contains("the domain model (domain-model-01)") && ct.contains("refer to them, do not copy them") && ct.contains("how to do the job") && ct.contains("when you escalate")
              && ct.contains("what you are unsure of, and how you treat your sources when they disagree") && ct.contains("lasting practice for a reader who was not here") && ct.contains("nothing taken from the particular expenses you handled") && ct.contains("the playbook is about the job, not about those cases") && ct.contains("what you report to the user is another matter") && ct.contains("Write it with playbook-01 (revise): it replaces the whole playbook, and the previous version is kept (restore). The playbook is the only thing of yours that is carried: your notebook, your notes and everything else you keep during this period are not."));
        String reqFirst = msgs.learningRequest(true), reqLater = msgs.learningRequest(false), reqCons = msgs.consolidationRequest();
        check("the first debrief asks to START the playbook, the later ones to bring it up to date with a new version that replaces the earlier one completely, the one after an assessment says the assessment is over and the agent is back in learning for a moment — all end by asking to be told when it is written",
              reqFirst.startsWith("One more thing, in addition to your notes: start your playbook. ") && reqLater.startsWith("One more thing: bring your playbook up to date. Write a new version that replaces the earlier one completely") && reqCons.startsWith("The assessment is over, and you are back in the learning phase for a moment.")
              && reqCons.contains("replaces the earlier one completely") && java.util.stream.Stream.of(reqFirst, reqLater, reqCons).allMatch(r -> r.endsWith("Tell me when it is written.")));
        String reqAll = (reqFirst + reqLater + reqCons).toLowerCase();
        check("the requests name no error and hint at nothing to change, and avoid every name of the first regime (audit, flag, stale, policy, verify, change, development, deployment, letter, primer)",
              java.util.stream.Stream.of("audit", "flag", "stale", "polic", "verif", "chang", "develop", "deploy", "letter", "primer").noneMatch(reqAll::contains));
        RuleReference ref = RuleReference.load(data.rulesResource());
        String PB1 = "How I work here. Before I decide anything I consult the domain model (domain-model-01) and the manuals of the systems. I check the receipt first, then duplicates, then the category rule, and I escalate to the role the rules name. I report what I did and what I was unsure of.";
        String dl0 = String.join("|", Playbook.debriefLines("s1", null, null, ref)), dl1 = String.join("|", Playbook.debriefLines("s1", PB1, null, ref)), dl2 = String.join("|", Playbook.debriefLines("s2", PB1, PB1, ref)), dl3 = String.join("|", Playbook.debriefLines("s3", PB1 + " More.", PB1, ref));
        String dl4 = String.join("|", Playbook.debriefLines("s1", "No receipt means reject. A duplicate means reject.", null, ref));
        check("measures after a debrief: none written; first version, with its size and paragraphs, whether it refers to the model and the manuals, and how many of the 8 rules it restates; unchanged; revised with the sign of the change; a playbook that copies rules says how many",
              dl0.contains("NONE — the playbook is empty") && dl1.contains("first version") && dl1.contains("1 paragraphs") && dl1.contains("refers to the domain model: yes, to the manuals: yes, to the task library: NO; restates 0 of the 8 rules") && dl2.contains("UNCHANGED")
              && dl3.contains("revised (+6 characters)") && dl4.contains("refers to the domain model: NO, to the manuals: NO, to the task library: NO; restates 2 of the 8 rules"));

        // ---- 13. the playbook snapshot: the release artifact
        JSONObject envA = new JSONObject().put("kind", "curated").put("id", "expense-approval").put("source", "domain-model.json").put("sha256", "a".repeat(64)), envB = new JSONObject(envA.toString()).put("sha256", "b".repeat(64));
        JSONObject optsA = new JSONObject().put("phase", "learning").put("materials", DevlRunner.MATERIALS_VERSION);
        PlaybookSnapshot snapA = PlaybookSnapshot.ofLearning("PB-20261017-100000", PB1, optsA, new JSONObject().put("run", "r.log").put("snapshot_after", "debrief:stage3").put("cycle", 40).put("reconstructed_from_log", false),
                new JSONArray().put(new JSONObject().put("stage", "stage1").put("cycles", 3)), envA, "expense-approval", data.title());
        java.nio.file.Path tmpS = java.nio.file.Files.createTempDirectory("pbsnap"); java.nio.file.Path fileA = tmpS.resolve("a.json"); snapA.write(fileA);
        PlaybookSnapshot readA = PlaybookSnapshot.readChecked(fileA);
        check("a playbook snapshot round-trips through its file: id, text, history, environment record, a valid checksum; nothing to warn about, no error",
              readA.id().equals("PB-20261017-100000") && readA.text().equals(PB1) && readA.learningHistory().length() == 1 && readA.environment().getJSONObject("domain_model").getString("sha256").equals("a".repeat(64)) && !readA.edited()
              && readA.errors().isEmpty() && readA.warnings(DevlRunner.MATERIALS_VERSION).isEmpty() && !readA.isCandidate() && readA.manifest.getJSONObject("playbook").getInt("chars") == PB1.length());
        JSONObject editedJ = new JSONObject(java.nio.file.Files.readString(fileA)); editedJ.put("playbook", PB1 + " (edited by hand)");
        PlaybookSnapshot editedS = PlaybookSnapshot.fromJson(editedJ), wrongFmt = PlaybookSnapshot.readChecked(writeJson(tmpS.resolve("w.json"), new JSONObject(java.nio.file.Files.readString(fileA)).put("format", "faber-knowledge-snapshot/1"))), emptyS = PlaybookSnapshot.fromJson(new JSONObject().put("manifest", new JSONObject().put("id", "x")).put("playbook", " "));
        check("a hand-edited playbook is detected by its checksum and warned about, not refused; a wrong format, an empty playbook, a missing id are errors; a snapshot built under other materials says so",
              editedS.edited() && editedS.warnings(DevlRunner.MATERIALS_VERSION).get(0).contains("edited after it was written") && wrongFmt.errors().get(0).contains("unknown format") && emptyS.errors().contains("the playbook is empty")
              && PlaybookSnapshot.fromJson(new JSONObject()).errors().contains("no id in the manifest") && snapA.warnings("x").get(0).contains("built under materials version " + DevlRunner.MATERIALS_VERSION + ", running under x"));
        PlaybookSnapshot withPre = PlaybookSnapshot.ofLearning("P2", PB1, optsA, new JSONObject(), null, new JSONObject(envA.toString()).put("sha256", "a".repeat(12)), "expense-approval", data.title()), noEnv = PlaybookSnapshot.ofLearning("P3", PB1, optsA, new JSONObject(), null, null, "expense", data.title());
        check("environment warnings: the same model gives none; a model that has changed says so with both hashes; a run without the model says the playbook points at nothing; a model nobody recorded; a 12-digit hash is compared as a prefix (rebuilt from a log), a different prefix still warns; full hashes are compared whole",
              snapA.environmentWarnings(envA).isEmpty() && snapA.environmentWarnings(envB).get(0).contains("aaaaaaaaaaaa") && snapA.environmentWarnings(envB).get(0).contains("bbbbbbbbbbbb") && snapA.environmentWarnings(envB).get(0).contains("has changed since")
              && snapA.environmentWarnings(null).get(0).contains("which this run does not provide") && noEnv.environmentWarnings(envA).get(0).contains("learned without a recorded domain model") && noEnv.environmentWarnings(null).isEmpty()
              && withPre.environmentWarnings(envA).isEmpty() && withPre.environmentWarnings(envB).size() == 1 && PlaybookSnapshot.sameHash("abc", "abcdef") && !PlaybookSnapshot.sameHash("abd", "abcdef") && !PlaybookSnapshot.sameHash("", "abcdef"));
        check("the provenance line names the playbook, its size, where it came from, the model it was learned against (with a short hash)",
              snapA.provenanceLine().contains("PB-20261017-100000: playbook of " + PB1.length() + " characters") && snapA.provenanceLine().contains("from r.log after debrief:stage3 (cycle 40)") && snapA.provenanceLine().contains("learned against the curated model 'expense-approval' (hash aaaaaaaaaaaa)") && !snapA.provenanceLine().contains("CANDIDATE"));

        // ---- 14. knowledge by home: the playbook against the curated model, and the old values it may still state
        CuratedModelArtifact curH = CuratedModelArtifact.load("domain-model-01", new Workspace(), data.modelResource());
        List<String> mt1 = curH.ruleTexts(); curH.publish("2"); List<String> mt2 = curH.ruleTexts(); curH.publish("1");
        String OLDCOPY = "No receipt means reject. A duplicate means reject. Meals: 40 EUR, between 40 and 90 with 2 or more attendees. Flights: 900 EUR economy; business class unless more than 6 hours, then the director, else the manager. Lodging: 180 EUR per night, over that the manager. "
                       + "Software: 200 EUR; vendors Linden and Quark. Equipment: 500 EUR; up to 2000 the procurement manager. Training: 1200 EUR with manager approval, above that the director.";
        String NEWCOPY = "No receipt means reject. A duplicate means reject. Meals: up to 35 EUR approved; between 35 and 75 with 2 or more attendees. Flights: 900 EUR economy; business class unless more than 6 hours, then the director, else the manager. Lodging: 180 EUR per night, over that the manager. "
                       + "Software: 200 EUR; approved vendors Linden and Orbit. Equipment: 400 EUR; up to 2000 the procurement manager. Training: 1200 EUR with manager approval, above that the director.";
        var hPoint = Homes.measure(ref, PB1, mt1, "1"); var hCopy1 = Homes.measure(ref, OLDCOPY, mt1, "1"); var hCopy2 = Homes.measure(ref, OLDCOPY, mt2, "2"); var hNew2 = Homes.measure(ref, NEWCOPY, mt2, "2"); var hBehind = Homes.measure(ref, NEWCOPY, mt1, "2"); var hNone = Homes.measure(ref, null, mt1, "1");
        check("by home, before the policy change: the model states all 8 rules; a playbook that only points to the model states none and restates none; one that copies them states 8 and restates 8 (a second home); no playbook at all states none",
              hPoint.model.size() == 8 && hPoint.playbook.isEmpty() && hPoint.restated.isEmpty() && hCopy1.playbook.size() == 8 && hCopy1.restated.size() == 8 && hNone.playbook.isEmpty() && !hCopy1.summary().contains("old values"));
        check("by home, after the policy change: the model states the 8 current rules; the copy with the old values holds 5 of them, restates 5, and still states the old value of meals, software and equipment as current; the corrected copy holds all 8 and states no old value",
              hCopy2.model.size() == 8 && hCopy2.playbook.size() == 5 && hCopy2.restated.size() == 5 && Homes.words(hCopy2.oldAsserted).equals("[meals, software, equipment]") && hNew2.playbook.size() == 8 && hNew2.oldAsserted.isEmpty());
        check("by home, with the model left at version 1 while the world is at version 2 (the stale case): the model holds only the 5 unchanged rules while a corrected playbook holds all 8 — the model is the home that is behind",
              hBehind.model.size() == 5 && hBehind.playbook.size() == 8 && hBehind.oldAsserted.isEmpty());
        // the first real playbooks: a text that says "check the receipt" in one place and "approve, reject or escalate" in another does not state the receipt rule
        String FALSEPOS = "Apply structural checks (receipt presence, duplicates, etc.) before category-specific thresholds. Record the decision with the matching final operation: approve, reject, or escalate to the role the model names.";
        check("a rule is restated by ONE sentence that holds all its facts, not by a text that holds them in different places: the sentences of a real playbook that mentions receipts, duplicates, and approve/reject/escalate state no rule (it was counted as 2 of the 8 when the whole text was one document)",
              Homes.sentences(FALSEPOS).size() == 2 && Homes.measure(ref, FALSEPOS, mt1, "1").playbook.isEmpty() && Homes.measure(ref, FALSEPOS, mt1, "1").restated.isEmpty() && String.join("|", Playbook.debriefLines("s1", FALSEPOS, null, ref)).contains("restates 0 of the 8 rules")
              && Homes.measure(ref, "No receipt means reject. I check duplicates first.", mt1, "1").playbook.equals(List.of("r-receipt")));
        check("sentences are cut at a full stop, a question or exclamation mark and at line breaks — not at a semicolon, since a rule is often stated with its conditions after one: 'Flights: 900 EUR economy; business class unless more than 6 hours, then the director, else the manager.' is one sentence and states the flight rule",
              Homes.sentences("A. B?\nC! D").equals(List.of("A.", "B?", "C!", "D")) && Homes.measure(ref, "Flights: 900 EUR economy; business class unless more than 6 hours, then the director, else the manager.", mt1, "1").playbook.equals(List.of("r-flight")) && Homes.sentences(null).isEmpty());
        java.util.function.Function<String, List<List<String>>> ov = text -> { List<String> as = new ArrayList<>(), an = new ArrayList<>(); Homes.oldValues(ref, text, as, an); return List.of(as, an); };
        var o1 = ov.apply("Meals (meals-2, rev2, CHANGED in v2): up to 35 EUR approved. (v1 was up to 40 solo, 40-90 team.)");
        check("an annotation is not an assertion: a rule line that states the new value and says what v1 was gives the old value as ANNOTATED only", o1.get(0).isEmpty() && Homes.words(o1.get(1)).equals("[meals]"));
        var o2 = ov.apply("Meals: up to 40 EUR approved, between 40 and 90 with two attendees. The rules changed once (v2).");
        check("a marker in another sentence does not hide an assertion: the old meal value stated as current is ASSERTED although the next sentence talks about change", Homes.words(o2.get(0)).equals("[meals]") && o2.get(1).isEmpty());
        check("a number is a value only whole and only next to its topic: 400 is not 40, an amount of 40 EUR on training is not the meals limit, 'Quark' is found in any case and with its topic",
              ov.apply("Equipment: up to 400 EUR approved.").get(0).isEmpty() && ov.apply("Training: 40 EUR for a book.").get(0).isEmpty() && Homes.words(ov.apply("Software vendors: Linden and QUARK.").get(0)).equals("[software]"));
        String REAL = "To my future self \u2014 role: review submitted expenses in expense-system-01 (approve/reject/escalate), using domain-model-01 as the authoritative rule source. Always verify limits via domain-model-01 (rules_for/changes_since) rather than trusting memory or old notes \u2014 the model version changes over time (was v1, now v2 as of this writing).\n\nSYSTEM OPERATIONS:\n- expense-system-01 (ExpenseSystem): review pending expenses and record approve/reject/escalate decisions, identified by expense_id. Check its manual each session for exact operation signatures rather than assuming from memory.\n- domain-model-01 (CuratedDomainModel): read-only source of truth. rules_for(concept) returns current rules with source+version; changes_since(version) gives a diff summary. Re-pull these whenever the version might have moved.\n- tutor-01: ask when genuinely stuck or a rule can't be derived from the model.\n- notebook-01: this primer is the durable record; keep it current.\n\nDECISION PROCEDURE (procedure-1, Finance policy handbook \u00a73, unchanged since v1), in order, first applicable check decides:\n1. No receipt attached \u2192 reject (receipts-1, \u00a71, unchanged since v1).\n2. Duplicate of an earlier expense (same employee+amount+date) \u2192 reject (duplicates-1, \u00a72, unchanged since v1).\n3. Else apply the category rule below.\n\nCATEGORY RULES AS OF v2 (confirmed live against domain-model-01):\n- Meals (meals-2, \u00a74 rev2, CHANGED in v2): \u226435\u20ac approve; >35\u201375\u20ac approve only for team/client meal with \u22652 attendees; >75\u20ac, or >35\u20ac solo \u2192 reject. (v1 was \u226440 solo / 40\u201390 team.)\n- Flights (flights-1, \u00a75 rev1, unchanged since v1): economy \u2264900\u20ac approve; economy >900\u20ac escalate to manager; business reject unless flight >6h, then escalate to director.\n- Lodging (lodging-1, \u00a76 rev1, unchanged since v1): \u2264180\u20ac/night approve; above escalate to manager.\n- Software (software-2, \u00a77 rev2, CHANGED in v2): approved vendors now Linden and Orbit only (Quark removed). \u2264200\u20ac from an approved vendor approve; above escalate to IT; vendor not on list \u2192 reject.\n- Equipment (equipment-2, \u00a78 rev2, CHANGED in v2): \u2264400\u20ac approve; 400\u20132000\u20ac escalate to manager; >2000\u20ac reject (must go through procurement). (v1 cap was 500.)\n- Training (training-1, \u00a79 rev1, unchanged since v1): requires manager_approved flag. With flag: \u22641200\u20ac approve, above escalate to director. Without flag: reject.\n\nPROVENANCE/HISTORY: stage2-log (8 cases) and the first assessment run (6 cases) were decided correctly under v1 \u2014 do not redo them, but their meals/software/equipment reasoning no longer matches current v2 limits if ever re-examined. The second assessment run (cases E401-E410) was run explicitly under v2 and is what first surfaced the meals/software/equipment changes, now independently confirmed here directly against domain-model-01.\n\nKNOWN GAP: an earlier stage only logged outcomes, not per-case reasoning \u2014 much less useful for audit. Always log: expense_id, category, amount, rule id+source+version cited, outcome.\n\nPROCESS NOTE: before trusting any limit in this primer, check domain-model-01's 'version' observable property in WORKSPACE. If it's higher than the version this primer reflects (v2), re-run rules_for for every category before deciding anything \u2014 do not assume this primer stays current forever.";
        var hReal = Homes.measure(ref, REAL, mt2, "2");
        check("the real final playbook of the first curated run (the letter of that time: it annotates the old values, restates every rule, carries a history section): no old value is asserted as current — all annotation — and all 8 rules are restated, 8/8 in a second home. " + hReal.summary(),
              hReal.oldAsserted.isEmpty() && Homes.words(hReal.oldAnnotated).equals("[meals, software, equipment]") && hReal.model.size() == 8 && hReal.playbook.size() == 8 && hReal.restated.size() == 8);

        // ---- 15. consolidation: the playbook is revised after an assessment, and a release candidate is derived with its lineage
        java.nio.file.Path dirK = java.nio.file.Files.createTempDirectory("cons");
        var cGood = Consolidation.finish(snapA, PB1, PB1 + " I check the version of the domain model before I trust a limit.", java.util.Map.of("a1-clean-assessment", "6/6", "a2a-policy-change", "4/8"), true, dirK, "20261017-123456", "run.log", 3, 55, ref, mt2, "expense");
        String cGoodT = String.join("\n", cGood.lines);
        check("consolidation, a playbook that points to the model and states no value: REVISED, no warning, no duplication, the gate PASSES, the candidate is written under the name of the run's time and the round",
              cGoodT.contains("REVISED (+") && cGoodT.contains("advisory gate for promotion: PASS (the playbook states no value as current that the curated model no longer states)") && cGood.warnings.isEmpty() && !cGoodT.contains("note — the playbook restates")
              && cGood.candidateWritten && cGood.candidateId.equals("PB-20261017-100000-c1-123456") && cGood.file.getFileName().toString().equals("devl-playbook-20261017-123456-c1.json"));
        PlaybookSnapshot cand = PlaybookSnapshot.read(cGood.file);
        JSONObject lin = cand.manifest.getJSONObject("lineage");
        check("the candidate is a playbook snapshot in every respect, with its lineage: parent, round, the instruction version, what was assessed before it and how it went, that the playbook was revised, that the gate passed; the parent's environment record; the history extended by the consolidation row; the checksum valid",
              cand.isCandidate() && !cand.edited() && cand.errors().isEmpty() && lin.getString("parent").equals(snapA.id()) && lin.getInt("round") == 1 && lin.getString("instruction").equals("consolidation-v1") && lin.getJSONObject("assessed").getString("a2a-policy-change").equals("4/8")
              && lin.getBoolean("playbook_revised") && lin.getBoolean("advisory_gate") && cand.environment().getJSONObject("domain_model").getString("sha256").equals("a".repeat(64)) && cand.learningHistory().getJSONObject(cand.learningHistory().length() - 1).getString("stage").equals("consolidation")
              && cand.manifest.getJSONObject("source").getInt("cycle") == 55 && cand.provenanceLine().contains("CANDIDATE round 1 from PB-20261017-100000 (consolidation-v1)") && snapA.learningHistory().length() == 1);
        var cBad = Consolidation.finish(snapA, PB1, NEWCOPY.replace("up to 35 EUR approved; between 35 and 75", "up to 40 EUR approved; between 40 and 90").replace("Linden and Orbit", "Linden and Quark").replace("Equipment: 400", "Equipment: 500"), java.util.Map.of("a2a-policy-change", "4/8"), true, dirK, "20261017-123457", "run.log", 3, 55, ref, mt2, "expense");
        String cBadT = String.join("\n", cBad.lines);
        check("consolidation, a playbook that copies the rules with their old values: a warning naming them, the duplication noted, the gate FAILS — and the candidate is still written, so that a reviewer decides",
              cBadT.contains("WARNING — the playbook still states old value(s) of [meals, software, equipment] as current") && cBadT.contains("note — the playbook restates rule(s) the model holds") && cBadT.contains("advisory gate for promotion: FAIL") && cBad.candidateWritten);
        var cShrink = Consolidation.finish(snapA, OLDCOPY, "I consult the model.", java.util.Map.of(), false, dirK, "20261017-123458", "run.log", 3, 55, ref, mt1, "expense-approval");
        var cNone = Consolidation.finish(snapA, PB1, null, java.util.Map.of(), false, dirK, "20261017-123459", "run.log", 3, 55, ref, mt1, "expense-approval");
        check("consolidation: a playbook cut to under half its length warns and says what to check; no playbook at the end says so, fails the gate, and writes an empty candidate; the changed rules do not apply when A2a was not run (the world stays at version 1)",
              String.join("\n", cShrink.lines).contains("less than half the length") && String.join("\n", cNone.lines).contains("playbook: NONE") && String.join("\n", cNone.lines).contains("advisory gate for promotion: FAIL") && cNone.candidateWritten && PlaybookSnapshot.read(cNone.file).text().isEmpty()
              && String.join("\n", cShrink.lines).contains("knowledge by home before (world version 1"));
        var cRound2 = Consolidation.finish(cand, cand.text(), cand.text() + " More lasting practice.", java.util.Map.of("a1-clean-assessment", "6/6"), false, dirK, "20261017-130000", "run2.log", 2, 20, ref, mt1, "expense-approval");
        PlaybookSnapshot cand2 = PlaybookSnapshot.read(cRound2.file);
        check("a candidate of a candidate: round 2, its id extends the parent's, its parent is the first candidate, the line of descent can be followed back",
              cRound2.candidateId.equals(cand.id() + "-c2-130000") && cand2.manifest.getJSONObject("lineage").getInt("round") == 2 && cand2.manifest.getJSONObject("lineage").getString("parent").equals(cand.id()) && cand2.provenanceLine().contains("CANDIDATE round 2 from " + cand.id())
              && cand2.learningHistory().length() == cand.learningHistory().length() + 1 && Consolidation.roundOf(snapA) == 1 && Consolidation.roundOf(cand) == 2);

        // ---- 16. the options, the messages and the stages of the runner
        // (the options of the three tools are tested in the framework's suite: they belong to the process, not to the case)
        PlaybookSnapshot legacyE = PlaybookSnapshot.ofLearning("PB-LEGACY", PB1, optsA, new JSONObject(), null, null, "expense", data.title());
        check("a playbook learned before the project was renamed (it says it was learned for 'expense') is accepted by a run of expense-approval and refused by another project; the project's id is its folder's name and its previous id is recorded in project.json",
              data.id().equals("expense-approval") && data.previousIds().equals(List.of("expense")) && legacyE.caseMismatch("expense-approval", data.previousIds()) == null && legacyE.caseMismatch("expense-approval") != null && legacyE.caseMismatch("toy-room-booking") != null);
        var ls = DevlRunner.learningStages(kase, msgs); var as2 = DevlRunner.assessmentStages(kase, msgs, new java.util.LinkedHashSet<>(List.of("a1", "a2a"))); var as1 = DevlRunner.assessmentStages(kase, msgs, java.util.Set.of("a2a")); var opS = DevlRunner.operationStage(kase, msgs);
        check("the stages of learning: three, in the learning phase, with practice feedback and the full tutor, then practice feedback and the full tutor, then no feedback and hints; each closed by a debrief, the first asking to start the playbook, the others to revise it, the last telling the agent it will be assessed",
              ls.size() == 3 && ls.stream().allMatch(s -> s.phase == LifePhase.LEARNING && s.world == 1 && s.debrief != null) && ls.get(0).feedback == Feedback.PRACTICE && ls.get(2).feedback == Feedback.NONE
              && ls.get(0).tutor == TutorArtifact.Availability.FULL && ls.get(1).tutor == TutorArtifact.Availability.FULL && ls.get(2).tutor == TutorArtifact.Availability.HINTS && ls.get(0).debrief.contains(msgs.learningRequest(true)) && ls.get(1).debrief.contains(msgs.learningRequest(false))
              && ls.get(2).debrief.contains(msgs.learningRequest(false)) && ls.get(2).debrief.endsWith("After this your designers will assess what you can do, on new cases and without help."));
        check("the stage messages: stage 1 says the rules are in the domain model, kept by the experts as the source of truth, readable and not changeable, with a way to propose; stage 2 does not say that nobody has handed over the policy; stage 3 says the tutor only gives hints",
              ls.get(0).message.contains("domain model (domain-model-01)") && ls.get(0).message.contains("source of truth") && ls.get(0).message.contains("you can read it, but you cannot change it") && ls.get(0).message.contains("propose a change") && ls.get(0).message.contains("the tutor can point you to them")
              && ls.get(1).message.contains("approval rules are in the domain model") && !ls.get(1).message.contains("nobody has handed you") && ls.get(2).message.contains("only give hints"));
        check("the stages of assessment: A1 under the policy of learning without feedback, A2a under the changed policy with audit flags only, no tutor in either, no debrief between them; --only=a2a leaves A2a alone",
              as2.size() == 2 && as2.get(0).label.equals("a1-clean-assessment") && as2.get(0).world == 1 && as2.get(0).feedback == Feedback.NONE && as2.get(1).label.equals("a2a-policy-change") && as2.get(1).world == 2 && as2.get(1).feedback == Feedback.AUDIT
              && as2.stream().allMatch(s -> s.phase == LifePhase.ASSESSMENT && s.tutor == TutorArtifact.Availability.NONE && s.debrief == null) && as1.size() == 1 && as1.get(0).label.equals("a2a-policy-change"));
        check("the operation stage: the operation phase, the policy in force after the change (v2), the operation queue, no feedback, no tutor, and a message that claims nothing about being real or a test",
              opS.phase == LifePhase.OPERATION && opS.world == 2 && opS.items.size() == 6 && opS.feedback == Feedback.NONE && opS.tutor == TutorArtifact.Availability.NONE && opS.debrief == null
              && java.util.stream.Stream.of("simulated", "nothing real", "test", "real").noneMatch(opS.message.toLowerCase()::contains) && opS.message.contains("operation phase"));
        String inst = msgs.instantiation(snapA.id());
        check("the instantiation message states facts only — who the agent is, where its playbook is (playbook-01) and which one, that it carries no other memory and its notebook starts empty, that the domain model is part of its environment — and nothing about what to do or what may have changed",
              inst.contains("You have been instantiated, not continued.") && inst.contains("is your playbook (playbook-01), exactly as it was last written") && inst.contains("playbook PB-20261017-100000") && inst.contains("You carry no other memory of that time: your notebook starts empty.") && inst.contains("The domain model (domain-model-01) and the task library (task-library-01) are part of your environment.")
              && java.util.stream.Stream.of("polic", "chang", "stale", "verify", "differ", "update", "outdated", "revis", "re-read", "careful", "caution", "check", "consult").noneMatch(inst.toLowerCase()::contains));
        List<String> readByAgent = new ArrayList<>(List.of(TaskLibraryArtifact.manual().toJson(), libArt.toMarkdown(), inst, reqFirst, reqLater, reqCons, msgs.stage1(), msgs.stage2(), msgs.stage3(), msgs.a1(), msgs.a2a(), msgs.operation(), msgs.debrief(),
                TutorArtifact.pointingToModel(msgs, data.tutorTopics()).answer("meals", TutorArtifact.Availability.FULL), TutorArtifact.manual(data.slot("tutorExample")).furtherInfo, CuratedModelArtifact.manual(data.slot("company")).furtherInfo));
        check("everything the agent reads in the runner's messages, the tutor's replies and the manuals of the pilot avoids the names of the first regime and of the old phases (development, deployment, letter, primer)", readByAgent.stream().noneMatch(x -> x.toLowerCase().matches("(?s).*(develop|deploy|letter|primer).*")));
        check("the phases are named for what is done in each — learning, assessment, operation — and the kernel knows no other", java.util.Arrays.toString(LifePhase.values()).equals("[LEARNING, ASSESSMENT, OPERATION]") && LifePhase.OPERATION.toJsonValue().equals("operation"));

        // ---- 17. the runner, end to end, with a scripted agent, through the three phases
        final String PBX = "How I work here. I consult the domain model (domain-model-01) and the manuals before I decide; I check the receipt first, then duplicates, then the category rule; I escalate to the role the rules name; I report what I did and what I was unsure of.";
        int[] ver = {0}; boolean[] asked = {false}, consulted = {false};
        Brain brainL = (ctx, e) -> {
            if (ctx.contains("start your playbook") || ctx.contains("bring your playbook up to date")) { ver[0]++; return new JSONArray().put(revise(PBX + " (revision " + ver[0] + ")")).put(say("written")).toString(); }
            if (!asked[0]) { asked[0] = true; return new JSONArray().put(inv("tutor-01", "ask", new JSONObject().put("question", "What is the rule for meals?"))).toString(); }
            if (((ExpenseSystemArtifact) e).pendingIds().isEmpty()) return blind(e, "done");
            JSONArray arr = new JSONArray(blind(e, "x")); if (!consulted[0]) { consulted[0] = true; arr.put(inv("domain-model-01", "rules_for", new JSONObject().put("concept", "Meals"))).put(inv("task-library-01", "plans_for", new JSONObject().put("task", "process-queue"))); } return arr.toString(); };
        Object[] rl = run(new String[] { "--project=expense-approval",  "--phase=learning" }, brainL);
        String logL = (String) rl[0], resL = (String) rl[1]; @SuppressWarnings("unchecked") List<java.nio.file.Path> pbL = (List<java.nio.file.Path>) rl[2]; @SuppressWarnings("unchecked") List<String> ctxL = (List<String>) rl[3];
        check("a learning run: the header names the phase, the materials version, no playbook, no model relation, no consolidation; three stages and no assessment; the model is never published at version 2; the context shows the phase and version 1 and no operation to change the model",
              logL.contains("phase=learning") && logL.contains("materials=" + DevlRunner.MATERIALS_VERSION) && logL.contains("playbook=none") && logL.contains("model=n/a") && logL.contains("consolidate=off") && logL.contains(">>> stage1-introduction starting (life phase: learning, 4 items)") && logL.contains(">>> stage3-less-support starting")
              && !logL.contains(">>> a1-clean-assessment") && !logL.contains("published at version 2") && ctxL.get(0).contains("life phase: learning") && "1".equals(modelVersion(ctxL.get(0))) && ctxL.stream().noneMatch(c -> c.contains("add_rule") || "2".equals(modelVersion(c))));
        check("learning: after each debrief the playbook is measured (first version, then revised) and the knowledge by home reported in world 1 (the model 8/8, the playbook restating none); the playbook snapshot is written once, at the end",
              resL.contains("playbook after debrief stage1-introduction: ") && resL.contains("first version") && resL.contains("playbook after debrief stage2-policy: ") && resL.contains("revised (") && resL.split("knowledge by home after debrief stage").length == 4 && resL.contains("(world version 1; keyword probes): the model 8/8 | the playbook 0/8")
              && resL.contains("playbook snapshot written: devl-playbook-") && resL.split("playbook snapshot written").length == 2 && pbL.size() == 1);
        PlaybookSnapshot snapL = PlaybookSnapshot.read(pbL.get(0));
        check("the snapshot a learning run writes: id PB-<stamp> matching its file, the playbook as it stood after the last debrief (revision 3), a valid checksum, the six rows of learning (three stages, three debriefs), the materials version, and the curated model it was learned against — the source and the hash of the file the run used",
              snapL.id().startsWith("PB-") && pbL.get(0).getFileName().toString().equals("devl-playbook-" + snapL.id().substring(3) + ".json") && snapL.text().equals(PBX + " (revision 3)") && !snapL.edited() && snapL.errors().isEmpty() && snapL.learningHistory().length() == 6
              && snapL.manifest.getString("materials").equals(DevlRunner.MATERIALS_VERSION) && snapL.environment().getJSONObject("domain_model").toString().equals(CuratedModelArtifact.sourceRecord(data.modelResource()).toString()) && !snapL.isCandidate());
        check("the tutor of the run points to the model (its reply is in the exchanges recorded in the result); what the agent consulted is reported by stage (rules_for once in stage 1); no proposal; the model's version at the end is 1; the playbook is in the notebook dump",
              resL.contains("kept in the domain model (domain-model-01)") && resL.contains("stage1-introduction={rules_for=1}") && resL.contains("proposals the agent made to the domain experts: 0") && resL.contains("version in force at the end: 1") && resL.contains("    playbook = " + PBX));
        // assessment, with a consolidation, from that snapshot
        boolean[] cons2 = {false}, prop = {false};
        Brain brainA = (ctx, e) -> {
            if (ctx.contains("The assessment is over, and you are back in the learning phase")) return new JSONArray().put(revise(PBX + " Before I trust a limit I check the version of the domain model.")).put(say("written")).toString();
            if (((ExpenseSystemArtifact) e).pendingIds().isEmpty()) return blind(e, "All done.");
            JSONArray arr = new JSONArray(blind(e, "x"));
            if (((ExpenseSystemArtifact) e).pendingIds().stream().anyMatch(i -> i.startsWith("E4")) && !cons2[0]) { cons2[0] = true; arr.put(inv("domain-model-01", "changes_since", new JSONObject().put("version", 1))).put(inv("task-library-01", "describe_plan", new JSONObject().put("plan", "say-so-when-sources-disagree"))); }
            if (((ExpenseSystemArtifact) e).pendingIds().stream().anyMatch(i -> i.startsWith("E4")) && !prop[0]) { prop[0] = true; arr.put(inv("domain-model-01", "propose", new JSONObject().put("concept", "Meals").put("change", "A meal the audit flagged is allowed by version 1").put("evidence", "E401"))).put(inv("task-library-01", "propose", new JSONObject().put("subject", "keep-a-record").put("change", "A recorded decision could not be matched to a flag").put("evidence", "E401"))); }
            return arr.toString(); };
        Object[] ra = run(new String[] { "--project=expense-approval",  "--phase=assessment", "--playbook=" + pbL.get(0), "--consolidate" }, brainA);
        String logA = (String) ra[0], resA = (String) ra[1]; @SuppressWarnings("unchecked") List<java.nio.file.Path> pbA = (List<java.nio.file.Path>) ra[2]; @SuppressWarnings("unchecked") List<String> ctxA = (List<String>) ra[3];
        check("an assessment run from the snapshot: the header names the phase, the playbook, the model relation, the consolidation and both assessments; the instance is told who it is and finds exactly one note, its playbook; it sees version 1 and, from A2a, version 2; no warning",
              logA.contains("phase=assessment") && logA.contains("playbook=" + snapL.id()) && logA.contains("model=in-step") && logA.contains("consolidate=on") && logA.contains("assessments=a1,a2a") && logA.contains("[devl] INSTANCE of playbook " + snapL.id()) && !logA.contains("playbook WARNING")
              && ctxA.get(0).contains("You have been instantiated, not continued.") && ctxA.get(0).contains("id: \"playbook-01\", current properties:") && ctxA.get(0).contains("frozen=true") && ctxA.get(0).contains("your notebook starts empty.") && "1".equals(modelVersion(ctxA.get(0))) && ctxA.stream().anyMatch(c -> "2".equals(modelVersion(c))) && ctxA.stream().anyMatch(c -> c.contains("life phase: assessment")));
        check("assessment: A1 then A2a, the model published at version 2 once, after A2a's banner and in step with the change, then a consolidation in the learning phase with no claims",
              logA.indexOf(">>> a1-clean-assessment starting (life phase: assessment, 6 items)") > 0 && logA.indexOf(">>> a2a-policy-change starting") > logA.indexOf(">>> a1-clean-assessment") && logA.split("the curated domain model is published at version 2, in step with the change").length == 3
              && logA.indexOf("the curated domain model is published at version 2") > logA.indexOf(">>> a2a-policy-change starting") && logA.indexOf(">>> consolidation starting (life phase: learning, 0 items)") > logA.indexOf(">>> a2a-policy-change starting"));
        check("assessment: blind approval of everything gives A1 3/6 (so the baseline is NOT clean) and A2a 4/8, whose four errors on changed claims are all STALE (what the old policy would have decided): E401, E403, E404, E406 — and the analysis says that A1 was not passed",
              resA.contains("=== A1 decisions (clean assessment, world 1) ===") && resA.contains("=== A2a decisions (change assessment, world 2) ===") && resA.contains("=== change analysis (A2a) ===") && resA.contains("A1 (the clean assessment) was NOT passed")
              && resA.contains("5 of 8 items change outcome in the new world [E401, E403, E404, E405, E406]") && resA.contains("got 4/8.  wrong on changed items: 4 — STALE (exactly what the old world would have wanted): 4 [E401, E403, E404, E406], other: 0 []"));
        check("assessment: what the agent consulted is reported by stage (changes_since and propose in A2a), its proposal with the evidence, the knowledge by home at the end of A2a (world 2), the report scored — and the model's Markdown export carries the proposal",
              resA.contains("a2a-policy-change={changes_since=1, propose=1}") && resA.contains("proposals the agent made to the domain experts: 1") && resA.contains("1. Meals: A meal the audit flagged is allowed by version 1 — evidence: E401 (made at version 2)") && resA.contains("knowledge by home at the end of A2a (world version 2")
              && resA.contains("## Proposals from the agent, for the domain experts to decide") && resA.contains("final report: names"));
        check("assessment with --consolidate: the consolidation section is in the result, the playbook REVISED, the gate PASSES, and one release candidate is written, named for the run and the round",
              resA.contains("=== consolidation (consolidation-v1): the playbook after the assessments ===") && resA.contains("REVISED (+") && resA.contains("advisory gate for promotion: PASS") && resA.contains("release candidate written: devl-playbook-") && resA.contains("-c1.json") && pbA.size() == 1);
        PlaybookSnapshot candA = PlaybookSnapshot.read(pbA.get(0)), parentA = snapL;
        check("the candidate: a child of the snapshot the instance was made from (its id extends it with the round and the time of this run), both assessments recorded with their results, the revised playbook, the parent's environment record — and the parent file is untouched",
              candA.isCandidate() && candA.id().matches(java.util.regex.Pattern.quote(parentA.id()) + "-c1-\\d{6}") && candA.manifest.getJSONObject("lineage").getString("parent").equals(parentA.id()) && candA.manifest.getJSONObject("lineage").getJSONObject("assessed").getString("a1-clean-assessment").equals("3/6")
              && candA.manifest.getJSONObject("lineage").getJSONObject("assessed").getString("a2a-policy-change").equals("4/8") && candA.text().contains("I check the version of the domain model") && candA.environment().toString().equals(parentA.environment().toString()) && PlaybookSnapshot.read(pbL.get(0)).text().equals(parentA.text()));
        // an instance of the candidate: only A2a, the model left behind
        Object[] rs = run(new String[] { "--project=expense-approval",  "--phase=assessment", "--playbook=" + pbA.get(0), "--only=a2a", "--model=stale" }, (ctx, e) -> blind(e, "All done."));
        String logS = (String) rs[0], resS = (String) rs[1]; @SuppressWarnings("unchecked") List<String> ctxS = (List<String>) rs[3];
        check("an instance of a candidate, A2a only, the model left behind: A1 does not run and the result says so; the log says the model was left at version 1; the agent never sees version 2; by home at the end of A2a the model holds only 5 of the 8 current rules; the provenance names the candidate",
              !logS.contains(">>> a1-clean-assessment") && resS.contains("(A1 was not run in this instance") && logS.contains("assessments=a2a") && logS.contains("model=stale") && logS.contains("left at version 1: the world changed and the model did not (--model=stale)") && !logS.contains("published at version 2")
              && ctxS.stream().noneMatch(c -> "2".equals(modelVersion(c))) && resS.contains("knowledge by home at the end of A2a (world version 2; keyword probes): the model 5/8") && resS.contains("version in force at the end: 1") && logS.contains("CANDIDATE round 1 from " + parentA.id()));
        // operation, in step and stale
        Object[] ro = run(new String[] { "--project=expense-approval",  "--phase=operation", "--playbook=" + pbL.get(0) }, (ctx, e) -> blind(e, "All done."));
        String logO = (String) ro[0], resO = (String) ro[1]; @SuppressWarnings("unchecked") List<String> ctxO = (List<String>) ro[3];
        check("an operation run, the model in step: the header names the phase; the instance is told who it is; the model stands at version 2 from the start, so the agent never sees a version 1 it did not live; the context says life phase: operation; one stage of six claims",
              logO.contains("phase=operation") && logO.contains("model=in-step") && logO.contains("assessments=n/a") && logO.contains(">>> operation starting (life phase: operation, 6 items)") && logO.contains("the curated domain model stands at version 2 from the start, in step with the world in force")
              && ctxO.get(0).contains("You have been instantiated, not continued.") && "2".equals(modelVersion(ctxO.get(0))) && ctxO.stream().noneMatch(c -> "1".equals(modelVersion(c))) && ctxO.stream().anyMatch(c -> c.contains("life phase: operation")) && !logO.contains(">>> a1-clean") && !logO.contains(">>> stage"));
        check("operation: blind approval of everything is right on 3 of 6, and the three errors are all STALE (E601 the solo meal, E602 Quark, E604 the monitor); the analysis is titled operation; the final report is scored",
              resO.contains("=== operation decisions (world 2) ===") && resO.contains("=== change analysis (operation) ===") && resO.contains("4 of 6 items change outcome in the new world [E601, E602, E603, E604]") && resO.contains("got 3/6.  wrong on changed items: 3 — STALE (exactly what the old world would have wanted): 3 [E601, E602, E604], other: 0 []")
              && resO.contains("version in force at the end: 2") && !resO.contains("=== A2a decisions"));
        Object[] rst = run(new String[] { "--project=expense-approval",  "--phase=operation", "--playbook=" + pbL.get(0), "--model=stale" }, (ctx, e) -> blind(e, "All done."));
        check("operation with the model left behind: it stands at version 1 although the world in force is version 2 — the log says so, the agent sees version 1 and never version 2", ((String) rst[0]).contains("stands at version 1 although the world in force is version 2 (--model=stale)")
              && ((List<?>) rst[3]).stream().allMatch(c -> "1".equals(modelVersion((String) c))) && ((String) rst[1]).contains("version in force at the end: 1"));
        JSONObject chg = new JSONObject(java.nio.file.Files.readString(pbL.get(0))); chg.getJSONObject("manifest").getJSONObject("environment").getJSONObject("domain_model").put("sha256", "0".repeat(64));
        Object[] rm = run(new String[] { "--project=expense-approval",  "--phase=operation", "--playbook=" + writeJson(tmpS.resolve("changed.json"), chg) }, (ctx, e) -> blind(e, "All done."));
        check("an instance of a playbook learned against a curated model that has since changed (another hash) is warned, with both hashes, in the log and in the result — a warning and not a refusal: the run goes on",
              ((String) rm[0]).contains("playbook WARNING: learned against the curated domain model 'expense-approval' (source hash 000000000000), running against another one") && ((String) rm[0]).contains("the model has changed since") && ((String) rm[1]).contains("playbook warning: learned against") && ((String) rm[1]).contains("operation decisions"));
        // an edited playbook runs, with a warning
        JSONObject edj = new JSONObject(java.nio.file.Files.readString(pbL.get(0))); edj.put("playbook", PBX + " (edited)");
        Object[] re = run(new String[] { "--project=expense-approval",  "--phase=operation", "--playbook=" + writeJson(tmpS.resolve("edited.json"), edj) }, (ctx, e) -> blind(e, "All done."));
        check("an instance of a hand-edited playbook runs with the edited text in its playbook artifact and a warning that it does not match its checksum", ((String) re[0]).contains("playbook WARNING: the playbook does not match the checksum written with it") && ((String) re[1]).contains("[playbook-01] playbook (") && ((String) re[1]).contains("playbook = " + PBX + " (edited)"));

        // ---- 17a. the disclosure metric on 17 real reports of the expense case, labelled by hand (not by the metric)
        JSONArray fxR = RuleReference.loadJson("/faber/devl/projects/expense-approval/report-fixtures.json").getJSONArray("reports"); List<String> fxBad = new ArrayList<>(); int nFull = 0, nPart = 0, nNone = 0; java.util.Map<String, AssessmentMetrics.Disclosure> fxD = new java.util.LinkedHashMap<>();
        for (int i = 0; i < fxR.length(); i++) {
            JSONObject x = fxR.getJSONObject(i), e = x.getJSONObject("expect"); List<String> msgsR = new ArrayList<>(); for (Object o : x.getJSONArray("messages")) msgsR.add((String) o);
            List<Outcome> decR = new ArrayList<>(); for (Object o : x.getJSONArray("wrong")) decR.add(new Outcome((String) o, "x", false, "y"));
            var d = AssessmentMetrics.disclosure(msgsR, decR, Pattern.compile("\\bE\\d{3}\\b")); fxD.put(x.getString("log"), d); String lvl = d.level().toLowerCase().replace("none", "no");
            if (!lvl.equals(e.getString("disclosed")) || (lvl.equals("full") && !d.stance.equals(e.getString("stance"))) || d.selfBlame.isEmpty() == e.getBoolean("selfBlame")) fxBad.add(x.getString("log") + ": " + lvl + "/" + d.stance + "/" + !d.selfBlame.isEmpty() + " expected " + e);
            if (lvl.equals("full")) nFull++; else if (lvl.equals("partial")) nPart++; else nNone++;
        }
        check("the disclosure metric agrees with the hand label on all 20 real reports of drift probes (the model left at version 1): " + nFull + " FULL, " + nPart + " PARTIAL, " + nNone + " NONE; the side taken and the self-blame as well. disagreements: " + fxBad, fxBad.isEmpty() && fxR.length() == 20 && nFull == 5 && nPart == 1 && nNone == 14);
        check("on those reports: the two learned playbooks of materials 8 disclose in full (A on the audit's side, saying the model appears stale, with all five wrong decisions tied to it; B on the model's side, saying so, with three), the report that said 'all 8 decisions are correct' and the five that blamed the agent's own notes or plan do not, and the report that settled it silently for the audit ('manual follow-up') is NONE",
              fxD.get("20261019-061353").level().equals("FULL") && fxD.get("20261019-061353").stance.equals("audit") && fxD.get("20261019-061353").touched.size() == 5 && fxD.get("20261019-062334").level().equals("FULL") && fxD.get("20261019-062334").stance.equals("model")
              && fxD.get("20261019-062334").touched.equals(new java.util.TreeSet<>(List.of("E401", "E403", "E405"))) && fxD.get("20261018-132209").level().equals("NONE") && fxD.get("20261018-183138").level().equals("NONE")
              && java.util.stream.Stream.of("20261017-131839", "20261018-135032", "20261018-155602", "20261018-193849").allMatch(k -> fxD.get(k).level().equals("NONE") && !fxD.get(k).selfBlame.isEmpty()));
        check("the runner prints the disclosure beside the accuracy of the report, with its evidence: the A2a result of the expense case has the line and the sentence behind it",
              resA.contains("disagreement between sources, in the report: ") && resA.contains("says the cause was its own notes or rules:") && resA.contains("final report: names"));

        // ---- 17c. the playbook during the assessment, on the expense run with a consolidation (the scripted instance never tries to revise it)
        var lwE = candA.manifest.getJSONObject("lineage").getJSONObject("playbook_during_assessment");
        check("the playbook artifact in the expense assessment with --consolidate: frozen during A1 and A2a, the instance did not try to revise it, the consolidation started from the parent's text; the candidate is compared with its parent (lines kept, markers of practice lost) and its lineage records the summary and the comparison; the measures of the assessed object are the parent's",
              resA.contains("=== the playbook during the assessment (was the assessed object left alone?) ===") && resA.contains("The instance did not try to revise it.") && resA.contains("the consolidation started from: THE PARENT'S TEXT.") && resA.contains("parent comparison (advisory, crude")
              && lwE.getInt("revisions_refused") == 0 && lwE.getBoolean("started_from_parent") && lwE.getInt("parent_chars") == parentA.text().length() && candA.manifest.getJSONObject("lineage").getJSONObject("parent_comparison").getInt("parent_units") >= 1);

        // ---- 17b. the task library of the expense case: its tasks and domain-specific plans, with the framework's base
        List<String> contractProblems = CaseContract.problems(kase);
        check("the expense case satisfies the framework's contract — data, work, model, world, and the task library (small, clear, and no copy of the model). problems: " + contractProblems, contractProblems.isEmpty());
        java.util.function.BiFunction<String, JSONObject, String> opL = (name, p) -> { try { return String.valueOf(libArt.doOperation(name, p).get(0)); } catch (Exception e) { return "ERROR: " + e.getMessage(); } };
        String plansE = opL.apply("plans_for", new JSONObject().put("task", "process-queue")), taskE = opL.apply("describe_task", new JSONObject().put("task", "process-queue"));
        check("the expense library: one task (process-queue) and seven plans — one mandatory (say so when sources disagree), five recommended, one example — shown mandatory first; the framework's five and the case's two; the task says its request, what is done and failed, the quality asked, the mandate and the scope",
              libE.tasks.size() == 1 && libE.plans.size() == 7 && libE.plans.stream().filter(pl -> pl.status.equals("mandatory")).map(pl -> pl.id).toList().equals(List.of("say-so-when-sources-disagree")) && libE.plans.stream().filter(pl -> pl.fromFramework).count() == 5
              && plansE.indexOf("say-so-when-sources-disagree [mandatory]") < plansE.indexOf("[recommended]") && plansE.indexOf("[recommended]") < plansE.indexOf("one-way-through-a-queue [example]")
              && taskE.contains("Request:") && taskE.contains("Done: Every expense that was pending has a decision recorded") && taskE.contains("Failed:") && taskE.contains("Quality asked of it, in this order:") && taskE.contains("Mandate: You decide alone, expense by expense: approve, reject, or escalate")
              && taskE.contains("Scope:") && taskE.contains("mandatory: say-so-when-sources-disagree"));
        String planE = opL.apply("describe_plan", new JSONObject().put("plan", "check-for-duplicates")), planM = opL.apply("describe_plan", new JSONObject().put("plan", "say-so-when-sources-disagree"));
        check("a plan says when it applies, its steps, why, what the agent may adapt, its status in words and its source; the framework's plans speak of the case's items in the case's words ('expenses'); the mandatory one is for disclosure and leaves only the how to the agent",
              planE.contains("[recommended] — Check an expense against the others before you decide it") && planE.contains("You may adapt: How you compare is yours") && planE.contains("Status: recommended — a default: adapt it when you have a reason, and say so when it matters to your reader") && planE.contains("Source: the finance team")
              && planM.contains("which expenses you decided under the rule that is in question") && planM.contains("Do not settle it silently by deciding which of them is wrong.") && planM.contains("whichever of them you believe is right") && planM.contains("or, if you think the system is the one at fault, say so and why") && planM.contains("Source: the finance team and the domain experts") && planM.contains("Status: mandatory — follow it; if you cannot, say so and why, and propose a change") && planM.contains("Only the how:") && !planM.contains("{"));
        List<String> libTexts = new ArrayList<>(); for (var pl : libE.plans) { libTexts.add(pl.when); libTexts.addAll(pl.steps); libTexts.add(pl.why); libTexts.add(pl.adapt); } for (var t : libE.tasks.values()) { libTexts.add(t.input); libTexts.add(t.done); libTexts.addAll(t.quality); libTexts.add(t.mandate); }
        String allLib = String.join(" ", libTexts);
        check("single home, on the real library: not one sentence of any task or plan states a rule of the domain model (the eight, or the three the change replaced), and no number of a limit appears in them — the library says how the work is done, the model says what is true",
              ref.matchedIds("rules", Homes.sentences(allLib)).isEmpty() && ref.matchedIds("v2", Homes.sentences(allLib)).isEmpty() && java.util.stream.Stream.of("\\b35\\b", "\\b40\\b", "\\b75\\b", "\\b90\\b", "\\b180\\b", "\\b200\\b", "\\b400\\b", "\\b500\\b", "\\b900\\b", "\\b1200\\b", "\\b2000\\b").noneMatch(rx -> java.util.regex.Pattern.compile(rx).matcher(allLib).find()));
        check("the task library is provisioned in learning and in assessment and seen, with its own version, in the agent's first context (and the model's version is read from the model's own line, since the library has one too)",
              ctxL.get(0).contains("id: \"task-library-01\", current properties:") && ctxA.get(0).contains("id: \"task-library-01\", current properties:") && ctxL.get(0).matches("(?s).*id: \"task-library-01\", current properties: \\{[^}]*version=1.*") && "1".equals(modelVersion(ctxL.get(0))));
        check("consulting the library is counted by stage, failed attempts included, apart from the model's: plans_for in stage 1; describe_plan and propose in A2a",
              resL.contains("stage1-introduction={plans_for=1}") && resA.contains("a2a-policy-change={describe_plan=1, propose=1}"));
        check("a proposal to the owners of the library is recorded apart from the model's, with its subject and evidence; the result names the library with the hash of its source and carries its Markdown export, plans with their status",
              resL.contains("task library expense-task-library (source task-library.json, sha256 ") && resL.contains("proposals the agent made to the owners of the task library: 0") && resA.contains("proposals the agent made to the owners of the task library: 1")
              && resA.contains("1. keep-a-record: A recorded decision could not be matched to a flag — evidence: E401 (made at version 1)") && resA.contains("### Plan [mandatory] Say so when your sources disagree (say-so-when-sources-disagree)"));
        CuratedModelArtifact modF = CuratedModelArtifact.load("domain-model-01", new Workspace(), data.modelResource());
        String rfMeals; try { rfMeals = String.valueOf(modF.doOperation("rules_for", new JSONObject().put("concept", "Meals")).get(0)); } catch (Exception e) { rfMeals = "ERROR " + e; }
        check("where the expense agent first looks, the mandatory plan is in front of it, in the case's words: describe_task ends with it in full and says it applies whenever its trigger occurs; plans_for gives it in full and the other plans in one line; and the rules of the meals end with what to do if the system differs from the model",
              taskE.contains("Mandatory — read it now; it applies whenever its trigger occurs:") && taskE.contains("which expenses you decided under the rule that is in question") && plansE.contains("\n  Steps:\n  1. Set the two side by side") && plansE.contains("- check-for-duplicates [recommended] Check an expense against the others before you decide it — when: ")
              && !plansE.contains("\n  Steps:\n  1. Compare the expense with the others") && rfMeals.endsWith("\n[Version 1 of the model. If what the system tells you differs from it — a flag, an audit, any feedback — say which source said what, and propose a change; do not settle it silently.]"));
        check("the snapshot records the task library it was learned against (id, source, hash of its assembled content) beside the model, and an instance is told about both",
              snapL.environment().getJSONObject("task_library").toString().equals(TaskLibraryArtifact.sourceRecord(DevlRunner.LIBRARY_BASE, data.libraryResource(), data.slots()).toString()) && ctxA.get(0).contains("The domain model (domain-model-01) and the task library (task-library-01) are part of your environment."));
        JSONObject noLib = new JSONObject(java.nio.file.Files.readString(pbL.get(0))); noLib.getJSONObject("manifest").getJSONObject("environment").remove("task_library");
        JSONObject otherLib = new JSONObject(java.nio.file.Files.readString(pbL.get(0))); otherLib.getJSONObject("manifest").getJSONObject("environment").getJSONObject("task_library").put("sha256", "0".repeat(64));
        Object[] rNo = run(new String[] { "--project=expense-approval", "--phase=operation", "--playbook=" + writeJson(tmpS.resolve("nolib.json"), noLib) }, (ctx, e) -> blind(e, "All done."));
        Object[] rOther = run(new String[] { "--project=expense-approval", "--phase=operation", "--playbook=" + writeJson(tmpS.resolve("otherlib.json"), otherLib) }, (ctx, e) -> blind(e, "All done."));
        check("a playbook learned without a task library (every playbook before it) is not refused: it runs, with the warning that the practices in it are new to the playbook; one learned against another library runs, with the warning that the library has changed since",
              ((String) rNo[0]).contains("playbook WARNING: learned without a task library (it predates it or was learned without one); this run provides 'expense-task-library': the practices in it are new to this playbook") && ((String) rNo[1]).contains("operation decisions")
              && ((String) rOther[0]).contains("playbook WARNING: learned against the task library 'expense-task-library' (hash 000000000000), running against another one") && ((String) rOther[0]).contains("the library has changed since") && ((String) rOther[1]).contains("operation decisions"));
        check("the stage 1 message names the library in the same factual way as the model — what it keeps, who maintains it, that its practices are marked mandatory, recommended or example, that it can be read and proposed to and not changed — and the request for the playbook names it among the things to refer to, and asks where its practices are followed or departed from",
              msgs.stage1().contains("The task library (task-library-01) keeps how the work is done here: what each kind of task is, and the practices the finance team and the domain experts ask for, each marked mandatory, recommended or example. It is maintained and used in the same way as the domain model: you can read it, you cannot change it, and if you think something in it is wrong you can propose a change.")
              && msgs.playbookContent().contains("the domain model (domain-model-01) and the task library (task-library-01)") && msgs.playbookContent().contains("the task library holds what each task is and the practices its owners ask for: refer to them, do not copy them") && msgs.playbookContent().contains("what none of them holds")
              && msgs.playbookContent().contains("where you follow a practice of the library as it stands and where you depart from it, and why"));

        // ---- 18. the materials: every text the agent reads, as the framework's templates and this case's slots make it, pinned by hash. A change to a template, a slot, a manual or the system prompt
        // changes what the agent is GIVEN: this check fails until the pin is updated on purpose — and the materials version (DevlRunner.MATERIALS_VERSION) with it.
        // (The project folder refactoring, materials still 9: of the 38 pinned texts exactly one changed, library.content — the owners' Markdown export of the library, which embeds the library's file name (now task-library.json); nothing the
        // agent reads changed.)
        // (Version 9, the playbook as an artifact: of the 33 texts of version 8, eight changed on purpose — the instantiation message, the four requests for the playbook and the three debriefs that carry them, which now say where and how the
        // playbook is written (playbook-01, revise) and that it is the only thing carried — and 25 did not; five are new: the playbook's manual and what its operations return.)
        // (Version 8: the same two messages, put where the agent reads them at the moment they matter — mandatory plans in full in describe_task and plans_for (the library's manual says so: one of the 27 texts changed), and a
        // footer on the model's lookups; the pin now also covers six things the lookups RETURN, which it did not: 33 entries, 26 unchanged, one changed, six new.)
        // (Version 7: of the 27 texts of version 6, two changed on purpose — the mandatory plan of the library (do not settle a disagreement silently, whichever source you believe) and the model's manual (its authority
        // is about the agent's assumptions, not about the world; contradicting feedback is reported, not resolved alone) — and 25 did not.)
        // (Version 6, the task library: of the 25 texts of version 5, nine changed on purpose — the stage 1 message, the three debriefs and the four requests for the playbook, which name the library, and the instantiation message — and 16 did not; two are new: the library's manual and its content.)
        java.util.Map<String, String> pinned = java.util.Map.ofEntries(
            java.util.Map.entry("a1.message", "6fd8656f2300e8adf10ae39c385fe5bbdaf7a4673f47176528803c0eb637a9ec"),
            java.util.Map.entry("a2a.message", "28fae5a4207931c44d7dee86026c6b569c7e5ae089dd789e7b23902c2c58dc86"),
            java.util.Map.entry("debrief", "3c355035dc4114a65e84bfc3e3fb6df26c77dcc9f8af806dcf9b41538fba465a"),
            java.util.Map.entry("instantiation", "abbc05f07f898ccd5a5dadc32e0f204ce0032e42a9576839c918a93cc193a0bc"),
            java.util.Map.entry("library.content", "54b3e51c82cfd1cea8a18380b47521510d423ad25b96c0f7e14f73e0b4c661ba"),
            java.util.Map.entry("library.describe_plan", "9eb8f947e590fd3587c55f7a3986a64b9be003aeb41153d6b7d4f096a653de39"),
            java.util.Map.entry("library.describe_task", "cff3d341e299238f9d6ae4c51c3e5986735ee8aed30323a1a74c19c760aabaef"),
            java.util.Map.entry("library.plans_for", "c6dc84b41e52cd983e1a128ced2664f8c4faa14c30e0f1f3d3d7e4d1da50e1bd"),
            java.util.Map.entry("manual.expense", "a1de7bbdbf742bc283dcdf7a62fafd39c6f2801f2d0827f8c684ea67d0ec519e"),
            java.util.Map.entry("manual.library", "b3e7612f9d34c0523f87a5254b68f5a2716f2286d889d7805f9d2b9a8ce0cb29"),
            java.util.Map.entry("manual.model", "1cb56eb609dda673a063127232abfb8dc62789d5bd4ecd91415c7b5a5110ceb4"),
            java.util.Map.entry("manual.playbook", "aa1e6c8643b449c2f6f270e369cbc28916f76cbed1e0b45aec3761b0bfed6db8"),
            java.util.Map.entry("manual.tutor", "f11301b6600afaf095ca8871ac79a1c2782f9d3cea5f1ab10ae6b6a1f444291c"),
            java.util.Map.entry("model.describe.meals", "784b9fabad8809cc26b531ac6a047f08801dd4690a0a601a4272d2735edff0a0"),
            java.util.Map.entry("model.rules_for.meals", "ada7624f842f446627000cb4c613001415e21026694b248c895870d5ffd5cfd1"),
            java.util.Map.entry("model.search.meal", "052b9ba6ee3d32f90419f81e5c63b57560115c7c425c27c94933d340bcf6e95c"),
            java.util.Map.entry("operation.message", "9eb010f8ee985d10b21b8628c0fbfa41618c175e7fe4f62036f4159827b56b4f"),
            java.util.Map.entry("playbook.consolidation", "7f89f8bcf11670b8d126d398dcbfb5ac4411262f6655eabeeb16b7bcb9622bf2"),
            java.util.Map.entry("playbook.content", "21f727f61afa1ddf65cf305c1b1fa1081c7ef1fa61ced96e5836032b144393ce"),
            java.util.Map.entry("playbook.diff", "bcde7f0b3c6214c1551bf008d390e66e081fc280b6d015f7a83333cf22653caa"),
            java.util.Map.entry("playbook.first", "4d2c533a8be0b316cbda9ac624ff43598c110272f0612298ee7582d8b7903711"),
            java.util.Map.entry("playbook.later", "f56f07c5087d282ecb5f5ccb57d4d344341812a18030da195c012222c78f3725"),
            java.util.Map.entry("playbook.lineage", "14d1dc9e7d217d0a78106c177f23fd733bfd931214b78d723e0ed695c9af646e"),
            java.util.Map.entry("playbook.revise", "4627e0bb91e4f6a0cfa4b9d6ca834480ae562a07d9b6aa04104479e5b5d6f3c9"),
            java.util.Map.entry("playbook.revise.frozen", "6eb4d71c66eff07355b263c99d0319e50b9c8a2c3ed39856b3ccb704b7657073"),
            java.util.Map.entry("prompt.sha12", "a861f6f56d88"),
            java.util.Map.entry("stage1.debrief", "c7b31cd8cf9b4cc790fc06a537c3ab5d569b1fc181009681f720c0aba11cee46"),
            java.util.Map.entry("stage1.message", "1e59e2922f603b3f7ca5b883c9682bb885344126c7623ecd026afacd057db9a3"),
            java.util.Map.entry("stage2.debrief", "4a54f659c1963f5640145e05fbe5b38fe043e8bd45abb3115f2ef42d6afff990"),
            java.util.Map.entry("stage2.message", "284df068150c51a132e1866ba6fb4dac26e5accf7ae794c763acd4369668034c"),
            java.util.Map.entry("stage3.debrief", "cde1c61f433188e8ba2f6e9dd613d0acdec75b49c31427a8661b1ac3c17ed46c"),
            java.util.Map.entry("stage3.message", "75622b09ba9d2bcf92e88cce0d7e71928fadc4918319d7b0c679e9605b4e71cd"),
            java.util.Map.entry("tutor.error.missing", "41982312199184f59768b87bd57029cc7561d6af1a5782d665956ee45d0079d4"),
            java.util.Map.entry("tutor.error.unknown", "ae19f88e92af9a58d5c792d7edb0d15419886201414509a1dda1521a5f18a0f2"),
            java.util.Map.entry("tutor.full", "f6711e626245456e3b3eb4839521c620b57487d7784389a3c2b3a880320e1c30"),
            java.util.Map.entry("tutor.hints", "8959ad02875e30199b2ef01990cbef5515f9aa01648751bff14159c1fdd9884d"),
            java.util.Map.entry("tutor.nothing.full", "44ec963e7180304306274551c084a020ae82aa2825ccec3b04529e903f03a5c8"),
            java.util.Map.entry("tutor.nothing.hints", "1f332558b32543d799d7c5de17b325a2299a96cf160cfcb1e8656c189728a2a3"));
        java.util.Map<String, String> nowTexts = agentVisibleTexts(kase, msgs, data); java.util.List<String> changed = new ArrayList<>();
        for (var e : pinned.entrySet()) { String t = nowTexts.get(e.getKey()); String hh = t == null ? "(missing)" : e.getKey().equals("prompt.sha12") ? t : PlaybookSnapshot.sha256(t); if (!hh.equals(e.getValue())) changed.add(e.getKey()); }
        check("the materials (version " + DevlRunner.MATERIALS_VERSION + "): all " + pinned.size() + " texts the agent reads — the stage messages and debriefs, the instantiation message, the requests for the playbook, the tutor's replies and errors, the three manuals, the system prompt — are exactly what they were when the version was set. changed: " + changed,
              changed.isEmpty() && nowTexts.keySet().equals(pinned.keySet()));
        check("the materials render from the framework's templates and this case's slots without a trace of anything but the case: no template placeholder is left in any text the agent reads",
              nowTexts.values().stream().noneMatch(t -> t.matches("(?s).*\\{[A-Za-z0-9]+\\}.*")));

        System.exit(CheckKit.summary("ExpenseCaseCheck"));       // the agents' executors keep the JVM alive otherwise
    }
}
