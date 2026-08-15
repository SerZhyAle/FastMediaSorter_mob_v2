# Phase 01 - Streams Panel Chip Width

**Strategic spec:** [`../S1049_main-panels-uniform-button-width.md`](../S1049_main-panels-uniform-button-width.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-15
**Completed:** 2026-07-15

---

## Objective

Retarget the streams-panel channel chip's minimum width from its own dedicated `56dp` dimen to the shared
`main_panel_item_min_width` (`48dp`) module the programs panel already uses, and delete the now-orphaned dimen.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Confirm current values: `app_v2/src/main/res/values/dimens_main_panels.xml` has `main_panel_item_min_width = 48dp` and `main_stream_channel_min_width = 56dp`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_main_stream_channel.xml` | Modified | ≤ 80 (currently ~71) |
| `app_v2/src/main/res/values/dimens_main_panels.xml` | Modified | ≤ 20 (currently 19) |

> No landscape counterpart: `item_main_stream_channel.xml` has no `layout-land/` override - it is the same
> file inflated by `view_main_streams_panel.xml` in every orientation/width bucket, so this change applies
> everywhere the streams panel renders. This is intentional and matches how the pre-existing programs-panel
> item (`item_main_program.xml`) already works - no separate mirrored edit needed.

---

## Steps

### Step 01.1 - Retarget channel chip minWidth to the shared item module

**Files:** `app_v2/src/main/res/layout/item_main_stream_channel.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `item_main_stream_channel.xml`, change the `channelRoot` `LinearLayout`'s `android:minWidth` from
> `@dimen/main_stream_channel_min_width` to `@dimen/main_panel_item_min_width`. This is the only edit in
> this file - no other attribute changes.

**Verification:**

- `Grep` - `app_v2/src/main/res/layout/item_main_stream_channel.xml` contains `android:minWidth="@dimen/main_panel_item_min_width"` (1 match).
- `Grep` - `app_v2/src/main/res/layout/item_main_stream_channel.xml` contains `main_stream_channel_min_width` → 0 matches.

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 2/2 PASS. Files: `app_v2/src/main/res/layout/item_main_stream_channel.xml` (minWidth retargeted to `main_panel_item_min_width`).

---

### Step 01.2 - Delete the orphaned dimen

**Files:** `app_v2/src/main/res/values/dimens_main_panels.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Remove the `main_stream_channel_min_width` dimen line from `dimens_main_panels.xml` (its only consumer was
> retargeted in Step 01.1). Leave every other dimen in the file untouched.

**Verification:**

- `Grep` - `app_v2/src/main/res/values/dimens_main_panels.xml` for `main_stream_channel_min_width` → 0 matches.
- `Grep` (repo-wide, `app_v2/src/**/*.{xml,kt}`) - `main_stream_channel_min_width` → 0 matches anywhere (dimen fully orphan-free, not just removed from its declaration file).

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 2/2 PASS. Files: `app_v2/src/main/res/values/dimens_main_panels.xml` (`main_stream_channel_min_width` removed, 0 references repo-wide).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). BUILD SUCCESSFUL in 1m 15s (`build_debug_20260715_003045.log`).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The streams panel's per-item module now matches the programs panel's (`main_panel_item_min_width`, `48dp`)
for every item after the shared leading anchor. Phase 02 and Phase 03 do not depend on this - they touch
unrelated resources/files.

---

## Rollback Plan

Low-risk: revert phase commit(s) - a dimen rename/removal with a single consumer, no data migration or
business logic touched.
