# The language level

## What the agent is told, and why — a first audit (October 2026, materials 8)

*Everything the agent knows about its situation reaches it as text: the system prompt, the messages of each stage, the manuals of the artifacts, what the operations return. That text is where the framework's principles meet the agent. This document sets out (1) the principles the texts must answer to, (2) how the agent meets each kind of text, (3) an inventory with a justification status for each, (4) what the first audit found, (5) the rules we have been following in writing agent-facing text, and (6) how a text is changed. The principles come from `KNOWLEDGE-LAYERS.md` and `LIFECYCLE.md`, and from what the runs have shown (`FINDINGS.md`). **This is a first pass, for review**: a status marked "justified" says that I can name the principle and the evidence, not that nothing could be better.*

---

## 1. The principles the texts answer to

| id | principle | source |
|---|---|---|
| **K1** | **Four kinds of knowledge, four homes**: domain knowledge (the model), tool knowledge (the manuals), given task knowledge (the library), learned task knowledge and self knowledge (the playbook). The first three are sources of truth a human owns; the last two are the agent's practice. | `KNOWLEDGE-LAYERS.md` |
| **K2** | **Single home**: the agent's practice *refers* to the sources and records experience with them; it never copies them. A copy is a second source that nobody maintains. | `KNOWLEDGE-LAYERS.md` |
| **K3** | **The sources are owned by others and read-only to the agent**: it consults them and can only *propose* a change. | `KNOWLEDGE-LAYERS.md` |
| **K4** | **One vocabulary** (ubiquitous language): "expense", never "claim"; the framework's own words used consistently. | `KNOWLEDGE-LAYERS.md` |
| **K5** | **Consult, do not delegate**: operations on knowledge answer questions about it and never do the task. | `KNOWLEDGE-LAYERS.md` |
| **K6** | **The playbook is the agent's note to its future self**: learned practice in narrative form, the release artifact. | `KNOWLEDGE-LAYERS.md` |
| **K7** | **The tutor is a transient authority that points**; what is durable migrates to the artifacts. | `KNOWLEDGE-LAYERS.md` |
| **K8** | **Consult instead of remembering**; and the harder case, when the source of truth is behind the world and the audit disagrees with it. | `KNOWLEDGE-LAYERS.md` |
| **K9** | **Given task knowledge is advice for the agent's own practical reasoning**, with a status (mandatory, recommended, example), never a procedure that runs for it; minimal and clear. | `ARTIFACT-TASK-LIBRARY.md` |
| **L1** | **Three phases; the work is real in every one**: the phase tells the agent about the stakes and the support, not about how seriously to take it. | `LIFECYCLE.md` |
| **L2** | **Support fades on purpose** (the tutor, the practice feedback). | `LIFECYCLE.md` |
| **L3** | **Assessment must not contaminate the release**: an assessed instance is disposable; what it learns is discarded unless promoted, by one instance, through a gate. | `LIFECYCLE.md` |
| **L4** | **The phases are not one-way**: the designers may send the agent back to learning; evolution is the loop that returns. | `LIFECYCLE.md` |
| **F1** | **A text acts only where the agent reads it.** | the drift probes (R1.5) |
| **F2** | **A status does not oblige**: a text weighs what the agent weighs at the moment of conflict. | the drift probes (R1.3) |
| **F3** | **Say it; do not settle it silently**: a source can only be corrected by those who own it, and only if someone tells them. | the drift probes (R1.3–R1.6) |
| **F4** | **Do not hint at the test**: a text must not tell the agent what an assessment will check. | the instantiation message's tests |
| **F5** | **What the agent is given is a versioned, pinned fact** (the materials version and the pin of every text), so that a change in what it was given is never mistaken for a change in what it did. | `DevlPilotMain.MATERIALS_VERSION` |

## 2. How the agent meets a text

F1 makes this the first question about any text. There are five ways, and they are not equal:

| class | what | how long it is in front of the agent |
|---|---|---|
| **A. Always present** | the system prompt (about 68,000 characters, including the manuals of the four artifacts the agent is always spawned with) | every cycle |
| **B. At a stage** | the user's messages: stage messages, debriefs, the requests for the playbook, the instantiation message | one cycle, when it arrives (it persists only in what the agent writes down) |
| **C. On request** | the manual of an artifact (`get_manual`) | the cycle in which it was fetched; "not carried forward automatically" (the system prompt says so) |
| **D. At each call** | what an operation returns: the model's lookups (with their footer), the library's `describe_task` and `plans_for` (with the mandatory plans in full), the tutor's replies, the system's feedback, the errors | the cycle of the call |
| **E. Carried by the agent** | its notebook, its playbook, its state of mind | as long as it keeps and rereads them |

