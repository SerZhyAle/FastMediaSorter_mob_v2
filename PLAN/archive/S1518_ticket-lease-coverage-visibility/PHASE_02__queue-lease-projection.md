# Phase 02 - Queue Lease Projection

**Strategic spec:** [`../S1518_ticket-lease-coverage-visibility.md`](../S1518_ticket-lease-coverage-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Expose active ticket ownership through an opt-in read-only release-queue projection.

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/release-queue.ps1` | Modified | ≤ 120 |
| `scripts/spec_catalog/release-queue.tests/Run-Tests.ps1` | New | ≤ 220 |

## Steps

### Step 02.1 - Add opt-in live lease projection

**Files:** `scripts/spec_catalog/release-queue.ps1`
**Depends on:** Phase 01

**Prompt for developer:**

> Add a `-WithLeases` switch valid only for `-List`. Read active leases through the existing lease CLI, retain the canonical output when the switch is absent, and append a deterministic read-only busy projection when it is present. The projection must identify the ticket, owner session, age, liveness, and reason without writing PLAN files.

**Why:**

The owner needs to see live ticket ownership alongside the release order, while the release-plan file must remain an owner-managed ordering surface with no claim/release churn.

**Verification:**

- `Grep` - `WithLeases` occurs in `scripts/spec_catalog/release-queue.ps1`.
- `Grep` - `ticket-lease.ps1` occurs in `scripts/spec_catalog/release-queue.ps1`.
- `Grep` - `PLAN/RELEASE_QUEUE.md` write is absent from the `-WithLeases` list branch.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Read-only projection and isolated fixture test passed; default queue output and PLAN files remain unchanged.

### Step 02.2 - Prove free and occupied projections

**Files:** `scripts/spec_catalog/release-queue.tests/Run-Tests.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add an isolated PowerShell test that invokes the list command with no leases and with a fixture lease. Assert default output remains canonical, the opt-in output names the fixture ticket and owner metadata, and no PLAN file content changes during either read.

**Why:**

The projection must make ownership visible without mutating the planning file or degrading the established default CLI output.

**Verification:**

- `Glob` - `scripts/spec_catalog/release-queue.tests/Run-Tests.ps1` exists.
- `Grep` - `WithLeases` occurs in the test file.
- `PowerShell` - `pwsh -NoProfile -File scripts/spec_catalog/release-queue.tests/Run-Tests.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Read-only projection and isolated fixture test passed; default queue output and PLAN files remain unchanged.

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Script test exits 0.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

The release queue remains immutable under normal and lease-annotated read paths.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
