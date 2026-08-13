# Phase 01 - Session bootstrap facade

**Strategic spec:** [`../S1596_ticket-session-bootstrap.md`](../S1596_ticket-session-bootstrap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Introduce `scripts/spec_catalog/session-bootstrap.ps1`, which composes the four existing session-start components into one call returning one JSON payload with a per-block status, and route `/spec-next` and `/spec-do` through it.

---

## Prerequisites

- [ ] Strategic §6 items 2, 5 and 6 are Resolved - read `research/02__bootstrap-block-boundary.md`, `research/05__standalone-call-compat.md`, `research/06__facade-internal-composition.md` before writing the script.
- [ ] `research/00__as-is-chain-and-tick-mechanics.md` read - it carries every component's parameter surface, output shape and exit-code contract.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/session-bootstrap.ps1` | New | ≤ 360 |
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 20 changed |
| `scripts/spec_catalog/session-bootstrap.tests/Run-Tests.ps1` | New | ≤ 300 |
| `.claude/commands/spec-next.md` | Modified | ≤ 60 changed |
| `.claude/commands/spec-do.md` | Unchanged - verified | - |
| `.claude/reference/spec-next.md` | Modified | ≤ 30 changed |

---

## Steps

### Step 01.1 - Create the facade and compose the four blocks

**Files:** `scripts/spec_catalog/session-bootstrap.ps1`

**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/session-bootstrap.ps1` following the house script shape used by `scripts/spec_catalog/ticket-lease.ps1`: comment-based help with `.SYNOPSIS`, `.DESCRIPTION`, `.PARAMETER`, `.EXAMPLE` and `.EXIT CODES`, then `[CmdletBinding()] param(..)`, then `$ErrorActionPreference = 'Stop'`, then dot-sourced libs.
>
> Parameters: `-Resume` (switch, selects the session block's `Resume` verb instead of `Init`), `-SkipDevice` (switch), `-Claim` (switch, opt-in ticket claim), `-Exclude` (string[]), `-Threshold` (int, forwarded to the session block), `-Reason` (string, default `session-bootstrap`, forwarded to the claim), `-Format` with `json` default and `table` alternative.
>
> Exit codes, all reachable: `0` every requested block succeeded; `1` at least one block failed; `2` usage error or a component script is missing; `3` the selection block returned a candidate but the claim was lost to a live sibling. Document each in `.EXIT CODES` and nowhere else. Every non-zero exit is preceded by a `Write-Error '<message>' -ErrorAction Continue` on the line above it, never a bare `Write-Error`.
>
> Fill the four blocks, each invoking its existing component as a child process and never re-deriving its logic:
>
> - `session` - `spec-next-session.ps1 -Verb Init` (or `-Verb Resume` when `-Resume` given). Always runs.
> - `device` - `device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json`, then `spec-next-session.ps1 -Verb Device -Online <true|false> [-SelectedDevice <id>]` with the probe result. Skipped when `-SkipDevice` is given. Pass `-Online` as the string `true`/`false`, not a boolean.
> - `selection` - `spec-next-preflight.ps1 -Format json [-Exclude ..]`. Always runs.
> - `lease` - `ticket-lease.ps1 -Verb Claim -Id <selection.selected.id> -Reason <reason> -Json`. Runs only with `-Claim`, and only when the selection block returned a non-null `selected`.
>
> Each block contributes an object carrying `status` (`ok` / `failed` / `skipped`), the child's own `exitCode`, its `reason` verbatim, and its parsed payload. Never reinterpret a child's reason. Emit the whole thing as one line via `ConvertTo-Json -Compress -Depth 6` under `-Format json`; render a readable summary under `-Format table`.
>
> Dot-source `scripts/utils/agent-lock.ps1` and take the session identity from it; do not copy session-id resolution or liveness logic into this file. `scripts/spec_catalog/_lib.ps1` is deliberately not dot-sourced - every catalog read happens inside a child, so importing it would add an unused dependency.

**Why:**

Strategic ADR-1 and research 06 fix composition over inlining because an inlined copy of the selection logic would become a second, drifting answer to "which ticket is next"; strategic §3.2 forbids a third implementation of session-id and liveness resolution when two already share one, and §3.2 "Отказоустойчивость" requires the package to distinguish "a block did not assemble" from "the package is unusable", which only the per-block status and the four-way exit code can express.

**Verification:**

- `Glob` - `scripts/spec_catalog/session-bootstrap.ps1` exists.
- `Grep` - `.EXIT CODES` matches exactly once.
- `Grep` - every `Write-Error` occurrence carries `-ErrorAction Continue`.
- `Grep` - `spec-next-session.ps1`, `device-ready.ps1`, `spec-next-preflight.ps1` and `ticket-lease.ps1` each match at least once.
- `Grep` - `agent-lock.ps1` is dot-sourced and `_lib.ps1` returns zero hits.
- `pwsh -NoProfile -File scripts/spec_catalog/session-bootstrap.ps1 -SkipDevice -Format json` prints one line that parses as JSON, carries the keys `session`, `device`, `selection`, `lease`, has `device.status` equal to `skipped` and `lease.status` equal to `skipped`, and exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 7/7 PASS. Files: scripts/spec_catalog/session-bootstrap.ps1 (+297 LOC), scripts/utils/agent-lock.ps1 (+17 LOC). Run with `-SkipDevice -Format json` exited 0 and returned all four blocks with device and lease `skipped`. assert-exit-contract PASS. `Get-AgentSessionId` was added to agent-lock.ps1 under CLAUDE.md Rule 13 rather than inlining a fifth copy of the session-id idiom; the four existing inline copies are left untouched as a separate change.

---

### Step 01.2 - Regression suite

**Files:** `scripts/spec_catalog/session-bootstrap.tests/Run-Tests.ps1`

**Depends on:** Step 01.1

**Prompt for developer:**

> Add a suite in the shape of `scripts/spec_catalog/preview.tests/Run-Tests.ps1`: snapshot the catalog journals into a sandbox directory and point the CLI at the copy via `$env:FMS_SPEC_CATALOG_DIR`, assert, then delete the copy. Never allocate ids from `next-id.ps1` and never write to the live journals.
>
> Cases: (A) `-SkipDevice` marks the device block `skipped` and leaves session and selection `ok`; (B) without `-Claim` the lease block is `skipped` and no lease file is created; (C) a failing child sets exactly its own block to `failed`, carries the child's exit code and reason verbatim, leaves sibling blocks untouched, and the package exits 1 - inject the failure through a real component guard, never a fake child; (D) the payload parses as JSON and every block object carries `status`, `exitCode` and `reason`.
>
> Case C must fail for the right reason - assert the sibling blocks are still `ok`, not merely that the exit code is 1.

**Why:**

Strategic §7 lists "the package hides a partial failure, the driver proceeds on incomplete data" as a live risk, and a suite that only asserts the happy path would leave exactly that risk unmeasured.

**Verification:**

- `Glob` - `scripts/spec_catalog/session-bootstrap.tests/Run-Tests.ps1` exists.
- `pwsh -NoProfile -File scripts/spec_catalog/session-bootstrap.tests/Run-Tests.ps1` exits 0 and reports four cases passed.
- `Grep` - `FMS_SPEC_CATALOG_DIR` matches at least once in the suite.
- `Grep` - `-File .*next-id` returns zero hits in the suite. The bare name may appear in the header comment that records why no id is allocated; what must be absent is an invocation.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 4/4 PASS. Files: scripts/spec_catalog/session-bootstrap.tests/Run-Tests.ps1 (+205 LOC). Suite exits 0, four cases pass. The `next-id.ps1` predicate was repaired mid-step: as written it matched the header comment that explains why no id is allocated, so it was narrowed to an invocation shape rather than the bare name - the check got more precise, not weaker. Case C's first failure injection (`-Resume` with no state file) was rejected as unreliable - the session component adopts a legacy or sibling state file and exits 0 - and replaced by _lib.ps1's own refusal of a non-existent catalog directory, which fails the selection child while leaving the session child, which does not import that library, demonstrably `ok`.

---

### Step 01.3 - Route the two pickers through the facade

**Files:** `.claude/commands/spec-next.md`, `.claude/commands/spec-do.md`

**Depends on:** Step 01.2

**Prompt for developer:**

> In `.claude/commands/spec-next.md`, replace the Stage 0 and Stage 1 command listings with a single `session-bootstrap.ps1` invocation, and rewrite Stage 3.5 to state that the claim is either already performed by the package (when it was called with `-Claim`) or is the separate `ticket-lease.ps1` call it is today. Keep every existing branch: the `--resume` fork now maps to `-Resume`, the drift gate still reads `selection.selected.drift.verdict` from the payload, and an exit code 3 still loops back with an extended `-Exclude`.
>
> Apply the same replacement to `.claude/commands/spec-do.md` wherever it restates a Stage 0 or Stage 1 command rather than inheriting it. Do not change either driver's behaviour, only the number of calls it orders.

**Why:**

Strategic §11 criterion 2 requires the drivers to call the entry point rather than the entry point merely to exist, and strategic §4 records that the repository already has one start-layer script nobody ever called - a script without a caller is the failure this ticket exists to undo.

**Verification:**

- `Grep` - `session-bootstrap.ps1` matches at least once in `.claude/commands/spec-next.md`.
- `Grep` - `device-ready.ps1` returns zero hits in `.claude/commands/spec-next.md`; `spec-next-session.ps1` survives only at Stage 5, 5b and 6, whose verbs are not part of bootstrap.
- `Grep` - `spec-next-preflight.ps1` appears in Stage 1 only as the later-iteration path, with the first iteration explicitly sourced from Stage 0's payload.
- `Grep` - `--resume` still matches in `.claude/commands/spec-next.md`.
- `Grep` - no Stage 0 or Stage 1 `pwsh` command listing remains in `.claude/commands/spec-do.md` that names a component script directly.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 5/5 PASS. Files: .claude/commands/spec-next.md (Stage 0 rewritten, Stage 1 split into first-iteration/later-iteration paths). `device-ready.ps1` now has zero references in the driver; `spec-next-session.ps1` remains only in Stages 5/5b/6. `.claude/commands/spec-do.md` needed no edit - it inherits Stages 0-6 verbatim and restates no component call, so its Files Touched row was corrected to Unchanged rather than edited to look busy. The second verification predicate was repaired: it demanded zero preflight references anywhere in Stage 1, but preflight is legitimately still the later-iteration path - the package deliberately does not own the selection loop (research 05). The driver never passes `-Claim`, because the drift gate sits between selection and claim.

---

### Step 01.4 - Update the ownership map

**Files:** `.claude/reference/spec-next.md`

**Depends on:** Step 01.3

**Prompt for developer:**

> Rewrite the "Spec Catalog hooks" block so the facade appears as the single read path for session start, listing which of its blocks read and which write, and keep the entries for the calls that still happen outside bootstrap - `select.ps1` in Stage 5, `search.ps1` in Stage 5.5, `release-plan.ps1`, `skip-cache.ps1` and `update.ps1` on status mismatch, and the Stage 5 lease release.
>
> Keep the `preflight:` handoff line contract unchanged - its seven fields are still sourced from the selection payload, which the facade now carries instead of the standalone call.

**Why:**

That block is the only place in the repository that states which script in the selection path reads and which writes, so leaving it describing the superseded chain would make the authoritative map wrong the moment step 01.4 lands.

**Verification:**

- `Grep` - `session-bootstrap.ps1` matches in `.claude/reference/spec-next.md`.
- `Grep` - the handoff contract is untouched: `preflight: status=` still matches in `.claude/commands/spec-next.md`, which owns the literal, and `Stage 4 handoff` still matches in `.claude/reference/spec-next.md`, which owns the prose.
- `Grep` - `select.ps1`, `search.ps1` and `release-plan.ps1` still match in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. Files: .claude/reference/spec-next.md (hooks block rewritten). The map now opens with a Session-start entry naming all four blocks and which of them write, and records that this skill never enables the claim block. Preflight moved to the Reads line as the later-iteration path. Predicate 2 was corrected: it looked for the handoff literal in the reference, but the literal lives in the driver and only its prose lives in the reference - both were checked and both are unchanged.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/session-bootstrap.tests/Run-Tests.ps1` exits 0.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `post-change.ps1` run per step, all exit 0 (one standing advisory: the generated script cheatsheet is stale, which is Phase 04's work).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Session start is one call. Every component keeps its standalone parameter surface and exit codes, so any later phase may still call one directly. Phase 02 is independent of this phase and shares no file with it.

---

## Rollback Plan

Revert the phase commit. The facade adds a script and rewrites driver prose; no component script was modified, so reverting restores the previous chain exactly.
