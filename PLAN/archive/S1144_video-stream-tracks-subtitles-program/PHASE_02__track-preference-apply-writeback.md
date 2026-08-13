# Phase 02 - Apply channel track preference at start + write-back on manual selection

**Strategic spec:** [`../S1144_video-stream-tracks-subtitles-program.md`](../S1144_video-stream-tracks-subtitles-program.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-23
**Completed:** 2026-07-27

---

## Objective

On stream start, overlay the channel's stored track languages on top of the global default via the existing selector parameters (not a whole-builder replace, so S1128 step-down is preserved); on a manual audio/subtitle pick in the playback dialog, persist the chosen language back to the channel by URL.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`updateTrackPreferences`, entity columns).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/StreamTrackPreferenceUseCase.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoTrackSelectionManager.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | ≤ 500 |

> If the write-back needs a coroutine scope the dialog/manager already holds, reuse it; do not introduce `GlobalScope` (Rule 19).

---

## Steps

### Step 02.1 - `StreamTrackPreferenceUseCase` (read + write channel preference)

**Files:** `domain/usecase/streams/StreamTrackPreferenceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class StreamTrackPreferenceUseCase @Inject constructor(private val streamSourceDao: StreamSourceDao)`. Expose `suspend fun read(url: String): TrackPreference?` (maps `getByUrl(url)` -> `TrackPreference(audioLang, subtitleLang, subtitlesEnabled)` when any field non-null, else null) and `suspend fun writeAudio(url: String, lang: String?)` / `writeSubtitle(url: String, lang: String?, enabled: Boolean?)` that call `updateTrackPreferences` preserving the other fields (read-modify-write within a single call). Declare a small `data class TrackPreference(val audioLang: String?, val subtitleLang: String?, val subtitlesEnabled: Boolean?)`. `@Inject constructor` only - no new Hilt module.

**Verification:**

- `Grep` - `class StreamTrackPreferenceUseCase @Inject constructor` present.
- `Grep` - `fun read(`, `fun writeAudio(`, `fun writeSubtitle(` present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 2/2 PASS. Files: StreamTrackPreferenceUseCase.kt (new, 61 LOC). `@Inject constructor(StreamSourceDao)`; read-modify-write preserving other fields. Note: injects the DAO per the explicit prompt (sibling stream use cases inject StreamSourceRepository, but `updateTrackPreferences` is a DAO method and the prompt names the DAO).

---

### Step 02.2 - Apply the channel preference over the global default at start

**Files:** `ui/player/VideoTrackSelectionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Respecified 2026-07-27 per strategic ADR-6/ADR-7 (all names below are confirmed to exist).
>
> 1. Give `VideoTrackSelectionManager` a mutable property `var channelPreference: StreamTrackPreferenceUseCase.TrackPreference? = null`. In `applyTrackSelection(...)` resolve the audio language as `channelPreference?.audioLang ?: <existing global-default mapping>`, the subtitle language the same way, and the subtitles on/off as `channelPreference?.subtitlesEnabled ?: settings.showSubtitles`. Keep building on `player.trackSelectionParameters.buildUpon()` - the API already used here (ADR-7). Do NOT use `selector.buildUponParameters()`: that is S1128's step-down call in `StreamPlaybackHelper` and both writers share the same `DefaultTrackSelector`, so composition holds only while each keeps using its own `buildUpon`. Holding the preference as a property avoids threading a new parameter through `VideoPlaybackControlsHelper.applyPlayerSettings` and `PlayerSettingsManager`, which call the apply path from several places.
> 2. Thread the use case in via ADR-6: add `streamTrackPreferenceUseCase: StreamTrackPreferenceUseCase` to `VideoPlayerStoreDependencies`; add `@Inject lateinit var streamTrackPreferenceUseCase: StreamTrackPreferenceUseCase` to `PlayerActivity`; pass `activity.streamTrackPreferenceUseCase` in `PlayerViewerFactory.createVideoPlayerManager()`; expose it on `VideoPlayerManager` as `internal val streamTrackPreferenceUseCase = storeDependencies.streamTrackPreferenceUseCase`, mirroring the existing `settingsRepository` line.
> 3. In `StreamPlaybackHelper.playStreamVideo` (already a `suspend` extension, so no new scope) read `streamTrackPreferenceUseCase.read(path)` and assign it to the manager's `trackSelectionManager.channelPreference` before `player.prepare()`. Make `trackSelectionManager` `internal` so the extension can reach it.
> 4. In `VideoPlayerManager.playVideo` clear `trackSelectionManager.channelPreference = null` at the same place S1158 resets the programme name - every new file and channel passes through there, so a stale channel preference cannot leak into the next item.
>
> The global default this overlays is today the session `PlayerSettings`; Phase 04 later makes it persistent. That makes this step independent of Phase 04, contrary to the 2026-07-23 deferral note.

**Verification:**

- `Grep` - `player.trackSelectionParameters.buildUpon(` still present in `VideoTrackSelectionManager.kt` (the apply path was not switched to the selector API).
- `Grep` - `channelPreference` referenced in `VideoTrackSelectionManager.kt` and assigned in `StreamPlaybackHelper.kt`.
- `Grep` - `streamTrackPreferenceUseCase` present in `VideoPlayerDependencies.kt`, `PlayerActivity.kt` and `PlayerViewerFactory.kt`.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - DEFERRED. The prompt reads the channel preference "on the stream-start path via `StreamTrackPreferenceUseCase.read(url)`", but the use case is a Hilt `@Inject` type while `VideoPlayerManager` is built from plain dependency bundles (`VideoPlayerHostDependencies`/`NetworkDependencies`/`StoreDependencies`) assembled in `PlayerViewerFactory`, not `@Inject`. Threading the use case in requires a named bundle addition + provider wiring the plan does not specify. Additionally the global-default read the override sits on top of depends on Phase 04's new `AppSettings` keys (not yet created), and the `buildUponParameters(` predicate targets a `DefaultTrackSelector`, whereas `VideoTrackSelectionManager.applyTrackSelection` operates on `player.trackSelectionParameters.buildUpon()` (player-level params). Needs `/spec-update` to name the DI path and reconcile the selector vs player-params apply.

---

### Step 02.3 - Write back the manual pick to the channel

**Files:** `ui/player/PlaybackControlDialogFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Respecified 2026-07-27 per strategic ADR-8/ADR-9.
>
> 1. The selected track's language now comes off `TrackInfo.language`, added in step 02.2 to BOTH `TrackInfo` declarations and both mappers. No second walk of `player.currentTracks`.
> 2. Resolve the use case with `EntryPointAccessors.fromApplication(.., StreamTrackPreferenceEntryPoint::class.java)` - a new `@EntryPoint` beside `MediaCapabilitiesEntryPoint`, which this same fragment already resolves that way. Do not make the fragment an `@AndroidEntryPoint`.
> 3. Channel URL: `host().currentMediaFile.value?.path`. Guard with the already-snapshotted `sourceIsStream`; a local file or an unknown URL is a silent no-op, never an error - remembering is a convenience, not a precondition for the pick.
> 4. Write on all three pick sites: audio row -> `writeAudio(url, track.language)`; subtitle row -> `writeSubtitle(url, track.language, true)`; the subtitles-OFF row -> `writeSubtitle(url, null, false)`. Run them on `viewLifecycleOwner.lifecycleScope`.

**Verification:**

- `Grep` - `writeAudio(` and `writeSubtitle(` called in `PlaybackControlDialogFragment.kt`.
- `Grep` - `StreamTrackPreferenceEntryPoint` exists in `di/` and is referenced by the fragment.
- `Grep` - no `@AndroidEntryPoint` added to `PlaybackControlDialogFragment.kt`.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - DEFERRED. The prompt says "resolve the selected track's language" for the write-back, but the track model exposed to the dialog (`VideoPlayerManager.TrackInfo` / `VideoTrackSelectionManager.TrackInfo`) carries only `groupIndex`/`trackIndex`/`label`/`isSelected` - no language code. Resolving the language requires either extending `TrackInfo` with a `language` field across the manager + every `VideoPlayerHandle` mapper (`PhotoVideoStandaloneVideoHandle`, `PlayerActivityVideoHandle`, `PlayerDialogHelper`, `StandalonePlayerSettingsManager`) or re-reading `player.currentTracks` in the fragment - neither is specified. Also `PlaybackControlDialogFragment` is not `@AndroidEntryPoint` (uses `EntryPointAccessors`), and the current-channel URL source (`host()` accessor) for the write key is unspecified. Needs `/spec-update` to define the track-language resolution + the URL accessor + how the use case reaches the fragment.

---

### Step 02.4 - Unit-test the preference read/write mapping (criterion 6)

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/StreamTrackPreferenceUseCaseTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> With a fake/mock `StreamSourceDao`, test: `read()` returns null when all three columns are null; returns a populated `TrackPreference` when any is set; `writeAudio`/`writeSubtitle` call `updateTrackPreferences` with the other fields preserved (read-modify-write). JUnit4; no Robolectric (pure mapping).

**Verification:**

- `Glob` - `StreamTrackPreferenceUseCaseTest.kt` exists.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*StreamTrackPreferenceUseCaseTest"` passes.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 2/2 PASS. testStandardDebugUnitTest BUILD SUCCESSFUL in 1m 9s; JUnit XML: tests=5 skipped=0 failures=0 errors=0. mockk fake DAO, no Robolectric.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` -> `BUILD SUCCESSFUL` (2026-07-27, after 02.2 and again after 02.3).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry per file.
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 05 (new `StreamTrackPreferenceUseCase`).
- [x] Phase-boundary audit - see below.

**Phase-boundary audit (2026-07-27):**

- **S1128 composition (P1 risk in §7) - clear.** The apply path still builds on `player.trackSelectionParameters.buildUpon()`; the step-down still uses `selector.buildUponParameters()`. Neither constructs a fresh `Parameters.Builder`, so the language overlay and the quality cap accumulate on the same `DefaultTrackSelector` instead of clobbering each other.
- **Main-thread I/O - clear.** The read runs inside the already-suspending `playStreamVideo`; the three write-backs run on `viewLifecycleOwner.lifecycleScope`, and the use case's DAO calls are `suspend`. No blocking call was added to a UI callback.
- **Leaks - clear.** `channelPreference` is a plain nullable data holder on a manager that already lives for the player's lifetime, and it is cleared in `playVideo` on every new item. No listener, no context capture, so nothing to unregister.
- **P2 noted:** `TrackInfo` remains duplicated across `VideoTrackSelectionManager` and `VideoPlayerManager`. This phase kept them in sync by hand (both gained `language`). Collapsing them into one type is a separate cleanup, not this ticket's scope - the duplication predates it.

---

## Handoff Notes to Next Phase

Track preferences now persist and re-apply per channel. Phase 04 exposes the same preference (global default + per-channel edit) in settings surfaces.

---

## Rollback Plan

Revert the three files - preference read/write becomes inert; global default behaviour is unchanged.
