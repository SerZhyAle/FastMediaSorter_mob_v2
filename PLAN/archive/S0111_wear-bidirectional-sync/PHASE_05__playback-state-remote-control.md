# Phase 05 — Playback State and Remote Control

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 13 / 13
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Watch players publish a Data Item with current playback state (track name, position, play/pause) when state changes. Phone `WearSyncSettingsFragment` shows a "Now Playing on Watch" card and sends play/pause/next/prev commands back. Commands arrive on the watch via `WatchWearListenerService` and are dispatched to the active player.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] INDEX.md Blocker 2 (battery impact of state updates) is checked and update frequency is decided.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPlaybackStatePayload.kt` | New | ≤ 40 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPlaybackCommand.kt` | New | ≤ 20 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/PublishPlaybackStateUseCase.kt` | New | ≤ 80 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WatchWearListenerService.kt` | Modified | ≤ 200 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt` | Modified | ≤ 290 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPlaybackStatePayload.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPlaybackCommand.kt` | New | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendPlaybackCommandUseCase.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt` | Modified | ≤ 340 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 5.1 — Create `WearPlaybackStatePayload` on watch side

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPlaybackStatePayload.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the listed file in package `com.sza.fastmediasorter.wear.domain.model`. Declare:
> ```
> data class WearPlaybackStatePayload(
>     val isPlaying: Boolean,
>     val fileName: String,
>     val sourceName: String,
>     val positionMs: Long,
>     val durationMs: Long,
>     val mediaType: String   // "AUDIO" | "VIDEO"
> )
> ```

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPlaybackStatePayload.kt` exists.
- `Grep` — `data class WearPlaybackStatePayload` matches.
- `Grep` — `val mediaType: String` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../domain/model/WearPlaybackStatePayload.kt (New, 10 LOC). Dev log recorded.

---

### Step 5.2 — Create `WearPlaybackStatePayload` mirror on phone side

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPlaybackStatePayload.kt`
**Depends on:** — start of phase (parallel with 5.1)

**Prompt for developer:**

> Create the listed file in package `com.sza.fastmediasorter.domain.model` with an identical `data class WearPlaybackStatePayload` as the watch-side copy in Step 5.1.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPlaybackStatePayload.kt` exists.
- `Grep` — `data class WearPlaybackStatePayload` matches.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 2/2 PASS. Files: app_v2/.../domain/model/WearPlaybackStatePayload.kt (New, 10 LOC). Dev log recorded.

---

### Step 5.3 — Create `WearPlaybackCommand` enum on both sides

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPlaybackCommand.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPlaybackCommand.kt`
**Depends on:** — start of phase (parallel with 5.1)

**Prompt for developer:**

> Create both files. In each, declare `enum class WearPlaybackCommand { PLAY_PAUSE, NEXT, PREVIOUS, STOP }` in the respective package.

**Verification:**

- `Glob` — both files exist.
- `Grep` — `enum class WearPlaybackCommand` present in each file.
- `Grep` — `PLAY_PAUSE` present in each file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../WearPlaybackCommand.kt (New, 3 LOC), app_v2/.../WearPlaybackCommand.kt (New, 3 LOC). Dev log recorded.

---

### Step 5.4 — Create `PublishPlaybackStateUseCase` on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/PublishPlaybackStateUseCase.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> Create the listed file. Declare `class PublishPlaybackStateUseCase @Inject constructor(private val context: Context, private val gson: Gson)`.
>
> Implement `suspend operator fun invoke(state: WearPlaybackStatePayload)` using `runCatching`:
> 1. Serialize `state` to JSON bytes.
> 2. Build `WearEventEnvelope(eventType = WearDataLayerPaths.EVENT_PLAYBACK_STATE, sentAt = System.currentTimeMillis(), data = stateBytes)`.
> 3. Serialize envelope to JSON bytes.
> 4. Build `PutDataMapRequest.create(WearDataLayerPaths.PLAYBACK_STATE)`, put `payload` byte array, set urgent, call `Wearable.getDataClient(context).putDataItem(request).await()`.
>
> On failure: log with `Timber.e` and swallow (non-critical path — do not crash the player).
> Add `Timber.d("S0111: PublishPlaybackStateUseCase — publishing state isPlaying=${state.isPlaying} file=${state.fileName}")`.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/PublishPlaybackStateUseCase.kt` exists.
- `Grep` — `class PublishPlaybackStateUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `WearDataLayerPaths.PLAYBACK_STATE` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 5/5 PASS. Files: wear/.../usecase/PublishPlaybackStateUseCase.kt (New, 37 LOC). Dev log recorded.

---

### Step 5.5 — Publish state from `AudioPlayerViewModel`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt`
**Depends on:** Step 5.4

**Prompt for developer:**

> Inject `PublishPlaybackStateUseCase` into `AudioPlayerViewModel`. At the end of `onIsPlayingChanged(isPlaying: Boolean)` and `onPlaybackStateChanged(playbackState: Int)` — after updating `_uiState` — call `publishPlaybackState()`. Implement `publishPlaybackState()` as a `private fun`:
> - Build a `WearPlaybackStatePayload` from current `_uiState` values.
> - Call `publishPlaybackStateUseCase(payload)` in `viewModelScope.launch`.
>
> Update frequency: only publish on `STATE_READY` and `STATE_ENDED`, and on every `isPlaying` toggle. Do NOT publish on a timer (decided in Blocker 2).

