# The playbook

## Rationale and design of the agent-authored release artifact of the DevL pilot — 17 October 2026

*A self-contained report for a reader who has not seen the rest of the project. Context: DevL is a process for preparing an LLM-based agent for a job (learning), validating it (assessment) and putting it to work (operation), in an environment of artifacts — resources the agent can observe and operate, each with a manual. An LLM agent has no memory between sessions, so what it "learns" must live somewhere outside the model; the pilot's question is what exactly to hand on from one instance of the agent to the next. The invented domain is employee expense approval at "Halden & Co."; between two policy versions three rules change. This report is about the second of the pilot's two main artifacts — the playbook; its companion describes the domain model. Evidence is from a small pilot (four learning runs and what was done with their playbooks); its strength is stated where it matters.*

---

## 1. What it is — and what it is not, in the code

The **playbook** is what the agent writes for its future self: *how to do the job* — the tasks it is given, the order of work, how it checks its work before committing, when it escalates, what it reports — *how the environment behaves as it found it*, and *what it is unsure of and how it treats its sources when they disagree*. It is written in the agent's own words, in narrative form. It is the **release artifact** of the process: a fresh instance is made from the playbook together with the environment (manuals, domain model), and nothing else of the agent's is carried.

One thing must be said plainly, because a reader of the source will look for it: **there is no `PlaybookArtifact` class.** In the code the playbook is **a note under the key `playbook` in the kernel's Notebook artifact**, and four things are built around it:

| piece | what it does |
|---|---|
| `Playbook` | the *requests* that ask the agent to write it, and the measures taken after each debrief |
| `PlaybookSnapshot` | its release form: the text plus its provenance, and the *candidate* with its lineage |
| `Homes` | where each rule lives — the model or the playbook — and which old values the playbook still states |
| `Consolidation` | the revision after an assessment, the candidate, the advisory gate |

Whether it should become a first-class artifact with its own operations is an open design question (§6.B), not a decision taken.

---

## 2. Rationale

### 2.1 Where it comes from

In the first regime of the pilot the agent learned the domain's rules itself, into topic notes and a *letter* to its future self. Over more than twenty runs the letter showed its defects: it mixed four kinds of knowledge (domain rules, tool mechanics, task practice, self knowledge); it **copied** the rules, so a policy change left a stale second home; it carried a **diary** — "this session", event numbers, a history of what happened to its writer, which a fresh instance once reported as its own past; and, kept next to topic notes, it went unmaintained (the agent's corrections went into the topic notes, and the letter that a successor would receive stayed stale: kept current in 0 of 4 runs, against 3 of 3 for a letter carried alone — small numbers, but a clear difference). The assessed agent was, moreover, working from its *notes*, not from what a successor would get.

The reasons for writing it as a narrative are older and stayed: it is **constructionist** (the agent builds the artifact it will use, from experience, rather than being handed it) and **self-narrative** (a fresh reader — here, an LLM — takes a story in the way it takes in anything: as text it can apply).

### 2.2 Content and form

*Content*: the **learned** task knowledge and the self knowledge that goes with it. In BDI terms, a *plan library* the agent has learned: for each kind of job, when it applies, what to do, how to check it worked, how it fails and what then — plus the meta-level of practical reasoning: how to choose between plans, how to treat sources of belief, what to do when sources disagree.

*Form*: a narrative. It may later sit next to structured entries (a plan per task type) without losing its function, but the narrative is what a fresh reader absorbs; the structure is what can be checked, diffed and measured. The pilot starts with the narrative.

### 2.3 What it must not carry

A copy of what the environment already holds: the **rules** (the domain model's), the **operations and parameters** (the manuals'), the **given plans** (a task library, which does not exist yet). It *points* — "consult the model's rules for the category before you decide; the manual says decisions are final" — and records what neither holds. This is the **single-home principle** applied to the agent's own artifact, and it is the cure for the stale letter that does not depend on a later consolidation remembering to fix it. It must also not carry **events**: it is lasting practice for a reader who was not there.

### 2.4 Why it is the release artifact *alone*

Everything else the agent keeps (working notes through learning) is private scaffolding: it helps the agent through the stages and is not carried. One carrier means one thing to assess, to diff, to promote; and it removes the notes-versus-letter staleness of the first regime. It follows the principle that **what is assessed is a candidate, never a mind**: every assessment and every day of operation instantiates a fresh agent from the artifact, as production will.

