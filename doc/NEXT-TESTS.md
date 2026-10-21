# Next tests and work — two tracks, four moments

*A running list, meant to be edited. Reference documents: `KNOWLEDGE-LAYERS.md` (the kinds of knowledge, their homes, the artifacts) and `LIFECYCLE.md` (the phases, formative and summative assessment, the release); `LANGUAGE-LEVEL.md` (what the agent is told, and why).*

## The two tracks

* **Track 1 — the framework, and the engineering process it supports (domain-independent).** The Faber architecture, *including the agent architecture*, and the DevL process: the artifacts' metamodels and authoring guidelines, the language level (the system prompt, the messages, the manuals), the instruments, and the practice of developing, validating, deploying and evolving an agent for *any* domain.
* **Track 2 — the engineering process for specific cases and problems (domain-dependent).** Given a domain and a task, how its knowledge is developed, validated, deployed and evolved: the content of the artifacts, the agents prepared for it, the experiments on it. Today the case is *expense approval*.

**The two feed each other, and at this stage of the research the flow runs mostly one way: experiments and experience in Track 2 are the main way to understand and shape Track 1.** Each Track 2 experiment is read twice, for what it says about the case and for what it says about the framework; the "both" table at the end lists where the second reading has already changed something.

**Litmus test:** *if the domain changed tomorrow, would this still matter?* If yes, Track 1; if it concerns the expense content or its workloads, Track 2.

**Three layers, not two.** For each of the three artifacts (domain model, task library, playbook): its **metamodel and design** (function, observable properties, operations, manual, what it can say) is **Track 1**; the **guidelines for authoring** content for a domain are **Track 1**; the **content** for expense approval is **Track 2**. The base of the task library shipped by the framework, the templates of the playbook's request and the manuals are Track 1 content; the slots a case fills and the rule probes and fixtures it keeps are Track 2 content in a Track 1 format.

Neither track is only about tools and artifacts. Each also concerns the **practice**: who does what, with what evidence, and what decides that something is good enough to go on.

## The four moments

| moment | in the lifecycle | the practice it covers |
|---|---|---|
| **Development** | *learning* (once called development) | how knowledge is prepared: the learner's stages, the tutor's fading, the debriefs, the request for the playbook, who authors the domain model and the library |
| **Validation** | *assessment* (testing) | how it is tested: the forms and workloads, the measures, the number of instances, formative versus summative, the gate that decides a candidate is fit |
| **Deployment** | *operation* (once called deployment) | how it goes to work: what is released, with what evidence, under what versions, and what is watched while it works |
| **Evolution** | not yet a phase (`LIFECYCLE.md`) | how it changes afterwards: incidents and proposals, new versions of the model and the library, consolidation, lineage — the loop that returns to development |

## The artifacts: metamodel (Track 1) and content (Track 2)

