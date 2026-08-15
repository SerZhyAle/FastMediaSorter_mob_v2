# Phase 02 — Dynamic Slot Count

**Strategic spec:** [`../S0208_player-big-buttons-tuning.md`](../S0208_player-big-buttons-tuning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Replace the hard-coded `BIG_BUTTONS_TOP_PANEL_SLOT_COUNT = 8` constant in `CommandPanelController` with a runtime formula `(panelWidthPx / minSlotWidthPx).coerceIn(5, 10)` so the visible top-panel slot count adapts to actual screen width (strategic §3.1.4 / §5.1.3). Planner signature unchanged.

---

## Prerequisites

- [ ] Phase 01 Done — `R.dimen.player_big_button_min_slot_width` exists.
- [ ] Working tree clean for `CommandPanelController.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1200 |

> File is currently 1189 LOC; phase delta ≈ +15 / −2 lines. Stays under the 1500 hard limit. No backup file required (< 500 LOC threshold not met but the file is large — verify post-edit size in step 02.3 closure).

---

## Steps

### Step 02.1 — Remove the hard-coded slot constant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In the `companion object` block (around lines 120–123) delete the line
> ```kotlin
> private const val BIG_BUTTONS_TOP_PANEL_SLOT_COUNT = 8
> ```
> Keep `SMALL_CONTROLS_SCALE` intact. The constant is replaced by the runtime formula introduced in step 02.2 — leaving both in the file would create two competing sources of truth.

**Verification:**

- Grep — `BIG_BUTTONS_TOP_PANEL_SLOT_COUNT` returns zero hits across `app_v2/src/main/`.
- Grep — `private const val SMALL_CONTROLS_SCALE` still matches exactly once in `CommandPanelController.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 — Introduce `resolveBigButtonsTopPanelSlotCount()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private method directly below `resolveAvailableCenterWidthPx()` (around line 1095):
> ```kotlin
> /**
>  * Big Buttons Mode total visible top-panel slot count.
>  *
>  * Formula: `(panelWidthPx / minSlotWidthPx).coerceIn(5, 10)`.
>  * `panelWidthPx` is the laid-out width of `topCommandPanel`; falls back to
>  * `displayMetrics.widthPixels` before the first layout pass.
>  * `minSlotWidthPx` is `R.dimen.player_big_button_min_slot_width`.
>  *
>  * Strategic §3.1.4 / §5.1.3.
>  */
> private fun resolveBigButtonsTopPanelSlotCount(): Int {
>     val dm = binding.root.resources.displayMetrics
>     val panelWidthPx = binding.topCommandPanel.width.takeIf { it > 0 } ?: dm.widthPixels
>     val minSlotWidthPx = binding.root.resources
>         .getDimensionPixelSize(R.dimen.player_big_button_min_slot_width)
>         .coerceAtLeast(1)
>     return (panelWidthPx / minSlotWidthPx).coerceIn(5, 10)
> }
> ```
> Do not log inside this method — it is called every `updateCommandAvailability` pass and would spam.

**Verification:**

- Grep — `private fun resolveBigButtonsTopPanelSlotCount\(\): Int` matches exactly once in the file.
- Grep — `R.dimen.player_big_button_min_slot_width` matches exactly once in this file (call site count = 1).
- Grep — `coerceIn(5, 10)` appears at least once in `CommandPanelController.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 — Wire the formula into `updateCommandAvailability`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In the `bigButtonsMode && effectiveShowCommandPanel` branch (around line 359) replace the existing block
> ```kotlin
> val commandSlots = (BIG_BUTTONS_TOP_PANEL_SLOT_COUNT - bigButtonsFixedButtons().count {
>     it.isVisible
> }).coerceAtLeast(0)
> ```
> with
> ```kotlin
> val totalSlots = resolveBigButtonsTopPanelSlotCount()
> val commandSlots = (totalSlots - bigButtonsFixedButtons().count {
>     it.isVisible
> }).coerceAtLeast(0)
> ```
> Update the inline comment that referenced "eight visible top-panel slots" (S0158 wording) to: `// S0158/S0208: total top-panel slot count is now a function of panel width (resolveBigButtonsTopPanelSlotCount). Fixed nav buttons reserve their share first, the remainder is handed to the planner.` Keep the rest of the branch (`planBigButtonsLayout` call, overflow handling) unchanged.

**Verification:**

- Grep — `val totalSlots = resolveBigButtonsTopPanelSlotCount()` matches exactly once in `CommandPanelController.kt`.
- Grep — `S0208: total top-panel slot count is now a function of panel width` matches once.
- Grep — `BIG_BUTTONS_TOP_PANEL_SLOT_COUNT` returns zero hits anywhere in the file.
- Build — run `/build` → `standardDebug`; build must pass (expected exit 0).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `/build` → `standardDebug` passes (build gate of the phase — closes the controller-level rewire).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" "S0208" "<msg>"`.
- [ ] `CommandPanelController.kt` LOC ≤ 1500 (verify in closure: `(Get-Content … | Measure-Object -Line).Lines`).

---

## Handoff Notes to Next Phase

After Phase 02 the controller hands the planner a slot count derived from the live panel width. Phase 03 swaps the in-memory button height and content layout in the manager — the planner output and the slot count from this phase need no further changes.

---

## Rollback Plan

Revert this phase's commit to restore `BIG_BUTTONS_TOP_PANEL_SLOT_COUNT = 8` and the inline `commandSlots` computation. No persistent state involved.