**Verification:**

- `Grep` — `publishPlaybackStateUseCase` injected in `AudioPlayerViewModel.kt`.
- `Grep` — `publishPlaybackState()` called from `onIsPlayingChanged`.
- `Grep` — `WearPlaybackStatePayload` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../ui/player/audio/AudioPlayerViewModel.kt (publishPlaybackState injected and called). Dev log recorded.

---

### Step 5.6 — Publish state from `VideoPlayerViewModel`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt`
**Depends on:** Step 5.4

**Prompt for developer:**

> Apply the same change as Step 5.5 to `VideoPlayerViewModel`: inject `PublishPlaybackStateUseCase`, add `publishPlaybackState()` helper, call it from `onIsPlayingChanged` and `onPlaybackStateChanged`. Use `mediaType = "VIDEO"` in the payload.

**Verification:**

- `Grep` — `publishPlaybackStateUseCase` injected in `VideoPlayerViewModel.kt`.
- `Grep` — `mediaType = "VIDEO"` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../ui/player/video/VideoPlayerViewModel.kt (publishPlaybackState injected; mediaType="VIDEO"). Dev log recorded.

---

### Step 5.7 — Add `WatchPlaybackCommandEvents` bus and command handler in `WatchWearListenerService`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WatchWearListenerService.kt`
**Depends on:** Step 5.3

**Prompt for developer:**

> 1. Add `object WatchPlaybackCommandEvents { val commandFlow = MutableSharedFlow<WearPlaybackCommand>(extraBufferCapacity = 4) }` at the bottom of `WatchWearListenerService.kt` (outside the class, same file as `WatchSyncEvents`).
>
> 2. Replace the stub `handlePlaybackCommand(data: ByteArray)` with a real implementation: deserialize `data` → `WearEventEnvelope` → inner data → `WearPlaybackCommand` via `gson.fromJson(...)` (the enum name is the JSON value); emit `WatchPlaybackCommandEvents.commandFlow.emit(command)`.
>
> On error: log with `Timber.e` and do not crash.

**Verification:**

- `Grep` — `object WatchPlaybackCommandEvents` present in `WatchWearListenerService.kt`.
- `Grep` — `commandFlow` present.
- `Grep` — `handlePlaybackCommand` is non-stub (no "not yet implemented").
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../data/wear/WatchWearListenerService.kt (WatchPlaybackCommandEvents added; handlePlaybackCommand implemented). Dev log recorded.

---

### Step 5.8 — Subscribe `AudioPlayerViewModel` to command bus

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt`
**Depends on:** Step 5.7

**Prompt for developer:**

> In `AudioPlayerViewModel.init { }`, launch a coroutine that collects `WatchPlaybackCommandEvents.commandFlow`. Handle each command:
> - `PLAY_PAUSE` → call `togglePlayPause()`
> - `NEXT` → call `player.seekToNextMediaItem()` (or equivalent)
> - `PREVIOUS` → call `player.seekToPreviousMediaItem()`
> - `STOP` → call `player.stop()`
>
> Guard against NPE if `exoPlayer` is null or not ready.

**Verification:**

- `Grep` — `WatchPlaybackCommandEvents.commandFlow` collected in `AudioPlayerViewModel.kt`.
- `Grep` — `PLAY_PAUSE` handled.
- `Grep` — `NEXT` handled.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../ui/player/audio/AudioPlayerViewModel.kt (commandFlow collected; PLAY_PAUSE/NEXT/PREVIOUS/STOP handled). Dev log recorded.

---

### Step 5.9 — Subscribe `VideoPlayerViewModel` to command bus

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt`
**Depends on:** Step 5.7

**Prompt for developer:**

> Apply the same change as Step 5.8 to `VideoPlayerViewModel`.

**Verification:**

- `Grep` — `WatchPlaybackCommandEvents.commandFlow` collected in `VideoPlayerViewModel.kt`.
- `Grep` — `PLAY_PAUSE` handled.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 2/2 PASS. Files: wear/.../ui/player/video/VideoPlayerViewModel.kt (commandFlow collected; PLAY_PAUSE handled). Dev log recorded.

---

