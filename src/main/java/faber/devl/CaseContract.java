package faber.devl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import faber.environment.Workspace;

/**
 * What the framework relies on from a case, as checks. A case's own suite calls {@code CaseContract.problems(case)} and fails on any entry; a new case is correct, as far as the framework
 * is concerned, when this list is empty. (What the case's own rules and texts SAY is the case's to test.)
 *
 *   data       the slots every message template needs are provided; every template renders; the item-id pattern recognises every item id
 *   work       every stage has items, ids are unique across the case, the clean stage is decided the same in both worlds, the change stage has items the change moved AND controls it did not,
 *              the operation stage has items the change moved
 *   model      the curated model and the rule probes load; the model has versions 1 and 2; version 1 states every rule, version 2 states what is not replaced and the replacements, and no
 *              longer the old values; the replaced and unchanged rules named by the probes exist
 *   world      the case provisions its system in a workspace; the system takes a stage, counts its items as pending, and has decided nothing
 *   library    the task library (the case's tasks and plans, with the framework's base) loads; it is SMALL and CLEAR: every plan says when it applies, why, and what may be adapted; only a few are
 *              mandatory; steps are few; no text of it restates a rule of the domain model (the single-home rule, with the same probes that measure a playbook)
 */
public final class CaseContract {
    private CaseContract() {}

    public static List<String> problems(DevlCase c) {
        List<String> p = new ArrayList<>(); CaseData d;
        try { d = c.data(); } catch (RuntimeException e) { return List.of("the case's data cannot be loaded: " + e); }
        // ---- data
        if (!d.base().equals(CaseData.ROOT + d.id() + "/")) p.add("data: the project's files are in " + d.base() + " and its id is '" + d.id() + "': the folder of a project is faber/devl/projects/<id>");
        for (String s : CaseData.REQUIRED_SLOTS) if (!d.slots().containsKey(s)) p.add("data: the text slot '" + s + "' is missing");
        if (!p.isEmpty()) return p;                                                       // the rest renders texts: no point without the slots
        Messages m = new Messages(d, DevlRunner.MODEL_ID);
        for (String key : Messages.templates().keySet()) {
            try { m.render(key, java.util.Map.of("id", "PB-X", "topics", "t")); } catch (RuntimeException e) { p.add("data: the template '" + key + "' does not render: " + e.getMessage()); }
        }
        // ---- work
        Set<String> ids = new LinkedHashSet<>();
        for (StageKey k : StageKey.values()) {
            List<? extends WorkItem> items;
            try { items = c.workload(k); } catch (RuntimeException e) { p.add("work: no workload for " + k + ": " + e); continue; }
            if (items.isEmpty()) p.add("work: " + k + " has no items");
            for (WorkItem it : items) {
                if (!ids.add(it.id())) p.add("work: the item id " + it.id() + " appears twice in the case");
                if (!d.itemIdPattern().matcher(it.id()).find()) p.add("work: the item id " + it.id() + " is not recognised by the case's itemIdPattern");
                if (it.expected(1) == null || it.expected(2) == null) p.add("work: " + it.id() + " has no expected outcome in a world");
            }
        }
        if (p.stream().anyMatch(x -> x.startsWith("work: no workload"))) return p;
        for (WorkItem it : c.workload(StageKey.CLEAN)) if (!it.expected(1).equals(it.expected(2))) p.add("work: the CLEAN stage must not be confounded by the change, but " + it.id() + " is decided differently in the two worlds");
        long moved = c.workload(StageKey.CHANGE).stream().filter(i -> !i.expected(1).equals(i.expected(2))).count(), ctrl = c.workload(StageKey.CHANGE).size() - moved;
        if (moved < 1) p.add("work: the CHANGE stage has no item the change moved");
        if (ctrl < 1) p.add("work: the CHANGE stage has no control (an item the change did not move), so over-correction cannot be seen");
        if (c.workload(StageKey.OPERATION).stream().noneMatch(i -> !i.expected(1).equals(i.expected(2)))) p.add("work: the OPERATION stage has no item the change moved");
        // ---- model
        CuratedModelArtifact model; RuleReference ref;
        try { model = CuratedModelArtifact.load(DevlRunner.MODEL_ID, new Workspace(), d.modelResource()); ref = RuleReference.load(d.rulesResource()); }
        catch (RuntimeException e) { p.add("model: cannot load the curated model or the rule probes: " + e); return p; }
        try {
            List<String> v1 = model.ruleTexts(); model.publish("2"); List<String> v2 = model.ruleTexts(); model.publish("1");
            Homes.Result h1 = Homes.measure(ref, null, v1, "1"), h2 = Homes.measure(ref, null, v2, "2");
            if (ref.ruleIds().isEmpty()) p.add("model: the rule probes name no rules");
            if (h1.model.size() != ref.ruleIds().size()) p.add("model: version 1 states " + h1.model.size() + " of the " + ref.ruleIds().size() + " rules the probes name");
            if (h2.model.size() != ref.ruleIds().size()) p.add("model: version 2 states " + h2.model.size() + " of the " + ref.ruleIds().size() + " current rules (the unchanged ones and the replacements)");
            for (var e : ref.replaced().entrySet()) {
                if (!ref.ruleIds().contains(e.getKey())) p.add("model: the probes say '" + e.getKey() + "' was replaced, but it is not a rule of the first layer");
                if (ref.matchedIds("v2", v1).contains(e.getValue())) p.add("model: version 1 already states the replacement " + e.getValue());
                if (ref.matchedIds("rules", v2).contains(e.getKey())) p.add("model: version 2 still states the replaced rule " + e.getKey());
                if (ref.oldValueFacts(e.getKey()).isEmpty() || ref.topicWords(e.getKey()).isEmpty()) p.add("model: no old-value facts or topic words for the replaced rule " + e.getKey());
            }
            for (String u : ref.unchangedIds()) if (!ref.ruleIds().contains(u)) p.add("model: '" + u + "' is named unchanged but is not a rule of the first layer");
        } catch (RuntimeException e) { p.add("model: version 2 cannot be published or read: " + e); }
        // ---- world
        try {
            CaseWorld w = c.provision(new Workspace());
            for (StageKey k : StageKey.values()) {
                w.loadStage(c.workload(k), k == StageKey.CHANGE || k == StageKey.OPERATION ? 2 : 1, Feedback.NONE);
                if (w.pendingCount() != c.workload(k).size()) p.add("world: after loading " + k + " the system has " + w.pendingCount() + " pending, not " + c.workload(k).size());
                if (w.decidedCount() != 0 || !w.outcomes().isEmpty() || w.correctCount() != 0) p.add("world: after loading " + k + " the system already holds decisions");
            }
        } catch (RuntimeException e) { p.add("world: the case's system cannot be provisioned or loaded: " + e); }
        // ---- library
        try { p.addAll(libraryProblems(TaskLibraryArtifact.parse(DevlRunner.LIBRARY_BASE, d.libraryResource(), d.slots()), ref)); }
        catch (RuntimeException e) { p.add("library: cannot be loaded or assembled: " + e); }
        return p;
    }

