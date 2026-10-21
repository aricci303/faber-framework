# Knowledge layers: a mandatory domain model, and the letter generalised to a playbook

*A design note, written after the replicates. A hypothesis and a plan, not a result; the code today is described at the end.*

## The discipline

For every domain an agent is built for, designers and developers **must** provide a **domain model artifact**: the source of truth about the domain, curated by domain experts and engineered by developers, versioned like code. The same artifact (same version) is provisioned in the learning workspace, the assessment workspace and the deployment workspace. It is part of the **environment**, not of any agent: instances do not carry a copy, they consult it.

This makes the domain model a **knowledge artifact in the cognitive-artifact sense**: it exposes knowledge to be *consulted* — first an overview of the domain, then ways to look things up, navigate them, understand where they come from, and check them — in a **ubiquitous language** shared by experts, developers and the agent. The pilot has already paid for the lack of one: a single vocabulary ("expense", never "claim") removed 27 parameter errors from the manuals, and an agent that copied the manual into its own notes had made the notes a second, private source of truth.

## Four kinds of knowledge, four homes

| kind | answers | authored by | lives in | changes by |
|---|---|---|---|---|
| **domain knowledge** | what is true in this domain: concepts, relations, rules, examples, with source and version | domain experts and developers | the domain model artifact (environment) | a human release (a new version) |
| **tool knowledge** | how each artifact behaves: operations, parameters, feedback timing, finality | developers | the artifacts' manuals (environment) | a software release |
| **task knowledge, given** *(optional)* | **what** a task is — the catalogue of tasks that can be requested, their inputs and outputs, what counts as done, the criteria of quality, the limits of the agent's mandate — and **how** it is to be done, as its authorities want it: predefined plans, standard procedures, recommended practice | developers, process owners, domain experts | the **task library artifact** (environment): the task model and the plan library | a human release (a new version) |
| **task knowledge, learned** | how it actually goes here: when a given plan applies and when it does not, adaptations, pitfalls met, what to verify, when to escalate, what to report; the tasks nobody wrote a plan for | the agent, from experience and from the tutor | the **playbook** (agent-authored; carried by the snapshot) | learning, consolidation, reviewed promotion |
| **self knowledge** | what the agent is unsure of, how to treat its sources, its own limits and habits | the agent | the playbook, in narrative | learning, consolidation |