### Step 5.10 — Create `SendPlaybackCommandUseCase` on phone

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendPlaybackCommandUseCase.kt`
**Depends on:** Steps 5.2, 5.3

**Prompt for developer:**

> Create the listed file. Declare `class SendPlaybackCommandUseCase @Inject constructor(private val wearableRepository: WearableDataLayerRepository, private val gson: Gson)`.
>
> Implement `suspend operator fun invoke(command: WearPlaybackCommand): Result<Unit>` using `runCatching`:
> 1. Get connected nodes; if empty, error("No watch connected").
> 2. Build `WearEventEnvelope(eventType = WearDataLayerPaths.EVENT_PLAYBACK_CMD, sentAt = System.currentTimeMillis(), data = gson.toJson(command.name).toByteArray())`.
> 3. Serialize envelope to bytes.
> 4. For each node: `wearableRepository.sendMessage(node.id, WearDataLayerPaths.PLAYBACK_CMD, envelopeBytes)`.
>
> Add `Timber.d("S0111: SendPlaybackCommandUseCase — sending command $command")`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendPlaybackCommandUseCase.kt` exists.
- `Grep` — `class SendPlaybackCommandUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `WearDataLayerPaths.PLAYBACK_CMD` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 5/5 PASS. Files: app_v2/.../usecase/SendPlaybackCommandUseCase.kt (New, 30 LOC). Dev log recorded.

---

### Step 5.11 — Wire playback state handler in `PhoneWearListenerService`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt`
**Depends on:** Step 5.2

**Prompt for developer:**

> Inject `Gson` into `PhoneWearListenerService`. In `onDataChanged`, replace the stub for `WearDataLayerPaths.PLAYBACK_STATE` with a real handler:
> 1. Extract `payload` bytes from `DataMapItem`.
> 2. Deserialize → `WearEventEnvelope` → inner data → `WearPlaybackStatePayload`.
> 3. Emit `WearSyncEvents.watchPlaybackStateFlow.emit(payload)` (add `watchPlaybackStateFlow: MutableSharedFlow<WearPlaybackStatePayload?>` to `WearSyncEvents`, initially emitting `null`).

**Verification:**

- `Grep` — `watchPlaybackStateFlow` present in `WearSyncEvents` object.
- `Grep` — `WearPlaybackStatePayload` imported in `PhoneWearListenerService.kt`.
- `Grep` — handler is non-stub for `PLAYBACK_STATE`.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: app_v2/.../service/PhoneWearListenerService.kt (handlePlaybackState implemented; watchPlaybackStateFlow added to WearSyncEvents). Dev log recorded.

---

### Step 5.12 — Expose watch playback state in `WearSyncViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt`
**Depends on:** Steps 5.10, 5.11

**Prompt for developer:**

> Inject `SendPlaybackCommandUseCase` into `WearSyncViewModel`. In `init`, collect `WearSyncEvents.watchPlaybackStateFlow` and forward to `_watchPlaybackState: MutableStateFlow<WearPlaybackStatePayload?>`. Expose as `val watchPlaybackState`.
>
> Add `fun sendPlaybackCommand(command: WearPlaybackCommand)`: launch coroutine → call `sendPlaybackCommandUseCase(command)`.

**Verification:**

- `Grep` — `watchPlaybackState` StateFlow present in `WearSyncViewModel.kt`.
- `Grep` — `fun sendPlaybackCommand` present.
- `Grep` — `SendPlaybackCommandUseCase` injected.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: app_v2/.../ui/settings/WearSyncViewModel.kt (watchPlaybackState, sendPlaybackCommand, SendPlaybackCommandUseCase injected). Dev log recorded.

---

### Step 5.13 — Add "Now Playing" card to `WearSyncSettingsFragment` with strings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt`, `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 5.12

**Prompt for developer:**

> Add strings to all three locales:
> - `wear_now_playing_title` — "Now Playing on Watch" / "Сейчас на часах" / "Зараз на годиннику"
> - `wear_playback_play_pause` — "Play / Pause" / "Воспр. / Пауза" / "Відтворити / Пауза"
> - `wear_playback_next` — "Next" / "Следующий" / "Наступний"
> - `wear_playback_previous` — "Previous" / "Предыдущий" / "Попередній"
>
> In `WearSyncScreen` composable: observe `watchPlaybackState`. When non-null, show a `Card` with:
> - `wear_now_playing_title` as card title.
> - File name and source name as body text.
> - A `LinearProgressIndicator` with `progress = positionMs / durationMs.toFloat()`.
> - Three `IconButton`s: Previous / Play-Pause / Next, each calling `viewModel.sendPlaybackCommand(...)`.
>
> When `watchPlaybackState` is null, show nothing (no empty placeholder card).

**Verification:**

- `Grep` — `wear_now_playing_title` string key present in all three locale `strings.xml` files.
- `Grep` — `sendPlaybackCommand` called in `WearSyncSettingsFragment.kt`.
- `Grep` — `LinearProgressIndicator` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: WearSyncSettingsFragment.kt (Now Playing card with LinearProgressIndicator and 3 IconButtons); strings.xml EN/RU/UK (wear_now_playing_title, wear_playback_*). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 05.* above is `[x] done`.
- [x] Project compiles — run `/build` for both modules.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_now_playing"` exits 0; same for `wear_playback`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Catalogs regenerated for both modules.

---

## Handoff Notes to Next Phase

- Watch publishes state on every play/pause toggle and track change.
- Phone receives and renders "Now Playing" with remote control buttons.
- Command bus `WatchPlaybackCommandEvents.commandFlow` is available for future commands.

---

## Rollback Plan

Revert phase commit(s). No schema change. "Now Playing" card disappears from `WearSyncSettingsFragment`. Players continue to work normally.