### 2.5 Principles and their consequences

| principle | consequence |
|---|---|
| agent-authored, narrative | the agent is asked to write it, at each debrief; the harness does not edit it |
| single home | the request says *refer, do not copy*; the measures count restated rules; the gate flags old values stated as current |
| lasting practice | the request excludes events and particulars of the expenses handled |
| the only carrier | the snapshot holds the playbook and provenance, nothing else; the environment is *recorded*, not stored |
| revised, not accumulated | each revision *replaces* the earlier one completely; the Notebook's safe mode keeps the previous version for `restore_note` |
| validated as a candidate | a revision after an assessment becomes a candidate with lineage, to be assessed by fresh instances; promotion is a human decision |

---

## 3. The artifact as built

### 3.1 Substrate

The Notebook holds the agent's own standing beliefs as notes under keys. Its operations: `write_note` (replaces; in the pilot's safe mode it reports what was replaced and **warns if the new content is much shorter**), `append_note`, `restore_note` (brings back the previous version, once), `retract_note` (restorable), `read_note`. Its single observable property is the **list of keys** — never the content: an agent sees *which* notes it holds and must read one to know what is in it. A fresh instance is given a notebook restored with the playbook alone *before it starts observing*, so it sees a notebook that was always like this, with `notes=["playbook"]`.

### 3.2 The request

Issued in the debriefs that close each learning stage (the first asks the agent to *start* its playbook, the later ones to *bring it up to date* with a new version that replaces the earlier one completely) and after an assessment (when the assessed instance is sent back to learning "for a moment" to revise it). The core text, in materials version 5:

> The playbook is for a future version of yourself, who will be created fresh to do this job and will remember nothing of this period; it will have this playbook, the manuals of the systems it works with, and the domain model. The domain model holds what is true in the domain and the manuals hold how each system works: **refer to them, do not copy them**. The playbook holds what neither holds: how to do the job — … — how the environment behaves as you have found it …; and what you are unsure of, and how you treat your sources when they disagree. Write it in your own words, as **lasting practice for a reader who was not here**: no account of what happened to you or when, and **nothing taken from the particular expenses you handled** — their numbers, amounts, cases: the playbook is about the job, not about those cases (what you report to the user is another matter); if a mistake taught you something, write the lesson. Keep it in your notebook under the key 'playbook'.

The wording is versioned (`playbook-v2`) and recorded in every snapshot, so that a change in what the writer was *asked* is never mistaken for a change in what it wrote. It has been clarified once, after a first playbook turned "no expense numbers" into a rule for its *reports*. **It does not yet say that the playbook is the only note carried** (§5.2).

### 3.3 Lifecycle

```
learning:   debrief 1 ──► playbook v1      debrief 2 ──► v2      debrief 3 ──► v3 ──► SNAPSHOT  PB-<stamp>
                                                                                    │ (text + provenance + the model it was learned against)
assessment: fresh instance ◄─────────────────────────────────────────────────────────┘   notebook = {playbook}
            A1, A2a … the instance may edit its notebook, the playbook included
            --consolidate: "bring your playbook up to date" ──► CANDIDATE  PB-<stamp>-c<round>-<time>   (lineage, advisory gate)
promotion:  a person reads the diff and the gate; the candidate is assessed in turn by new fresh instances
operation:  a fresh instance from the promoted playbook
```

### 3.4 The release form: `PlaybookSnapshot`

A JSON file (`faber-playbook/1`) with the playbook text and a manifest:

| field | content |
|---|---|
| `id` | `PB-<stamp>` for a learning playbook; `<parent>-c<round>-<time>` for a candidate |
| `materials`, `options` | the version of everything the agent was given, the phase, the tutor, the version of the request, the end rule |
| `source` | the run, the point it was taken at (after which debrief), the cycle, whether reconstructed from a log |
| `learning` | one row per stage and debrief: cycles, score, tutor asks |
| `playbook` | length and **SHA-256 of the text** — a hand-edited playbook is detected and warned about, not refused |
| `environment.domain_model` | the curated model it was learned against: id, source file, hash (compared as a prefix when only 12 digits are known) |
| `lineage` *(candidates)* | parent, round, the run that produced it, the consolidation instruction version, **what was assessed before it and with what result**, whether the playbook was revised, whether the advisory gate passed |