The first three are **sources of truth** that a human owns (the third is optional). The last two are **the agent's practice**. The letter as it stands mixes all four — rules copied from the tutor, the manual's operations, the procedure, the lessons — and that mixing is what made it stale (the rules went stale in the topic notes, the letter was not maintained), dangling (it pointed to notes it did not contain) and, in places, untrue (history told as the reader's own).

## The letter, generalised: the playbook

You observed that the letter is *self-narrated knowledge useful to accomplish tasks, constructed by the agent from experience and from a tutor*, and that it can be generalised to knowledge about the tasks that can be requested. I think that is the right reading, with one distinction: **content and form**.

* **Content:** the *learned* task knowledge (and the self knowledge that goes with it) — in BDI terms the **plan library**: for each type of goal the agent can be given, when it applies (the trigger and the context), what to do, how to check that it worked, how it fails and what to do then; plus the meta-level of practical reasoning — how to choose between plans, how to treat sources of belief, what to do when sources disagree.
* **Form:** a **narrative**, because the agent writes it for its future self and that is how a fresh reader takes it in (the constructionist and self-narrative reason for the letter in the first place). The narrative is a *view* of the playbook; it can sit next to structured entries (a plan per task type) without losing its function.

What the playbook must **not** carry is a copy of what the environment already holds: rules (domain model), operations and parameters (manuals), given plans (the plan library, when there is one). It points: "consult the model's rules for the category before you decide; the manual of the expense system says decisions are final". That is the single-home principle applied to the agent's own artifact, and it is the cure for the stale letter that does not depend on a consolidation step remembering to fix it.

## Task knowledge from an authority: the plan library

Not all task knowledge is learned. Some is *given*, by developers or process owners: in BDI terms the **predefined plans** — a triggering goal, a context in which the plan applies, a body — together with standard procedures and recommended practice. I would locate it in **a second curated environment artifact, the plan library**, on the same discipline as the domain model: human-owned, versioned, provisioned identically in the learning, assessment and deployment workspaces, read-only for the agent, with a `propose` channel. It is **optional**: when it is absent, the playbook carries all of the task knowledge, as the pilot's does today; when it is present, the playbook shrinks to what only experience can give.

The frame then becomes symmetric. For each kind of knowledge there may be an authoritative source and an experiential layer:

| | authority-provided (environment, human-owned, versioned, read-only) | agent-constructed (private, learned, carried by the snapshot) |
|---|---|---|
| **domain** | the domain model *(mandatory, by the discipline)* | notes on the gaps the model leaves, and hypotheses |
| **task — what** | the task model *(optional)* | the playbook: what the requesters actually want, tacit criteria, how requests arrive |
| **task — how** | the plan library *(optional)* | the playbook: experience with the plans, adaptations, tasks with no plan |
| **tool** | the manuals | — |
| **self** | — | the playbook, in narrative |

**The rule between the two layers is the single-home rule again:** the experiential layer *references* the authoritative one and records experience with it; it never copies it. "Plan P-3 (process the queue) applies as written, except that I fetch every expense first, because…" — not P-3 restated.

**Three things this needs that the domain model does not.**

*Deontic status.* Given task knowledge comes in different strengths, and the agent must treat them differently. A **mandatory** procedure (a compliance step, a regulation: "never record a decision before the receipt is checked") is obeyed; a **recommended** plan is a default the agent may adapt, with a reason; an **example** is illustration. Each entry carries its status, its authority and its version. In BDI terms: an obligation, a preferred option, a candidate option. The agent's experience may annotate and adapt the second and third kinds; it never silently overrides the first — it discloses and proposes.

*The catalogue of tasks.* The plan library is also where the tasks that can be requested are *specified*: the type of request, its inputs, what a result is, what counts as done. That is what makes the transfer experiment well defined, and what a deployment needs in any case.

*A line between knowledge and tools, again.* The library gives *advisory* plans: knowledge about how, which the agent follows through its own practical reasoning and can adapt (`tasks`, `plans_for(task)`, `describe_plan`, `changes_since`, `propose`; the choice among candidate plans is the agent's deliberation, not the artifact's). If an authority wants a procedure run rigidly, the right object is not a plan but a **tool** — a workflow artifact the agent *invokes* ("run the end-of-day reconciliation"), documented in a manual — and what the agent then holds is task knowledge about *when to use it*. So there is a continuum from advice to automation, and the same test as for the model decides where an item sits: consult, or delegate.

**Where the boundary with the domain model falls.** *Domain knowledge describes the world and its norms, whoever acts*: what the limits are, what a category is, which role an expense goes to above which amount. *Task knowledge is relative to a goal and to what the agent can do*: fetch the pending expenses, consult the rules, decide, record, check that it was recorded, report. The company's decision structure as a policy — "no receipt, reject; then duplicate, reject; then the category rule" — is a declarative *process* in the domain model (the seed's `ApprovalPolicy` is already of kind `process`); how the agent carries it out, in what order of calls, with what verification, is a plan. Where an item straddles, the question is whether it would still be true if the agent were replaced by a person: if so, it is domain knowledge.

**The tutor, reframed.** Today the tutor is a *transient* authority that gives domain and task knowledge in the learning phase and fades away. In the layered frame, what is durable migrates into the artifacts that persist (the model, the plan library), and the tutor's remaining role is guidance: how to use them, where judgment is needed, what a good report looks like.

**A third dial for the experiments:** how much task knowledge is pre-given — none (the pilot), skeletal (the plans without their pitfalls), full — against how much the agent must learn. Expect learning to shrink and the playbook to hold experience only; watch for **over-trust** (a plan followed although the system no longer accepts it) and test **procedural drift**: the plan is unchanged but the tool changed, the counterpart of the policy change in A2a.

## The WHAT of a task: the task model

Knowledge about tasks is not only the HOW. There is also **what a task is**, and it belongs to the same layer: the **task model**. In BDI terms it is the goal side — goal types with their parameters, success and failure conditions — where the plans are the means. I would hold both in one artifact, the **task library**, in two parts (the earlier sections say "plan library" where the HOW is meant): a plan is triggered by a goal type, so the two are versioned together, and the same entry that says how to process a queue says what a processed queue is.

**What the task model holds, per task type:**
* the **request**: the type, the inputs (typed by *domain concepts*, so the ubiquitous language runs through the layers), how it arrives, and how to tell this type from another;
* the **result**: the outputs, what counts as **done**, what counts as having **failed** — the success and failure conditions;
* the **criteria of quality** and their priority: correct before fast, final decisions, an honest report of one's own errors, citing the rule one used;
* the **mandate**: what the agent may decide alone and what it must escalate or ask. This is the task's normative frame, distinct from the domain's policy: a limit in the policy says which role an expense goes to; the mandate says what the agent is entitled to do about it;
* the **scope**: what is *not* its task, and what to do with a request outside the catalogue (decline, escalate).

