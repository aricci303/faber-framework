# Project `expense-approval`

**Employee expense approval at Halden & Co.** The agent approves, rejects or escalates the expenses that are pending in an expense system, by the company's rules (categories, amounts, receipts, approved vendors, duplicates). Between two versions the rules change (meals, software, equipment): that change is what the assessments look at.

## The files (standard names)

| file | what it holds here |
|---|---|
| `project.json` | id `expense-approval` (previous id `expense`, so that playbooks learned before the folders existed are accepted); the class `faber.devl.cases.expense.ExpenseDevlCase`; the expense-id pattern (`E` and three digits); the tutor's topics; the 12 text slots ("expenses", "the finance team", …) |
| `domain-model.json` | the curated model `expense-approval`: 13 concepts and 8 rules, each with its source (a finance policy handbook section) and the versions it applies in; version 2 changes the rules for meals, software and equipment |
| `rules-reference.json` | the probes for the 8 rules and the three that the change replaced (with their old values) |
| `task-library.json` | one task (process the pending expenses) and two plans of the project's own (check for duplicates; one way through a queue); the framework adds five |
| `report-fixtures.json` | 17 real reports of drift probes, labelled by hand, for `ExpenseCaseCheck` (it pins the disclosure metric); not read by a run |

## The code

`faber.devl.cases.expense`: `ExpenseClaim` (a work item), `ExpenseQueues` (the six workloads), `ExpensePolicy` (what is right in each world), `ExpenseSystemArtifact` (the expense system the agent works with), `ExpenseDevlCase` (puts it together), and `ExpenseCaseCheck` (the project's own checks, 104).

## Running it

```
faber.devl.DevlLearningMain    --project=expense-approval
faber.devl.DevlAssessmentMain  --project=expense-approval --playbook=devl-logs/devl-playbook-<stamp>.json [--consolidate] [--only=a1|a2a] [--model=in-step|stale]
faber.devl.DevlOperationMain   --project=expense-approval --playbook=devl-logs/devl-playbook-<stamp>.json [--model=in-step|stale]
```
`--model=stale` leaves the domain model at version 1 while the world has changed to version 2: the *drift probe*.

## Where to read about it

`FINDINGS.md` (every run in order), `ARTIFACT-DOMAIN-MODEL.md`, `ARTIFACT-TASK-LIBRARY.md`, `ARTIFACT-PLAYBOOK.md`.