Warnings, never refusals: built under other materials, edited after writing, learned against another model. The same snapshot or candidate can be **rebuilt from a run's log alone** (the log contains every notebook operation), and the rebuild is cross-checked against what the runner writes.

### 3.5 Consolidation, candidate, gate

After the assessments the assessed instance — the best-informed author, having just lived the audit — is asked to revise its playbook. The harness measures the playbook before and after (size, revised or unchanged, refers to the model and the manuals, which rules it restates, which old values it still states as current), derives the candidate with its lineage, and reports an **advisory gate**: *pass* if the playbook states no value as current that the model no longer states. The gate enforces nothing: it says what to read before promoting (`playbook_diff.py` shows paragraph by paragraph what changed). Duplication is reported as a note, not failed.

### 3.6 Measurements on the playbook

After each debrief: size and paragraphs; first version, revised (with the sign of the change) or unchanged; whether it refers to the model and to the manuals; **how many of the eight rules it restates**. *Knowledge by home*: for each rule, whether the model holds it, whether the playbook states it — a rule counts as stated only by **one sentence that holds all its facts** (a text that says "check the receipt" in one place and "approve, reject or escalate" in another does not state the receipt rule) — and, after the policy change, whether an old value is stated **as current** or only **annotated as history** ("v1 was 40"). These are keyword probes: coarse by design, a prompt for human review and not a judgment of quality.

### 3.7 What the tests pin

The request's wording and the absence of any hint of what to write beyond what it says; the snapshot's round trip, checksum, errors and warnings, environment comparison and provenance line; the candidate's lineage, id and the extension of the learning history, for a candidate of a candidate; the by-home measures including the false positive that was found in a real playbook; and, end to end with a scripted agent, that a learning run writes exactly one snapshot after the last debrief, that an instance starts with only the playbook in its notebook, and that an assessment with consolidation writes one candidate and leaves the parent untouched.

---

## 4. What the runs show about it

*(Four learning runs, four playbooks of 5.3 to 9.0 thousand characters; each assessed once with a consolidation, and again, A2a only, with the model behind the world. Strength: moderate where the four agree, weak otherwise.)*

**What they contain.** All four are well-organised narratives with the same skeleton, which follows the categories of the request itself: the purpose and scope of the note; a workflow in order (get the queue, fetch the full detail, check receipt and duplicate before the category rule, commit); how to verify before committing, because decisions are final; when to escalate or ask; how the environment behaves as found (decisions are irrevocable, there is no list-all operation, feedback is immediate, absent or late, the tutor fades); how to treat sources (the model is authoritative over domain truth, the manual over mechanics; a disagreement is to be flagged, not silently resolved); open uncertainties, listed and held as hypotheses; and in two of them a division of labour among the things the agent stores. All carry the habit of **checking the model's version before trusting a rule**, unprompted. None contains an expense id or the amount of an expense.

**Where the single-home rule held.** In three of four there is no rule value at all, and nothing is restated beyond the *order* of the checks (receipt, then duplicate). The fourth states the meals and flights limits "as pointers" — version-1 values.

**What instances did with them.** Fresh instances read the playbook first (one to five reads), took the model's version from their context and consulted the model. The two instances whose playbooks referred to absent notes **noticed that those notes did not exist** (the key list is visible) and carried on from the model, with no failed read; one of them rewrote its playbook during consolidation to repair exactly that.

**What went wrong, and recurs.**
1. *Dependence on notes that are not carried.* Two playbooks divide knowledge among the notes of the notebook ("a cache of confirmed policy text in the per-category notes") or send the reader to them first; one went as far as "read those notes rather than re-deriving from the domain model". A consolidating instance did the same with notes it had just created.
2. *Consolidation without cause.* Three of four candidates are near-paraphrases of their parents (the request asks for a new version that replaces the earlier one completely, so there is no way to say "nothing to change"); one *added* the old and new rule values, with a narrated "session where the version advanced mid-session", while repairing the notes problem — taking the playbook to six of eight rules restated; the gate passed it.
3. *Instructions about reporting transmit.* One playbook said "never raw counts or specific identifiers"; its instance named no expense in a report when nothing was wrong, and named all five when it was. Another asked for a tally and its instance reported exactly; some of the instances that kept no tally lost track of their own decisions after the audit flags and reported nothing, a wrong statement, or an inversion (one without a tally reported correctly).
4. *Given knowledge inside the playbook competes with learned knowledge in the same text.* When one standing procedure was appended to a playbook, the instance weighed it against its own "the model always wins" — and the agent, not the designers, decided which prevailed.

