# Phase 01 - Probe Audit

**Strategic spec:** [`../S1582_bugfix-acceptance-quotes-log-literals-absent-from-source.md`](../S1582_bugfix-acceptance-quotes-log-literals-absent-from-source.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Completed
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-11
**Completed:** 2026-08-11

## Objective

Provide a deterministic parser and audit for explicit acceptance probe literals across application and Wear source sets.

## Prerequisites

- [x] Strategic §6 research item is Resolved.
- [ ] Working tree is clean or on a feature branch.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/ticket-acceptance-probes.ps1` | New | ≤ 350 |
| `scripts/quality/assert-ticket-acceptance-probes.ps1` | New | ≤ 300 |
| `scripts/quality/assert-ticket-acceptance-probes.tests/Run-Tests.ps1` | New | ≤ 350 |

## Steps

### Step 01.1 - Add reusable probe-template matcher

**Files:** `scripts/quality/lib/ticket-acceptance-probes.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `Get-TimberProbeTemplates`, `Get-TicketAcceptanceProbeContracts`, and `Test-TicketAcceptanceProbeContracts` in the shared matcher. Reconstruct complete Timber calls across `app_v2/src` and `wear/src`, exclude test/build trees, ignore comments, and match a ticket-owned static prefix while allowing format and interpolation tails. Each function accepts explicit catalog/source-root inputs so fixture tests do not read the live catalog.

**Why:**

> The strategic contract requires accurate matching of multiline and parameterized temporary messages; per-line literal search creates false missing-probe findings.

**Verification:**

- `Glob` - `scripts/quality/lib/ticket-acceptance-probes.ps1` exists.
- `Grep` - `Get-TimberProbeTemplates`, `Get-TicketAcceptanceProbeContracts`, and `Test-TicketAcceptanceProbeContracts` are present in that file.
- `Grep` - `app_v2/src` and `wear/src` are present in that file.

**Status:** `[x]` done

### Step 01.2 - Add fail-closed catalog audit

**Files:** `scripts/quality/assert-ticket-acceptance-probes.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a read-only audit that calls `Test-TicketAcceptanceProbeContracts`, extracts only explicit `Probe literal:` and `Probe template:` entries from active BlockNeedUserTest notes, validates that `Probe none:` names alternative evidence, reports ticket/literal/source candidates, returns zero in audit mode, returns one for mismatches with `-Gate`, and returns two when required catalog or source roots cannot be read.

**Why:**

> The acceptance contract must detect a missing signal before a device operator spends time searching logs, while ordinary prose remains outside the machine-enforced scope.

**Verification:**

- `Glob` - `scripts/quality/assert-ticket-acceptance-probes.ps1` exists.
- `Grep` - `-Gate` and `Probe literal:` are present in that file.
- `Grep` - `exit 2` is present in that file.

**Status:** `[x]` done

### Step 01.3 - Cover parser and gate regression cases

**Files:** `scripts/quality/assert-ticket-acceptance-probes.tests/Run-Tests.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add isolated fixture tests for multiline calls, comments, format placeholders, interpolation, ticket mismatch, absent literals, valid `Probe none:`, invalid alternative evidence, and unmarked prose. Keep fixtures temporary and leave the live catalog untouched.

**Why:**

> The strategic risk is false failure from parsing prose or dynamic message tails, so both positive and negative parser boundaries need executable proof.

**Verification:**

- `Glob` - `scripts/quality/assert-ticket-acceptance-probes.tests/Run-Tests.ps1` exists.
- `Grep` - `multiline`, `interpolation`, and `unmarked` are present in that file.
- `pwsh -NoProfile -File scripts/quality/assert-ticket-acceptance-probes.tests/Run-Tests.ps1` exits 0.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-ticket-acceptance-probes.ps1 -Gate` has a read result.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Step Log

- 2026-08-11 - Verification 3/3 PASS. Files: `scripts/quality/lib/ticket-acceptance-probes.ps1` (+167 LOC). Dev log recorded. `script-cheatsheet-sync` advisory is pre-existing/project-wide and is assigned to Phase 03.
- 2026-08-11 - Verification 3/3 PASS. Files: `scripts/quality/assert-ticket-acceptance-probes.ps1`, `scripts/quality/assert-ticket-acceptance-probes.tests/Run-Tests.ps1`. Fixture suite and live gate passed. Phase-boundary audit: P0/P1 none; scripts are read-only and contain no lifecycle, storage, or UI changes.

## Handoff Notes to Next Phase

The matcher exposes one source of truth for catalog-wide audit and write-time validation.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
