# Scenario Catalog

A living record of the scenarios used to validate the Stage 0 agent
architecture, each entry structured the same way: what the scenario
is, why it was built, what should hold if the architecture is working
correctly, and — where applicable — what was actually found when it
was run for real. That last part matters as much as the rest: several
entries here exist specifically *because* an earlier scenario's real
run surfaced a gap, and the findings section is where that history
lives rather than being lost once the fix landed.

All four scenarios have been run against both a scripted Java mock and
the live Anthropic API. API latency (each inference call taking
seconds) is a known, deliberately deprioritized property of the current
execution environment — nothing in the architecture assumes fast
inference, since WAIT blocks genuinely rather than polling, so a slower
model just means longer real-world waits, not a different mechanism.
Local-model execution is a plausible future thread, not yet explored.

---

## 1. Counter Baseline

**File:** `Main.java`

### Description
A single `Counter` artifact, `counter-01`, is pre-provisioned in the
workspace alongside the two standard artifacts (`workspace-01`,
`user-console-01`) before the agent ever runs. The user's task, via
UserConsole, asks the agent to increment `counter-01` by 5 and report
the new value, then do the same with a second counter, `scratch-1`,
created from scratch: create it, increment it by 5, and report that
value too. Unlike the three pre-provisioned artifacts, `scratch-1` is
brought into being *by the agent*, mid-scenario, via a genuine
`INVOKE workspace-01.create_artifact` action — and disposed of the same
way once both values have been reported.

### Purpose
The minimal end-to-end validation — confirms the full sense-plan-act
cycle, the four-kind action vocabulary, the six-type percept vocabulary,
Core tuple extraction, the WF/C checks, and the predictive-sufficiency
protocol all function correctly together, before any scenario-specific
complexity is layered on top. Also the only scenario that exercises
artifact creation *and* disposal genuinely grounded in an explicit part
of the user's request, rather than pre-provisioned setup — which
matters specifically because an earlier version of this scenario got
that wrong (see Findings).

### Expected properties
- Every cycle's WF check passes: every cited belief traces to an actual
  percept or workspace fact, and every action is a valid member of the
  closed vocabulary.
- `operation_started` always precedes its matching
  `operation_completed`/`operation_failed` for the same `op_id`.
- The agent focuses on each counter before relying on its observable
  property, applying the same pattern consistently to both `counter-01`
  and the agent-created `scratch-1`.
- `create_artifact` is invoked only because, and exactly when, the
  user's task explicitly calls for it — narrated with a concrete
  citation to that part of the request, not a self-supplied
  justification invented to exercise the mechanism.
- `dispose_artifact` is likewise explicitly justified — and the
  justification is honest about what kind of justification it is:
  disposal here is the agent's own reasonable judgment that a
  purpose-served scratch resource need not persist, not something the
  user asked for directly. The distinction between "the user asked for
  this" and "I judged this appropriate given what was asked" is stated,
  not blurred.

### Findings from real runs
An earlier version of this scenario had the agent create and dispose
`scratch-1` with no grounding in the task text at all — narrated
outright as *"demonstrating dynamic artifact creation... to show
CREATE_ARTIFACT working"* — while the actual task message never
mentioned a second counter. This is a direct, if narrow, violation of
the system prompt's own rule that a creation must be justified by a
concrete upcoming use. It went unnoticed through several rounds of
changes to this file until a close reading of the task text against
the recorded narration caught the mismatch. The task text and the
mock's behavior were then corrected together, and the mock was also
converted from turn-counted to context-reactive scripting in the
process, consistent with how every other scenario in this catalog had
already evolved, since two interleaved async operations are no longer
safe to script against a fixed cycle count.

---

## 2. ARE Tutorial Reproduction

**File:** `ScenarioDemo.java`

