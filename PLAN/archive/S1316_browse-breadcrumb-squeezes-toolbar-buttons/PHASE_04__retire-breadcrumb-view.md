# Phase 04 - Retire BreadcrumbView

**Strategic spec:** [`../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md`](../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Delete the orphaned `BreadcrumbView` custom view and the detekt baseline entries that referenced it, so no dead widget or stale suppression survives the swap.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] `Grep` for `BreadcrumbView` outside `dev/CATALOG/` returns only `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/BreadcrumbView.kt` and `config/detekt/baseline-app_v2.xml`.
- [x] `scripts/utils/enter-code-lock.ps1 -Reason "S1316 phase 04"` acquired; `scripts/utils/lock-status.ps1 -Name Build` reports no live build.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/BreadcrumbView.kt` | Deleted | -143 |
| `config/detekt/baseline-app_v2.xml` | Modified | -6 |

---

## Steps

### Step 04.1 - Delete `BreadcrumbView.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/BreadcrumbView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the file. Its only consumers were the three `activity_browse` layouts and `BrowseManagerInitializer`, all re-pointed in Phase 02; it has no other reference in the tree, no test, and no doc mention. Do not leave a deprecated stub - Rule 20 dead-weight hygiene.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/BreadcrumbView.kt` returns no match.
- `Grep` - `BreadcrumbView` returns zero hits across `app_v2/` and `wear/`.

**Status:** `[x]` done

---

### Step 04.2 - Prune the stale detekt baseline entries

**Files:** `config/detekt/baseline-app_v2.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Remove the six `<ID>` lines whose signature names `BreadcrumbView.kt` - four `MagicNumber:BreadcrumbView.kt$BreadcrumbView$*` (around line 3845), one `NoTrailingSpaces:BreadcrumbView.kt$BreadcrumbView$ ` (around line 9195) and one `Wrapping:BreadcrumbView.kt$BreadcrumbView$(` (around line 12284). Delete only those lines; do not reformat, re-sort or regenerate the baseline, and do not touch any other signature - a baseline rewrite shifts unrelated signatures and resurfaces findings that belong to other tickets.

**Verification:**

- `Grep` - `BreadcrumbView` returns zero hits in `config/detekt/baseline-app_v2.xml`.
- `Grep -c` - total `<ID>` line count in `config/detekt/baseline-app_v2.xml` decreased by exactly 6 versus the pre-step count recorded in the step log.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

`ui/common/BreadcrumbView.kt` no longer exists, so `dev/CATALOG/app_v2.jsonl` holds a stale record until Phase 05 regenerates it.

---

## Rollback Plan

Revert phase commit - restores the file and the six baseline lines; nothing else in the build depends on them.
