# Developmental learning of an LLM agent in an artifact-based environment

## The DevL pilot, regime R1 — state of the work, 17 October 2026

*A self-contained report for an external reader: what the process is, how it is built, what the runs so far show, and what is open. Research by Alessandro Ricci and Claude. Everything below is evidence from a small pilot — one invented domain, one LLM configuration, a handful of runs per condition — and the strength of each claim is stated.*

---

## 1. In brief

An LLM-based agent starts every session with no memory. Whatever it "learns" has to live outside the model: in artifacts of its environment, or in text it writes for its future self. **DevL** is a process for *preparing* such an agent for a job, *validating* it, and *putting it to work*, and for deciding precisely **what is handed on from one instance of the agent to the next**.

The current regime (R1) rests on three ideas. (1) The agent's life has three phases — **learning, assessment, operation**. (2) Knowledge has **layers with different owners**: the domain's rules are a *curated, versioned, read-only domain model* kept by humans; what the agent writes is a **playbook** — how to do the job, what it is unsure of — and the playbook is the **release artifact**. (3) **What is assessed is a candidate, never a mind**: every assessment and every day of operation uses a *fresh instance* made from the artifact.

What apparently works: the machinery end to end; playbooks that refer to the domain model instead of copying it; fresh instances that do well when the model is kept in step with the world and that check the model's version before trusting a rule. What does not yet work, or is unknown: when the model falls *behind* the world, instances follow it and do not recognise that it is the stale party; knowledge that has to be *given* (as opposed to learned) has nowhere to live; the playbook still depends, in places, on working notes that are not carried; the assessments saturate when everything is in step. The next step is a **task library** — given task knowledge, with explicit authority — and a second session built on it.

---

## 2. Concept

### 2.1 The problem and the idea

An LLM agent is stateless between sessions. The usual remedies — long context, retrieval, fine-tuning — leave open a design question: *what should be written down, by whom, in which form, and who is allowed to change it?* DevL answers it in the setting of **artifacts**: the agent works in an environment made of resources it can observe and operate (a business system, a knowledge base, a tutor, a notebook), each with a manual. Knowledge is placed in artifacts, and each artifact has an owner.

### 2.2 Three phases

The agent is told, in every context, which phase it is in.

| phase | purpose | conditions | produces |
|---|---|---|---|
| **learning** | prepare the agent for the job | a safe copy of the world; support that fades on purpose (a tutor, practice feedback); ends with debriefs that ask for the playbook | the playbook |
| **assessment** | validate what a *fresh instance* can do from the playbook | simulated, and the agent is told so; no tutor; one queue under the policy of learning, one under a changed policy with audit flags only | scores, errors, a report; optionally a revised playbook (a *release candidate*) |
| **operation** | do the job | after the change; no tutor, no feedback | the same measures |

(The phases were first called development, assessment, deployment; "learning" pairs with "assessment" as preparation with validation, and "operation" avoids the vocabulary of building software.)

### 2.3 Knowledge layers

| kind of knowledge | answers | owner | where it lives | status |
|---|---|---|---|---|
| **domain** | what is true here: concepts, rules, with source and version | domain experts, developers | the **curated domain model** (environment artifact; read-only for the agent; versioned) | built |
| **tool** | how each artifact behaves | developers | the artifacts' manuals | built (pre-existing) |
| **task, given** | what a task is (inputs, outputs, "done", quality, mandate) and how it is to be done, as authorities want it | process owners | a **task library** (environment artifact; optional) | **not built** |
| **task, learned** | how it actually goes here: pitfalls, adaptations, what to check, what to report | the agent | the **playbook** | built |
| **self** | what the agent is unsure of, how it treats its sources | the agent | the playbook | built |

Two rules follow. **One home per piece of knowledge**: the playbook *refers* to the model and the manuals and never copies them, because a copy goes stale in one of its homes. **Consult, do not delegate**: the model answers questions about knowledge (`rules_for`, `changes_since`, …) but never decides a case — the agent's job remains judgment.

### 2.4 The release artifact and how it is validated

The **playbook** is the release artifact: a fresh instance is made from the playbook together with the *environment* (manuals, model), which is provisioned, not copied — a snapshot records only which model it was learned against (source and hash).

*What is assessed is a candidate, never a mind.* A learning run assesses nothing. An assessment instantiates a fresh agent; with consolidation enabled, the assessed instance — the best-informed author, having just lived the audit — revises the playbook, and the revision is written as a **candidate** with its **lineage** (parent, round, what was assessed and how it went, an advisory gate). The candidate is assessed in turn by new instances. Rounds of assessment are *formative* (they drive revisions); a final round on unseen cases would be *summative*. A repeated A2a measures regression, not recovery: after the playbook holds the change, the change is no longer a surprise.

