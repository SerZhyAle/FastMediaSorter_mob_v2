# Phase 01 - Identity Contract Propagation

**Strategic spec:** [`../S0265_bugfix-audio-cover-lyrics-track-race.md`](../S0265_bugfix-audio-cover-lyrics-track-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Extend the audio-metadata callback contract to carry the originating `MediaFile.path` from `AudioCoverArtLoader` through `ImageLoadingManager` and `PlayerImageLoadingCallbackImpl` to `PlayerActivity.onAudioMetadataLoaded`. No semantic change yet - the new parameter is wired and propagated but not yet inspected by callers. The build must stay green.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] §6 research §6.1 confirmed: identity carrier is `MediaFile.path` (string).
- [ ] `AudioCoverArtLoader.kt` current LOC = 479 (will exceed 500 after this phase) - **backup required** in Step 01.1.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` | Modified | ≤ 520 (was 479; backup required - crosses 500 threshold) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Modified | ≤ 1280 (was 1274) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt` | Modified | ≤ 90 (was 86) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1720 (was 1715; **pre-existing >1500 LOC tech debt - minimal surgical touch, +2 LOC; splitting PlayerActivity is out of scope of this bugfix**) |

> No new files. No new classes. No new Hilt module. No new Room migration.

---

## Steps

### Step 01.1 - Backup AudioCoverArtLoader.kt

**Files:** `temp/S0265_AudioCoverArtLoader.kt.<timestamp>.bak`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a timestamped backup of `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` under `temp/` before any edits. This file is at 479 LOC and will cross the 500 LOC threshold after this phase. Use `Copy-Item` with `Get-Date -Format 'yyyyMMdd_HHmmss'` in the suffix. Do not edit the original file in this step.

**Verification:**

- `Glob` - `temp/S0265_AudioCoverArtLoader.kt.*.bak` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification PASS. Backup: `temp/S0265_AudioCoverArtLoader.kt.20260520_120106.bak` (29704 bytes).

---

### Step 01.2 - Extend `AudioCoverArtLoader.Callback.onAudioMetadataLoaded` signature

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Change the nested interface declaration so the callback receives the originating path:
>
> ```kotlin
> interface Callback {
>     fun onAudioMetadataLoaded(metadata: AudioMetadata, originatingPath: String)
> }
> ```
>
> Then update **every** call site inside `AudioCoverArtLoader.kt` that invokes `callback.onAudioMetadataLoaded(...)` to pass `file.path` as the second argument. There are four call sites (cache-hit branch and online-fresh branch in both `loadAudioCoverArt` and `searchOnlineAndDisplayCover`). The `file` variable is in scope at every call site - it is the `MediaFile` parameter of the enclosing method. Do not change any other behaviour in this step.

**Verification:**

