# Phase 02 — Slideshow Position

**Strategic spec:** [`../S0103_player-top-bar-polish.md`](../S0103_player-top-bar-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Move `btnSlideshowCmd` from inside the adaptive center group (portrait XML) to a fixed anchor position immediately before the navigation block (`btnPreviousCmd`). Remove Slideshow from the layout planner's adaptive set; control its visibility directly in `CommandPanelController`, consistent with the landscape branch.

Landscape XML is already correct (slideshow is before the nav block at the right position) — no change needed there.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | — |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | — (verify only, comment update) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1050 |

> `CommandPanelController.kt` backup already exists from Phase 01. If editing after a new session, create a fresh backup.

---

## Steps

### Step 02.1 — Move `btnSlideshowCmd` to fixed anchor in portrait XML

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `activity_player_unified.xml` (portrait), `btnSlideshowCmd` is currently inside the center group `LinearLayout` (the `layout_weight="1"` container). Cut the entire `<ImageButton android:id="@+id/btnSlideshowCmd" .../>` element and paste it as a direct child of `topCommandPanel` LinearLayout, immediately **before** `<ImageButton android:id="@+id/btnPreviousCmd"` (which is after the closing `</LinearLayout>` of the center group).
>
> Result structure inside `topCommandPanel`:
> ```
> [btnBack]
> [LinearLayout weight=1 — center group (no btnSlideshowCmd)]
> [btnSlideshowCmd]   ← NEW FIXED POSITION
> [btnPreviousCmd]
> [btnRandomCmd]
> [btnNextCmd]
> ```
>
> The landscape counterpart (`layout-land/activity_player_unified.xml`) already has `btnSlideshowCmd` before the nav block — no XML change needed there, but add a comment on the existing slideshow button line: `<!-- S0103: fixed anchor — already before nav block -->`.

**Verification (portrait):**

- Open `activity_player_unified.xml`. Confirm `btnSlideshowCmd` appears AFTER the closing `</LinearLayout>` of the center group and BEFORE `btnPreviousCmd`.
- `Grep` on `layout/activity_player_unified.xml` — `btnSlideshowCmd` appears exactly once.
- `Grep` on `layout-land/activity_player_unified.xml` — `S0103: fixed anchor` comment is present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: layout/activity_player_unified.xml, layout-land/activity_player_unified.xml.

---

### Step 02.2 — Remove SLIDESHOW from layout planner's adaptive set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 02.1 *(XML change must be in place before removing from planner)*

**Prompt for developer:**

> In `CommandPanelLayoutPlanner.buildActiveCommands()`, remove the line:
> ```kotlin
> if (isImage || isVideo) add(PlayerCommand.SLIDESHOW)
> ```
> Slideshow is now a fixed anchor controlled directly by `CommandPanelController`, not by the planner.

**Verification:**

- `Grep` — `PlayerCommand.SLIDESHOW` matches zero times in the `buildActiveCommands` function body of `CommandPanelLayoutPlanner.kt` (the enum declaration still references it, that is acceptable).
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelLayoutPlanner.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: CommandPanelLayoutPlanner.kt.

---

### Step 02.3 — Wire slideshow as fixed anchor in `CommandPanelController`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Apply four changes to `CommandPanelController`:
>
> **A. Remove from `getOverflowableButtons()`**: delete the `binding.btnSlideshowCmd,` entry from the list.
>
> **B. Remove from `barViewForCommand()`**: delete the `CommandPanelLayoutPlanner.PlayerCommand.SLIDESHOW -> binding.btnSlideshowCmd` case.
>
> **C. Portrait branch — add direct visibility control**: in the `showInPortrait` branch, immediately before the `val activeCommands = planner.buildActiveCommands(...)` call, add:
> ```kotlin
> val showSlideshow = isImage || isVideo
> binding.btnSlideshowCmd.isVisible = showSlideshow
> ```
>
> **D. Update `resolveAvailableCenterWidthPx()`**: change the return expression from:
> ```kotlin
> return (panelWidth - buttonPx * 3).coerceAtLeast(0) // Back + Previous + Next
> ```
> to:
> ```kotlin
> val slideshowFixed = if (binding.btnSlideshowCmd.isVisible) 1 else 0
> return (panelWidth - buttonPx * (3 + slideshowFixed)).coerceAtLeast(0) // Back + Slideshow + Prev + Next
> ```
>
> Also add a debug verification tag in the portrait branch after setting slideshow visibility:
> ```kotlin
> Timber.d("S0103: slideshow-anchor isVisible=$showSlideshow type=${currentFile.type}")
> ```

**Verification:**

- `Grep` — `btnSlideshowCmd,` in `getOverflowableButtons()` body returns zero hits in `CommandPanelController.kt`.
- `Grep` — `SLIDESHOW -> binding.btnSlideshowCmd` returns zero hits in `CommandPanelController.kt`.
- `Grep` — `val showSlideshow = isImage || isVideo` matches exactly once in `CommandPanelController.kt`.
- `Grep` — `slideshowFixed` matches at least once in `CommandPanelController.kt`.
- `Grep` — `S0103: slideshow-anchor` matches exactly once in `CommandPanelController.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 6/6 PASS. Files: CommandPanelController.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added for all four files via `.\scripts\add_to_dev_log.ps1`.
- [x] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run and `dev/CATALOG/app_v2.jsonl` updated. MANUAL-REQUIRED (deferred to Phase 04).

---

## Handoff Notes to Next Phase

- `btnSlideshowCmd` is now a fixed anchor in portrait and landscape. The planner no longer manages it.
- `resolveAvailableCenterWidthPx()` accounts for the slideshow button's width when visible.
- Phase 03 adds `btnSleepTimerCmd` as a new bar-capable view.

---

## Rollback Plan

Revert the two `.kt` files and both XMLs. No data migration. The `SLIDESHOW` enum entry in `CommandPanelLayoutPlanner` remains but is unused by `buildActiveCommands` — remove the enum entry only if desired (it is still referenced by `barViewForCommand` in the else clause, which is now dead code; leave cleanup for a future housekeeping commit).