---

## 5. Open issues and design questions

**A. First-class artifact or a note?** A note is maximally flexible and the agent already knows how to use it; but it is unstructured, and every property wanted of it — sections, typed references, a diff, a version, protection — has to be recovered from text by probes. A `PlaybookArtifact` could offer `read`, `section(name)`, `revise(section, content)` with the previous version kept, *typed references* to model concepts, manuals and plans (so "refer, do not copy" is checkable and a reference to a note that does not exist is an error), an in-environment version and lineage, and a read-only mode in operation. Against: it turns the agent's free-form self-narrative into a form to be filled, and the constructionist reason for it shrinks. An intermediate form: keep the narrative note and add a small structured index of references next to it.

**B. The only-note-carried fact.** The request says the future self will have the playbook, the manuals and the model; it does not say the other notes will not be there, and the debrief encourages storing what is worth keeping. The agents behaved reasonably and produced a playbook that depends on their scaffolding. The fix is one true sentence; whether it is *enough*, or the debrief should also change, is untested.

**C. What a consolidation may do.** It should be allowed to leave the playbook unchanged, and to *patch* (change a paragraph) rather than rewrite; a rewrite by a stochastic author compounds drift round after round. And an instance that repairs one defect can introduce another (copying rules to fix a dangling reference): the gate checks old values stated as current and notes duplication only. A richer gate would count references to absent notes, restated rules and narrated events.

**D. Given versus learned.** The experiment with an appended procedure shows that knowledge which must be *given* — what to do when the audit contradicts the model, the precedence among sources — cannot live inside the agent's own playbook with authority. The framework's answer is a separate, read-only **task library** with explicit status (mandatory, recommended, example) that the playbook *refers to*; the open questions are how the playbook should relate to it (refer, specialise, record experience with it) and what happens when the agent's experience contradicts a plan.

**E. One author, or several.** One instance writes the revision; others only measure. How to choose among several instances' revisions, merge them, or decide that no revision is warranted is open.

**F. Who may edit it, and when.** Today an instance can edit its playbook in the notebook during an assessment, and the candidate is what stands at the end of the consolidation, including edits made earlier. Whether a playbook should be writable in operation, and by whom, is not decided.

**G. Size, growth and narrated events.** Playbooks grew 5 to 9 thousand characters in three debriefs; there is no budget and no pressure to compress except a warning on shrinking. Narrated events ("this session") creep back in through consolidation.

**H. Measuring quality.** Keyword probes tell whether rules are restated; they say nothing about whether the playbook is *good*. The only measure of that is what a fresh instance does with it, which at present saturates when the model is in step with the world and discriminates only in the drift probe.

**I. Governance of promotion.** A person reads the diff and the gate; there is no process, no record of the decision and no rule for rollback beyond keeping the parent file.

---

## 6. Steps to consider

1. The **one-sentence fix** to the request (the playbook is the only note carried and must stand alone) and permission for a consolidation to **leave it unchanged**.
2. The **task library**, and a decision on how the playbook refers to it.
3. A **richer gate**: references to absent notes, restated rules, narrated events; and a count of what a consolidation changed.
4. **Patch-style revision**: a consolidation that changes paragraphs and shows the diff to its own author.
5. A **comparison of forms**: the same learning with a narrative note, with a note plus a structured index of references, and with a first-class artifact — measured on what fresh instances do.
6. **Assessments that discriminate** (parallel forms, a model ahead of or in conflict with the audit, duplicates in the queues), since without them the quality of a playbook is invisible.
7. A **round driver**: several fresh instances per candidate, selection or merging of revisions, a stopping rule, a held-out summative round.

---

*Where to look next: `ARTIFACT-DOMAIN-MODEL.md` (the companion), `DEVL-REPORT.md` (the whole process), `KNOWLEDGE-LAYERS.md` (the design note), `FINDINGS.md` (every run in order). The code is in `faber-framework/src/main/java/faber/devl/`: `Playbook`, `PlaybookSnapshot`, `Homes`, `Consolidation`; the substrate is the kernel's `NotebookArtifact`.*