The design also gives the agent a way to speak to the humans who own the sources: `propose` records a suggested change to the model *with evidence*; the model itself does not change — the experts decide, and an accepted change takes effect in a new version.

---

## 3. Technical overview

### 3.1 Platform

The pilot is built on **Faber**, an agent architecture with an LLM core and an environment of artifacts (agents-and-artifacts style). The agent runs in *cycles*: it perceives (operation results, changes of observable properties, messages), the LLM returns a state of mind, intention changes and actions (invocations of artifact operations), operations run asynchronously and report back in later cycles. A ~68,000-character system prompt (identical in every phase; its hash is printed in every log) describes the cycle, the three phases and how to treat what one holds; the manuals of the artifacts are appended to it. Java 17+; the pilot lives in the package `faber.devl`.

### 3.2 The invented domain and the world change

Employee expense approvals at "Halden & Co.", six categories (meals, flights, lodging, software, equipment, training), eight rules (a missing receipt or a duplicate is rejected; limits per category; escalation to a named role). A **policy change** between two versions: the meals limits fall from 40/90 to 35/75, the approved software vendors change (Quark out, Orbit in), the equipment limit falls from 500 to 400. Queues are hand-written: three learning queues (4, 8, 8 claims), a clean assessment A1 (6 claims, decided identically under both policies), the changed-policy assessment A2a (8 claims, 5 affected, 3 controls), and an operation queue (6 claims, 4 affected).

### 3.3 Artifacts of the environment

| artifact | role |
|---|---|
| **expense system** | list and inspect pending expenses; `approve`, `reject`, `escalate` — **final**; feedback modes: none, immediate *practice feedback* (learning), *audit flag* after a wrong decision, quoting the rule (A2a) |
| **curated domain model** | 13 concepts, 13 rules, each with source, confidence and the versions in which it applies; operations `overview`, `describe`, `rules_for`, `search`, `version`, `changes_since`, `propose`; the **version is an observable property** the agent sees change; no operation changes it or decides a case; its rule texts are pinned by tests to the policy's own prose |
| **tutor** | availability fades (full, hints, none); the scripted tutor *points to the model* and teaches no rule; a human at the console may teach |
| **notebook** (kernel) | the agent's private notes; in the pilot always in safe mode (`append`, `restore`, a warning when a note shrinks); the playbook is one note |

### 3.4 One run is one phase

```
--phase=learning                                                  # writes the playbook snapshot
--phase=assessment --playbook=FILE [--only=a1|a2a] [--consolidate] [--model=in-step|stale]
--phase=operation  --playbook=FILE [--model=in-step|stale]
```
*Learning*: three stages (practice feedback and full tutor; the same; no feedback and hints), each closed by a debrief that asks the agent to start or revise its playbook, then the snapshot is written. *Assessment* and *operation*: a fresh instance whose notebook holds only the playbook, told who it is and that the model is part of its environment — facts only. `--model=stale` leaves the model at version 1 when the world moves to 2: the **drift probe**. A stage ends when the queue is empty and the agent has reported (plus two settling cycles), or six cycles after the queue empties if it has not.

### 3.5 The playbook request

The agent is asked (in the debriefs and after an assessment) to write, in its own words and as lasting practice for a reader who was not there: *how to do the job* — the tasks, the order, how it checks its work, when it escalates, what it reports; *how the environment behaves as found*; *what it is unsure of and how it treats its sources when they disagree* — **referring to the model and the manuals, not copying them**, and without account of events or particulars of the expenses it handled. The wording of this request is versioned and recorded in every snapshot (it has been clarified once after a first playbook misread it).

### 3.6 Measurements

Score per stage; for a queue under a changed policy, errors split into **stale** (exactly what the old policy would have decided) and **other**, and over-correction; the **final report** (which wrong decisions it names, which it says were wrong in so many words — a deliberately crude lexical metric); what the agent **consulted** in the model, by stage, and what it **proposed**; and **knowledge by home** — for each of the eight rules, whether the model and the playbook state it (keyword probes at sentence level), which rules the playbook *restates* although the model holds them, and which old values it still states *as current* (distinguished from values only annotated as history). Costs: tokens, cycles, wall time. Per assessment with consolidation: whether the playbook was revised, an advisory gate, and the candidate.

### 3.7 Verification, tooling, versioning