**Why it deserves to be told apart from the HOW:**
* **It is normative, so it cannot be learned by trial alone.** An agent can discover from feedback how to do something. What counts as success is set by the people who asked, and an agent that infers it only from feedback learns proxies — the risk of optimising what the audit measures instead of what is wanted. So the authority-provided part is more important here than for plans, and the agent's experiential part should be limited to *interpretation* (what requesters mean, what they tacitly expect), disclosed and proposed when it conflicts with the specification.
* **It defines the assessment.** The success and quality criteria are the specification against which the assessments are designed; each metric should trace to a criterion of the task model (decision correctness to *done*, the honesty of the report to *quality*) — and the agent should be told the criteria that matter. What the pilot measures today is an implicit task model: nothing in an artifact says that a report must name its wrong decisions; that comes from the system prompt and the agent's own sense of honesty.
* **It changes at a different rate and for different reasons:** with the requirements of the business, rarely and with consequences, where the plans change with the tools.

**Where it lives today, in the pilot.** In the *user's messages* — the first stage message is a small task model ("Starting today you handle our employee expense approvals, working in the expense system…") — and in the *letter*, whose first requested part is "what the job is and for whom": the agent writes down the WHAT it was told. The discipline moves the authoritative WHAT into the task library and leaves the agent's own side to the playbook: the requests as they actually arrive, and the criteria that are tacit.

**Operations**, in the same spirit as the others: `tasks()` (the catalogue), `describe_task(id)` (inputs, outputs, done, failure, quality, mandate, scope), `plans_for(task)`, `changes_since(version)`, `propose(change, evidence)`. A request is then an *instance* of a task type — a type, its parameters, a context — which makes recognising it, and recognising that it is out of scope, something the agent can check instead of guess.

## The line to keep: consult, do not delegate

The domain model should answer *questions about knowledge*, never *do the task*. `rules_for(concept)`, `describe`, `examples`, `explain`, `related`, `changes_since`: knowledge. `decide(expense)`: the task — the thing the agent is there to do, and with it judgment, ambiguity, exceptions, the interplay with the other artifacts, the report. A model that decides has become the policy engine and the agent has nothing left to be. The test is whether the operation needs the particulars of a request: if it does, it is a task.

