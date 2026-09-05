# Core Operational Semantics for the STATE OF MIND Vocabulary

### Toward a rigorous floor for the cognitive-hourglass neck in generative BDI agents

---

## 1. Motivation and scope

This note formalizes **Layer 0 (Core)** of a layered STATE OF MIND vocabulary
intended to serve as (a piece of) the "neck" of a cognitive hourglass
[Ricci et al., 2024]: a level of abstraction meant to let humans and other
agents understand, predict, and govern an agent's behavior, independent of
whatever implementation sits underneath it — an LLM today, a classical BDI
interpreter tomorrow, some hybrid after that.

The governing design constraint, established informally over prior
discussion and made precise here, is **predictive sufficiency**: a
STATE OF MIND entry has done its job if and only if an observer given
*only* that entry — no raw history, no other context — can correctly
predict the action the agent is about to take, and why. This is a direct
operationalization of Newell's Knowledge Level principle of rationality
[Newell, 1982]: an agent acts as its goals and knowledge dictate, so a
knowledge-level description should be sufficient, on its own, to predict
behavior.

Core is deliberately minimal. Four further layers — **Normative**
(constraints/deontic bounds), **Stability** (Bratman-style commitment
strength and reconsideration conditions), **Social** (commitments to
other agents, delegation, trust), and **Quantitative** (preference and
tradeoff, addressing the utility-theoretic critique of purely qualitative
cognitive vocabularies) — are sketched only as forward references here.
Their job is to extend Core without needing to renegotiate it; Section 6
explains precisely how that extension is meant to work.

This note is a proposal, not a validated result. Section 7 states its
limitations candidly.

---

## 2. Syntax

A Core STATE OF MIND entry, produced once per outer-loop cycle, is a
4-tuple:

$$
\langle W, G, A, J \rangle
$$

### W — What (cited beliefs)

A finite, non-empty set of belief atoms *actually driving this cycle's
decision* — not the whole belief base, only what's cited. Each element
must be traceable to something concrete: a percept from NEW PERCEPTS, a
fact from WORKSPACE, or a belief the agent itself asserted in a prior
cycle's W.

### G — Goal

A reference to exactly one of:

- an identifier into the persistent-intentions ledger (an existing goal),
- the goal's content spelled out inline, if this cycle is the one
  *introducing* it, or
- the explicit symbol **⊥**, meaning this cycle's action is not
  goal-directed deliberation (a reflex or default response).

⊥ must be asserted, never left implicit by omission — a missing G and an
explicit ⊥ are different claims about the cycle, and conflating them
(by simply not writing anything) hides which one is true.

### A — How (the committed action)

Exactly one instance of the harness's closed action vocabulary — at
minimum `REPLY`, `INVOKE`, `WAIT`, `FOCUS`/`STOP_OBSERVING`,
`CREATE_ARTIFACT`, `DISPOSE_ARTIFACT`, each with its parameters, as
established in the accompanying system prompt. A is the literal thing
about to execute, not a description of intent toward it.

### J — Why (the justification relation)

A relation of the form

$$
r(W', G) \rightarrow A, \qquad W' \subseteq W
$$

where $W'$ is the subset of $W$ actually doing justificatory work, and
$r$ is drawn from a **closed enumeration** of relation types. At Core:

$$
r \in \{\textbf{means-end},\ \textbf{reactive}\}
$$

*means-end*: A is proposed as a way of achieving G, given W′.
*reactive*: A is a direct response to W′ with no active goal (G = ⊥).

This closed enumeration is the precise mechanism by which extension
layers plug in — see Section 6.

---

## 3. Well-formedness conditions (per-slot)

Mechanically checkable, independent of content quality:

| ID | Condition |
|----|-----------|
| **WF1** (Groundedness) | Every element of W corresponds to an actual entry in NEW PERCEPTS, WORKSPACE, or a prior cycle's recorded W. No fabricated facts. |
| **WF2** (Goal validity) | G, if not ⊥, resolves to a registered ledger id, or — on first introduction — carries inline content sufficient to be registered as one. |
| **WF3** (Action closure) | A parses as a syntactically valid member of the closed action vocabulary. Free-text descriptions of intended action are not valid values of A. |
| **WF4** (Citation validity) | $W' \subseteq W$ strictly: J cannot cite a belief absent from W. |

---

## 4. Cross-slot coherence conditions

Passing WF1–WF4 is necessary but not sufficient — a tuple can be
well-formed slot-by-slot and still be incoherent as a whole. Four
further conditions catch that:

**C1 — Means-end coherence** (after Bratman, 1987). If $r = $ means-end,
A's declared effects (per its target artifact/operation's manual) must
plausibly bear on G's target state. A tuple citing a report's
inconsistent formatting as W, consistent formatting as G, and invoking
an unrelated operation as A is well-formed but fails C1.

