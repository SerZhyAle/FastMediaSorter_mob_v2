# Phase 02 - Document Icon Routing

**Strategic spec:** [`../S1545_gate-wiring-orphan-and-duplicate-steps.md`](../S1545_gate-wiring-orphan-and-duplicate-steps.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-14

---

## Objective

Route the document-icon consistency gate only for its map, assets, generators and checked document surfaces, with regression coverage for boundary paths.

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `scripts/quality/assert-doc-icons-sync.ps1 -Gate` passes on the current tree.
- [ ] CODE.LOCK acquired immediately before source edits.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/doc-icon-gate-routing.ps1` | New | ≤ 180 |
| `scripts/quality/doc-icon-gate-routing.tests/Run-Tests.ps1` | New | ≤ 220 |
| `scripts/post-change.ps1` | Modified | ≤ 120 |
| `scripts/quality/gate-recovery-hints.psd1` | Modified | ≤ 40 |

## Steps

### Step 02.1 - Define the document-icon input classifier

**Files:** `scripts/quality/lib/doc-icon-gate-routing.ps1`, `scripts/quality/doc-icon-gate-routing.tests/Run-Tests.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Implement a pure changed-path classifier for the icon map, generated assets, icon generators, three landing pages and checked markdown surfaces. Add hermetic included-path and excluded-path cases; normalise separators before matching.

**Why:**

The strategic specification requires complete coverage of icon inputs without imposing this gate on unrelated documentation edits.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/doc-icon-gate-routing.tests/Run-Tests.ps1` exits 0.
- Included cases cover every input class declared by the icon gate.
- Excluded cases include unrelated documentation and Android source paths.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Added a normalized changed-path classifier with 8 included and 4 excluded regression cases; routing suite passed.

### Step 02.2 - Wire the conditional gate into closure

**Files:** `scripts/post-change.ps1`, `scripts/quality/lib/doc-icon-gate-routing.ps1`, `scripts/quality/gate-recovery-hints.psd1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Use the classifier to add one named icon-consistency step to the closure facade. Pass only its gate mode, preserve accumulated-failure behaviour, and register an exact recovery hint for the emitted label.

**Why:**

The existing icon gate is correct but currently depends on the operator remembering a manual command.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1` exits 0.
- A fixture-backed routing test proves a relevant path enables the gate and an unrelated documentation path disables it.
- `pwsh -NoProfile -File scripts/quality/assert-doc-icons-sync.ps1 -Gate` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Wired the named conditional icon gate and recovery hint; routing, hint-sync and icon-sync gates passed.

### Step 02.3 - Verify facade integration without mutation

**Files:** `scripts/post-change.tests/Run-Tests.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend the closure-dispatch regression suite to require the icon gate label, the dedicated classifier route and its recovery hint while keeping the test free of changelog, catalog and source mutations.

**Why:**

The strategic specification requires the new protection to remain both conditionally reachable and diagnosable after later facade edits.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.tests/Run-Tests.ps1` exits 0.
- The suite detects a missing icon route, missing hint or unconditional icon gate.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Extended dispatch tests to require changed-set icon routing and a conditional named gate; regression, hint and icon-sync tests passed.

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-doc-icons-sync.ps1 -Gate` exits 0.
- [x] `pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1` exits 0.
- [x] Dev log entry added for every file in Files Touched.
- [x] Phase-boundary audit run with no unresolved P0/P1 finding.

## Handoff Notes to Next Phase

Icon routing is explicit and testable by path class; no arbitrary documentation edit enables the gate.

## Rollback Plan

Revert the phase commit(s); no data migration or user-facing surface changes.