    /** The checks of the task library: small and clear, and no copy of the domain model. Public so that a test can hand it a library built to break them. */
    public static List<String> libraryProblems(TaskLibraryArtifact.Parsed lib, RuleReference ref) {
        List<String> p = new ArrayList<>();
        if (lib.tasks.isEmpty()) p.add("library: it has no task");
                    Set<String> planIds = new LinkedHashSet<>(); int mandatory = 0;
        for (TaskLibraryArtifact.Plan pl : lib.plans) {
            if (!planIds.add(pl.id)) p.add("library: the plan id " + pl.id + " appears twice");
            if (!TaskLibraryArtifact.STATUSES.contains(pl.status)) p.add("library: the plan " + pl.id + " has the status '" + pl.status + "' (known: " + TaskLibraryArtifact.STATUSES + ")");
            if (pl.status.equals("mandatory")) mandatory++;
            for (String t : pl.appliesTo) if (!t.equals("*") && !lib.tasks.containsKey(t)) p.add("library: the plan " + pl.id + " applies to the task '" + t + "', which the library does not have");
            if (pl.steps.isEmpty() || pl.steps.size() > 6) p.add("library: the plan " + pl.id + " has " + pl.steps.size() + " steps (one to six: a plan is advice, not a script)");
            if (pl.when.isBlank() || pl.why.isBlank()) p.add("library: the plan " + pl.id + " does not say when it applies and why");
            if (!pl.status.equals("mandatory") && pl.adapt.isBlank()) p.add("library: the plan " + pl.id + " (" + pl.status + ") does not say what the agent may adapt");
            if (pl.appliesTo.isEmpty() || pl.steps.stream().anyMatch(String::isBlank)) p.add("library: the plan " + pl.id + " has an empty step or no task");
            String text = pl.name + ". " + pl.when + " " + String.join(" ", pl.steps) + " " + pl.why + " " + pl.adapt;
            if (text.matches("(?s).*\\{[A-Za-z0-9]+\\}.*")) p.add("library: the plan " + pl.id + " has a placeholder left in its text");
            List<String> restated = new ArrayList<>(ref.matchedIds("rules", Homes.sentences(text))); restated.addAll(ref.matchedIds("v2", Homes.sentences(text)));
            if (!restated.isEmpty()) p.add("library: the plan " + pl.id + " restates the domain model's rule(s) " + restated + ": refer to the model, do not copy it");
        }
        if (mandatory > 2) p.add("library: " + mandatory + " plans are mandatory (two at most: what is mandatory is disclosure, and the rest is advice the agent may adapt)");
        if (lib.plans.size() > 10) p.add("library: " + lib.plans.size() + " plans (ten at most: a library is small, or it is not read)");
        for (TaskLibraryArtifact.Task t : lib.tasks.values()) {
            if (t.quality.isEmpty()) p.add("library: the task " + t.id + " says nothing of the quality asked of it");
            String text = t.summary + " " + t.input + " " + t.done + " " + t.failed + " " + String.join(" ", t.quality) + " " + t.mandate + " " + t.scope;
            List<String> restated = new ArrayList<>(ref.matchedIds("rules", Homes.sentences(text))); restated.addAll(ref.matchedIds("v2", Homes.sentences(text)));
            if (!restated.isEmpty()) p.add("library: the task " + t.id + " restates the domain model's rule(s) " + restated + ": refer to the model, do not copy it");
        }
        return p;
    }
}
