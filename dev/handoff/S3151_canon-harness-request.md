# S3151 - canon harness change request

This is input for the canon update after S3151 closes.

- Harness version read: `sza` plugin `2026.913.1`, `tools/harness/`.
- Consumer: FastMediaSorter_mob_v2. Every local path is a generated forwarder, so none of these changes touches a call site here.
- Why a request and not an edit: harness bodies are overwritten by the next plugin update and never reach the other consumers (S3151 ADR-2).

## 1. Quiet output on success for the three per-ticket harness scripts

Files:
- `spec_catalog/spec-preamble.ps1`
- `spec_catalog/close-and-log.ps1`
- `spec_catalog/plan-tick.ps1`

Current behaviour:
- `spec-preamble.ps1` writes 13 `Write-Host` sites; the success block alone is lines 166-171 plus one line per registry record, all printed on every clean call.
- `plan-tick.ps1` prints a header and one line per ticked step or checkbox (lines 295-297, 634-636) on success.
- `close-and-log.ps1` prints each `Step` block's banner and child output whether or not it passed.

Required behaviour, matching the contract S3151 Phase 01 put into this repo's `scripts/post-change.ps1` and `scripts/quality/assert-fast-gates.ps1`:
- Success prints one verdict line: the script name, the outcome, the elapsed milliseconds and the counts (for example `close-and-log: S3151 -> Verified (4210 ms, 5 steps)`).
- Refusal prints the refusing step's full output, its fix hint and `protocol: <path>`.
- The full output of every run, pass or fail, goes to a protocol file under the profile's `tempDir` (`metrics/<script>-runs/<yyyyMMdd-HHmmss>-<pid>.log`).
- `-ShowPasses` and an environment switch (`SZA_VERBOSE=1`) restore today's full console output.
- `spec-preamble.ps1` keeps the fields a driver branches on (`status`, `file`, `tactical`, `last audit`, `lease`, `lease handoff`, `drift`) as one compact block; registry records print only with `-ShowPasses` or when a facet resolves to a record the caller has not acknowledged.

How a consumer observes it: a clean `/spec-all` step returns 1-4 lines instead of 15-40; a refusal is unchanged in content.

## 2. `close-and-log.ps1` validates `-FeatArea` before the status lands

Current behaviour:
- The pre-flight (lines 141-153) checks only that `-FeatArea` is non-empty and has alphanumeric content.
- The status transition runs first (lines 195-208, `close.ps1` or `update.ps1`).
- The capability record runs after it (lines 229-247), through `all_features/add.ps1`, whose own check (lines 99-102) is again only non-empty.
- Result: a mistyped area creates a new area silently, and any `add.ps1` refusal arrives after the ticket has already moved, so the retry re-runs a transition that already happened.

Required behaviour:
- The pre-flight reads the areas already in the inventory (the `add.ps1 -ListAreas` set) and refuses an unknown `-FeatArea` before any mutation, naming the three closest existing areas.
- A genuinely new area passes with an explicit `-NewFeatArea` switch.
- Every other `add.ps1` argument check that can be made without writing moves into the same pre-flight.

How a consumer observes it: a wrong area exits 1 with the ticket status unchanged and no dev-log row written; re-running after the fix produces exactly one transition and one row.

## 3. `plan-tick.ps1` moves the catalog status at the two plan boundaries

Current behaviour:
- `plan-tick.ps1` edits only plan files. `-State` accepts `NotDone`, `InProgress`, `Done`, `Manual` (line 102) and no code path calls `update.ps1`.
- Every driver therefore issues two status calls by hand: `Tactical`/`Approved` -> `In Progress` before the first step, and `In Progress` -> `Implemented` after the last one.

Required behaviour:
- `-State InProgress` or `-State Done` on the first step of a ticket whose catalog status is `Approved` or `Tactical` moves it to `In Progress` through `update.ps1`, with the closing gates it already runs.
- `-State Done` on the last not-done step of the last phase moves the ticket to `Implemented` the same way.
- `-NoAutoStatus` suppresses both, for a driver that owns the transition itself (a device-gated ticket that ends in `BlockNeedUserTest`).
- A gate refusal during the automatic transition leaves the tick written, prints the refusal verbatim and exits with a distinct code, so a caller can tell "ticked, status refused" from "tick refused".

How a consumer observes it: no call site changes; once the canon ships, `/spec-dev` and `/spec-all` drop their two manual `update.ps1` calls, and a plan whose last step is ticked can no longer sit at `In Progress`.
