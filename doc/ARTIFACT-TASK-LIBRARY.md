# The task library

## Rationale and design of `TaskLibraryArtifact` in the DevL pilot — 18 October 2026

*A self-contained report for a reader who has not seen the rest of the project. Context: DevL is a process for preparing an LLM-based agent for a job (learning), validating it (assessment) and putting it to work (operation), in an environment made of artifacts — resources the agent can observe and operate, each with a manual. The pilot is built as a framework (the process, which knows no domain) and cases (each domain in its own package); the first case is expense approval at an invented company. This report is about the third of the pilot's main artifacts, the task library; its companions describe the domain model and the playbook. **Unlike the other two, it has not yet been used in a run**: what is said below about evidence is about the failures that motivated it, not about its effect.*

---

## 1. What it is, in one paragraph

The task library is a **curated, versioned, read-only statement of how the work is done here**, kept by the people who own the process. It holds two kinds of *given* task knowledge: **tasks** (what a kind of request is — its input, what counts as done and as failed, the quality asked of it, what the agent may decide alone, what is outside it) and **plans** (practices the owners ask for, each with a *status* — mandatory, recommended or example — a trigger, a short body, the reason, and what the agent may adapt). It is part of the environment in every phase, like the domain model; the agent consults it and can only propose changes. **A plan is advice for the agent's own practical reasoning, never a procedure that runs for it**: no operation of the library does the work, and none chooses among plans.

---

## 2. Rationale

### 2.1 The problem it answers

The domain model gives the agent what is *true* in the domain. What it cannot give is what the *owners of the process* want done and how — and the experiments showed what happens without it. When the model was left behind the world, instances trusted the audit over everything, but **none recognised that the model was the party behind**, none re-read it, none proposed a change; and when a procedure that fixes this was appended *to the agent's own playbook*, it competed with the agent's own rule that "the model always wins" and the agent decided which prevailed. Knowledge that has to be *given* — what to do when sources disagree, what a good report contains — cannot be learned from a world in which the model is never behind, and cannot carry authority when it is one paragraph among the agent's own rules. It needs **its own source, with its own authority and an explicit status**: the library.

### 2.2 Design principles and what each implies

| principle | consequence in the design |
|---|---|
| **Minimal and clear** | seven plans in the expense library; each says *when* it applies, *why*, and *what may be adapted*; steps are one to six; the contract enforces these bounds (§3.8) |
| **Advice, not automation** | no operation does the task or chooses a plan; the manual says a practice is "advice for your own judgment, not a procedure that runs for you". If an owner wants a procedure run rigidly, the right object is a tool with its own manual, not a plan |
| **Status says how binding** | *mandatory* — followed, or its impossibility is said; *recommended* — a default the agent may adapt, with a reason, saying so when it matters; *example* — an illustration |
| **Only disclosure is mandatory** | what is obeyed is *saying so* (when sources disagree); how the agent decides meanwhile is a recommendation it can adapt. Autonomy is kept where judgment lives |
| **One home per piece of knowledge** | the library never states a rule of the domain model (the contract checks every sentence with the same probes that measure a playbook); a plan refers: "using what the domain model says makes one expense a duplicate of another" |
| **Read-only, versioned, observable version, `propose`** | the same discipline as the model: owners change it in new versions; the agent can only say what it observed |
| **A trusted artifact among the agent's tools** | named in the stage 1 message in the same factual way as the model; in the instantiation message; in the playbook's request, which asks the playbook to *refer* to it and to record where the agent follows or departs from it |
| **Framework and case, separated** | the practices about doing *any* work in this process are the framework's; the task and the domain-specific practices are the case's |

### 2.3 What it deliberately is not

