# Phase 03 - Fallback Error Flow

**Strategic spec:** [`../S0305_mid-audio-playback-support.md`](../S0305_mid-audio-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 - MIME Routing Policy
**Blocks:** Phase 04 - Tests Validation
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Make Media3 MIDI the primary path, preserve remote/cloud cache routing, and surface a specific error when MIDI playback fails.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or intentionally dirty with unrelated changes documented.
- [ ] Read all comments in files listed below before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/VideoPlayerManager_S0305_<timestamp>.kt` | New | backup |
| `temp/PlayerMediaLoaderManager_S0305_<timestamp>.kt` | New | backup |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 90 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 10 |

> `VideoPlayerManager.kt` and `PlayerMediaLoaderManager.kt` are both above 500 lines. Create timestamped backups in `temp/` before editing.

---

## Steps

### Step 03.1 - Back Up Large Playback Files

**Files:** `temp/VideoPlayerManager_S0305_<timestamp>.kt`, `temp/PlayerMediaLoaderManager_S0305_<timestamp>.kt`  
**Depends on:** start of phase

**Prompt for developer:**

> Create timestamped backups of `VideoPlayerManager.kt` and `PlayerMediaLoaderManager.kt` under `temp/` before modifying either file. Do not write backups to project root.

**Verification:**

- `Glob` - `temp/VideoPlayerManager_S0305_*.kt` exists.
- `Glob` - `temp/PlayerMediaLoaderManager_S0305_*.kt` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. Created `temp/VideoPlayerManager_S0305_20260530_152953.kt` and `temp/PlayerMediaLoaderManager_S0305_20260530_152953.kt`. Dev log recorded.

---

### Step 03.2 - Remove Eager MIDI MediaPlayer Route

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`  
**Depends on:** Step 03.1

**Prompt for developer:**

> Remove the pre-flight branch in `playVideo()` that sends local `.mid` and `.midi` directly to `playWithMediaPlayer()`. Let local MIDI follow the normal `ResourceType.LOCAL -> playLocalVideoInternal()` path so the Media3 MIDI extension is the primary backend. Keep the existing `MediaPlayer` fallback helper for non-MIDI format-error fallbacks.

**Verification:**

- `Grep` - `Using MediaPlayer.*MIDI` returns zero matches in `VideoPlayerManager.kt`.
- `Grep` - `path.endsWith(".mid"` returns zero matches in `VideoPlayerManager.kt`.
- `Grep` - `ResourceType.LOCAL -> playLocalVideoInternal(path, playWhenReady)` still exists in `VideoPlayerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Removed eager local MIDI `MediaPlayer` routing so `.mid` and `.midi` reach the normal local Media3 path. Dev log recorded.

---

### Step 03.3 - Pass MIDI MIME For Cached Remote And Cloud Audio

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`  
**Depends on:** Step 02.4, Step 03.1

**Prompt for developer:**

> When cloud or network audio is pre-cached and then started via `playAudioWithMetadata`, pass `MimeTypes.AUDIO_MIDI` if the original source path is `.mid` or `.midi`. Use `MidiPlaybackPolicy.isMidiPath(path)`. This is required because `UnifiedFileCache` cache names are hash-based and do not preserve the original extension.

**Verification:**

- `Grep` - `MidiPlaybackPolicy.isMidiPath(path)` exists in `PlayerMediaLoaderManager.kt`.
- `Grep` - `MimeTypes.AUDIO_MIDI` exists in `PlayerMediaLoaderManager.kt`.
- `Grep` - `playAudioWithMetadata(uri, title,` exists in `PlayerMediaLoaderManager.kt`.
- `Grep` - `playAudioWithMetadata(uri, netTitle,` exists in `PlayerMediaLoaderManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Cached cloud and network audio now pass `MimeTypes.AUDIO_MIDI` for original `.mid` and `.midi` source paths. Dev log recorded.

---

### Step 03.4 - Add MIDI-Specific Service Error Message

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`  
**Depends on:** Step 03.3

**Prompt for developer:**

> Add `midi_playback_failed` in EN/RU/UK and use it when `servicePlaybackListener.onPlayerError()` fails for a `.mid` or `.midi` current file. Check `docs/COMMUNICATION_POLICY.md` §2 and §6 before writing the string. Show the message before delegating to the existing skip/error callback. Do not change layout or controls.

**Verification:**

- `Grep` - `<string name="midi_playback_failed">` exists in all three string files.
- `Grep` - `R.string.midi_playback_failed` exists in `PlayerMediaLoaderManager.kt`.
- `Grep` - `Strings pass COMMUNICATION_POLICY §6 checklist` appears in the step log when implemented.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Added `midi_playback_failed` in EN/RU/UK and showed it before the existing service playback error callback for current MIDI files. Strings pass COMMUNICATION_POLICY §6 checklist: no raw exception primary text, human explanation plus one next step, EN/RU/UK parity confirmed by `check_strings_localized.ps1`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep` for `Log\.d\(` returns zero hits in modified Kotlin files.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` or `scripts/post-change.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Phase Log:**

- 2026-05-30 - Phase validation PASS. `TODO_PHASE_03=PASS`, `LOG_D_MODIFIED_FILES=PASS`, `BUILD_DEBUG_PHASE_03=PASS` via `.\build-debug.PS1` (`assembleStandardDebug`, build log `temp/build_debug_20260530_153522.log`). Dev log, string audit, and catalog sync completed.

---

## Handoff Notes to Next Phase

Local, cached remote, and cached cloud MIDI all reach Media3 with a MIDI-capable backend and MIME hints. The legacy `MediaPlayer` helper remains available only as fallback logic.

---

## Rollback Plan

Restore the timestamped backups for the two large Kotlin files or revert phase commit(s). Revert the three string additions if the Kotlin change is reverted.