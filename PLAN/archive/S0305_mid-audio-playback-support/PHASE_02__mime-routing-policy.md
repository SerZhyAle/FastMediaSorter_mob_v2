# Phase 02 - MIME Routing Policy

**Strategic spec:** [`../S0305_mid-audio-playback-support.md`](../S0305_mid-audio-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 - Media3 MIDI Backend
**Blocks:** Phase 03 - Fallback Error Flow
**Steps done:** 5 / 5
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Centralize `.mid` / `.midi` policy and propagate MIDI MIME hints through local, playlist, and cached remote/cloud service playback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or intentionally dirty with unrelated changes documented.
- [ ] Read all comments in files listed below before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MidiPlaybackPolicy.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaExtensions.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt` | Modified | ≤ 30 |

---

## Steps

### Step 02.1 - Add MIDI Playback Policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MidiPlaybackPolicy.kt`  
**Depends on:** start of phase

**Prompt for developer:**

> Add a pure domain policy object for first-version MIDI scope. It must expose `SUPPORTED_EXTENSIONS = setOf("mid", "midi")`, `isMidiExtension(extension: String)`, and `isMidiPath(path: String)`. `isMidiPath` must ignore query parameters and fragments before reading the extension.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MidiPlaybackPolicy.kt` exists.
- `Grep` - `object MidiPlaybackPolicy` exists in `MidiPlaybackPolicy.kt`.
- `Grep` - `setOf("mid", "midi")` exists in `MidiPlaybackPolicy.kt`.
- `Grep` - `kar|rmi|xmf|mxmf|rtttl|rtx|ota|imy` returns zero matches in `MidiPlaybackPolicy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Added pure `MidiPlaybackPolicy` for `.mid` and `.midi` only. Dev log recorded.

---

### Step 02.2 - Route MediaExtensions Through Policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaExtensions.kt`  
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace duplicated MIDI literals in the audio extension set with `MidiPlaybackPolicy.SUPPORTED_EXTENSIONS`. Keep every existing non-MIDI audio extension unchanged.

**Verification:**

- `Grep` - `MidiPlaybackPolicy.SUPPORTED_EXTENSIONS` exists in `MediaExtensions.kt`.
- `Grep` - `"mp3", "flac", "aac", "ogg", "m4a"` still exists in `MediaExtensions.kt`.
- `Grep` - `"kar"|"rmi"` returns zero matches in `MediaExtensions.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. `MediaExtensions.AUDIO` now includes `MidiPlaybackPolicy.SUPPORTED_EXTENSIONS`. Dev log recorded.

---

### Step 02.3 - Use Policy For Local MIME Detection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt`  
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the direct `"mid", "midi" -> MimeTypes.AUDIO_MIDI` branch with a policy-backed branch. Keep all existing MIME mappings unchanged.

**Verification:**

- `Grep` - `MidiPlaybackPolicy.isMidiExtension(extension)` exists in `LocalPlaybackHelper.kt`.
- `Grep` - `MimeTypes.AUDIO_MIDI` still exists in `LocalPlaybackHelper.kt`.
- `Grep` - `"mid", "midi" ->` returns zero matches in `LocalPlaybackHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Local MIME detection now uses `MidiPlaybackPolicy.isMidiExtension(extension)`. Dev log recorded.

---

### Step 02.4 - Add Optional MIME To Service Media Items

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt`  
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `mimeType: String? = null` to `MediaItemWithMeta`. Add optional `mimeType: String? = null` parameters to service playback methods that build `MediaItem`s. When `mimeType` is non-null, call `.setMimeType(mimeType)` on the `MediaItem.Builder`. Preserve existing method compatibility with default parameter values.

**Verification:**

- `Grep` - `val mimeType: String? = null` exists in `MediaItemWithMeta.kt`.
- `Grep` - `.setMimeType(mimeType)` exists in `AudioServiceController.kt`.
- `Grep` - `fun playAudioWithMetadata(` still exists in `AudioServiceController.kt`.
- `Grep` - `fun playAudioPlaylistWithMetadata(` still exists in `AudioServiceController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Service MediaItems now accept optional MIME hints. Dev log recorded.

---

### Step 02.5 - Pass MIME Hints From Playlist Builder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`  
**Depends on:** Step 02.4

**Prompt for developer:**

> Populate `MediaItemWithMeta.mimeType` for `.mid` and `.midi` files in `NowPlayingManager.startPlayback()`. Use `MidiPlaybackPolicy.isMidiPath(file.path)` and `MimeTypes.AUDIO_MIDI`; leave non-MIDI tracks with `null` MIME.

**Verification:**

- `Grep` - `MidiPlaybackPolicy.isMidiPath(file.path)` exists in `NowPlayingManager.kt`.
- `Grep` - `MimeTypes.AUDIO_MIDI` exists in `NowPlayingManager.kt`.
- `Grep` - `mimeType =` exists in `NowPlayingManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. `NowPlayingManager.startPlayback()` now passes MIDI MIME hints into playlist metadata. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` or `scripts/post-change.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Phase Log:**

- 2026-05-30 - Phase validation PASS. `TODO(phase-02)` grep returned zero hits. `:app_v2:assembleStandardDebug '-Pchaquopy.enabled=false' --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks --max-workers=1` exited 0 after Windows Gradle cache recovery. Dev log and catalog sync were recorded during steps.

---

## Handoff Notes to Next Phase

MIDI scope is centralized and service media items can carry MIME hints even when the cached file path has no `.mid` extension.

---

## Rollback Plan

Revert phase commit(s). No schema or persisted user data changes are introduced.