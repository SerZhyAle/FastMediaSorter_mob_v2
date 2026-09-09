# scripts/agent_latency

Repository side of the agent latency measurement contract (S2760). The operator protocol, the four stages and the rules about what does not count as evidence live in `docs/AGENT_LATENCY_PLAYBOOK.md`; this file documents the files here and their result shapes.

## Files

- `latency-record.schema.json` - the record shape for one task run, version 1.
- `record-run.ps1` - validates a record and stores it under `temp/S2760/records/`.
- `compare-routes.ps1` - groups stored records by control task and reports the stage distributions, the completion outcomes and the validation results per route.

## Who may write which number

- **Client-observed** - `firstResponseMs` and `modelDurationMs`. The operator reads these off the client. `record-run.ps1` copies them through untouched and never fills them in: no script here can see the model's own timing, and a script-authored value would be a guess wearing a measurement's shape.
- **Repository-observed** - `toolDurationMs` and `queueWaitMs`. Measured monotonically by the scripts. `record-run.ps1` accepts a caller-measured value and stamps its source; it does not accept a typed-in one presented as repository-observed.

A stage that could not be measured is written as `{ "unavailable": true, "unavailableReason": "<why>" }`. It is never omitted, because an omitted stage averages as zero.

## Not evidence

Neither script accepts, and no report here derives, a conclusion from:

- token cost or context size - that is `docs/AGENT_COST_PLAYBOOK.md`'s subject, and cost reduction is not a latency result;
- a shorter answer - brevity is a formatting choice, not a quality or speed measurement;
- a run whose mandatory validation was skipped or whose acceptance criteria differ from the route it is compared against.

## record-run.ps1

```powershell
pwsh -NoProfile -File scripts/agent_latency/record-run.ps1 -Record <path-to-record.json> [-OutRoot temp/S2760] [-Json]
```

Validates the record against the schema, verifies that every required stage carries either a measurement or an unavailable reason, and writes the accepted record to `temp/S2760/records/<recordId>.json`. Nothing is written outside that root.

Exit codes: 0 accepted and stored; 1 the record is invalid against the schema; 2 the record is well-formed but missing required evidence (a stage with neither a value nor a reason, or an empty validation list); 3 the input file could not be read or parsed.

## compare-routes.ps1

```powershell
pwsh -NoProfile -File scripts/agent_latency/compare-routes.ps1 [-Path temp/S2760/records] [-ControlTask <id>] [-Json]
```

Groups the stored records by `controlTaskId`, then by `routeId`, and reports per route: the stage durations, the completion outcomes, the required-validation results and the rework counts - each as its own column, never merged into a single score.

It declines to name a faster route when the compared routes carry different `acceptanceCriteria` sets, when a route has no accepted run, or when a stage is unavailable on one side only. That verdict is `insufficient-evidence` and it is a result, not a failure of the tool.

Exit codes: 0 the comparison ran and every group reached a verdict; 1 a comparison group carries unequal acceptance evidence and cannot be judged; 2 there is not enough data to compare (fewer than two routes on any control task, or a required stage missing without a reason); 3 the record directory could not be read.
