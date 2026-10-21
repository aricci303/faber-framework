# The curated domain model artifact

## Rationale and design of `CuratedModelArtifact` in the DevL pilot — 17 October 2026

*A self-contained report for a reader who has not seen the rest of the project. Context: DevL is a process for preparing an LLM-based agent for a job (learning), validating it (assessment) and putting it to work (operation), in an environment made of artifacts — resources the agent can observe and operate, each with a manual. The pilot's invented domain is employee expense approval at "Halden & Co."; between two policy versions three rules change (meals limits 40/90 → 35/75, approved software vendors, equipment limit 500 → 400). This report is about one of the pilot's two main artifacts: the domain model. Its companion describes the playbook. Evidence is from a small pilot (eight learning-and-assessment runs and six drift runs); its strength is stated where it matters.*

---

## 1. What it is, in one paragraph

The domain model is a **curated, versioned, read-only source of truth** about the domain: its concepts, how they relate, and the rules that hold, each rule with its source, its confidence and the versions in which it applies. It is authored by humans as a source file, part of the *environment* in every phase (learning, assessment, operation), and provisioned identically in each. The agent **consults** it — it never carries a copy and cannot change it — and can only **propose** changes, which are recorded for the human experts. Its operations answer *questions about knowledge*; none decides a case.

---

## 2. Rationale

### 2.1 The problem it answers

In the first regime of the pilot (R0) the agent *learned the domain itself*, into notes and a letter to its future self, from a scripted tutor and feedback. More than twenty runs showed what that costs: the knowledge lived in two or three places in the agent's own artifacts; a correction went into the place the agent happened to be reading and not into the one a successor would receive; and after a policy change an agent working from its own notes was *stale without knowing it*. Under that regime the central skill under test was remembering; the failure was a half-remembered rule.

The alternative is to say who owns domain truth. In a real organisation the rules of the domain are owned by domain experts and maintained as an artifact with a release process; an agent that relies on its private copy of them has made itself a second, unreviewed owner. So in R1 the domain is **not the agent's to learn or keep**: it is given, and what remains for the agent to learn is *how to work with it*.

### 2.2 Design principles and what each implies

| principle | consequence in the design |
|---|---|
| **One home per piece of knowledge** | the model is the only home of the rules; the agent's playbook *refers* to it and a copy is a measurable defect (see the companion report) |
| **Curated, not constructed** | authored as a source file under version control, with a source and a confidence on every rule; not built by the agent (the agent-built variants of R0 are a separate regime, now archived) |
| **Versioned, and the version is observable** | integer versions; each rule carries `since`/`until`; the **current version is an observable property** that the agent sees in every context and sees change |
| **Read-only for the agent** | no operation adds, retracts or defines; the manual says "you can read it; you cannot change it" |
| **Consult, do not delegate** | the model answers questions about *knowledge* (`rules_for`, `describe`, `changes_since`…); it never decides a case or evaluates a rule against a request. The test: if an operation needs the particulars of a request, it is a task, not knowledge. A model that decides has become the policy engine and the agent has nothing left to be |
| **A way to disagree, not a way to overwrite** | `propose(concept, change, evidence)` records a suggestion for the experts; the model itself does not change; an accepted change takes effect in a *new version* |
| **Teaching errors** | every error names what was wrong and the way out ("no such concept: X (overview() lists the concepts of this model)"; "missing parameter 'concept': nothing was done") |

### 2.3 Why the version is an observable property

It is the cheapest honest cue available: an agent that looks sees the number move (`version 1 → 2`), and a delta operation (`changes_since`) tells it what moved. This turns "staleness" from *an agent that does not know it is out of date* into *an agent that does not look*. It is also the most important simplification of the pilot, and should be read as one: many real sources carry no version signal at all (see §7).

---

## 3. The artifact as built

### 3.1 Identity and provisioning

Type `CuratedDomainModel`; the pilot provisions one instance, `domain-model-01`, from the resource `expense-domain-curated.json`. It is registered, with its manual, before the agent starts, in all three phases. The **source file is identified by the SHA-256 of its bytes**; that record — kind, model id, source, hash — is what a playbook snapshot stores as *the environment it was learned against*, so that a mismatch is a warning and not a surprise.

### 3.2 The source file

