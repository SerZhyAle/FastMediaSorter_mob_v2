# Phase 01 - Session-state foundation

**Strategic spec:** [`../S1339_spec-next-bounded-rounds.md`](../S1339_spec-next-bounded-rounds.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Create `scripts/spec_catalog/spec-next-session.ps1` owning `temp/spec-next-session.json`: the on-disk round state (`processed[]`, tally, device facts) that lets a bounded loop survive a `/clear` between rounds. This phase covers the bookkeeping verbs only - `-Init`, `-Record`, `-Device`, `-Resume`, `-Report`. `-CheckContext` / `-Handoff` are Phase 02.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/spec_catalog/skip-cache.ps1` read as the style precedent (root resolution via `$PSScriptRoot`, `temp/` storage, `ValidateSet` verbs, `Write-Cache`/`Read-Cache` pattern, exit-code discipline).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/spec-next-session.ps1` | New | ≤ 300 (this phase's share; Phase 02 adds ~80 more to the same file) |

---

## Steps

### Step 01.1 - Scaffold script, state schema, `-Verb Init` and `-Verb Resume`

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the script with a header comment (problem/schema/usage, mirroring `skip-cache.ps1`'s header shape) and:
>
> ```powershell
> [CmdletBinding()]
> param(
>     [Parameter(Mandatory = $true)]
>     [ValidateSet('Init', 'Record', 'Device', 'CheckContext', 'Resume', 'Report', 'Handoff')]
>     [string]$Verb,
>     [string]$Id,
>     [ValidateSet('advanced', 'verified', 'blocked', 'skipped')]
>     [string]$Outcome,
>     [string]$Note = '',
>     [string]$Online,
>     [string]$SelectedDevice,
>     [int]$Threshold = 300000
> )
> ```
>
> Resolve root the same way `skip-cache.ps1` does: `$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path`, `$statePath = Join-Path $root 'temp\spec-next-session.json'`. Create `temp/` if absent.
>
> State file schema:
>
> ```json
> {
>   "round": 1,
>   "startedAt": "2026-08-01T11:40:00",
>   "threshold": 300000,
>   "deviceOnline": false,
>   "selectedDevice": null,
>   "processed": [
>     { "id": "S1339", "outcome": "verified", "note": "", "at": "2026-08-01T11:55:00" }
>   ],
>   "tally": { "processed": 0, "verified": 0, "blocked": 0 }
> }
> ```
>
> `-Verb Init`: always overwrite (round-memory is session-scoped per the driver's existing hard rule - a fresh `/spec-next` invocation must not inherit a stale prior session's `processed[]`). Write `round=1`, `startedAt=<now>`, `threshold=$Threshold` (default 300000), `deviceOnline=false`, `selectedDevice=$null`, empty `processed`, zeroed `tally`. Print the state file path. Exit 0. Exit 1 if the write fails.
>
> `-Verb Resume`: read existing state file. Missing file -> `Write-Error "no session state to resume - run -Verb Init" -ErrorAction Continue`, exit 1 (nothing to resume is a real error here, not "cannot verify" - the caller asked to continue something that does not exist). Otherwise increment `round` by 1, persist, and print one JSON line to stdout: `{ "excludeCsv": "<comma-joined processed ids>", "deviceOnline": <bool>, "selectedDevice": "<id-or-null>", "round": <n> }`. Exit 0.
>
> Exit-code header comment (S1070 contract, this script's own meaning - distinct from `skip-cache.ps1`'s): `0 ok, 1 error (missing state / write failure), 2 cannot verify (CheckContext only, Phase 02), 3 threshold crossed (CheckContext only, Phase 02)`.

**Verification:**

- `Glob` - `scripts/spec_catalog/spec-next-session.ps1` exists.
- Run `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Init` -> exit 0, `temp/spec-next-session.json` exists, `(Get-Content temp/spec-next-session.json | ConvertFrom-Json).round -eq 1`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Resume` -> exit 0, stdout parses as JSON, `.round -eq 2`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Resume` immediately after deleting `temp/spec-next-session.json` -> exit 1.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS (Glob exists; Init exit 0 round=1; Resume exit 0 round=2 with excludeCsv=""; Resume-with-no-state exit 1). Files: scripts/spec_catalog/spec-next-session.ps1 (new, +117 LOC). `post-change.ps1 -ChangeType Script` PASS after regenerating `docs/SCRIPT_CHEATSHEET.md` (`help.ps1 -Generate`). Dev log recorded by post-change.ps1.
- 2026-08-01 - Addendum (found during Step 01.3): documented param block corrected from `[bool]$Online` to `[string]$Online` - see Step 01.3 Step Log for the cross-process binding bug this fixes. `Init`/`Resume` behavior unaffected, doc-only correction here.

---

### Step 01.2 - `-Verb Record`

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Append `{ id=$Id, outcome=$Outcome, note=$Note, at=<now ISO> }` to `processed[]` and increment `tally.processed`; increment `tally.verified` when `$Outcome -eq 'verified'`, `tally.blocked` when `$Outcome -eq 'blocked'`. `-Id` must match `^S\d{4}$` and `-Outcome` is enforced by the `ValidateSet` already on the shared param. Missing/malformed `-Id` -> `Write-Error ... -ErrorAction Continue`, exit 2 (bad request, mirrors `skip-cache.ps1`'s own convention for its `-Id` validation). Missing state file -> exit 1 (Record before Init/Resume is a caller error, same class as Resume-without-state). Persist and exit 0 on success. This must be called **before** `-Verb CheckContext` in the driver (Phase 03) so a context-threshold stop can never lose a just-completed ticket.

**Verification:**

- Run `-Verb Init` then `-Verb Record -Id S0001 -Outcome verified -Note "smoke"` -> exit 0.
- `(Get-Content temp/spec-next-session.json | ConvertFrom-Json).processed.Count -eq 1` and `.tally.verified -eq 1`.
- Run `-Verb Record -Id bad-id -Outcome verified` -> exit 2.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS (Record valid id exit 0, processed.Count=1, tally.verified=1; Record bad-id exit 2). Files: scripts/spec_catalog/spec-next-session.ps1 (+29 LOC). `post-change.ps1 -ChangeType Script` PASS. Dev log recorded by post-change.ps1.

---

### Step 01.3 - `-Verb Device`

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Persist `-Online` into `deviceOnline` and `-SelectedDevice` (if provided) into `selectedDevice`. This replaces the in-memory `DEVICE_ONLINE` / `selectedDevice` variables the driver currently holds only in the conversation - Phase 03 wires Stage 0 to call this instead. `-Online` is a **string** (`"true"`/`"false"`, matched case-insensitively; also accepts `"1"`), not a typed `[bool]` - a typed bool parameter converts inconsistently crossing the `pwsh -File` process boundary depending on whether the caller is the PowerShell tool (`$true` stringifies to `"True"`) or Bash (`-Online true`); parsing a string sidesteps both. Missing `-Online` -> exit 2. Missing state file -> exit 1. Exit 0 on success.

**Verification:**

- Run `-Verb Init` then `-Verb Device -Online true -SelectedDevice emulator-5554` -> exit 0.
- `(Get-Content temp/spec-next-session.json | ConvertFrom-Json).deviceOnline -eq $true` and `.selectedDevice -eq 'emulator-5554'`.
- Run the same `-Verb Device -Online $true -SelectedDevice emulator-5554` from the PowerShell tool (not just Bash) -> exit 0, same resulting state - confirms both cross-process argument shapes bind correctly.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS, after fixing a real cross-process binding bug found during this step: the shared param block originally declared `[bool]$Online` (written in Step 01.1); invoking `-Online $true` from the PowerShell tool crossed the `pwsh -File` process boundary as the string `"True"` and the child's typed-bool binder rejected it (`Cannot convert value "System.String" to type "System.Boolean"`), while `-Online:$true` (colon syntax) and Bash's `-Online true` both happened to work. Changed the shared param to `[string]$Online`, parsed manually in the `Device` case; corrected Step 01.1's documented param block to match (no functional change to `Init`/`Record`, doc-only fix). Files: scripts/spec_catalog/spec-next-session.ps1 (+18 LOC net). `post-change.ps1 -ChangeType Script` PASS after two `help.ps1 -Generate` cheatsheet regens (header comment text changed twice this step). Dev log recorded by post-change.ps1.

---

### Step 01.4 - `-Verb Report`

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Read the state file and print the same "session complete" shape the driver's Stage 6 final report already uses for "Processed this run" (`.claude/reference/spec-next.md` "Stage 6 - final report format"): one line per `processed[]` entry (`id`, `outcome`), plus the tally. This makes Stage 6 reconstructable after a reset - the driver (Phase 03) calls this instead of relying on an in-memory list that a `/clear` would have destroyed. Missing state file -> exit 1. Exit 0 on success, plain-text output (not JSON - this is printed straight into the chat report).

**Verification:**

- Run `-Verb Init`, `-Verb Record -Id S0001 -Outcome verified`, `-Verb Record -Id S0002 -Outcome blocked`, then `-Verb Report` -> exit 0, stdout contains `S0001` and `S0002` and a tally line showing `processed: 2`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 1/1 PASS (exit 0, output lists `S0001 - verified`, `S0002 - blocked`, `tally: processed: 2, verified: 1, blocked: 1`). Files: scripts/spec_catalog/spec-next-session.ps1 (+16 LOC). `post-change.ps1 -ChangeType Script` PASS (cheatsheet regenerated proactively before the gate ran this time). Dev log recorded by post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Script runs standalone: `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Init` returns exit 0 (validation ladder "Script: Run, exit 0" - no gradle build applies to this ticket, no Kotlin touched).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `scripts/spec_catalog/spec-next-session.ps1` - `post-change.ps1`'s `[dev-log]` gate logged one line per step (4 entries covering this phase); no separate manual entry needed.
- [x] No public Kotlin API changed - catalog regen not applicable.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Reviewed the full file (204 lines): `Read-State`'s `catch` has a safe default (`return $null`) plus a `Write-Warning` log, not a bare/broad catch (Rule 19 satisfied); `Write-State` calls in `Record`/`Device`/`Resume` are unwrapped but `$ErrorActionPreference = 'Stop'` means a write failure surfaces as a non-zero exit rather than silently succeeding - acceptable, matches the "1 = error" contract without extra code. No dead code, no stub verbs left unimplemented for the four verbs this phase owns.

---

## Handoff Notes to Next Phase

`spec-next-session.ps1` exists with `Init`/`Record`/`Device`/`Resume`/`Report` verbs and the state-file schema fixed. Phase 02 adds `CheckContext` and `Handoff` to the **same file** (same param block already declares `-Threshold`; `Handoff` reuses `-Id`-less invocation and reads `processed[]`/`tally` this phase wrote).

---

## Rollback Plan

Revert phase commit(s) - a new, self-contained script with no callers yet (Phase 03/04 are the only consumers, wired in later phases). No data migration, no user-facing surface changed, `temp/` is gitignored so nothing persists past a clean checkout.
