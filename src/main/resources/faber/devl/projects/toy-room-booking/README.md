# Project `toy-room-booking`

**Meeting-room booking requests at Northwind Studio**: two room kinds (focus rooms, workshop rooms) and two worlds (after the change, focus rooms allow one hour instead of two). It is the **smallest complete project** — about 200 lines of code — and exists for two reasons: it is what the framework's own suite (`DevlPilotCheck`) runs on, so that any coupling of the framework to the expense project would show; and it is the **template for a new project**.

The agent never meets it unless a run says `--project=toy-room-booking`.

## The files (standard names)

`project.json` (id `toy-room-booking`, previous id `toy`; class `faber.devl.cases.toy.ToyDevlCase`; the 12 slots), `domain-model.json`, `rules-reference.json`, `task-library.json`: the same roles as in every project (see `../README.md`).

## The code

`faber.devl.cases.toy`: `BookingRequest` (a work item), `BookingSystemArtifact` (the booking system the agent works with), `ToyDevlCase` (the workloads and the provisioning).

## To make a new project from it

Copy this folder and the package, rename them (the folder, the `id` in `project.json` and the class named in `devlCase` must agree), then change the contents. `ADDING-A-CASE.md` has the steps and the checks (`CaseContract.problems(...)` must be empty).