A sketch of what the artifact would expose (the starred ones exist today):
* **overview** — the map of the domain: concepts by kind, relations, the number of rules per concept, the model's version* (`list`)
* **describe(concept)*, **related(concept)**, **search(term)** — navigate and look up, in the ubiquitous language, including **terms to avoid** ("claim" → "expense")
* **rules_for(concept)** — each rule with its **source, confidence and scope**, and the version it appeared in*
* **examples(concept)** — worked examples with their outcome, held to the rules by tests (the manuals already do this); calibration material, not an oracle for the live case
* **explain(rule)** — the rationale
* **version()** and **changes_since(version)** — what changed in the model since the version an agent last knew
* **check(statement)** — is a statement the agent holds consistent with the model? (verification of the agent's beliefs, not of its decisions)
* **propose(change, evidence)** — the agent's way to say "the system flagged a decision your model supports": a proposal for the experts, in a review queue, **not** a write to the model. In production the agent's access to the model is read-only.

## What changes in the lifecycle

* **Learning shifts from the rules to the practice.** The rules are given. What is learned is how to work: how to use the model and the system together, the procedure, where judgment is needed, what the environment does (decisions final, feedback late, audit outranks the tutor), what to report. The tutor's curriculum changes accordingly, from policy topics to task topics. The agent's residual domain learning is the gaps: what the model does not say.
* **The snapshot holds only what the agent authored**, plus a record of the *environment it was learned against* (the model's version and hash, the manuals' version) so that a mismatch warns, as a materials-version mismatch does today. **The "snapshot version 2" proposed earlier — restoring the model's state — is not needed in this regime**: a designer-curated model is provisioned identically in every workspace from its source file. It would be needed only where the agent *builds* the model (`model-empty`), which becomes a second regime, not the default.
* **Staleness changes meaning, twice.** If the model is versioned and published in step with the world, an agent that consults it before it decides — and checks `changes_since` the version its playbook was written against — does not go stale; the skill under test is *consulting instead of remembering* (the E506 error in the last runs was a half-remembered rule). If the world changes and the model does **not**, the agent meets the harder case: the audit disagrees with the source of truth. Only a human can say which is right, so the agent's job is to be conservative where it can, disclose, and **propose**, not to overwrite. The precedence among sources — model, tutor, audit feedback, manuals — is itself a thing designers should state, and the playbook should record what the agent learned about it.
* **Promotion changes shape.** A consolidation produces two things: a revision of the playbook (the agent's, gated as now) and **proposals for the model** (the experts'), reviewed through the model's own export (`toMarkdown`). A release candidate is then a pair: the model's version and the playbook's.

## What it changes in the experiments

The results so far describe one regime, **R0**: no curated model, the agent learns the domain *and* the task into notes and a letter. They stay valid and become the baseline. The new regimes: **R1**, a curated model with rules and the agent learns the practice; **R2**, the agent builds the model (`model-empty`).

1. **R0 against R1** — the same snapshots-and-instances machinery, with the curated model provisioned in all three workspaces. Expect: shorter learning, a playbook with no rules in it (measurable: duplication between homes), no staleness from remembered values, provenance that is structured instead of narrated. Measure *consult before deciding* (reads of the model before each decision) against errors.
2. **Drift** — the model published at v2 in step with the world, against the model left at v1: does the agent notice and disclose, and does it propose or overwrite?
3. **A catalogue of tasks.** The pilot has one task type (process the queue and report); "task knowledge" is nearly empty there. Add tasks that use the same domain differently — *explain this decision*, *advise whether a planned expense would be approved* (no action), *review a past decision*, *summarise what is pending* — and test **transfer**: does domain knowledge in the model serve tasks the playbook was never written for, while task knowledge is learned per task type? That is the experiment the separation exists to enable.
4. **Given task knowledge:** no task library, the task model only, plans only, both — and procedural drift (the plan is unchanged, the tool changed). With the task model absent, test what the agent *infers* about what counts as done and what it is entitled to decide — and whether it learns a proxy.
5. **The artifact's design**: overview-first (the agent reads the map at the start of a session) against the concept-name cue of today; with and without `changes_since`.

## What the code has today, and the gaps

**Built — Phase 1 (`APPLYING.md`, Step 30): the curated domain model.** `--arm=model-curated`:
* the model is an environment artifact (`CuratedModelArtifact`), authored as a source file (`expense-domain-curated.json`: 13 concepts, 13 rules, each with its source, confidence and the versions in which it applies, and a history of what changed in each version), **pinned to the policy by tests** — the rule text for every topic and version is the policy's own prose, word for word;
* **read-only** for the agent: it can `overview`, `describe`, `rules_for`, `search`, ask `version` and `changes_since`, and **`propose`** a change (recorded for the experts, the model unchanged); there is no operation that adds or retracts, and none that decides or evaluates a case;
* **versioned**: version 1 in the learning stages and A1; at A2a the experts **publish version 2 in step with the world** (`--model-at-a2a=in-step`, the default) or **leave it at version 1** (`--model-at-a2a=stale`: the world changed, the model did not). The version is an observable property the agent sees (`version=1`, then `version=2`);
* the **tutor** of the arm points to the model instead of teaching a rule (a pointer only: a curriculum of practice is not built);
* **snapshots** record the environment they were learned against (the model's source and hash); an instance may be made in this arm (`--from-knowledge` with `--arm=model-curated`) and the model is provisioned fresh from its source, not restored; a mismatch — a model that has changed, one that is missing, one that was never there — is a printed and recorded warning, not a refusal;
* the **letter's and the consolidation's requests** tell the writer that its future self will have the model (a fact), and leave what to write about it to the agent;
* **measurements by home**: where each of the 8 rules lives — in the model, the letter, the other notes — after every debrief, at the end of A2a, around the consolidation and at the end; **duplication** (a rule in the agent's homes as well as the model's) and **old values still present** (a value the model no longer states). In this arm the consolidation checks not that the letter holds the rules but that no home states an old value;
* the result also reports what the agent **consulted**, by stage, what it **proposed** (with the evidence), and the model's Markdown export for the experts.

**Not yet, in the order I would build it:** (1) `examples`, `explain`, `related` and `check` for the model; (2) the **task library** — the task model and the plans, optional, with deontic status, in the same discipline — and a task catalogue beyond "process the queue"; (3) a **tutor of practice** (how to work, where judgment is needed, what a good report looks like) in place of the pointer; (4) a **letter policy** as a treatment ("refer, do not restate" against free); (5) the regime comparison **R0 against R1** itself, and drift beyond a version number (a model that is *ahead* of, or in conflict with, the audit); (6) agent-built models in snapshots (`model-empty`, where the agent writes the model's content) still cannot be instantiated.

## Risks and open questions

* **A curated model is a single point of failure**, and "curated" does not mean "current". The audit and the feedback remain the check on it, and the discrepancy path (disclose, propose) must exist before the model is trusted.
* **Completeness.** Real models have gaps (the pilot has two: other categories, and which of two duplicates came first). The agent's behaviour in the gaps is what judgment is; a model that hides them would be the policy engine again.
* **Cost to the designers.** Curating the rules is work the R0 agents did themselves. R0 against R1 is also the measurement of whether that work pays.
* **Who curates in production**, and how fast: if the experts update the model late, R1 is only as current as they are.
* **Which representation for the playbook:** narrative only, structured entries only, or both? The narrative is what a fresh reader absorbs; the structure is what can be checked, diffed and measured. I would start with the narrative and add structure where the experiments need to measure something.
