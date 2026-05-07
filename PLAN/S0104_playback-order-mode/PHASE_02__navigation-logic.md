# Phase 02 — Navigation Logic

**Strategic spec:** [`../S0104_playback-order-mode.md`](../S0104_playback-order-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Make `PlayerNavigationCoordinator.nextFile()` / `previousFile()` respect `PlayerState.playbackOrderMode`, introduce a `PlayerEvent.StopPlayback` event for the PLAY_THROUGH end-of-list case, and add an `applyPlaybackOrderMode()` helper to `AudioServiceController` for ExoPlayer-level mode synchronisation.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt` | Modified | ≤ 280 |

> `PlayerNavigationCoordinator.kt` is 300 lines — under 500, no backup needed.
> `AudioServiceController.kt` is 243 lines — under 500, no backup needed.
> `PlayerViewModel.kt` backup was already taken in Phase 01 — take a new timestamped backup before this edit.

---

## Steps

### Step 2.1 — Add `PlayerEvent.StopPlayback` to ViewModel event set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Inside the `PlayerEvent` sealed class in `PlayerViewModel`, add a new object entry:
> ```kotlin
> object StopPlayback : PlayerEvent()
> ```
> This event is fired by `PlayerNavigationCoordinator` when `PLAY_THROUGH` mode reaches the last file and auto-advance is triggered (not by a manual Next tap — manual taps wrap to first in all modes except PLAY_THROUGH).

**Verification:**

- `Grep` — `object StopPlayback` present in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Files: PlayerViewModel.kt (+1 LOC). Dev log recorded.

---

### Step 2.2 — Update `PlayerNavigationCoordinator` — constructor and `nextFile()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Extend `PlayerNavigationCoordinator`'s constructor with one new parameter:
> ```kotlin
> private val sendEvent: (PlayerViewModel.PlayerEvent) -> Unit
> ```
> Add it after `clearPlaybackWatchdogs`. Update the single call site in `PlayerViewModel` where the coordinator is instantiated (pass `::sendEvent`).
>
> Replace the hardcoded loop logic in `nextFile()` with a branch on `currentState.playbackOrderMode`:
>
> - **`LOOP_LIST`** — existing behaviour: wrap from last to first.
> - **`PLAY_THROUGH`** — if at last file AND `!manual`: send `PlayerEvent.StopPlayback` and return without changing index. If at last file AND `manual`: wrap to first (manual skip always succeeds).
> - **`SHUFFLE`** — find current position in `currentState.shuffleIndices` (`shuffleIndices.indexOf(currentIndex)`). If not found or at last position, call `viewModel.rebuildShuffleIndices()` first (observer auto-updates `currentState`), then pick `shuffleIndices[0]`. Otherwise pick `shuffleIndices[pos + 1]`.
> - **`REPEAT_ONE`** — set `nextIndex = currentState.currentIndex` (same file). Returning the same index is intentional; the caller handles replay by detecting no-change.
>
> For REPEAT_ONE, add special handling: after `updateState`, call `saveResumeState()` and return immediately (skip the debounced `saveLastViewedFile` call — file hasn't changed).
>
> Insert the debug tag at the top of `nextFile()` (replaces the existing debug block for SHUFFLE/REPEAT_ONE branches):
> ```kotlin
> Timber.d("S0104: nextFile mode=${currentState.playbackOrderMode} manual=$manual idx=${currentState.currentIndex}")
> ```

**Verification:**

- `Grep` — `sendEvent: \(PlayerViewModel.PlayerEvent\)` present in `PlayerNavigationCoordinator.kt` (constructor param).
- `Grep` — `PLAY_THROUGH` present in `PlayerNavigationCoordinator.kt`.
- `Grep` — `SHUFFLE` present in `PlayerNavigationCoordinator.kt`.
- `Grep` — `REPEAT_ONE` present in `PlayerNavigationCoordinator.kt`.
- `Grep` — `S0104:` present in `PlayerNavigationCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerNavigationCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 6/6 PASS. Files: PlayerNavigationCoordinator.kt (+50 LOC), PlayerViewModel.kt (+1 LOC). Dev log recorded.

---

### Step 2.3 — Update `PlayerNavigationCoordinator` — `previousFile()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> Apply mode-aware logic to `previousFile()`:
>
> - **`LOOP_LIST`** and **`PLAY_THROUGH`** — existing wrap-around behaviour (no change for either; PLAY_THROUGH only blocks auto-advance via Next, not manual prev).
> - **`SHUFFLE`** — find `pos = shuffleIndices.indexOf(currentIndex)`. If `pos <= 0`: wrap to `shuffleIndices.last()`. Otherwise: pick `shuffleIndices[pos - 1]`.
> - **`REPEAT_ONE`** — set `prevIndex = currentState.currentIndex` (no movement).

**Verification:**

- `Grep` — `previousFile` function contains `SHUFFLE` branch in `PlayerNavigationCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerNavigationCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: PlayerNavigationCoordinator.kt (+16 LOC). Dev log recorded.

---

### Step 2.4 — Add `applyPlaybackOrderMode()` to `AudioServiceController`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Add a new public function to `AudioServiceController`:
>
> ```kotlin
> fun applyPlaybackOrderMode(mode: PlaybackOrderMode) {
>     val player = mediaController ?: return
>     when (mode) {
>         PlaybackOrderMode.LOOP_LIST -> {
>             player.repeatMode = Player.REPEAT_MODE_ALL
>             player.shuffleModeEnabled = false
>         }
>         PlaybackOrderMode.PLAY_THROUGH -> {
>             player.repeatMode = Player.REPEAT_MODE_OFF
>             player.shuffleModeEnabled = false
>         }
>         PlaybackOrderMode.SHUFFLE -> {
>             player.repeatMode = Player.REPEAT_MODE_ALL
>             player.shuffleModeEnabled = true
>         }
>         PlaybackOrderMode.REPEAT_ONE -> {
>             player.repeatMode = Player.REPEAT_MODE_ONE
>             player.shuffleModeEnabled = false
>         }
>     }
>     Timber.d("S0104: AudioServiceController.applyPlaybackOrderMode mode=$mode")
> }
> ```
>
> Add the necessary import for `PlaybackOrderMode` from `domain.model`.

**Verification:**

- `Grep` — `fun applyPlaybackOrderMode` present in `AudioServiceController.kt`.
- `Grep` — `shuffleModeEnabled` present in `AudioServiceController.kt`.
- `Grep` — `S0104:` present in `AudioServiceController.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `AudioServiceController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: AudioServiceController.kt (+22 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- `PlayerNavigationCoordinator` now branches on `playbackOrderMode` for all four modes.
- `PlayerEvent.StopPlayback` is defined; `PlayerActivity` will observe and handle it in Phase 03.
- `AudioServiceController.applyPlaybackOrderMode()` is ready; `PlayerActivity` will call it from the button click handler wired in Phase 03.

---

## Rollback Plan

Revert phase commit(s). No SharedPreferences writes, no UI surfaces changed yet.
