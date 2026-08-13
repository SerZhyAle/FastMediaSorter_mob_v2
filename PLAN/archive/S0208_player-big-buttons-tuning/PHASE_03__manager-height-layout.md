# Phase 03 — Manager Height & Layout

**Strategic spec:** [`../S0208_player-big-buttons-tuning.md`](../S0208_player-big-buttons-tuning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Switch `PlayerBigButtonsModeManager` from the legacy `2 × player_cmd_button_size` height (80dp) to the new unified `player_big_button_height` (100dp) on both panels; raise the icon-to-label ratio from 3:1 to 85:15 on the top panel; drop the label entirely on the bottom playback row; and lower the top-panel label font to `player_big_button_top_label_text_size`. The overflow popup row height switches to the same new dimen for visual parity. Visible behaviour change — the device-verify gate at the end of this phase is what flips the spec into `BlockNeedUserTest`.

---

## Prerequisites

- [ ] Phase 01 Done — three new dimens exist.
- [ ] Phase 02 Done — dynamic slot count active.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt` | Modified | ≤ 500 |

Layout XML parity: this phase changes only the in-memory layout transforms applied by the manager. No `activity_player_unified.xml` / `activity_player_unified-land.xml` edits — Rule 12 not triggered. Both orientations consume the same manager.

---

## Steps

### Step 03.1 — Top panel: unified 100dp height

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Inside `applyToTopCommandPanel` (around lines 68–77), replace the two-line block
> ```kotlin
> val buttonSize = topCommandPanel.resources.getDimensionPixelSize(R.dimen.player_cmd_button_size)
> // ..
> panelParams.height = buttonSize * 2
> ```
> with
> ```kotlin
> // S0208: height is its own dimen, not a multiple of the command-icon width.
> val buttonHeight = topCommandPanel.resources.getDimensionPixelSize(R.dimen.player_big_button_height)
> // ..
> panelParams.height = buttonHeight
> ```
> Keep the surrounding `topPanelOriginalHeight` snapshot intact. Replace the S0158 comment about runaway growth with a single line: `// S0158/S0208: fixed dimen prevents runaway growth on restore→apply cycles.`

**Verification:**

- Grep — `R.dimen.player_big_button_height` matches at least once in `PlayerBigButtonsModeManager.kt`.
- Grep — `buttonSize * 2` inside `applyToTopCommandPanel` returns zero hits.
- Grep — `S0158/S0208: fixed dimen prevents runaway growth` matches once.

**Status:** `[ ]` not done

---

### Step 03.2 — Top wrapper: 85/15 icon:label ratio + smaller label

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the same `applyToTopCommandPanel` loop (around lines 107–142):
>
> 1. Change the wrapper `weightSum` from `4f` to `20f`.
> 2. Change the icon's `LayoutParams` weight from `3f` to `17f` (85% of 20).
> 3. Change the label `TextView`'s `LayoutParams` weight from `1f` to `3f` (15% of 20).
> 4. Replace the hard-coded `textSize = 11f` on the label with:
>    ```kotlin
>    textSize = context.resources.getDimension(R.dimen.player_big_button_top_label_text_size) /
>        context.resources.displayMetrics.scaledDensity
>    ```
>    (Reading the sp value as a float via the displayed dimen, divided by `scaledDensity` to feed `TextView.textSize` which expects sp.)
> 5. Add `maxLines = 1` and `ellipsize = android.text.TextUtils.TruncateAt.END` to the label `TextView` so long uk/ru translations get truncated cleanly instead of wrapping into the icon area.
>
> Leave the icon `ImageView.scaleType = FIT_CENTER` and `setPadding(dp8, dp8, dp8, dp8)` unchanged — the 85% slice handles the visual dominance, padding stays as the tap-target buffer (still ≥ 48dp on a 100dp button).

**Verification:**

- Grep — `weightSum = 20f` matches exactly once.
- Grep — `LinearLayout.LayoutParams.MATCH_PARENT, 0, 17f` matches exactly once.
- Grep — `LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f` matches exactly once in `PlayerBigButtonsModeManager.kt`.
- Grep — `textSize = 11f` returns zero hits in this file.
- Grep — `maxLines = 1` matches at least once in this file.
- Grep — `ellipsize = android.text.TextUtils.TruncateAt.END` matches at least once.

**Status:** `[ ]` not done

---

### Step 03.3 — Bottom row: 100dp height, drop label

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inside `applyToBottomPlaybackRow` (around lines 245–267):
>
> 1. Replace
>    ```kotlin
>    val buttonSize = buttonRow.resources.getDimensionPixelSize(R.dimen.player_cmd_button_size)
>    rowParams.height = buttonSize * 2
>    ```
>    with
>    ```kotlin
>    // S0208: unified big-button height applies to both top and bottom panels.
>    val buttonHeight = buttonRow.resources.getDimensionPixelSize(R.dimen.player_big_button_height)
>    rowParams.height = buttonHeight
>    ```
> 2. The MaterialButton branch currently sets `textSize = 18f`. Bump it to `textSize = 22f` so the icon-glyphs (play / skip arrows) scale proportionally with the new 100dp height. Do NOT wrap MaterialButton children in label wrappers — strategic §3.1.3 says the bottom row stays icon-only.
> 3. The ImageView branch keeps `FIT_CENTER` and `setPadding(dp8, dp8, dp8, dp8)`. Tap-target check: 100dp − 2×8dp = 84dp ≥ 48dp — OK.
>
> Do not change the per-child `weight = 1f` distribution — the row still spreads visible children evenly.

**Verification:**

- Grep — within `applyToBottomPlaybackRow`, `R.dimen.player_big_button_height` matches once.
- Grep — within `applyToBottomPlaybackRow`, `buttonSize * 2` returns zero hits.
- Grep — `is MaterialButton -> child.textSize = 22f` matches exactly once.
- Grep — `S0208: unified big-button height applies to both top and bottom panels` matches once.

**Status:** `[ ]` not done

---

### Step 03.4 — Overflow popup row height parity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `buildBigButtonsOverflowMenu` (around line 310) replace
> ```kotlin
> val rowHeight = context.resources.getDimensionPixelSize(R.dimen.player_cmd_button_size) * 2
> ```
> with
> ```kotlin
> val rowHeight = context.resources.getDimensionPixelSize(R.dimen.player_big_button_height)
> ```
> The overflow ListPopupWindow rows now match the panel-button height visually — strategic §2 goal 2 ("единое значение на обеих панелях, в обеих ориентациях") extends naturally to the menu the same buttons spawn.

**Verification:**

- Grep — `getDimensionPixelSize(R.dimen.player_big_button_height)` matches at least twice in `PlayerBigButtonsModeManager.kt` (top panel + overflow; bottom panel makes three).
- Grep — `player_cmd_button_size) * 2` returns zero hits anywhere in `PlayerBigButtonsModeManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.5 — Build gate + LOC budget

**Files:** none — closure step.
**Depends on:** Step 03.4

**Prompt for developer:**

> Run `/build` → `standardDebug`. Fix any compile error inline. After the build passes, confirm `PlayerBigButtonsModeManager.kt` stays at ≤ 500 LOC via PowerShell: `(Get-Content app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt | Measure-Object -Line).Lines` — expected ≤ 460 (was 432, delta ≈ +10/−5). Manual device-verify (320dp/411dp/600dp/1240dp + uk locale label fit) is the user-test gate — defer it to the spec status transition (Phase 04 closure).

**Verification:**

- Build — `/build` → `standardDebug` exits 0.
- Grep — `R.dimen.player_big_button_height` count across `PlayerBigButtonsModeManager.kt` ≥ 3 (top, bottom, overflow).
- LOC — actual file line count is ≤ 500 (expected ≈ 437, actual measured in closure).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `/build` → `standardDebug` passes (closes the manager rewrite).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt" "S0208" "<msg>"`.

---

## Handoff Notes to Next Phase

Phase 04 handles catalog refresh and dev-log finalisation. After Phase 04 the spec transitions to `BlockNeedUserTest` because §6.1 / §6.4 require visual verification on real devices that the chosen `player_big_button_min_slot_width = 50dp` survives the 411dp regress floor and the long-uk label fit test.

---

## Rollback Plan

Revert the three commits (one per code step). The legacy `2 × player_cmd_button_size` behaviour returns immediately; the new dimens introduced in Phase 01 stay but become unused.
