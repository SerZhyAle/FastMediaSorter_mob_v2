# Phase 03 — Sleep Timer Bar Button

**Strategic spec:** [`../S0103_player-top-bar-polish.md`](../S0103_player-top-bar-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Make `SLEEP_TIMER` bar-capable so the existing N=1 single-promote rule in `planLayout()` kicks in when Sleep Timer is the sole overflow item. Add `btnSleepTimerCmd` to both portrait and landscape layouts, wire its click listener, and flip `barCapable = true` in the planner enum.

The N=1 promote logic in `planLayout()` is already implemented and correct — no changes to `planLayout()` itself are needed.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | — |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1050 |

---

## Steps

### Step 03.1 — Add `btnSleepTimerCmd` to portrait and landscape XMLs

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`, `app_v2/src/main/res/layout-land/activity_player_unified.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `btnSleepTimerCmd` to the center group (the adaptive `layout_weight="1"` LinearLayout) in **both** XMLs. Place it alongside other Group 2 action buttons, near `btnLyricsCmd` (audio-specific commands area). Use the same attributes pattern as neighboring buttons. Start with `android:visibility="gone"` since visibility is fully managed by the planner.
>
> Portrait XML — insert inside the center group LinearLayout (e.g., before or after `btnLyricsCmd`):
> ```xml
> <ImageButton android:id="@+id/btnSleepTimerCmd"
>     android:layout_width="@dimen/player_cmd_button_size"
>     android:layout_height="@dimen/player_cmd_button_size"
>     android:background="?attr/selectableItemBackgroundBorderless"
>     android:contentDescription="@string/menu_sleep_timer"
>     android:src="@drawable/ic_sleep_timer"
>     android:visibility="gone"
>     app:tint="@color/selector_player_button_tint"
>     android:scaleType="centerInside"
>     android:padding="@dimen/player_button_padding" />
> ```
>
> Apply the identical element in the landscape XML at the equivalent location in the landscape center group.

**Verification:**

- `Grep` on `layout/activity_player_unified.xml` — `btnSleepTimerCmd` matches exactly once.
- `Grep` on `layout-land/activity_player_unified.xml` — `btnSleepTimerCmd` matches exactly once.
- `Grep` on `layout/activity_player_unified.xml` — `ic_sleep_timer` is referenced (src attribute).
- `Grep` on `layout-land/activity_player_unified.xml` — `ic_sleep_timer` is referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: layout/activity_player_unified.xml, layout-land/activity_player_unified.xml.

---

### Step 03.2 — Flip `SLEEP_TIMER` to `barCapable = true` and wire into controller

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> **In `CommandPanelLayoutPlanner.kt`:** Change the `SLEEP_TIMER` enum entry from `barCapable = false` to `barCapable = true`:
> ```kotlin
> SLEEP_TIMER(500, R.id.menu_sleep_timer, true,    // was false
>     R.string.menu_sleep_timer, R.drawable.ic_sleep_timer),
> ```
>
> **In `CommandPanelController.kt`:** Apply two changes:
>
> 1. In `getOverflowableButtons()`, add `safeViews.btnSleepTimerCmd` (or `binding.btnSleepTimerCmd` — use whichever reference pattern is consistent with nearby buttons).
>
> 2. In `barViewForCommand()`, add the mapping case:
>    ```kotlin
>    CommandPanelLayoutPlanner.PlayerCommand.SLEEP_TIMER -> binding.btnSleepTimerCmd
>    ```
>    Place it near the `LYRICS` and `SEARCH_YOUTUBE_MUSIC` cases (audio-specific group).

**Verification:**

- `Grep` on `CommandPanelLayoutPlanner.kt` — `SLEEP_TIMER(500` is followed by `true,` (barCapable).
- `Grep` on `CommandPanelController.kt` — `SLEEP_TIMER -> binding.btnSleepTimerCmd` matches exactly once.
- `Grep` on `CommandPanelController.kt` — `btnSleepTimerCmd` in `getOverflowableButtons` body matches exactly once.
- `Grep` — `Log\.d\(` returns zero hits in both modified `.kt` files.

**Status:** `[ ]` not done

---

### Step 03.3 — Wire click listener for `btnSleepTimerCmd`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `CommandPanelController.setupCommandPanelControls()`, add a click listener for `btnSleepTimerCmd` alongside the other button wiring:
> ```kotlin
> binding.btnSleepTimerCmd.setOnClickListener {
>     Timber.d("S0103: btnSleepTimerCmd clicked — sleep timer promoted from overflow")
>     callback.onSleepTimerClicked()
> }
> ```

**Verification:**

- `Grep` on `CommandPanelController.kt` — `btnSleepTimerCmd.setOnClickListener` matches exactly once.
- `Grep` on `CommandPanelController.kt` — `S0103: btnSleepTimerCmd` matches exactly once.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: CommandPanelController.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entries added for all four files via `.\scripts\add_to_dev_log.ps1`.
- [x] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run and `dev/CATALOG/app_v2.jsonl` updated. MANUAL-REQUIRED (deferred to Phase 04).

---

## Handoff Notes to Next Phase

- `SLEEP_TIMER` is now bar-capable; the existing N=1 single-promote logic in `planLayout()` will promote it when it is the sole overflow item.
- Other overflow-only commands (PDF_SCROLL_MODE, READ_ALOUD, etc.) remain overflow-only. If N=1 promotion is needed for them in the future, the same pattern applies.
- Phase 04 is the final cleanup phase.

---

## Rollback Plan

Revert the two `.kt` files and both XMLs. No data migration. The `SLEEP_TIMER` enum entry reverts to `barCapable = false`.
