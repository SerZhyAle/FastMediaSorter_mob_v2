# Phase 01 - Fold a section's own row members

**Strategic spec:** [`../S1642_launcher-section-header-grid-span.md`](../S1642_launcher-section-header-grid-span.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Teach `LauncherSectionMembership` that a collapsed section hides the cells sharing its header's row while
the header itself stays drawn. No span or storage change yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt` | Modified | ≤ 180 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionCollapseTest.kt` | Modified | ≤ 140 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 01.1 - Give `renderRowFor` the caller's header flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `renderRowFor(row, headerRows, collapsedHeaderRows)` to `renderRowFor(row, isHeader, headerRows, collapsedHeaderRows)`. When `row` is itself a collapsed header row: return the lifted row if `isHeader` is true, and `null` otherwise. Rows strictly between a collapsed header and that section's end keep folding exactly as they do now, and the lift arithmetic is unchanged. Rewrite the KDoc paragraph that currently states this function reasons about rows and never columns - a section now ends at the next header row but starts inside the header's own row, so the header's row is partly folded.

**Why:**

Strategic §2.2 lets shortcuts occupy the free positions of the header's own row, and §2.4 requires that
collapsing hides that section's content; without this branch those shortcuts stay on screen while the rest
of their section folds, which reads as content that belongs to no section.

**Verification:**

- `Grep` - `fun renderRowFor(row: Int, isHeader: Boolean` matches exactly once in that file.
- `Grep` - `collapsedHeaderRows` still present in the same signature.
- `Grep` - `HEADER_STORED_SPAN_W = 12` still matches - this phase changes no span.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - renderRowFor gained isHeader; a collapsed header row now hides every cell but the header. Grep: signature 1 hit, HEADER_STORED_SPAN_W = 12 unchanged.

---

### Step 01.2 - Pass the flag from the render plan

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `renderPlan`, pass `item.cell.kind == LauncherCellKind.SECTION` as the new `isHeader` argument of `renderRowFor`. Change nothing else in that function.

**Why:**

`renderPlan` is the only caller that turns stored cells into drawn ones, so the flag added in step 01.1
reaches the renderer through it or through nothing at all.

**Verification:**

- `Grep` - `LauncherSectionMembership.renderRowFor(` in that file passes four arguments.
- `Grep` - `LauncherCellKind.SECTION` appears inside `renderPlan`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - renderPlan passes isHeader; collapse test helper gained the flag plus three cases (same-row cell hidden, same-row cell drawn when expanded, header drawn at its lifted row). check-standard-fast -Mode Unit -Tests '*LauncherSectionCollapseTest,*LauncherSectionMembershipTest' exit 0.

---

### Step 01.3 - Cover the header-row fold in unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionCollapseTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Update the `renderRow` test helper for the new signature and add two cases: a non-header cell stored on a collapsed header's own row renders as `null`, and the header on that same row still renders at its lifted row. Keep every existing case passing unchanged - a fold must still lift only the rows below the header.

**Why:**

Strategic §11.4 requires that a collapse-expand cycle leaves stored coordinates untouched and the next
section visible, and the row arithmetic is the only place that invariant is expressible as a static test.

**Verification:**

- `Grep` - `isHeader` present in the test file.
- Run `.\a.ps1 fu` filtered to `LauncherSectionCollapseTest` - all cases pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - renderPlan passes isHeader; collapse test helper gained the flag plus three cases (same-row cell hidden, same-row cell drawn when expanded, header drawn at its lifted row). check-standard-fast -Mode Unit -Tests '*LauncherSectionCollapseTest,*LauncherSectionMembershipTest' exit 0.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

A collapsed section now hides every cell on its header's row except the header. The branch is unreachable
until phase 05 narrows the header, so the desktop's behaviour is unchanged by this phase.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
