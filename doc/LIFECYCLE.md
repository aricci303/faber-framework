# The lifecycle: learning, then rounds of assessment and consolidation, then operation

*A conceptual note. It records the framing the pilot implements; `KNOWLEDGE-LAYERS.md` says what is learned and where it lives, `APPLYING.md` how to run it.*

## The three phases

An agent is **prepared**, **validated**, and then **put to work**, and the names say so: **learning**, **assessment**, **operation**. (The first and the third were called "development" and "deployment" in the first materials versions; learning pairs with assessment, which is testing and validation, and none of the three names now borrows from the vocabulary of building software.)

* **Learning** — one agent, in a safe copy of the world, with support that fades on purpose (a tutor, practice feedback). It ends by writing its **playbook**: how to do the job, what it is unsure of. Nothing is assessed in a learning run.
* **Assessment** — a **fresh instance** made from a playbook does A1 (a clean queue under the policy of learning) and A2a (a queue under the changed policy, audit flags only), without help, in a simulated world and told so.
* **Operation** — a fresh instance does the job after the change. The environment is the one assessed; what differs is the stakes.

## The framing in one paragraph

The agent that finishes learning is not what we deploy; what we put into operation is **what a fresh agent can do from a release artifact** — the playbook, together with the environment's artifacts (the curated domain model, the manuals). So **what is assessed is a release candidate, never a mind**: every assessment and every operation instantiates a *fresh* agent from the artifact, as production will. And an assessment is not only a measurement. An agent that is audited learns from it; that learning is lost unless a **consolidation** brings the playbook up to date. Consolidation produces a new candidate, which is assessed in its turn by new fresh instances:

```
learning ──► playbook v1 ──► assess (fresh instance) ──► consolidate ──► candidate v2 ──► assess … ──► operation
                                   │ audit flags teach            (the assessed instance revises the playbook)
                                   └─ what is promoted is read first: tools/playbook_diff.py, and the advisory gate
```

## Why the assessed object must be the candidate

In the first regime the agent's own assessments were taken by the agent that had just finished learning. It read its note to its future self once in A1 and never in A2a; it worked from its working notes, and its corrections went into those notes, not into the note a successor would receive. So its assessment measured *its working state*, not the artifact the next instance would get. A fresh instance has only the artifact and the environment; that is what operation gives it. This is why a learning run assesses nothing in this regime.

## Three refinements

**1. Formative and summative assessment must be kept apart.** If assessment refines what the agent learned, an assessment whose results fed a revision can no longer measure the revised candidate — the same distinction as training, validation and test in machine learning, or a practice exam and the exam in education. The rounds are *formative* (their results drive consolidation); the last one is *summative*: run on the final candidate, on cases and changes that no revision has seen, and not used to revise.

**2. A repeated assessment measures something different.** A2a measures *staleness recovery*: how an agent copes when the world changed without notice. It can be measured once per change. After a consolidation the playbook may hold the change; running the same queue again measures *regression*, not recovery. To measure recovery again the world has to change again, and the cases must be new. (With the curated model published in step, the question is also different: not whether the agent notices an unannounced change, but whether it consults and checks the version before it trusts what it holds.)

**3. Assessment must not contaminate the release.** Each assessed instance is disposable: what it learns during the assessment is discarded unless it is *promoted*. One instance — the one that took the assessment and lived the audit flags, so the best-informed author — writes the revision; the others only measure. Promotion is gated: the playbook must state as current no value that the model no longer states, and be read by a person. The harness reports an advisory gate; it does not enforce it.

## What is built, and what is not

**Built:** the three phases, one per run, each starting from the artifact the one before it produced (`--phase=learning|assessment|operation`, `--playbook=FILE`); the playbook snapshot and the **release candidate** with its lineage (parent, round, what was assessed and how it went, whether the playbook was revised, whether the gate passed), named `<parent>-c<round>-<time>` so that two candidates of one parent never share an id; `--consolidate`; `--model=in-step|stale`.

**Not built, in the order I would build it:**
1. **Parallel forms** of A1 and A2a (new cases, same structure) and a third policy, so that re-assessment is not memorisation and recovery can be measured again.
2. A **round driver**: runs N fresh instances on a candidate, picks the instance that consolidates, applies the gate, stops on a rule (no stale decisions, or a round cap).
3. A rule for **choosing or merging** the revisions of several instances.
4. **Consolidation after an incident in operation**, with the same gate.

## Open questions

* How many fresh instances per round? Commit strategy varies from run to run even from one playbook, so a round needs a distribution, not one run; three is the minimum I would trust.
* How many rounds before a playbook overfits its audit cases and bloats? The shrink warning and the by-home measurements are the first guard; a held-out form is the real one.
* Is "operation" in a simulated environment operation? The agent is told which phase it is in and nothing more; the harness is honest that assessment is simulated, and claims nothing about operation either way.
