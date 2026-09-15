# Agent latency playbook

Optional method for publishing a comparative claim about agent speed. Ticket: S2760.

The companion document `docs/AGENT_COST_PLAYBOOK.md` governs what a session **costs**. This document records a comparable latency experiment only when the owner wants to publish or compare a measured route. It is not a gate for changing the workflow: the owner may act on directly observed session effectiveness without a latency record.

## The four stages

A latency claim names which stage moved. A total that got smaller with no stage attributed explains nothing and cannot be acted on.

1. **First response** - request submitted until the first visible output token. Client-observed.
2. **Model reasoning and output** - the model thinking and emitting. Client-observed, and frequently not exposed at all.
3. **Tool and local-command execution** - gradle, the gate scripts, greps, catalog queries. Repository-observed.
4. **Queue and lock waiting** - a build or code domain lock, a ticket lease, a sibling session holding a turn. Repository-observed.

## Client-observed versus repository-observed

The split decides who may write a number.

- **Client-observed** (stages 1 and 2): only the operator can supply these, read off the client. No script in this repository can see them, so `record-run.ps1` preserves whatever the operator supplied and never writes over it.
- **Repository-observed** (stages 3 and 4): measured monotonically by the scripts themselves. An operator does not type these in from memory.

A stage that could not be measured is recorded as unavailable **with its reason**, never omitted. An omitted stage reads as zero in every later average, which turns a missing measurement into a favourable one.

## Limits of a formal comparison

- A lower token cost or a smaller context. That is the cost playbook's subject and its own measurement declines to call it speed.
- A shorter answer. Brevity is a formatting choice; the same route may produce a short wrong answer faster.
- A run with a mandatory check removed. Two routes are comparable only when they were judged against the same acceptance criteria and ran the same required validation.
- A perceived improvement without a record is not a publishable comparison. It is still sufficient for the owner to choose a simpler working process; no latency record is required for that operational decision.

## Recording a run when a comparison is wanted

1. Write a record against `scripts/agent_latency/latency-record.schema.json`, filling the client-observed stages from the client.
2. Validate and store it: `pwsh -NoProfile -File scripts/agent_latency/record-run.ps1 -Record <path>`. Artifacts land under `temp/S2760/`.
3. Compare routes on one control task: `pwsh -NoProfile -File scripts/agent_latency/compare-routes.ps1 -Path temp/S2760/records`.

The comparison refuses to name a winner when the acceptance criteria differ or a stage is missing without a reason. That refusal is the useful output - it says the experiment was not run, rather than reporting a result the data does not carry.

## Control set

Three owner-named classes, from the ticket's approval gate:

- **mechanical** - script and repository chores, catalog and journal operations.
- **research** - code search, locating a feature, reading the codebase before a decision.
- **engineering** - Kotlin development and build.

Thresholds are per class, not one global number: a complex engineering task compared against a mechanical threshold is always red and the threshold decides nothing. The numeric limits for first response and total completion are set by the owner **after** a baseline exists for each class, not before.

## Route evaluation record

Every evaluated route lands here with one of three outcomes - **accepted**, **rejected**, or **insufficient evidence** - the control task it ran, the acceptance criteria it was judged against, and for anything accepted, the exact rollback value.

### 2026-09-09 - baseline state

| Route | Control task | Task class | Outcome | Reason | Rollback |
|-------|--------------|:----------:|---------|--------|----------|
| any | none run | - | insufficient evidence | No record exists yet for any control task, so no route has been measured against another. The measurement contract and the runner exist; the baseline runs are an owner-driven activity because stages 1 and 2 are client-observed and only the owner can supply them. | not applicable - nothing was changed |

**Repository routing policy decision:** no change applied. The comparison that would justify one has no data, and this ticket's own constraints forbid a global model or reasoning downgrade made without it. `.sza-profile.json` is therefore untouched by S2760, which is also its rollback value: the routing policy it already carries is the one in force.

The policy that remains in force, unchanged and restated here only so the accepted/rejected columns above have a baseline to be read against: mechanical leaf work may take a cheaper route, while Kotlin, architecture, risk-bearing edits and final validation stay on the strong route. That distinction predates this ticket and is not a finding of it.

### Owner-external settings

These are client settings. No repository script sets them, and this playbook does not claim to.

- Model selection for the session and for subagents.
- Answer verbosity.
- Reasoning effort.
- **Service tier** - not available to the owner's account at all; listed so a later reader does not spend a session looking for it.

To move any of these the owner changes them in the client and records a run per the section above; the repository's part is the evidence, not the switch.
