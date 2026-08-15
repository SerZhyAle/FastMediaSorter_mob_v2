# Phase 04 - Streams Exit Rule

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Apply the background-audio exit rule when leaving the streams screen: when a stream is playing in the background-service (ON) mode, route back/finish through the same resolver + dialog as the player; when in OFF (local) mode, stop and finish plainly.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`AudioExitBehaviorResolver`, `BackgroundAudioExitDialog`).
- [ ] Phase 02 ✅ Done (`StreamsViewModel.settings`, `updateExitBehavior`).
- [ ] Phase 03 ✅ Done (ON/OFF playback modes, `playingId`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | Modified | ≤ 215 |

---

## Steps

### Step 04.1 - Expose a "service audio active" predicate on the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val isServiceAudioActive: Boolean` that is true only when a stream is currently playing through the background-service path (service mode active AND `playingId != null`), and expose the underlying `androidx.media3.common.Player?` (the service player) for the resolver - e.g. `val activeServicePlayer: Player?` returning the player only in service mode, null in local mode. These mirror `PlayerMediaLoaderManager.isServiceAudioActive` + `audioServiceController.player` for the streams context, where there is no `currentFile`.

**Verification:**

- `Grep` - `val isServiceAudioActive` present in `StreamInlineAudioManager.kt`.
- `Grep` - a `Player?` accessor for the active service player present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Files: StreamInlineAudioManager.kt. Added isServiceAudioActive + activeServicePlayer (Player?).

---

### Step 04.2 - Route streams exit through the shared resolver + dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a single `exitStreamsWithAudioCheck()` and route both the toolbar navigation click (`binding.toolbar.setNavigationOnClickListener`) and a registered `OnBackPressedCallback` through it (replace the direct `finish()`). Inside: call `AudioExitBehaviorResolver.resolve(inlineAudio.isServiceAudioActive, inlineAudio.activeServicePlayer, viewModel.settings.value.backgroundAudioExitBehavior)`; on `FINISH` -> finish, leaving service audio playing (background continue); on `STOP_AND_FINISH` -> `inlineAudio.stop()` then finish; on `ASK` -> `BackgroundAudioExitDialog.show(this, onStopThisTime = { inlineAudio.stop(); finish() }, onContinueThisTime = { finish() }, onAlwaysStop = { viewModel.updateExitBehavior(ALWAYS_STOP); inlineAudio.stop(); finish() }, onAlwaysContinue = { viewModel.updateExitBehavior(ALWAYS_CONTINUE); finish() })`. When `isServiceAudioActive` is false (OFF mode or nothing playing) the resolver returns `FINISH` and the screen closes plainly.

**Verification:**

- `Grep` - `AudioExitBehaviorResolver.resolve(` referenced in `StreamsActivity.kt`.
- `Grep` - `BackgroundAudioExitDialog.show(` referenced in `StreamsActivity.kt`.
- `Grep` - `OnBackPressedCallback` registered in `StreamsActivity.kt`.
- `Grep` - the bare `setNavigationOnClickListener { finish() }` no longer present (routed through the exit check).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Files: StreamsActivity.kt. exitStreamsWithAudioCheck routes toolbar nav + `onBackPressedDispatcher.addCallback` through the shared resolver/dialog.

---

### Step 04.3 - Keep the service alive on background-continue

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure `onDestroy` does not stop a stream the user chose to continue in the background. Today `onDestroy` calls `inlineAudio.release()` unconditionally, which tears down the service. Add a `releaseKeepingBackgroundService()` (or a `keepServiceAlive` flag honored by `release()`) so that, when the exit decision was background-continue (service mode + FINISH-with-continue), `onDestroy` releases only the Activity-side wiring and leaves `AudioPlaybackService` playing. For OFF mode and stop decisions, keep the full teardown (release local ExoPlayer / stop service).

**Verification:**

- `Grep` - `onDestroy` in `StreamsActivity.kt` no longer calls the unconditional full-stop `release()` on a background-continue exit (a guarded teardown path exists).
- `Grep` - a keep-alive teardown entry point exists in `StreamInlineAudioManager.kt`.
- Compile: `.\a.ps1 fc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Files: StreamsActivity.kt, StreamInlineAudioManager.kt. onDestroy honors keepBackgroundService; releaseKeepingBackgroundService keeps the service alive. `.\a.ps1 fc` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` exits 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the phase.

---

## Handoff Notes to Next Phase

Streams now honor the background-playback gate (Phase 03) and the exit rule (this phase) using the Phase 01 reuse units. The behavior side of S0577 is complete; Phase 05 relocates the settings UI and renames the exit label.

---

## Rollback Plan

Revert the phase commit(s); the streams screen reverts to direct `finish()` and unconditional teardown. No persisted data or schema changed.