```
{ id, title,
  versions: [ { id, released, changes: [ "free-text description of what changed", … ] } ],
  concepts: [ { name, kind, definition,
                relations: [ { type, target } ],
                groundings: [ "where this lives in what the agent can do, e.g. expense-system-01.get_expense (attendees)" ],
                rules:     [ { id, text, source, confidence, since, until? } ] } ] }
```
A rule is *active at version v* iff `since ≤ v < until` (no `until`: still in force). The current content: **13 concepts** (Employee, Expense, Category, Receipt, Decision, EscalationRole, ApprovalPolicy, and the six categories), **13 rule entries** — the ten in force at each version, of which the three that the policy change replaces appear twice, once per version (e.g. `meals-1` for versions 1 to 2, `meals-2` from 2) — and **two versions**, whose history lists the three changes with old and new values. Every rule cites the *Finance policy handbook*, a section and a revision, with confidence "confirmed". Concept kinds in use: actor, entity, value, process.

### 3.3 Operations the agent has

| operation | returns |
|---|---|
| `overview()` | the model's map at the version in force: title, version and release date, every concept with a short definition and its number of rules, and where to go next |
| `describe(concept)` | a concept in full: definition, relations, groundings, and its rules with source, confidence and the version since which each applies (name is case-insensitive) |
| `rules_for(concept)` | the rules of one concept at the version in force, each with source and `since`; says so if there are none |
| `search(term)` | the concepts and rules, at the version in force, that mention a term |
| `version()` | the version in force and the versions published so far — **it does not reveal that a later version exists** |
| `changes_since(version)` | what the experts changed since a version the agent knows (version by version); "no changes" at or beyond the version in force |
| `propose(concept?, change, evidence)` | records a proposal with its number and the version it was made at; the model is unchanged; `change` and `evidence` are required, `concept` must exist if given |

Observable properties: `version` (a string), `concepts` (the names), `proposals` (a count). The agent's manual describes the model as "the company's source of truth about the domain… you can read it; you cannot change it", states that what it records holds "even where it differs from what you would assume", and ends: "If what you observe contradicts the model, say which, and propose a change". **The manual gives no hint about staleness or about what may have changed**; a test holds it to that.

### 3.4 The designer's side (not agent operations)

`publish(version)` — the experts release a version; from then on every consultation reads it, and the observable `version` changes. `ruleTexts()` — the rules in force as texts, for the measurements. `proposals()` — what the agent proposed. `opCountsByStage()` — what was consulted, by stage, **failed attempts included** (consulting is the thing measured). `toMarkdown()` — an export for the experts: the model in force, its hash, every rule with source and confidence, and the agent's proposals with their evidence. The artifact is `synchronized` and notifies observers on each change.

### 3.5 The model in the process

*Learning*: version 1; the stage messages tell the agent where the rules are and that it cannot change the model but can propose; the scripted tutor *points to the model* and teaches no rule. *Assessment A1*: version 1. *Assessment A2a*: the experts **publish version 2 in step with the world** (the default) before the stage's first message, so the agent's first cycle shows the change; or the model is **left at version 1** (`--model=stale`), the *drift probe*. *Operation*: version 2 from the start — a fresh instance never sees a change it did not live — or version 1 under `--model=stale`. A fresh instance is told only that "the domain model is part of your environment".

### 3.6 What the tests pin

The source file is **pinned to the policy**: for every topic and both versions the model's rule text is word for word the policy's own prose, so the world and the model cannot silently disagree. Version 1 states all eight rules at their first values and none of the changes; version 2 states the three changes and the rules they replace no longer match. And: the observed behaviour of each operation including its errors; the rules follow the version in force; `version()` at version 1 does not reveal version 2; `changes_since` and `propose` as above; **read-only** (no operation adds, retracts or defines; none decides); the manual's content; `publish` and its refusals; per-stage counting; the stable hash of the source; the Markdown export.

---

## 4. What the runs show about it

*(Four learning runs and four assessments with the model in step; four drift probes; two probes with a given procedure. Strength: moderate where the four agree, weak otherwise.)*

