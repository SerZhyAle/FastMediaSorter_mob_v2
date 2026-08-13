# Phase 01 — State Foundation

**Strategic spec:** [`../S0127_image-player-draw-crop-immersive.md`](../S0127_image-player-draw-crop-immersive.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Introduce the `PlayerImageEditMode` enum and propagate it through `PlayerState` + `PlayerViewModel`. No UI consumers yet.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/state/PlayerImageEditMode.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 800 |

---

## Steps

### Step 01.1 — Create `PlayerImageEditMode` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/state/PlayerImageEditMode.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `PlayerImageEditMode.kt` in package `com.sza.fastmediasorter.ui.player.state` with a single enum class `PlayerImageEditMode` declaring three values exactly: `NONE`, `DRAW`, `CROP`. No additional members, properties, or KDoc beyond a one-line class header comment "Active image editor mode for PlayerActivity (S0127)."

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/state/PlayerImageEditMode.kt` exists.
- `Grep` — `enum class PlayerImageEditMode` matches exactly once.
- `Grep` — `NONE, DRAW, CROP` matches in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/state/PlayerImageEditMode.kt (+6 LOC). Dev log recorded.

---

### Step 01.2 — Add `imageEditMode` field to `PlayerState`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Open `PlayerViewModel.kt`. Inside the `PlayerState` data class (the one already containing `showCommandPanel`), add a new field `val imageEditMode: PlayerImageEditMode = PlayerImageEditMode.NONE`. Place it immediately after the `showCommandPanel` field. Add the import `import com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode` at the top.

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode` matches once.
- `Grep` — `val imageEditMode: PlayerImageEditMode = PlayerImageEditMode.NONE` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt (+2 LOC). Dev log recorded.

---

### Step 01.3 — Add `setImageEditMode()` API to `PlayerViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `PlayerViewModel.kt`, add a new public method directly after `enterCommandPanelMode()`:
> ```kotlin
> fun setImageEditMode(mode: PlayerImageEditMode) {
>     if (state.value.imageEditMode == mode) return
>     Timber.d("S0127: setImageEditMode $mode")
>     updateState { it.copy(imageEditMode = mode) }
> }
> ```
> Do not modify any other method. Use existing `updateState` helper.

**Verification:**

- `Grep` — `fun setImageEditMode(mode: PlayerImageEditMode)` matches once.
- `Grep` — `Timber.d("S0127: setImageEditMode \$mode")` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` BUILD SUCCESSFUL in 45s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 wires Draw and Crop entry/exit to `viewModel.setImageEditMode(...)` via callbacks on the existing managers.

---

## Rollback Plan

Revert the phase commit(s); no schema, no user-visible surface yet.