| artifact | metamodel and design — Track 1 | content for expense — Track 2 | open design questions the runs raised (Track 1) | authoring guidelines |
|---|---|---|---|---|
| **Domain model** | concepts, rules (source, confidence, versions they apply in), groundings; `overview`, `describe`, `rules_for`, `search`, `version`, `changes_since`, `propose`; the footer on the lookups; read-only, versioned | 13 concepts, 8 rules and their change at version 2 | a **structured revision** of a rule's source (so that a flag citing "revision 2" can be compared with "revision 1"); whether `changes_since` should say what a change means for decisions already taken; how the model states its own limits; the authority sentence of its manual | the contract's checks (probes load, versions state what they should); **no written guide** to what a good concept or rule is |
| **Task library** | tasks (request, done, failed, quality, mandate, scope) and plans (status, `when`, steps, why, adapt, source); the status semantics; `describe_task` showing mandatory plans in full; the base shipped by the framework | one task, seven plans (two of them the case's) | `when` is free text, and **the failure was recognising the situation** (P1, P2): should a plan carry how to recognise its trigger (the observable cues)? several tasks and the catalogue; whether mandatory plans can be made to oblige; the base's independence of the domain | the contract's bounds (small, clear, no copy of the model); `ARTIFACT-TASK-LIBRARY.md` |
| **Playbook** | a note in the Notebook, plus snapshot, lineage, request, consolidation and gate — a **convention, not a class** | A, B, P1–P4 and the candidates | whether it should have **a structure** (practice, experience, uncertainties; references to library plans marked *followed* or *departed from*; the **trigger** of each practice); a single-home check; frozen during assessment; the gate comparing with the parent | the playbook request and `ARTIFACT-PLAYBOOK.md`; **no guide for the learner or the reader of a playbook** |
| **Manuals and the system prompt** | what each artifact, and the architecture, says about itself to the agent (`LANGUAGE-LEVEL.md`) | the expense system's manual | the first audit found that **the Faber-specific part of the system prompt still describes the first regime**, in which the agent owns the domain knowledge | none yet beyond `LANGUAGE-LEVEL.md` |

*What the guidelines cover least is exactly what a person adding a second domain would need first: how to write a good domain model and a good playbook. Writing them is a Track 1 item that the second domain would test.*

## Where things stand, by moment

| | built and exercised | built, not exercised | not built |
|---|---|---|---|
| **Development** | learning (4 + 2 runs), tutor, debriefs, playbook request, model and library as artifacts | the toy case as a template for another domain | rehearsal of exceptional procedures; the tutor pointing to the library |
| **Validation** | A1, A2a, drift probe, consolidation, the gate (advisory), the report and disclosure metrics | — | A2b; parallel forms; a summative round; a round driver; protection of the assessed playbook |
| **Deployment** | — | the operation phase exists in the runner | **no operation run has ever been made**; a release decision and a release manifest |
| **Evolution** | consolidation of a playbook (a first form) | `propose` is recorded and exported | review of proposals; new versions made from them; consolidation after drift or an incident; versioning across artifacts |

## Track 1 — the framework and its process, by moment

**Development**
* **The language level (`LANGUAGE-LEVEL.md`).** The first audit found that the part of the system prompt that is about the lifecycle still describes the first regime (the agent keeps "facts about the domain that nothing else keeps for you"; "if something you hold turns out to be wrong, correct it"; "update or retract what no longer holds"), and it is the only Faber text the agent has in front of it at every cycle. *Decision needed:* whether to revise it (a draft is in the document; it would be materials 9 and a treatment to measure).
* **The artifacts' metamodels and authoring guidelines** (the table above): a guide to writing a domain model, a task library and a playbook for a new domain; a **structured trigger** in a library plan; a **structure for the playbook** (references to plans marked followed or departed from); a structured **revision** of a rule's source. Each is a design decision, to be tested on the expense case and then on a second domain.
* **The playbook request** states, for each library practice, *when it applies in this environment* (the payoff of the trigger test, below).
* **Rehearsal**: learning has no conflict, by construction, so the procedure for conflicting sources is never exercised (both new playbooks say so). Is a drill a part of the process?
* **The tutor** pointing to the library for questions of procedure.
* **A single-home check at the debrief** that tells the agent when its playbook restates what the model holds (an option, a new treatment).
* **Working notes versus the playbook**: today an instance can use the playbook's own key as a scratchpad, and the system prompt and the debrief do not tell the two apart.

**Validation**
* **Protect the assessed playbook (first).** An instance that obeys its playbook's "replace this note wholesale" rewrites it mid-run (P2, twice in six runs); the consolidation then starts from the instance's cache and the end-of-A2a measures read the mutated note. The system prompt's "if something you hold turns out to be wrong, correct it" invites the same in assessment. *Options:* measure and warn (writes to the key, minimum size, the size the consolidation started from) — cheap; keep the parent snapshot for the measures and the lineage; or declare the playbook frozen to the instance.
* **The gate** to compare a candidate with its parent for *practices lost* (P2's candidate dropped its proposal step and the gate passed).
* **Instrument robustness**: the disclosure metric's lexicon (English, tuned on the expense case); how many instances a round needs; a control without the library that keeps the materials version; a rule for several treatments in a row.
* **Parallel forms, a summative round, a round driver** (`LIFECYCLE.md`).

**Deployment**
* **What the operation phase is, as a practice**: the evidence required to release a candidate, who decides, and a **release manifest** (playbook, library, model: which versions go together).
* **What is watched while it works**: the observable versions as the cue for drift; what an incident is.

**Evolution**
* **The proposal flow (design discussion before any code):** who reviews a proposal, how a version 2 of the library or the model is made, and what the proposer is told.
* **Consolidation fed by drift and by incidents**, not only by in-step assessment (which cannot import what it never experienced); lineage that stays true to the parent.
* **Versioning across artifacts**, and the roles and authority behind it (domain experts, process owners, the agent, the researcher).

## Track 2 — the case's process, by moment

**Development**
* **Learning replicates** under materials 8 (two more): how often a playbook copies the rules (A) or stays lean (B), and what it keeps. *2 runs.*
* **Authoring the library**: I wrote it from observed failures, so it is a treatment. *Ablations:* drop one plan (*let feedback reach you*; the mandatory one) or change one status. *Replicated drift probes.*
* **Whether a copied rule set matters** (A vs B under a drift where the playbook's copy and the model differ).
* **The trigger minimal pair** (see "both" below).

**Validation**
* **Drift probes in replicate** (three per playbook): the rate of disclosure, of proposals, of each stance. *6 to 12 runs.*
* **A2b**: novel and ambiguous expenses, where there is no rule to follow and judgment shows.
* **Re-assess the first four playbooks in step under materials 8** (cost and behaviour).
* **Option 3: the audit flag names its source revision** ("section 4, revision 2"). A change of the case's system; the most leading of the options; the researcher's call whether it is fair.
* **Hold and ask**: whether any instance asks the requester when sources disagree; then a scripted reply.

**Deployment**
* **The first operation runs** of the case: the job after the change, with a released playbook. What counts as good (decisions, report, disclosure), and what a release would need.
* **A second task** in the expense case (explain a decision; review a past one): the catalogue and the scope.

**Evolution**
* **Library drift**: a version 2 of the library, in step and left behind (and the playbook's restated copy of its plan).
* **Read the proposals as the owner would**: what P2's and P3's proposals say, and what the finance team would accept, change or reject.
* **The change event as an evolution test**: policy version 2 arrives; operate; incident; consolidate; round 2.

## Both tracks: where a Track 2 experiment shapes Track 1

| item | Track 2 (the case) | Track 1 (the framework) |
|---|---|---|
| **The trigger minimal pair** (`experiment/devl-playbook-20261019-165724-c1-trigger.json`): the consolidated P2 plus one bullet naming the system's feedback as a trigger | does P2 now disclose? | if so, the playbook request should ask for the trigger, and a library plan may need to carry how to recognise it |
| **The system prompt's lifecycle paragraphs against the drift probes** | agents update their own notes when flagged, copy rules, rewrite the playbook in assessment | the always-present text says "update or retract what no longer holds"; the owners-and-propose regime lives only in texts read once |
| **Who is believed** (B: "sources of truth above my own judgment") | where it comes from in this case | whether the model manual's authority sentence is right in general |
| **Why no `propose`** (2 of 6; none by A or B) | what the agents say when asked | whether the report should carry the proposal |
| **A second real domain** | new content | the real test that the framework/case separation and the library's base are domain-independent |
| **Teaching to the test** | a fix may give away this case's change | a rule for what counts as a legitimate improvement: one that would work in any domain |

## Proposed order

1. **Track 1, validation:** protect the assessed playbook, and the gate's comparison with the parent. It changes nothing the agent reads, and every other result depends on it.
2. **Both:** the trigger minimal pair, one to two drift probes, under materials 8 so that it stays comparable.
3. **Track 1, language:** decide about the system prompt's lifecycle paragraphs. If revised (materials 9) it is a treatment of its own: re-probe the key cases under it.
4. **Track 2, validation:** replicates and A2b, on the sound instrument.
5. **Track 1, deployment and evolution:** a design discussion on the operation phase, the release and the proposal flow, before any code.
6. **Track 2, deployment:** the first operation runs.
7. **Both:** a second real domain.