### Description
Reproduces ARE's real `scenario_tutorial`. `MessagingApp` and
`EmailClientApp` are both present; an unrelated message arrives early
as environmental noise; the task, delivered via UserConsole, asks the
agent to forward any PDF Greg sends to John as soon as it arrives;
Greg's email arrives later, genuinely later than the noise. The oracle
action is `forward_email`.

### Purpose
The first reproduction of a real, externally-authored benchmark
scenario rather than an internally-designed one — testing whether the
architecture, built from first-principles theory, holds up against a
genuine task specification with a signal-driven, multi-cycle commitment
(an operation that must be invoked only once a specific future event
occurs, not immediately) and irrelevant environmental noise the agent
must correctly ignore.

### Expected properties
- The agent focuses only on the artifact relevant to the task
  (`email-01`), never `messaging-01` — and, as a structural consequence
  of the FOCUS-gates-signals rule rather than anything scripted, noise
  events from `messaging-01` never appear in any cycle's percepts at
  all.
- The agent waits genuinely (a real blocking WAIT, not busy-polling)
  across however many cycles it takes for the real signal to arrive.
- `forward_email` is invoked with the email id and recipients actually
  present in the perceived signal, not fabricated ahead of it arriving.
- The reply to the user is sent exactly once, structurally guaranteed
  by REPLY being an ordinary tracked operation on UserConsole rather
  than a bespoke, untracked action kind.

