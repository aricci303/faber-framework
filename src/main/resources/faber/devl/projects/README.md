# Projects

A **project** is a case study: one domain and one task, for which an agent is prepared (`DevlLearningMain`), assessed (`DevlAssessmentMain`) and put to work (`DevlOperationMain`). Each project is **a folder here**, and the tools take its name:

```
faber.devl.DevlLearningMain    --project=<folder>
faber.devl.DevlAssessmentMain  --project=<folder> --playbook=FILE
faber.devl.DevlOperationMain   --project=<folder> --playbook=FILE
```

The files of a project folder have **standard names**, because they stand for the DevL artifacts and the process reads them by role, not by name:

| file | what it is | read by |
|---|---|---|
| `project.json` | the project's identity (`id` = the folder's name), the class of its code (`devlCase`), previous ids, the item-id pattern, the scripted tutor's topics, and the **12 text slots** that complete the framework's messages | every tool |
| `domain-model.json` | the **curated domain model** (`domain-model-01`): concepts, rules with their source and the versions they apply in, the history of versions | every tool |
| `rules-reference.json` | the **rule probes** (what a text must say to state a rule) and what the change did to the rules; used to measure, never shown to the agent | every tool |
| `task-library.json` | the project's **task library** (`task-library-01`): its tasks and its domain-specific plans (the framework adds its own base of five practices) | every tool |
| `README.md` | what the project is and how to run it | people |

A project also has **code**, because a business system is not a file: a class implementing `faber.devl.DevlCase` (its work, its rules in the two worlds, its business system as an artifact). `project.json` names that class in `devlCase`, so that nothing is registered. See `ADDING-A-CASE.md` for how to write one; copy `toy-room-booking`, the smallest complete project.

| project | domain |
|---|---|
| `expense-approval` | employee expense approval at an invented company (the first project; the one all the runs so far are about) |
| `toy-room-booking` | meeting-room bookings at a small office (the framework's own test project, and the template) |
