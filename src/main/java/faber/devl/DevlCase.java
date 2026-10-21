package faber.devl;

import java.util.List;

import faber.environment.Workspace;

/**
 * A case study: the domain the agent is prepared for, with everything that is about THAT domain and nothing that is about the process. The framework owns the phases, the stages, the playbook,
 * the curated-model mechanism, the measurements and the texts' scaffolding; a case owns —
 *
 *   data     its identity, the slots that fill the framework's message templates, the topics the scripted tutor recognises, the pattern of an item id in a report (project.json), and its curated
 *            domain model, rule probes and task library (the standard files of its project folder, faber/devl/projects/<project>: see CaseData);
 *   world    the business system the agent works with (an artifact) and its rules, in the two worlds a stage may be judged in (before and after the change);
 *   work     the items of each stage (StageKey).
 *
 * A project's code lives in its own package, faber.devl.cases.&lt;name&gt;, and its resources in its project folder; project.json names the class (a constructor that takes the CaseData), so nothing is
 * registered. ADDING-A-CASE.md says how; CaseContract checks that a project satisfies what the framework relies on.
 */
public interface DevlCase {
    CaseData data();
    /** The items of a stage. Ids must be unique across the whole case. */
    List<? extends WorkItem> workload(StageKey key);
    /** Registers the case's artifact types and its business system in the workspace, and returns the framework's handle on the system. */
    CaseWorld provision(Workspace workspace);
}