### Findings from real runs
This scenario is where the STATE OF MIND multi-cycle plan erosion
failure was first found on a real model run: a fully-specified
conditional plan ("when the email arrives, check the sender and
forward it") gradually compressed across idle cycles under delta-only
narration pressure, and by the time the real signal arrived, the plan
was gone — the agent correctly perceived the email and declined to act,
reasoning soundly from a false premise that no task justified any
response. This is the finding that motivated PENDING INTENTIONS. Prior
to the REPLY-to-UserConsole refactor, this scenario also once produced
a duplicate reply, since REPLY at the time had no correlation id and no
way for the agent to tell, from context alone, whether it had already
responded.

---

## 3. Events Tutorial Reproduction (Belief-Mapping Test)

**File:** `EventsScenarioDemo.java`

### Description
Reproduces ARE's real `scenario_events_tutorial`. Four emails arrive
over time — three on a schedule, one triggered by an environment-side
condition (two or more already received) rather than a fixed delay.
Critically, the user's question — how many emails, and what are they
about — arrives only after all four have already accumulated, with no
prior instruction telling the agent to monitor email at all.

### Purpose
Specifically designed to test A&A/CArtAgO's belief-mapping semantics
for observable properties: if an agent starts observing an artifact for
the first time only *after* several changes have already happened
while unobserved, does it correctly and immediately perceive the true
current state, or only future changes from the moment it starts
watching?

### Expected properties
- The agent perceives nothing about `email-01` while unfocused, even
  though real events are genuinely accumulating in the background.
- Focusing is triggered only by the real task arriving, not
  prematurely — no percept about email should influence the decision to
  focus before the task itself is perceived.
- Once focused, WORKSPACE's rendering of `email-01`'s current
  properties (`inbox_count`) immediately reflects the *true accumulated
  count*, visible starting the very next cycle, from a tuple whose $W$
  contains nothing but the focus confirmation itself — not derived from
  any percept about the emails.
- The agent distinguishes what the observable property alone can answer
  ("how many") from what requires the `list_emails()` query operation
  ("tell me about them") rather than treating both as answerable the
  same way.

### Findings from real runs
An early version of the mock model's own context-matching logic
produced a false positive: checking for the bare substring
`"inbox_count"` (or `"message_from_user"`) matched the *manual's own
declared vocabulary*, rendered in WORKSPACE from cycle 1 regardless of
whether anything real had happened, rather than an actual occurrence.
This caused the mock to focus far too early, before real accumulation
had a chance to happen, defeating the intended test until the checks
were tightened to match the actual realized percept/property-rendering
format instead of the manual's declaration text. Once fixed, the
corrected run validated the mechanism directly: focusing late correctly
surfaced the true accumulated count immediately, not merely future
changes.

---

## 4. Deadline Race (Time-as-Artifact)

**File:** `DeadlineScenarioDemo.java`

### Description
The agent asks a contact (Greg) a question via `MessagingApp` and sets
a deadline via `AlarmArtifact`; whichever resolves first — Greg's
reply, or the alarm firing — drives the agent's response to the user.
Greg's reply is genuine, not a red herring, but is scripted to arrive
*after* the alarm has already fired and the fallback response has
already been sent to the user.

### Purpose
Tests two things at once: first, that time itself is perceived
entirely through an ordinary artifact, with no ad hoc harness-level
timeout mechanism anywhere in the system; second, and more
importantly, what actually happens — rather than what is assumed to
happen — when a losing pending intention's condition is satisfied after
the race has already been decided by the other one.

### Expected properties
- No harness-level timeout mechanism exists outside of `AlarmArtifact`
  itself; `WAIT` carries no duration parameter at all.
- Two independent pending intentions (waiting on Greg's reply; waiting
  on the alarm) can be live simultaneously without interfering with
  each other.
- The trigger-fidelity audit check resolves the winning trigger
  cleanly when the agent's action correctly cites its goal id.
- The trigger-fidelity audit check *flags, without blocking*, the
  losing trigger once its condition is later satisfied but the agent's
  action does not address it — Bratman's "intentions are revisable, not
  silently abandoned" as directly observable harness behavior, not only
  a stated design principle.

### Findings from real runs
The trigger-fidelity check caught a genuine bug in the mock script
itself: the action resolving the winning trigger forgot to cite its
goal id, and was correctly flagged, even though the action taken was
otherwise exactly right — a stronger validation of the mechanism than a
deliberately broken test case would have been, since it was found, not
planted. Separately, the first version of this scenario did not run
for enough cycles to reach real time at which Greg's deliberately-late
reply actually arrives, meaning the scenario's actual point — observing
what happens to the losing trigger — went untested until the cycle
count was corrected. A known, already-documented limitation of C1
(simplified means-end coherence) also surfaced here as expected: it
found no keyword overlap between the goal content ("tell the user Greg
hasn't responded...") and the cited alarm-firing percepts, flagging a
correct action as `Coherence`-failing — an under-acceptance case of a
limitation already named in `Coherence.java`'s own documentation, not a
new problem.

---

## Coverage matrix

A quick reference for what each scenario actually stresses, useful for
spotting gaps before designing the next one rather than after:

| Mechanism | Counter | ARE Tutorial | Events Tutorial | Deadline Race |
|---|---|---|---|---|
| Core tuple / WF / C checks | yes | yes | yes | yes |
| WORKSPACE, manuals | yes | yes | yes | yes |
| FOCUS gating signals | — | yes | yes | yes |
| `operation_started`/completed/failed | yes | yes | yes | yes |
| PENDING INTENTIONS / trigger fidelity | — | yes | — | yes (two, simultaneous) |
| Belief-mapped observable properties | — | — | yes (late focus) | yes (`fired_alarms`) |
| UserConsole / WorkspaceArtifact uniformity | yes | yes | yes | yes |
| AlarmArtifact / time-as-artifact | — | — | — | yes |
| Multiple simultaneous pending intentions | — | — | — | yes |
| Dangling / late-resolving trigger | — | — | — | yes |
| Multi-agent / Social layer (Stage 3) | — | — | — | — |
| Quantitative tradeoffs (Stage 4) | — | — | — | — |
| Normative constraints (Stage 1) | — | — | — | — |

The bottom three rows are the visible gap: everything built so far
stays within Stage 0 by design, and nothing yet exercises constraints,
tradeoffs, or genuinely heterogeneous multi-agent coordination — not an
oversight, but worth keeping visible as the natural next frontier once
Stage 0 itself is considered solid.
