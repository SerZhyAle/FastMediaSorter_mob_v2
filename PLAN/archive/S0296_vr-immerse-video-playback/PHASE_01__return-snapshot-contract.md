# Phase 01 - Return Snapshot Contract

**Strategic spec:** [`../S0296_vr-immerse-video-playback.md`](../S0296_vr-immerse-video-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Extend the shared VR launch contract so VIDEO launch and return can carry play position, play state, playback speed and volume.

---

## Prerequisites

- [x] INDEX Pre-Implementation Blockers are checked.
- [x] Strategic spec status is `Tactical` or `In Progress`.
- [x] Working tree changes in `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/` and `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/` are understood before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt` | Modified | current 136, change <= 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrLaunchArgs.kt` | Modified | current 68, change <= 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt` | Modified | current 390, change <= 50 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/VrLaunchContractTest.kt` | New | <= 160 |

---

## Steps

### Step 01.1 - Add video fields to player snapshot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `videoIsPlaying: Boolean = false` and `videoVolume: Float = 1.0f` to `PlayerStateSnapshot`. Keep defaults compatible with existing serialized callers and leave `PlayerStateSnapshot.EMPTY` as the default constructor instance.

**Verification:**

- `Grep` - `val videoIsPlaying: Boolean = false` exists exactly once in `VrLaunchContract.kt`.
- `Grep` - `val videoVolume: Float = 1.0f` exists exactly once in `VrLaunchContract.kt`.
- `Grep` - `val EMPTY = PlayerStateSnapshot()` still exists exactly once in `VrLaunchContract.kt`.

**Status:** `[x]` done

---

### Step 01.2 - Carry snapshot in launch input

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrLaunchArgs.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `snapshot: PlayerStateSnapshot? = null` to `VrLaunchInput` and pass `request.snapshot` through `VrLaunchInput.fromRequest`. Add `val snapshot: PlayerStateSnapshot? get() = input.snapshot` to `DiagnosticXrLaunchArgs.Parsed` so the VR host can read the launch snapshot without re-reading intent extras.

**Verification:**

- `Grep` - `val snapshot: PlayerStateSnapshot? = null` exists exactly twice in `VrLaunchContract.kt` after this step: once on `StartVrPlaybackRequest`, once on `VrLaunchInput`.
- `Grep` - `snapshot = request.snapshot` exists exactly once in `VrLaunchContract.kt`.
- `Grep` - `val snapshot: PlayerStateSnapshot? get() = input.snapshot` exists exactly once in `DiagnosticXrLaunchArgs.kt`.
- `Grep` - `import com.sza.fastmediasorter.core.xr.PlayerStateSnapshot` exists exactly once in `DiagnosticXrLaunchArgs.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Capture video play state and volume before launch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Update `captureSnapshot(currentFile: MediaFile)` to populate `videoIsPlaying` from `videoPlayer?.isPlaying == true` and `videoVolume` from `videoPlayer?.volume ?: 1.0f`. Do not change VR badge placement, prompt rendering, media eligibility or finish behavior.

**Verification:**

- `Grep` - `videoIsPlaying = videoPlayer?.isPlaying == true` exists exactly once in `PlayerVrLaunchManager.kt`.
- `Grep` - `videoVolume = videoPlayer?.volume ?: 1.0f` exists exactly once in `PlayerVrLaunchManager.kt`.
- `Grep` - `VR_ENTRY_MEDIA_TYPES = setOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.GIF)` is unchanged.
- `Grep` - `Log.d(` returns zero hits in `PlayerVrLaunchManager.kt`.

**Status:** `[x]` done

---

### Step 01.4 - Add contract serialization test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/VrLaunchContractTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `VrLaunchContractTest` with a JVM unit test named `fromRequest_preserves_video_snapshot`. The test must build a `StartVrPlaybackRequest` with a `PlayerStateSnapshot(videoPositionMs = 1234L, videoPlaybackSpeed = 1.25f, videoIsPlaying = true, videoVolume = 0.5f)`, call `VrLaunchInput.fromRequest`, and assert that `input.snapshot` preserves those four fields.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/VrLaunchContractTest.kt` exists.
- `Grep` - `fun fromRequest_preserves_video_snapshot()` exists exactly once in `VrLaunchContractTest.kt`.
- `Grep` - `assertEquals(1234L, input.snapshot?.videoPositionMs)` exists exactly once in `VrLaunchContractTest.kt`.
- `Grep` - `assertTrue(input.snapshot?.videoIsPlaying == true)` exists exactly once in `VrLaunchContractTest.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` completed after Kotlin changes.
- [x] JVM unit test for `VrLaunchContractTest` passes through the project build/test wrapper selected by `/build`.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

The transport model can now carry the flat-player launch snapshot into the VR host.

---

## Rollback Plan

Revert phase commit(s). No schema, resource or native ABI changes are introduced in this phase.