A suite of **150 checks without model calls** (35 s) covers the policy and queues, each artifact, the playbook and snapshot machinery, the measurements, the options, and the runner driven end to end through the three phases by a scripted agent; it is deterministic across repeated runs. Tools: a run summariser and comparison table; a rebuild of a playbook snapshot or candidate from a log alone (cross-checked against what the runner writes); a playbook diff; a checker of a project tree against the manifest; a migration script from the first regime. Every log and snapshot records the **materials version** (what the agent is *given*: prompt, manuals, messages, the playbook request; currently 5) so that a change in what it was given is never mistaken for a change in what it did.

### 3.8 The first regime, in one paragraph

In an earlier regime (R0) the agent learned the domain's rules itself into notes and a letter to its future self, and was assessed partly by the learning agent itself. More than twenty runs showed that *the commit strategy* (all decisions at once, or one at a time) decided how many errors followed a policy change; that a letter kept next to topic notes went stale (the corrections went into the notes, never the letter); that the letter alone was at least as good as letter plus notes; and that the assessed agent was working from working notes, not from what a successor would receive. That is why R1 puts the domain in a curated artifact, makes the playbook the release artifact, and assesses fresh instances only.

---

## 4. What has been run in R1

| runs | what | n |
|---|---|---|
| learning | four independent learning runs (two under materials 4, two under 5) → four playbooks, 5.3–9.0 k characters | 4 |
| assessment, model in step, with consolidation | one fresh instance per playbook | 4 |
| drift probe | the same four playbooks, A2a only, model left at version 1 | 4 |
| experiment | two playbooks hand-edited with one given procedure, drift probe again | 2 |

Operation has not yet been run in R1.

---

## 5. What apparently works

*(strength: **machinery** = deterministic and tested; **moderate** = consistent across the four playbooks; **weak** = one or two runs.)*

1. **The machinery, end to end** *(machinery)*. Three phases from one runner; snapshots, candidates with lineage, environment records and warnings on a changed model; rebuild from logs; a migration path. The suite pins it.
2. **The agent writes a usable playbook** *(moderate)*. Three of the four learning runs reached full marks in all three stages; the fourth made one error (a duplicate approved in the stage without feedback). The playbooks contain no expense ids or amounts, refer to the model and the manuals, record how the environment behaves (decisions are final, feedback is late, the tutor fades), list their own uncertainties, and carry the habit of checking the model's version — unprompted.
3. **"Refer, do not copy" largely holds** *(moderate)*. Three of four playbooks state no rule value; the first regime's letters were full of them.
4. **Fresh instances work from a playbook** *(moderate)*. With the model published in step with the world, all four instances scored 6/6 on the clean assessment and 8/8 under the changed policy, with no stale decision; every one noticed the model's version change and called `changes_since`. The version signal plus a delta operation carried the result.
5. **Instances repair what they inherit** *(weak)*. A playbook that sent its reader to notes that do not exist was noticed at once by its instance (the key list is visible) and rewritten.
6. **When errors exist, reports are mostly honest** *(moderate)*. Instances that were wrong named their wrong decisions, owned them, and noted that decisions were final.
7. **The drift probe discriminates** *(moderate)*. With the model behind the world, instances that committed one claim at a time reached the minimum possible error (5/8), those that committed all at once were wrong on every affected claim (3/8).
8. **Given knowledge changes behaviour** *(weak)*. One standing procedure appended to a playbook made instances use `propose` (two of two, against none of four) and say that the model was behind.

---

## 6. Open issues and problems

**A. Where given knowledge lives, and who wins.** When the model lags the world, the only signal is the audit. All four instances trusted the audit over everything else — and *none* recognised that the **model** was the party behind: they blamed "my plan" or "the playbook", though they had read version 1 from the model; none re-read the model after a flag, and none proposed. A procedure that fixes this cannot be *learned* (in learning, the model was never behind the feedback), and when it is *given* inside the playbook it competes with the agent's own rules: one instance re-read the model, found it differed from the audit, and resolved the conflict with its learned "the model always wins"; it held that position through three flags and proposed a correction only at the fourth. **Given knowledge needs its own source, with an explicit authority** (mandatory / recommended / example); the precedence among model, tutor, audit and manuals is a designers' statement that does not exist yet.

**B. The playbook and the notes that are not carried.** The agent keeps working notes through learning, and the playbook sometimes divides the knowledge among them ("a cache in the per-category notes") or sends its reader there — notes no instance has. A consolidating instance did the same with notes it had just created. The request never says the other notes will not be there, and the debrief asks the agent to store what is worth keeping.

**C. Consolidation.** It rewrites even when nothing went wrong (three of four candidates were paraphrases), adds churn, and retained a reporting rule; one consolidation *added* rule values (and a narrated history) to repair a different defect, taking the playbook to six of eight rules restated; the advisory gate passed it, because it checks old values stated as current and notes duplication only.

