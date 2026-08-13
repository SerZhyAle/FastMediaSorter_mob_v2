# Phase 02 - Command Bar Grid (Row 1)

**Strategic spec:** [`../S1049_main-panels-uniform-button-width.md`](../S1049_main-panels-uniform-button-width.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-15
**Completed:** 2026-07-15

---

## Objective

Give the top command-bar row (`layoutControlButtons`, portrait only) the same shared leading anchor and
per-button width module the programs panel already uses, so its buttons start and align on the same grid
as the other three panels.

---

## Prerequisites

- [ ] Phase 01 status is whatever it is - no dependency, but avoid touching `activity_main.xml` concurrently with Phase 03 in the same working session.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | ≤ 700 (currently ~660; backup required, see Step 02.1) |

> **Landscape / wide parity - deliberately not touched.** `app_v2/src/main/res/layout-land/activity_main.xml`
> and `app_v2/src/main/res/layout-w600dp/activity_main.xml` both already render row 1 in a distinct label
> mode (`android:text="@string/exit"` etc, `paddingStart="4dp"`/`paddingEnd="8dp"`, no `minWidth` grid) that
> predates this ticket and is not part of the reported issue (the owner's screenshot and complaint are the
> compact icon-only portrait row). Strategic §3.2 scopes this ticket to portrait; do not port this phase's
> `minWidth`/leading-`Space` changes into either file.

---

## Steps

### Step 02.1 - Backup before editing a >500-line file

**Files:** none (backup only)
**Depends on:** - start of phase

**Prompt for developer:**

> Per CLAUDE.md Rule 5 (`activity_main.xml` is ~660 lines, over the 500-line backup threshold): copy the
> current file to a timestamped backup under `temp/S1049/` before making any edit in this phase.

**Verification:**

- `Glob` - `temp/S1049/activity_main*.xml*` matches at least one file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 1/1 PASS. Backup: `temp/S1049/activity_main_20260715_phase02.xml.bak`.

---

### Step 02.2 - Add the shared leading anchor

**Files:** `app_v2/src/main/res/layout/activity_main.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the portrait `activity_main.xml`, insert a leading `Space` as the very first child of
> `layoutControlButtons`, before the `btnExit` button:
> ```xml
> <Space
>     android:layout_width="@dimen/main_top_panel_leading_anchor"
>     android:layout_height="match_parent" />
> ```
> This mirrors how `view_main_programs_panel.xml`'s leading three-dots button and `view_main_streams_panel.xml`'s
> leading entry button already reserve `main_top_panel_leading_anchor` (`56dp`) before their first real item,
> and how `tabResourceTypes` reserves it via `paddingStart` - row 1 has no equivalent "leading service control"
> of its own, so a plain `Space` is the simplest way to give it the same start X.

**Verification:**

- `Grep` - `app_v2/src/main/res/layout/activity_main.xml` for `main_top_panel_leading_anchor` → 2 matches (the new row-1 `Space` + the pre-existing `tabResourceTypes` `paddingStart`).
- `Grep` - the new `Space` line appears before the `btnExit` declaration (line number of the new `Space` < line number of `android:id="@+id/btnExit"`).

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 2/2 PASS. `main_top_panel_leading_anchor` count = 2 (new `Space` at line 27, pre-existing `tabResourceTypes` padding at line 419). `Space` (line 27) precedes `btnExit` (line 32).

---

### Step 02.3 - Give every row-1 button the shared item-width module

**Files:** `app_v2/src/main/res/layout/activity_main.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> For each of these 8 buttons in `layoutControlButtons` - `btnExit`, `btnAddResource`, `btnFilter`,
> `btnRefresh`, `btnSettings`, `btnToggleView`, `btnFavorites`, `btnStartPlayer` - change
> `android:minWidth="0dp"` to `android:minWidth="@dimen/main_panel_item_min_width"`.
>
> Also normalize horizontal padding to the shared spacing dimen: for the 7 buttons that currently have
> `android:paddingHorizontal="2dp"`, change it to `android:paddingHorizontal="@dimen/main_panel_item_spacing"`.
> `btnSettings` is the one exception - it currently has `android:padding="0dp"` (all four sides, not
> `paddingHorizontal`); replace that single `padding="0dp"` line with
> `android:paddingHorizontal="@dimen/main_panel_item_spacing"` so it matches its siblings.
>
> Do not touch `btnMainDropdownMenu` (inside `layoutMainDropdownMenu`, `visibility="gone"` by default) - it
> already carries its own distinct `main_programs_button_width_portrait` min-width for a separate fallback
> path, is not part of the reported issue, and is out of scope here. Do not remove or resize any `<Space>`
> element already between buttons (the large gap after Exit and the small 2dp gaps are unchanged - kept as
> a deliberate simplification, see strategic spec discussion; only `minWidth`/padding on the buttons change).

**Verification:**

- `Grep` -c `app_v2/src/main/res/layout/activity_main.xml` for `android:minWidth="@dimen/main_panel_item_min_width"` → 8 matches.
- `Grep` - `app_v2/src/main/res/layout/activity_main.xml`, within the `layoutControlButtons` block, for `android:minWidth="0dp"` → 0 remaining matches among the 8 named buttons (the dropdown-menu button's own `minWidth` is untouched and uses a different dimen, not `0dp`, so this check has no false positive from it).
- `Grep` -c `app_v2/src/main/res/layout/activity_main.xml` for `android:paddingHorizontal="@dimen/main_panel_item_spacing"` → at least 8 matches (7 retargeted + `btnSettings`).
- `Grep` - `app_v2/src/main/res/layout/activity_main.xml` for `android:padding="0dp"` on `btnSettings` → 0 matches (line replaced).

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 4/4 PASS. `minWidth="@dimen/main_panel_item_min_width"` count = 8 (exact). `minWidth="0dp"` count = 0 (file-wide). `paddingHorizontal="@dimen/main_panel_item_spacing"` count = 9 (8 from this step + 1 pre-existing on `mainCollapsedPanelsRow`, ≥ 8 as required). `android:padding="0dp"` count = 0. `btnMainDropdownMenu` left untouched (still `main_programs_button_width_portrait`).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). BUILD SUCCESSFUL in 15s (`build_debug_20260715_003615.log`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `activity_main.xml` via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` confirms `layout-land/activity_main.xml` and `layout-w600dp/activity_main.xml` are byte-for-byte unchanged (still 9 occurrences of `android:minWidth="0dp"` each) - proves no accidental cross-file edit.

---

## Handoff Notes to Next Phase

Row 1 now starts at the same `56dp` leading anchor as rows 2-4 and its buttons use the same `48dp` item
module as the programs panel. Phase 03 (resource tabs) edits a different section of the same file
(`tabResourceTypes`, further down) - re-open the file fresh rather than relying on a stale in-memory copy.

---

## Rollback Plan

Low-risk: revert phase commit(s), or restore the Step 02.1 backup from `temp/S1049/`. No data migration or
business logic touched - pure layout geometry.
