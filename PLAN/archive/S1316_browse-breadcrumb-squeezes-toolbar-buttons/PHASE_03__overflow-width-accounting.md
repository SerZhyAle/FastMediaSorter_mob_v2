# Phase 03 - Overflow Width Accounting

**Strategic spec:** [`../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md`](../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Teach `BrowseCommandOverflowManager` that `btnPath` occupies bar width, so the S0374 allocator budgets around it instead of over-allocating and pushing commands off the visible bar.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] `scripts/utils/enter-code-lock.ps1 -Reason "S1316 phase 03"` acquired; `scripts/utils/lock-status.ps1 -Name Build` reports no live build.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCommandOverflowManager.kt` | Modified | ≤ 175 |

> No layout file is touched in this phase, so landscape parity does not apply here; the three orientation variants were covered in Phase 02.

---

## Steps

### Step 03.1 - Reserve `btnPath` width in `applyPartition`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCommandOverflowManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `applyPartition()` the `reserved` value is currently `binding.btnResourceOps?.let { measuredWidthOf(it) } ?: 0` (line 73), which is the only width subtracted from the bar before `allocateCommandBar` distributes the rest. Extend it to also reserve `btnPath` while that button is visible:
> compute `val pathWidth = if (binding.btnPath.isVisible) measuredWidthOf(binding.btnPath) else 0` and add it to `reserved`.
> Guard on `isVisible` deliberately: `measuredWidthOf` force-measures a non-visible view and that probe poisons `measuredHeight` (see the S1258 note already in that method), so a `GONE` path button must contribute `0` without being probed.
> Extend the class KDoc: `btnPath` is not an overflow candidate - it is a reserved anchor like `btnResourceOps`, because it is the only way back up the folder tree and must never be pushed into the "⋮" menu. Do not add it to `candidates()` and do not add an `action_overflow_path` item to `menu_resource_ops.xml`.
> Keep every touched line ≤ 120 chars and introduce no bare numeric literal.

**Verification:**

- `Grep` - `binding.btnPath.isVisible` matches exactly once in `BrowseCommandOverflowManager.kt`.
- `Grep` - `R.id.btnPath` returns zero hits in `BrowseCommandOverflowManager.kt` (it must not become a candidate).
- `Grep` - `action_overflow_path` returns zero hits across `app_v2/src/main/res/menu/` and `app_v2/src/main/java/`.
- `Grep -c` - `Candidate(R.id.` count in `BrowseCommandOverflowManager.kt` is unchanged at 13 (match on `Candidate(R.id.`, not on `Candidate(` - the latter also hits the `data class Candidate(` declaration and reports 14).
- `Grep` - `btnPath` appears in the class KDoc block above `class BrowseCommandOverflowManager`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

The bar's width budget is now complete: every element between the padding edges is either an allocated candidate or a reserved anchor. No element in `layoutControls` has a width that depends on runtime content length.

---

## Rollback Plan

Revert phase commit - a single arithmetic change inside one private method; no persisted state, no user data.