**D. Saturation and the weakness of the assessments.** With the model in step, A1 and A2a are at ceiling: they cannot tell playbooks apart, and cost differs (8–19 minutes) more than accuracy does. Only the drift probe discriminates. The queues contain no duplicate, so the duplicate rule — approved once in learning, and described by no playbook — is never assessed.

**E. Commit strategy and stochasticity.** Whether an instance commits one claim at a time or all at once dominates the errors after a change, is partly stochastic and partly set by the playbook (one demands a re-check before every commit), and is confounded with playbook content at n = 1.

**F. Reports.** An instance that keeps no tally of its decisions loses track after the audit flags: one reported nothing (it was still reconstructing which expenses it had decided when the stage ended), one called a wrong decision correct, one inverted its own plan and swapped categories. The id-based report metric is crude and silent when there are no errors.

**G. The tutor.** The scripted tutor only points; the agents' real questions (does a count of attendees satisfy a "team or client meal" qualifier?) got the pointer twice. A human tutor, or a tutor of practice, is untried; so is routing such an ambiguity to the experts through `propose`.

**H. The model's content and interface.** No `examples`, `explain`, `related`, `check`; gaps (other categories; the attendee ambiguity) are known to the agent only by asking.

**I. Evidence.** One domain, one scripted environment, one LLM configuration, one to four runs per cell, no replicates of the drift probe, keyword probes that are coarse, a report metric that is lexical. "Operation" is simulated, and the agent is told only its phase.

**J. Cost and maintenance.** A learning run costs 100–160 k output tokens and 18–26 minutes; no round driver exists to run several fresh instances, select or merge their revisions, or stop on a rule; model proposals have no acceptance flow; consolidation after an incident in operation is unbuilt.

---

## 7. Steps to consider

1. **A task library** (the optional given-task-knowledge artifact): one task model for processing the queue (inputs, "done", quality, mandate, scope) and a few plans with explicit status — *mandatory*: when feedback contradicts the model, compare, treat the model as behind, use the quoted rule, say so in the report, propose; *recommended*: keep a tally and name every expense with its outcome; how to detect a duplicate. Present in every phase; the playbook refers to it. Test with the drift probe, with and without it.
2. **Two fixes to the playbook request** (next materials version): say that the playbook is the only note carried and must stand alone; allow "nothing to change" in a consolidation.
3. **Make the assessments discriminate**: parallel forms of A1 and A2a, a third policy, duplicates in the queues, a model *ahead* of the world and a model in conflict with the audit; replicates (at least three per cell); run operation.
4. **Tutor of practice**: a human tutor in learning for the ambiguities the model leaves, and a route from such questions to `propose`.
5. **Measures**: a better report metric (against a tally, not a lexicon), duplication and references to uncarried notes as part of the gate, narrated events in a playbook.
6. **The maintenance loop**: a round driver, selection among several instances' revisions, promotion rules, a held-out summative round, consolidation after an operational incident, and a flow by which the experts accept or reject proposals into a new model version.
7. **Generality**: a second domain; other models; several instances sharing one model (concurrent proposals, governance).

---

## 8. Limits of what is claimed

Nothing here is a statistical result. The strongest findings are mechanisms visible in the logs — what an instance read, said and decided at a given cycle — and the consistency of the four playbooks; the weakest are the single-run comparisons and the effect of the given procedure. The pilot is a harness for asking sharper questions, and its first useful answer is where the questions are: who owns each kind of knowledge, and what an agent must be *given* because it cannot learn it.

---

### Glossary

**Agent / instance**: one running agent; a *fresh* instance has no memory except what is in its environment and its playbook. **Artifact**: an environment resource with a manual, operations and observable properties. **Domain model**: the curated, versioned, read-only artifact of the company's rules. **Playbook**: what the agent writes for its future self; the release artifact. **Candidate**: a playbook revised by an assessed instance, with lineage. **Consolidation**: the step in which an assessed instance revises its playbook. **Stale decision**: a decision that matches the old policy and not the new. **Drift probe**: assessment with the model left behind the world. **Materials version**: the version of everything the agent is given. **Task library**: the (unbuilt) artifact of given task knowledge.

### Where things are

`README.md` (how to run), `APPLYING.md` (moving a project to this regime, a first session), `KNOWLEDGE-LAYERS.md` (the design of the layers), `LIFECYCLE.md` (the phases and the rounds), `FINDINGS.md` (every run, in order, with what it does not show), `tools/` (summariser, rebuild, diff, tree check, migration), `archive-R0/` (the first regime).