- `Grep` - `fun onAudioMetadataLoaded\(metadata: AudioMetadata, originatingPath: String\)` matches once (interface declaration).
- `Grep` - `callback\.onAudioMetadataLoaded\(` returns 4 matches inside `AudioCoverArtLoader.kt`.
- `Grep` - `callback\.onAudioMetadataLoaded\(.+,\s*file\.path\)` returns 4 matches inside `AudioCoverArtLoader.kt`.
- `Grep` - `callback\.onAudioMetadataLoaded\([^,]+\)` (single-arg form) returns 0 matches inside `AudioCoverArtLoader.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: AudioCoverArtLoader.kt (interface + 4 callsites at lines 54, 224, 250, 335, 355).

---

### Step 01.3 - Update `ImageLoadingManager.ImageLoadingCallback.onAudioMetadataLoaded` signature

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Change the abstract method declaration inside `interface ImageLoadingCallback` (around line 84) to:
>
> ```kotlin
> fun onAudioMetadataLoaded(metadata: com.sza.fastmediasorter.domain.model.AudioMetadata, originatingPath: String)
> ```
>
> Then update the anonymous `AudioCoverArtLoader.Callback` object created inside `audioCoverArtLoader` lazy initializer (around line 165) to override the new two-arg signature and forward both arguments:
>
> ```kotlin
> callback = object : AudioCoverArtLoader.Callback {
>     override fun onAudioMetadataLoaded(
>         metadata: com.sza.fastmediasorter.domain.model.AudioMetadata,
>         originatingPath: String
>     ) = callback.onAudioMetadataLoaded(metadata, originatingPath)
> }
> ```

**Verification:**

- `Grep` - `fun onAudioMetadataLoaded\(metadata:.+AudioMetadata, originatingPath: String\)` returns at least 2 matches inside `ImageLoadingManager.kt` (interface declaration + override).
- `Grep` - `callback\.onAudioMetadataLoaded\(metadata, originatingPath\)` returns 1 match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: ImageLoadingManager.kt (interface line 84 + override lines 166-170). Multi-line override required `[\s\S]` regex to detect both matches.

---

### Step 01.4 - Update `PlayerImageLoadingCallbackImpl` override

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Change the existing `override fun onAudioMetadataLoaded(metadata: AudioMetadata)` (around line 46) to match the new interface:
>
> ```kotlin
> override fun onAudioMetadataLoaded(metadata: AudioMetadata, originatingPath: String) {
>     activity.onAudioMetadataLoaded(metadata, originatingPath)
> }
> ```
>
> Do not add any logging in this step. Do not add any guard logic in this step - that lives in Phase 02.

**Verification:**

- `Grep` - `override fun onAudioMetadataLoaded\(metadata: AudioMetadata, originatingPath: String\)` returns 1 match.
- `Grep` - `activity\.onAudioMetadataLoaded\(metadata, originatingPath\)` returns 1 match.
- `Grep` - `override fun onAudioMetadataLoaded\(metadata: AudioMetadata\)` (single-arg) returns 0 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: PlayerImageLoadingCallbackImpl.kt.

---

### Step 01.5 - Update `PlayerActivity.onAudioMetadataLoaded` signature

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Change the existing `fun onAudioMetadataLoaded(metadata: AudioMetadata)` (around line 1567) to accept the second parameter, but **keep the current behaviour identical** in this phase - the parameter is unused for now. Phase 02 adds the actual identity check.
>
> ```kotlin
> /** Called by ImageLoadingManager when audio metadata is loaded from the online source. */
> fun onAudioMetadataLoaded(
>     metadata: com.sza.fastmediasorter.domain.model.AudioMetadata,
>     originatingPath: String
> ) = audioMetadataManager.onMetadataLoaded(metadata, viewModel.state.value.currentFile)
> ```
>
> Do not modify `audioMetadataManager.onMetadataLoaded` itself. Do not touch `searchAndShowLyrics` in this phase.

**Verification:**

- `Grep` - `fun onAudioMetadataLoaded\([^)]*originatingPath: String[^)]*\)` returns 1 match inside `PlayerActivity.kt`.
- `Grep` - `fun onAudioMetadataLoaded\(metadata:[^,)]+\)\s*=` (single-arg form) returns 0 matches inside `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: PlayerActivity.kt (signature only - body uses currentFile lookup unchanged; guard added in Phase 02).

---

### Step 01.6 - Verify project builds

**Files:** none (build only)
**Depends on:** Step 01.5

**Prompt for developer:**

> Trigger a debug build of the `standard` flavor via `/build` skill (do not invoke `gradlew` directly). All four files above must compile together; signature change must propagate cleanly. If compilation fails, fix the missing match - do not introduce semantic changes.

**Verification:**

- Build exits with code 0.
- `Grep` - `Timber\.d\("S0265:` returns 0 hits across `app_v2/src/main/java/` (the BlockNeedUserTest probes belong to the final transition, not to Phase 01).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Build SUCCESSFUL (assembleStandardDebug, 38s, exit 0). APK: `FastMediaSorter_standard_debug_v2.60.5201.203-DEBUG.apk`. No new compile errors. Pre-existing `open` warnings on PlayerActivity remain (not introduced by S0265).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 05 (public Callback signature changed - catalog must reflect after all phases).

---

## Handoff Notes to Next Phase

- Two-argument callback contract is established end-to-end. `PlayerActivity.onAudioMetadataLoaded` receives `originatingPath` but currently ignores it.
- Phase 02 introduces the actual identity check at the application point.
- `AudioCoverArtLoader` now carries `file.path` to every callback invocation - this is the only identity carrier used in subsequent phases (per §6.1 resolution).

---

## Rollback Plan

Revert the phase commit. No data migration, no Hilt rewiring, no Room schema change. The single source of risk is build break across the four files - if reverted, all four return to single-arg signature.