1. **It is read, and read the way the design hopes.** Learning agents read the overview and the rules of most concepts early (11 to 28 consultations in a run); fresh instances looked the rules up before deciding (4 to 12 lookups, `rules_for` or `describe`, in an assessment run).
2. **The version cue plus the delta operation carry the in-step case.** With the model published in step with the world, every one of the four instances saw the version move, called `changes_since(1)`, and decided all eight A2a claims correctly: no stale decision.
3. **With the model left behind, the cue is gone, and so is the recovery.** Instances trusted the model, were audited, and updated from the rule the audit quotes — but *none recognised that the model was the party behind*: none re-read it after a flag, none proposed a change, no report says that the model and the audit disagree. The disclose-and-propose path of the design was not taken once in four runs.
4. **`propose` is used when the agent is *given* a reason to.** With one standing procedure appended to the playbook, instances proposed (one and three proposals) and said the model was behind. The proposals were useful but uneven: one quoted the audit's rule exactly as evidence, another cited only "audit-confirmed rule".
5. **A small interface friction.** The parameter of `rules_for` is literally `concept`; agents called it `category` and had to correct (one playbook even records the lesson).
6. **The agents' real questions were about what the model does not say** — whether an attendee count of two or more satisfies "a team or client meal" — and the scripted tutor could only point back to the model.

---

## 5. Open issues and design questions

**A. Two homes in the code.** The simulated world's policy (`ExpensePolicy`, which decides what is right) and the model's rule texts are authored separately and *pinned* to each other by a test of text equality. In a real setting the world is not generated from the model; the pilot's pinning is a convenience, and it hides the case that matters most — the two diverging (see D).

**B. The change history is hand-written.** `changes_since` returns textual descriptions authored per version, not a diff computed from the rules; they can drift from the rules they describe. A derived diff, or a check that the history matches the `since`/`until` ranges, is missing.

**C. The interface is thin where the design sketch is rich.** Not built: `examples(concept)` (worked examples held to the rules by tests), `explain(rule)` (the rationale), `related(concept)`, `check(statement)` (is a statement the agent holds consistent with the model?), and the *ubiquitous-language* vocabulary with terms to avoid. The concept graph (relations, groundings) is stored and shown but not navigable. `search` is a substring match.

**D. Staleness modes beyond "behind by a version number".** Tested: in step, and left behind. Untested: a model *ahead* of the world, a model in conflict with the audit about a rule that did *not* change, a model that is silent, a model changed mid-task. And the precedence among model, tutor, audit feedback and manuals is nowhere stated — by the designers or the model — although the experiments show agents need it.

**E. The cue may be unrealistically generous.** An observable version number plus a delta operation is the best case. The drift probe removes it for the agent but not for the *artifact*: the question of how an agent would detect a stale source with no version at all (by cross-checking, by feedback, by asking) is open.

**F. Content.** Every rule is "confirmed" from one handbook; `confidence` and `source` are barely exercised. `EscalationRole` and `Decision` carry no rules; the attendee ambiguity is a gap the agent can only meet by asking. The domain is one invented, small, clean policy.

**G. Proposals have no life.** They are recorded and exported, and nothing follows: no review queue, no acceptance producing a new version, no deduplication, no feedback to the proposer, no check of evidence quality.

**H. Concurrency and governance.** One agent, one model. Several instances sharing a model raise concurrent proposals, versioning of the artifact under concurrent change, and who the experts are.

---

## 6. Steps to consider

1. A **derived change history** and a consistency check between the history and the rules' version ranges.
2. The missing operations, in the order the agents' questions suggest: `examples`, `explain`, `check`, `related`; accept `category` as a synonym of `concept`, or make the error teach it.
3. **Stale-model variants** of the assessments beyond the one tested: model ahead; model in conflict with the audit on a rule that did not change; and an *un-versioned* variant that removes the cue.
4. A **proposal flow**: structured evidence (the quoted audit text), deduplication, an experts' decision that publishes a new version, and feedback to the agent.
5. **Authority statements** in or next to the model: the precedence among sources — to be designed together with the task library of the framework, since the same statement governs what a plan tells the agent to do.
6. A **second domain**, to separate what is about this policy from what is about the artifact.

---

*Where to look next: `ARTIFACT-PLAYBOOK.md` (the companion), `DEVL-REPORT.md` (the whole process), `KNOWLEDGE-LAYERS.md` (the design note), `FINDINGS.md` (every run in order). The artifact is `faber-framework/src/main/java/faber/devl/CuratedModelArtifact.java`; its source `…/resources/faber/devl/expense-domain-curated.json`.*
