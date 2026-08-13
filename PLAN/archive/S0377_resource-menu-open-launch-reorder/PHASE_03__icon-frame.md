# Phase 03 - Media Storage Icon Frame

**Strategic spec:** [`../S0377_resource-menu-open-launch-reorder.md`](../S0377_resource-menu-open-launch-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Draw a persistent visible frame around the clickable icon of media-storage resources in both grid and list view holders, signalling that the icon is a separate tappable target.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/bg_icon_media_storage_frame.xml` | New | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 830 |

> No `res/layout/*.xml` is edited - the frame is applied as the icon view's `background` in code, and `ivResourceTypeIcon` has no layout-defined background to overwrite. No `layout-land` counterpart applies.
> `ResourceAdapter.kt` >500 lines → reuse / refresh the `temp/` backup from Phase 02 before editing.

---

## Steps

### Step 03.1 - Add the icon frame drawable

**Files:** `app_v2/src/main/res/drawable/bg_icon_media_storage_frame.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an oval `<shape>` drawable with a transparent solid and a visible stroke (e.g. width `@dimen/destination_border_width` or a new small dimen, colour `?attr/colorPrimary`), matching the oval shape of the existing click ripple mask (`ripple_icon_quick_slideshow.xml`). The frame must read as a non-colour-only affordance (a stroke outline), not merely a tint.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/bg_icon_media_storage_frame.xml` exists.
- `Grep` - `android:shape="oval"` present in the file.
- `Grep` - `<stroke` present in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS: file exists, `android:shape="oval"` present, `<stroke` present. Oval matches ripple_icon_quick_slideshow mask; stroke `@dimen/destination_border_width` / `?attr/colorPrimary` (non-colour-only outline). Dev log recorded.

---

### Step 03.2 - Apply / clear the frame in both view holders

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In BOTH view holders, inside the existing `isQuickSlideshowEligible(resource)` true branch (where the ripple foreground and icon click listener are already set), additionally set `ivResourceTypeIcon.background = ContextCompat.getDrawable(root.context, R.drawable.bg_icon_media_storage_frame)`. In the matching else branch (non-eligible), set `ivResourceTypeIcon.background = null` so recycled rows do not keep a stale frame. Do not change the existing foreground/clickable/contentDescription logic.

**Verification:**

- `Grep -c "R.drawable.bg_icon_media_storage_frame"` returns 2 (both view holders).
- `Grep -c "ivResourceTypeIcon.background = null"` returns 2.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS: `R.drawable.bg_icon_media_storage_frame` = 2, `ivResourceTypeIcon.background = null` = 2 (both holders), 0 `Log.d(`. Frame set in the quickEligible true branch, cleared in else (prevents stale frame on recycled rows). File 805 LOC. Dev log recorded. Catalog regen deferred to Phase 04.2.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - consolidated `a.ps1 dq` BUILD SUCCESSFUL 1m35s (temp/build_debug_20260607_040914.log), covers Phases 01-03.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to single regen in Phase 04.2.

---

## Handoff Notes to Next Phase

Feature-complete. Phase 04 is catalog/docs cleanup and device-test handoff.

---

## Rollback Plan

Revert phase commit(s) - new drawable and two code branches only; no data migration or persisted state changed.