It is not a workflow engine, a checklist the agent must tick, or a place for rules (those are the model's). It does not say *how to decide* an expense, nor in what order to call the system. It does not replace the tutor's guidance or the playbook's experience: the playbook records **what the agent found in practice with these plans** — "applies as written, except that…" — and never restates them.

### 2.4 Why these plans, and the caveat that matters

The five practices of the framework's base and the two of the expense case are each traced to something observed in the runs of the first regime and of R1:

| plan | status | what it answers |
|---|---|---|
| Say so when your sources disagree | mandatory | the model left behind: no instance recognised the model as the stale party; no report said that sources disagreed; no proposal was made |
| Deciding while your sources disagree | recommended | after a flag that quotes a rule, instances had no stated default; one held a stale rule through three flags because its own "model always wins" outranked the evidence |
| Keep a record of what you decide | recommended | instances without a tally lost track of their decisions after the audit flags; one reported nothing, one called a wrong decision correct, one inverted its plan |
| Tell your reader what was decided | recommended | reports of counts and summaries; reports that did not name the wrong decisions |
| Let feedback reach you before you commit the rest | recommended | the commit strategy dominated the errors after a change: instances that decided all at once could not learn from the first flags |
| Check an expense against the others | recommended *(case)* | the duplicate was approved once in learning; no playbook said how to find one; no assessment queue contains one |
| One way through a queue | example *(case)* | an illustration of the whole task, to show what an example is |

**This makes the library a treatment, not a neutral instrument.** The plans were written from observed failure modes, so a better result under the library is the effect of *this content*, and the comparison that means something is with the same playbooks without the library (the materials-5 results). The base is deliberately small, and every plan can be dropped to see what it was worth.

---

## 3. The artifact as built

### 3.1 Identity and provisioning

Type `TaskLibrary`; the pilot provisions one instance, `task-library-01`, in learning, assessment and operation, and the agent observes it (its `version` is in its context every cycle, as the model's is). Its observable properties are `version`, `tasks` (the task ids) and `proposals` (a count).

### 3.2 Two sources, one library

The library is **assembled when it is loaded** from two files: the framework's **base** (`faber/devl/devl-task-library-base.json`: practices for doing any work in this process, written with no word of any domain — the few that name it are slots such as `{items}`, `{item}`, `{experts}`, filled from the case's text slots) and the **case's file** (`cases/<name>/<name>-task-library.json`: its title, its versions, its tasks, its domain-specific plans). The base's plans apply to every task (`applies_to: ["*"]`); the case's to the tasks it names. The agent sees one library; the owners' export marks which plans came from the framework. The **version is the case file's**; the library's **hash is of the assembled content in a fixed order**, so it changes if the case's file changes or a slot that fills the base changes.

### 3.3 The content, per entry

*Task:* `id`, `name`, `summary`, `input` (the request and how to recognise it), `done`, `failed`, `quality` (an ordered list), `mandate` (what the agent decides alone), `scope` (what is outside it, and what to do then).
*Plan:* `id`, `name`, `status`, `applies_to`, `when` (the trigger and context), `steps` (one to six), `why`, `adapt` (what the agent may change), `source` (who asks), `since` (the version).

### 3.4 Operations the agent has

| operation | returns |
|---|---|
| `tasks()` | the catalogue: each task with a one-line summary and its number of plans |
| `describe_task(task)` | the task in full: request, done, failed, the quality asked of it in order, mandate, scope, and its number of plans with the mandatory ones named |
| `plans_for(task)` | the practices that apply to the task, each with its status and when it applies — mandatory first, then recommended, then examples |
| `describe_plan(plan)` | one practice in full: when, steps, why, what may be adapted, its status **in words**, its source and the version since which it applies |
| `version()`, `changes_since(version)` | as in the model |
| `propose(subject?, change, evidence)` | records a suggestion for the owners with the version it was made at; the library is unchanged; `subject` (a task or a plan) is optional and checked |

Errors teach (no such task / plan, a missing parameter that says *nothing was done*, an unknown operation that says the library "can be read … and you can propose a change; you cannot change it"). **The manual** states what the library is (the owners' statement of how the work is done), what each status asks, and that a practice is advice for the agent's own judgment.

### 3.5 The designer's side, and what is measured

`proposals()`, `opCountsByStage()` (every consultation counted by stage, failed attempts included — kept apart from the model's), the content hash, a Markdown export for the owners with the proposals. The result reports: the library's id, source and hash, **what it was consulted for, by stage**, the proposals with their subject and evidence, and the Markdown export. The playbook's measures after each debrief now also say **whether the playbook refers to the library**.

### 3.6 In the process

*Learning:* provisioned from stage 1; the **stage 1 message names it in the same factual way as the model** — what it keeps, who maintains it, that its practices are marked mandatory, recommended or example, that it can be read and proposed to and not changed. *Assessment and operation:* provisioned identically; the instantiation message names both artifacts. *The playbook:* its request now names the library among the things to refer to, and asks it to record where the agent follows a practice of the library as it stands and where it departs from it, and why. *Snapshots:* a snapshot records the library it was learned against (id, source, hash), as it records the model, and a run is **warned** — never refused — when a playbook was learned without a library (every playbook so far), against another one, or when the run provides none.

### 3.7 The contract: small, clear, and no copy of the model

`CaseContract` checks any case's library, mechanically: it has at least one task, each with a quality; every plan has a valid status, applies to a task that exists, has one to six steps, says when and why, and — unless mandatory — what may be adapted; **at most two plans are mandatory and at most ten plans in all**; plan ids are unique; no placeholder is left; and **no sentence of any task or plan states a rule of the domain model**. The checks are themselves tested by building a library that breaks each one.

### 3.8 What the tests pin

The assembly (the framework's five and the case's plans, in the case's words), the operations and their errors, the order of plans by status, the manual's statements, the hash's stability and sensitivity, the snapshot's records and warnings, the library's presence and consultation counts in the end-to-end runs of both cases, and — for the expense case — the **rendered library by hash** in the pin of every text the agent reads (so that any change to a plan or a slot fails until the pin and the materials version are updated on purpose). Of the 25 texts of materials 5, nine changed by design (the stage 1 message, the three debriefs and the four requests for the playbook, which name the library, and the instantiation message), 16 did not, and two are new (the library's manual and its content): materials version 6.

---

## 4. What the runs show about it

**Nothing yet.** The library exists, passes its contract and its tests, and reads as intended; no agent has used it. What can be said is indirect: the failure modes it answers are documented (§2.4), and the four playbooks of materials 4 and 5 are valid inputs to assessment under the library (an instance is told it exists; a warning says the playbook predates it) — which gives a **within-playbook comparison**: the same four playbooks, assessed and drift-probed again, with the library present, against their earlier results without it.

---

## 5. Open issues and design questions

**A. Treatment and measurement.** The library's plans come from observed failures; its effect on those failures is the first thing to measure, and ablations (drop one plan, or the status of one) are the way to separate what each is worth. A control is needed that keeps the materials version fixed and removes the library (not built: the texts that mention it would have to adapt).

**B. How binding is "mandatory", in practice?** The status asks for obedience, or for saying why not; whether an agent obeys, adapts silently, or ignores is what over-trust and under-trust experiments would show. The design note expects the agent's experience to annotate recommended and example plans and never silently override a mandatory one.

**C. Where the base ends and the case begins.** Five practices are called domain-independent because they speak of "items", "sources", "reports"; whether they are in fact independent of every domain is a hypothesis that a second real case will test (the toy case satisfies the contract but is too small to disprove it).

**D. One task.** The expense library has one task; the catalogue, the recognition of out-of-scope requests, and the transfer experiment (new task types in the same domain) are not exercised.

**E. Versions and drift.** The library has one version; "procedural drift" (the plan unchanged but the tool changed), a library that is ahead of or at odds with the model, and a change in the library during a task are not tested. The same observable-version cue that carried the model's in-step case is available here and untested.

**F. Interplay with the playbook.** The playbook is asked to refer to the library and to record experience with it; whether it does, and whether it still copies plans, is measured only by whether it refers (a keyword). Whether the playbook shrinks to experience, as the design note expects, is open.

**G. The tutor.** The scripted tutor points to the model for rules and does not yet point to the library for questions of procedure.

**H. Proposals.** As with the model: recorded and exported; no review flow, no new version produced, no feedback to the proposer.

---

## 6. Steps to consider

1. **The within-playbook comparison**: the four existing playbooks assessed and drift-probed with the library present (three fresh instances each is the minimum that means something), against their materials-5 results.
2. **Learning under materials 6**, two runs, and what the playbooks keep when the library holds the practices.
3. **Ablations**: without `let-feedback-reach-you`; without the mandatory plan; with the mandatory plan made recommended.
4. A **control condition** that removes the library and keeps the materials version.
5. A **second task** in the expense case (explain a decision; review a past decision), to exercise the catalogue and the scope, and a **second real case**.
6. **Library drift**: a version 2 of the library, in step and left behind, tested like the model's.
7. The **tutor of practice** pointing to the library; a **proposal flow** shared with the model.

---

*Where to look next: `ARTIFACT-DOMAIN-MODEL.md` and `ARTIFACT-PLAYBOOK.md` (the companions), `DEVL-REPORT.md` (the whole process), `KNOWLEDGE-LAYERS.md` (the design note), `ADDING-A-CASE.md` (how a case supplies its library), `FINDINGS.md`. The artifact is `faber-framework/src/main/java/faber/devl/TaskLibraryArtifact.java`; the base `…/resources/faber/devl/devl-task-library-base.json`; the expense case's file `…/resources/faber/devl/cases/expense/expense-task-library.json`.*