**C2 — Non-vacuous citation.** $W'$ genuinely justifies A only if,
counterfactually, a materially different $W'$ would likely have produced
a different A. This is a direct application of counterfactual-dependence
accounts of causal explanation [Lewis, 1973; Woodward, 2003; cf. Halpern
& Pearl's structural account of actual causation, 2005] to the narrower
question of whether a stated justification actually did causal work, or
is a plausible-sounding confabulation layered post hoc onto a decision
made on other grounds. In principle checkable by perturbation — replay
the cycle with a modified $W'$ and observe whether A changes — though
Section 7 addresses the practical cost of this.

**C3 — Consistency.** W must not assert both $p$ and $\neg p$. If G is
present (not ⊥), W must not already entail that G is satisfied — an
agent citing active pursuit of an already-achieved goal is incoherent,
not merely imprecise.

**C4 — Delta discipline.** $\langle W, G, A, J \rangle$ must differ from
the immediately preceding cycle's tuple in at least one component,
*unless* $A = $ `WAIT`, which is the one case where an unchanged tuple
is correct rather than padded. This converts the earlier informal
"state only what changed" style guidance into an equality check over
structured data.

---

## 5. The predictive-sufficiency test as a protocol

Because A is drawn from a closed, finite vocabulary (WF3), the
predictive-sufficiency criterion from Section 1 stops being a subjective
judgment and becomes an automatable classification task:

1. Given a logged cycle, hide A.
2. Present $\langle W, G, J \rangle$ to an evaluator (human, or a second,
   independent model call).
3. Ask the evaluator to select A from the closed action vocabulary.
4. Score exact-match accuracy over a trace or a corpus of traces.

This is directly instrumentable against agentic benchmarks with logged
trajectories (e.g. Gaia2/ARE) at negligible marginal cost, since it
requires no ground-truth annotation beyond the trace itself — the
recorded A *is* the label.

---

## 6. How extension layers attach (forward reference)

The four layers introduced previously attach primarily by **extending
the closed enumeration for $r$ in J**, not by adding unconstrained new
slots:

- **Normative** adds $r = $ constraint-driven, and a Constraint object
  bound into $W'$.
- **Stability** attaches a commitment-class and reconsideration-trigger
  to G itself, rather than to J.
- **Social** adds $r = $ trust-based / delegated, permitting $W'$ to cite
  ascribed beliefs about another agent.
- **Quantitative** adds $r = $ preference-driven, and permits J to carry
  a comparison between A and at least one rejected alternative action.

This preserves closure one level down: a deployment declares which
values of $r$ (and which attached objects) are in scope, and anything
outside that declared set is itself a WF violation — a layer leaking in
without being declared active.

---

## 7. Worked example

```
W = { "op_47 completed", "op_47 result: 210 words",
      "section 2 is 340 words" }
G = ledger_id("consistent_report_formatting")
A = INVOKE(compare_sections, {a: 2, b: 3})
J = means-end({"op_47 result: 210 words", "section 2 is 340 words"}, G) -> A
```

| Check | Result |
|---|---|
| WF1 | Both cited facts trace to actual percepts. Pass. |
| WF2 | G resolves to a registered ledger id. Pass. |
| WF3 | A is a valid `INVOKE`. Pass. |
| WF4 | $W' \subseteq W$. Pass. |
| C1 | Comparing sections plausibly bears on "consistent formatting." Pass. |
| C2 | Had the two word counts been equal, A would very likely have been a `REPLY` instead — genuine counterfactual dependence. Pass. |
| C3 | No contradiction; G is not already satisfied. Pass. |
| C4 | Tuple differs from the prior cycle's. Pass. |

---

## 8. Limitations and open issues

- **C2 is expensive to enforce online.** Perturb-and-replay is a sound
  *definition* of non-vacuous citation but an impractical *runtime*
  check at every cycle. Treated here as an audit/spot-check tool, not an
  online gate — a real gap between semantics and enforceable practice
  that this note does not close.
- **WF1 assumes upstream perception is accurate.** Groundedness only
  checks that a cited belief traces to *something* in the record; it
  says nothing about whether that record correctly reflects the world.
  Misperception upstream of Core is invisible to these conditions.
- **The single-G assumption may be too strong.** Real cycles may serve
  multiple concurrent goals with one action. Whether to relax G to a
  set, and what that does to C1's coherence check, is open.
- **The closed relation-enumeration for J trades expressiveness for
  checkability by design.** Whether {means-end, reactive} — plus the
  four forward-referenced extensions — is *sufficient* for real agent
  traces, or whether additional base relation types are needed, is an
  empirical question this note does not yet answer.
- **This has not been validated against real traces.** The worked
  example in Section 7 is illustrative, not evidence. The natural next
  step is running the Section 5 protocol against logged trajectories
  (e.g. from the Gaia2/ARE evaluation sketched separately) and reporting
  actual predictive-accuracy numbers, not just arguing for the design.

---

## References (informal)

- Bratman, M. (1987). *Intentions, Plans, and Practical Reason.* Harvard University Press.
- Halpern, J., & Pearl, J. (2005). Causes and explanations: A structural-model approach.
- Lewis, D. (1973). Causation. *Journal of Philosophy.*
- Newell, A. (1982). The Knowledge Level. *Artificial Intelligence*, 18(1), 87–127.
- Rao, A. S., & Georgeff, M. P. (1995). BDI agents: from theory to practice.
- Ricci, A., Mariani, S., Zambonelli, F., Burattini, S., & Castelfranchi, C. (2024).
  The Cognitive Hourglass: Agent Abstractions in the Large Models Era. *AAMAS 2024, Blue Sky Ideas Track.*
- Russell, S., & Wefald, E. (1991). *Do the Right Thing: Studies in Limited Rationality.* MIT Press.
- Woodward, J. (2003). *Making Things Happen: A Theory of Causal Explanation.* Oxford University Press.
