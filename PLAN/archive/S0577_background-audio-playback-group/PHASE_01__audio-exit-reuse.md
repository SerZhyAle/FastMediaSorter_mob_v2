# Phase 01 - Audio-Exit Reuse Extraction

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Extract the background-audio exit decision and the exit dialog out of `PlayerLifecycleManager` into two reusable, Activity-agnostic units, and refactor the player to delegate to them with zero behavior change. No streams or settings-UI changes in this phase.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `PlayerLifecycleManager.kt` is > 500 LOC - take a timestamped backup into `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioExitBehaviorResolver.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundAudioExitDialog.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 690 |

> `ui/player/helpers` is the shared audio-helper location: `StreamInlineAudioManager` already imports `ui.player.helpers.AudioServiceController`, so the streams screen can consume these units in Phase 04 without a new module.

---

## Steps

### Step 01.1 - Add the pure exit-action resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioExitBehaviorResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an Activity-agnostic resolver that maps the current audio-exit situation to a single action. Declare `enum class AudioExitAction { FINISH, STOP_AND_FINISH, ASK }` and an `object AudioExitBehaviorResolver` with `fun resolve(serviceAudioActive: Boolean, player: androidx.media3.common.Player?, behavior: BackgroundAudioExitBehavior): AudioExitAction`. Encode exactly the current `PlayerLifecycleManager.exitPlayerWithAudioCheck` decision tree: if `!serviceAudioActive` -> `FINISH`; else if `player != null && !player.isPlaying && player.playbackState != Player.STATE_BUFFERING` -> `STOP_AND_FINISH` (paused-implicit-stop); else dispatch the enum: `ALWAYS_STOP` -> `STOP_AND_FINISH`, `ALWAYS_CONTINUE` -> `FINISH`, `ASK` -> `ASK`. Pure function, no Android Context, no Activity reference.

**Verification:**

- `Glob` - `AudioExitBehaviorResolver.kt` exists.
- `Grep` - `enum class AudioExitAction` matches once.
- `Grep` - `fun resolve(` present with the three parameters.
- `Grep` - `STATE_BUFFERING` present (paused-vs-connecting branch preserved).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Files: AudioExitBehaviorResolver.kt (New, +52 LOC). Enum AudioExitAction + pure resolve().

---

### Step 01.2 - Add the reusable exit dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundAudioExitDialog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `object BackgroundAudioExitDialog` with `fun show(context: Context, onStopThisTime: () -> Unit, onContinueThisTime: () -> Unit, onAlwaysStop: () -> Unit, onAlwaysContinue: () -> Unit)`. Build the existing 4-item `MaterialAlertDialogBuilder` using the current string keys: title `R.string.background_audio_exit_message`; items `background_audio_exit_stop`, `background_audio_exit_continue`, `background_audio_exit_always_stop`, `background_audio_exit_always_continue`; dispatch index 0->onStopThisTime, 1->onContinueThisTime, 2->onAlwaysStop, 3->onAlwaysContinue. No persistence and no player calls inside - the caller supplies behavior via the lambdas.

**Verification:**

- `Glob` - `BackgroundAudioExitDialog.kt` exists.
- `Grep` - `fun show(` present with the four lambda parameters.
- `Grep` - `background_audio_exit_always_continue` referenced.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Files: BackgroundAudioExitDialog.kt (New, +40 LOC). Reusable 4-item dialog, caller-supplied lambdas.

---

### Step 01.3 - Delegate PlayerLifecycleManager to the shared units

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Refactor `exitPlayerWithAudioCheck` to compute its branch via `AudioExitBehaviorResolver.resolve(activity.mediaLoaderManager.isServiceAudioActive, activity.audioServiceController?.player, viewModel.state.value.backgroundAudioExitBehavior)` and act on the returned `AudioExitAction` (FINISH -> `doFinish`; STOP_AND_FINISH -> stop the service player then `doFinish`; ASK -> show the dialog). Keep the `!isMediaLoaderManagerInitialized` short-circuit and the `warnIfBackgroundHandoffMissed()` call exactly as before. Replace the body of `showExitAudioDialog` with a call to `BackgroundAudioExitDialog.show(..)`, wiring: onStopThisTime -> stop player + `doFinish`; onContinueThisTime -> `doFinish`; onAlwaysStop -> `viewModel.updateExitBehavior(ALWAYS_STOP)` + stop + `doFinish`; onAlwaysContinue -> `viewModel.updateExitBehavior(ALWAYS_CONTINUE)` + `doFinish`. Player-visible behavior must be identical to before.

**Verification:**

- `Grep` - `AudioExitBehaviorResolver.resolve(` referenced in `PlayerLifecycleManager.kt`.
- `Grep` - `BackgroundAudioExitDialog.show(` referenced in `PlayerLifecycleManager.kt`.
- `Grep` - the inline `MaterialAlertDialogBuilder(activity)` 4-item `.setItems(` block is removed from `showExitAudioDialog` (no duplicate dialog construction remains).
- `Grep` - `warnIfBackgroundHandoffMissed()` still called in `exitPlayerWithAudioCheck`.
- Compile: `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 5/5 PASS. Files: PlayerLifecycleManager.kt (Modified, delegates to resolver+dialog; removed unused Player import). `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (two new classes) via `scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`AudioExitBehaviorResolver.resolve(..)` + `BackgroundAudioExitDialog.show(..)` are the stable reuse points. Phase 04 wires the streams screen to the same two units with its own `serviceAudioActive` predicate and its own stop/continue/persist lambdas. No player-side behavior changed.

---

## Rollback Plan

Revert the phase commit(s); the two new files are unreferenced after revert. No data migration or user-facing surface changed.
