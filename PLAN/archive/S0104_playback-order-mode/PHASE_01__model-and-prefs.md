# Phase 01 — Model and Prefs

**Strategic spec:** [`../S0104_playback-order-mode.md`](../S0104_playback-order-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Introduce the `PlaybackOrderMode` enum, add persistence key constants, and extend `PlayerState` and `PlayerViewModel` with mode cycling and shuffle-index management — no UI, no navigation changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PlaybackOrderMode.kt` | New | ≤ 35 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt` | Modified | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 790 |

> `PlayerViewModel.kt` is 728 lines → backup required before editing: copy to `temp/PlayerViewModel_backup_<timestamp>.kt`.

---

## Steps

### Step 1.1 — Create `PlaybackOrderMode` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PlaybackOrderMode.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file at the path above. Define `enum class PlaybackOrderMode` with four entries in cycle order: `LOOP_LIST`, `PLAY_THROUGH`, `SHUFFLE`, `REPEAT_ONE`. Add a `fun next(): PlaybackOrderMode` extension or companion method that returns the next mode in the cycle (`LOOP_LIST` follows `REPEAT_ONE`). Add a `fun toPrefsString(): String` that returns the enum name as a lowercase string for SharedPreferences storage, and a companion `fun fromPrefsString(value: String): PlaybackOrderMode` that parses it back (defaulting to `LOOP_LIST` on unknown values).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PlaybackOrderMode.kt` exists.
- `Grep` — `enum class PlaybackOrderMode` matches exactly once in that file.
- `Grep` — `LOOP_LIST` present in the file.
- `Grep` — `REPEAT_ONE` present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: domain/model/PlaybackOrderMode.kt (new, 17 LOC). Dev log recorded.

---

### Step 1.2 — Add persistence key constants

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `PlaybackControlPreferences`, add two new string constants after the existing `KEY_SPEED`:
> - `KEY_PLAYBACK_ORDER_AUDIO = "playback_order_audio"`
> - `KEY_PLAYBACK_ORDER_VIDEO = "playback_order_video"`

**Verification:**

- `Grep` — `KEY_PLAYBACK_ORDER_AUDIO` present in `PlaybackControlPreferences.kt`.
- `Grep` — `KEY_PLAYBACK_ORDER_VIDEO` present in `PlaybackControlPreferences.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: PlaybackControlPreferences.kt (+2 LOC). Dev log recorded.

---

### Step 1.3 — Extend `PlayerState` with mode and shuffle fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Backup `PlayerViewModel.kt` to `temp/PlayerViewModel_backup_<timestamp>.kt` first (file is 728 lines).
>
> Inside the `PlayerState` data class, add two new fields after `showBlackScreenButton`:
> - `val playbackOrderMode: PlaybackOrderMode = PlaybackOrderMode.LOOP_LIST`
> - `val shuffleIndices: List<Int> = emptyList()`
>
> `shuffleIndices` is a shuffled permutation of file indices used when `playbackOrderMode == SHUFFLE`. It is populated by `PlayerViewModel.rebuildShuffleIndices()` whenever files are loaded or the mode switches to SHUFFLE.

**Verification:**

- `Grep` — `val playbackOrderMode: PlaybackOrderMode` present in `PlayerViewModel.kt`.
- `Grep` — `val shuffleIndices: List<Int>` present in `PlayerViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: PlayerViewModel.kt (+3 LOC). Backup created. Dev log recorded.

---

### Step 1.4 — Add mode cycling and shuffle management to `PlayerViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 1.3

**Prompt for developer:**

> Add the following to `PlayerViewModel` (same file, same backup from 1.3):
>
> **`rebuildShuffleIndices()`** — private function. Takes `state.files` indices, shuffles them with `kotlin.random.Random`, ensures the current `currentIndex` is not at position 0 (swap if needed so the currently playing file is not immediately replayed). Calls `updateState { it.copy(shuffleIndices = newList) }`.
>
> **`setPlaybackOrderMode(mode: PlaybackOrderMode)`** — public function. Calls `updateState { it.copy(playbackOrderMode = mode) }`. If `mode == PlaybackOrderMode.SHUFFLE` and `state.value.files.isNotEmpty()`, also calls `rebuildShuffleIndices()`.
>
> **`cyclePlaybackOrderMode()`** — public function. Reads `state.value.playbackOrderMode`, calls `setPlaybackOrderMode(current.next())`. Returns the new mode so callers can persist it or show a toast.
>
> The debug tag to insert at the entry point of `cyclePlaybackOrderMode()`:
> ```kotlin
> Timber.d("S0104: cyclePlaybackOrderMode → ${state.value.playbackOrderMode.next()}")
> ```

**Verification:**

- `Grep` — `fun rebuildShuffleIndices` present in `PlayerViewModel.kt`.
- `Grep` — `fun setPlaybackOrderMode` present in `PlayerViewModel.kt`.
- `Grep` — `fun cyclePlaybackOrderMode` present in `PlayerViewModel.kt`.
- `Grep` — `S0104:` present in `PlayerViewModel.kt` (debug tag).
- `Grep` — `Log\.d\(` returns zero hits in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 5/5 PASS. Files: PlayerViewModel.kt (+29 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `PlaybackOrderMode` enum is in `domain/model/` and importable from all layers.
- `PlayerState.playbackOrderMode` is the single source of truth for the current mode.
- `PlayerState.shuffleIndices` holds a valid permutation whenever `playbackOrderMode == SHUFFLE`.
- `PlayerViewModel.cyclePlaybackOrderMode()` returns the new mode; caller (Activity) is responsible for SharedPreferences persistence and toast display.
- No UI surfaces are changed yet; the ViewModel compiles and mode cycling works in isolation.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no UI changes, no SharedPreferences writes yet.