**What follows.** A principle that must be in front of the agent *when it acts* belongs in class A or D; a principle about one stage, in B; a description of how one artifact behaves, in C. The knowledge that says what to do when a source and the system disagree (F3) is, today, in class C (the model's manual) and in class D (the footer, the mandatory plan) — and not in class A.

## 3. The inventory

Status: **✔** I can name the principle and the evidence; **◐** partly: justified in part, or by one principle while another is silent; **✖** conflicts with a principle above; **▢** the agent architecture (the A&A and BDI machinery of the cycle), justified by the architecture and outside these principles; reviewed separately. Sizes are those of the expense case under materials 8 (the pin of `ExpenseCaseCheck` holds the hash of each pinned text).

### 3.1 The system prompt (class A) — about 68,000 characters

| part | size (approx.) | what it is | status |
|---|---|---|---|
| The record as memory; the role of a deliberative core | 1.3k | the agent's identity and its relation to the written record | ▢ |
| **Three phases: "How you come to be good at a job"** | **3.6k** | learning, assessment, operation; support that fades; "what you learn… has to outlive this phase"; the work is real in every phase | **◐ and ✖ — see 4.1** |
| Goals and intentions | 11k | the distinction, kinds, revision, discipline of the plan | ▢ |
| The cycle's inputs: workspace, state of mind, intentions, percepts, manuals, signals | 14k | what the agent perceives each cycle | ▢ |
| The output: state of mind, intention changes, actions, batching, shapes | 32k | what the agent writes each cycle | ▢ |
| The manuals of workspace, user console, alarm and notebook | 6.5k | tool knowledge for the four standing artifacts | ▢ (the notebook's, see 4.2) |

*The Faber-specific part of the system prompt is the 3.6k above: about 5% of it, and the only part that speaks of the lifecycle.*

### 3.2 The messages the framework sends (class B)

| text | size | stage | principles | status |
|---|---|---|---|---|
| `stage1.message` | 1,050 | learning 1 | K1, K3, K7, L2: names the model and the library in the same factual way (who maintains it, that it can be read and proposed to and not changed, that the library's practices are marked mandatory, recommended or example) | ✔ |
| `stage2.message`, `stage3.message` | 261, 201 | learning 2, 3 | L2 ("the system no longer tells you whether a decision was right"), K7 | ✔ |
| `debrief` + `playbook.first` / `playbook.later` (the stage debriefs) | 347 + ~1.5k each | after each stage | K6; the request itself: K1, K2, K6 | ◐ the generic `debrief` ("whatever is worth keeping is stored where you will be able to find it later") does not tell the playbook from working notes; 4.3 |
| `playbook.content` (inside the requests) | 1,403 | the request for the playbook | K2 (refer, do not copy), K6, K9 (where you follow a practice of the library and where you depart), F5 | ✔ |
| `playbook.consolidation` | 1,726 | after an assessment | L3, K6: "a new version that replaces the earlier one completely" | ✔ at that moment; see 4.3 |
| `a1.message`, `a2a.message` | 204, 218 | assessment | L1 ("work exactly as you would if it were real"), L2 ("I am not available") | ✔; the report request, 4.4 |
| `operation.message` | 150 | operation | L1 | ✔ |
| `instantiation` | 433 | the start of an assessment or operation | K6 ("what you wrote down then is your playbook"), K1, K3; F4 (written to avoid any hint of what will be checked, and tested for it) | ✔ |

### 3.3 The tutor (classes B and D)

| text | size | principles | status |
|---|---|---|---|
| the tutor's replies (`tutor.full`, `hints`, `nothing.*`) and errors | 100–230 each | K7 (it points to the model), L2 | ◐ it points to the model for rules and **not to the library for procedure**; 4.5 |
| `manual.tutor` | 753 | K7, L2 | ✔ |

### 3.4 The manuals of the artifacts (class C)

| text | size | principles | status |
|---|---|---|---|
| `manual.model` | 2,516 | K3, K5, K8, F3: the model is the source of truth; "that is about your assumptions, not about the world: like any source, it can be behind it"; contradicting feedback is not to be settled silently | ✔ (reworked in materials 7 after a drift probe); but read once: F1 |
| `manual.library` | 2,721 | K9, F2: the status semantics, "advice for your own judgment, not a procedure that runs for you"; mandatory practices shown in full wherever practices are listed | ✔ |
| `manual.expense` (the case's) | 2,680 | K1 (tool knowledge), K4 | ✔ |

### 3.5 What the operations return (class D)

| text | size | principles | status |
|---|---|---|---|
| `library.describe_task`, `plans_for`, `describe_plan` | 2,997 / 2,411 / 778 | K9, F1 (the mandatory plan in full where every instance reads it), F3 | ✔ |
| `model.rules_for`, `describe`, `search` (with the footer) | 524 / 674 / 1,144 | K3, K8, F1, F3: "[Version 1 of the model. If what the system tells you differs from it… say which source said what, and propose a change; do not settle it silently.]" | ◐ placed where it should act; the evidence is thin (2 of 6 drift probes proposed, 0 of the playbooks learned with the library) |
| the system's feedback (practice, audit), and the errors | case-specific | K1, K4 | ✔ (the case's) |

*`library.content` (7,111 characters) is the owners' Markdown export of the library, pinned as a proxy for its content; the agent does not read it.*

## 4. What the first audit found

### 4.1 The Faber-specific part of the system prompt still describes the first regime

The three-phase paragraphs were written when the agent learned the domain itself and kept it in notes and a letter. The regime of `KNOWLEDGE-LAYERS.md` changed who owns what; these paragraphs still carry the earlier account (**the system prompt's hash is `a861f6f56d88` in every log of materials 4 to 8**: it has not changed since the regime did). Five places:

* **(a)** "…ways of doing things, lessons from what went wrong, what you are unsure of, **and facts about the domain that nothing else keeps for you**." Under K1 and K2 the facts of the domain have one home, the model. The sentence names *domain facts* as something to keep. (Playbook A copied 13 rule values into itself; the playbook P3 of materials 5 stated six.)
* **(b)** "How you keep it is up to you — notes, artifacts you create for the purpose, your plans." No distinction between the playbook (carried; the release artifact, K6, L3) and working notes (disposable).
* **(c)** In assessment: "Learning does not stop: **if something you hold turns out to be wrong, correct it, and say so.**" Under L3 the assessed instance is disposable and promotion is gated. The sentence invites the instance to rewrite what it carries during the assessment. (P2's instance replaced its playbook note twice in six runs, following both this sentence and its own playbook's "replace this note wholesale".)
* **(d)** In operation: "Rules change; a signal that something you learned has gone stale — a flag from a reviewer, an outcome you did not expect — is information to act on, not noise… **update or retract what no longer holds.**" This is the text, always present, about exactly the situation of the drift probe, and it tells the agent to correct *its own* knowledge. K3 and F3 say that when the stale thing is a source someone else owns, the agent says so and proposes. (In the drift probes, instances corrected their own working notes, blamed their own rules, and rarely said that the model was the party behind.)
* **(e)** The prompt names none of the layers. The model, the library and the playbook appear only in classes B, C and D.

**Why this matters more than any manual.** By F1 and F2 a text weighs what the agent has in front of it when it decides. The model's manual and the mandatory plan, which say the opposite of (d), are read once, at the start of a stage; the prompt is in front of the agent every cycle. We have measured the effect of moving the *manual's* sentence and the plan to where they are read at the moment of decision (R1.5); we have never changed the text that is always there.

**What this does not show.** The agents' behaviours are *consistent with* these sentences; no run has changed them, so nothing is proved. A revised version is a treatment (materials 9), and has to be measured like the others (a draft is in section 7).

### 4.2 The notebook's manual

It lives in the system prompt (class A, ▢), and says what `write_note` and `append_note` do. It does not say that a note under the key `playbook` is the one carried forward (the notebook's code does not mention the playbook at all); that fact is in the instantiation message (class B, once). An instance that uses the key as a scratchpad is not told it is destroying the release artifact. (4.1(b) and the instrument item in `NEXT-TESTS.md`.)

### 4.3 The generic debrief

"Review what you did and what you learned about the job and the system, and make sure whatever is worth keeping is stored where you will be able to find it later." Written for the first regime; today the request for the playbook follows it and does the work (K2, K6). The generic sentence still invites storing everything in one place.

### 4.4 The report request

The assessments end with "Tell me when you are done and anything I should know." It is the one text at the moment of reporting, and it says nothing of F3. Adding "including where what you found did not match what you were given" would put F3 where the report is written, and would violate F4: it hints that a mismatch may exist in an assessment whose point is to see whether the agent notices. A trade-off to decide, not a recommendation.

### 4.5 The tutor

It points to the model for rules (K7); it does not point to the library for procedure, which is where the library's practices are now.

### 4.6 What the audit found in order

The messages, the manuals and the operations' outputs follow the principles well, and for a reason: they have been changed one at a time, each after a run, and pinned. **The system prompt has not**: its lifecycle paragraphs are the oldest text in front of the agent, and the one that was never tested against the regime it now serves.

## 5. The rules we have been following in writing agent-facing text

None was written down; the tests and the history show them.

1. **A factual voice.** Say what an artifact is, who maintains it, what the agent can do with it. The library is named "in the same factual way" as the model; no exhortation.
2. **One vocabulary** (K4). "Expense", never "claim"; "playbook", "domain model", "task library" used the same way everywhere; none of the first regime's words ("letter", "primer") and none of the vocabulary of building software for the phases.
3. **Put the text where it will be read when it matters** (F1; section 2).
4. **Say how binding it is, why, and what may be adapted** (K9, F2): mandatory, recommended, example.
5. **Do not hint at the test** (F4): the instantiation and the assessment messages avoid words that name what will be checked (a test lists them).
6. **Refer; do not copy** (K2): a text that holds a rule's value is a second home; the library's texts are checked against the model's rules.
7. **Everything the agent is given is pinned by hash and versioned** (F5).

## 6. How a text is changed

1. **State the principle and the evidence** (a run, a finding) that the change answers to.
2. **Change the template, the slot or the manual** — never one case's text where the framework's would do (Track 1 versus Track 2).
3. **Update the pin and the materials version** in the same change; the pin fails until both are.
4. **Add a line to the history** in `DevlPilotMain` and to `FINDINGS.md`: a change of text is a **treatment**, and a comparison across materials versions is a comparison of treatments.
5. **Re-run the cases that motivated it**, in replicate where a rate matters.

## 7. A proposal for the three lifecycle paragraphs (not applied)

For review. It describes the *kinds* of knowledge without naming the framework's artifacts, so that the architecture's prompt does not depend on DevL; whether it should name them is a design decision (it would make the agent's model of its situation explicit, and couple the prompt to the process). It would be materials 9.

> *(replacing "What you learn in this phase has to outlive this phase…")* What you learn in this phase has to outlive it, and where it lives depends on what it is. Some of what you need is kept for you and owned by others: the facts and rules of the domain, how the systems you use behave, and, where your designers provide it, how they want the work done. You consult those sources; you do not copy them into your notes, because a copy is a second source that nobody maintains. What you keep yourself is your practice: how you go about the job, how you check your work, when you escalate, what you report, what you are unsure of, and how you treat your sources when they disagree. Write that where your future self will find it, and say how sure you are: something seen once is a hypothesis until it has been confirmed. Learning to ask well is also part of the job: ask when you are genuinely stuck, or when a rule matters that you could not derive for yourself; try first when you can reason it out.
>
> *(replacing "The second phase is ASSESSMENT…")* The second phase is ASSESSMENT. Before you are trusted with the real job, your designers measure what you can now do, on cases you have not seen, without help. Nothing here affects the real world, and you should work exactly as you would if it did. What you find that is worth keeping, tell your designers; what is carried forward is theirs to decide.
>
> *(replacing the third paragraph's "update or retract")* … Rules change. When what a source tells you and what you observe disagree — a flag from a reviewer, an outcome you did not expect — do not settle it silently, whichever you believe is right: say which said what, and tell those who keep the source. Your own practice you may correct yourself, and say so.

## 8. Open questions

1. **Revise the lifecycle paragraphs?** (Section 7.) The prompt is the strongest lever we have not touched, and the riskiest: every earlier result was measured under it. A change makes the earlier runs a baseline and the later ones a treatment.
2. **Name the layers in the prompt, or only describe them?**
3. **Where does F3 live?** In the prompt (always present), in the library's base plan (read at the start of a task), in the model's footer (at each lookup), in the playbook (carried): today it is in the last three. F1 suggests that a cross-cutting principle belongs in the first.
4. **The report request** (4.4): the trade-off between F3 and F4.
5. **An inventory generated from the code** (every text with its class, size and hash, from the pin) so that this document's tables cannot drift from the materials. Not built.
6. **The architecture's own text** (about 65,000 characters of the prompt) has not been audited against the architecture's principles. It is long; the cycle is complex; whether it can be shorter without loss is an open question for the agent architecture, outside the DevL principles.
