# Phase 06 — unit-edge-cases

**Strategic spec:** [`../S0095_integration-test-review.md`](../S0095_integration-test-review.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phases 01–05
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Add boundary-condition test cases to three existing unit-test files covering: empty file list sorting, zero-progress completion detection, and null-source error propagation in the video player manager.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/test/java/com/sza/fastmediasorter/` source set compiles without errors.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/filelist/BrowseFileListManagerTest.kt` | Modified | ≤ 100 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetectorTest.kt` | Modified | ≤ 100 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerRouteErrorTest.kt` | Modified | ≤ 80 |

> All files are under 500 lines — no backup step required.

---

## Steps

### Step 6.1 — Add empty-list sort edge cases to BrowseFileListManagerTest

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/filelist/BrowseFileListManagerTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two `@Test` methods to `BrowseFileListManagerTest`: (a) `` `sortFiles_emptyList_returnsEmpty` `` — call `fileListManager.sortFiles(emptyList(), SortMode.NAME_ASC, forceSort = true, randomSeed = 0L)` and assert the result `isEmpty()`; (b) `` `sortFiles_emptyList_randomMode_returnsEmpty` `` — call the same with `SortMode.RANDOM` and assert `isEmpty()`. No new imports are required.

**Verification:**

- `Grep` — `` `sortFiles_emptyList_returnsEmpty` `` present in `BrowseFileListManagerTest.kt`.
- `Grep` — `` `sortFiles_emptyList_randomMode_returnsEmpty` `` present in `BrowseFileListManagerTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: BrowseFileListManagerTest.kt (modified). Dev log recorded.

---

### Step 6.2 — Add zero-duration edge cases to PlaybackCompletionDetectorTest

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetectorTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two `@Test` methods to `PlaybackCompletionDetectorTest`: (a) `` `zeroDuration_positionZero_isNotNearEnd` `` — assert `assertFalse(PlaybackCompletionDetector.isNearEnd(0L, 0L))`; (b) `` `exactlyAtThreshold_isNearEnd` `` — for a 10 s file compute `threshold = PlaybackCompletionDetector.nearEndThresholdMs(10_000L)` and assert `assertTrue(PlaybackCompletionDetector.isNearEnd(10_000L - threshold, 10_000L))`. These edge cases ensure the threshold boundary itself is classified correctly. No new imports required.

**Verification:**

- `Grep` — `` `zeroDuration_positionZero_isNotNearEnd` `` present in `PlaybackCompletionDetectorTest.kt`.
- `Grep` — `` `exactlyAtThreshold_isNearEnd` `` present in `PlaybackCompletionDetectorTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: PlaybackCompletionDetectorTest.kt (modified). Dev log recorded.

---

### Step 6.3 — Add null-route edge case to VideoPlayerManagerRouteErrorTest

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerRouteErrorTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add one `@Test` method to `VideoPlayerManagerRouteErrorTest`: `` `emptyPath_mapsToOther` `` — call `NetworkPlaybackContainerHint.fromPath("")` and assert the result is `NetworkPlaybackContainerHint.OTHER`. This guards against a NPE/crash when an empty path reaches the classifier. No new imports required.

**Verification:**

- `Grep` — `` `emptyPath_mapsToOther` `` present in `VideoPlayerManagerRouteErrorTest.kt`.
- `Grep` — `NetworkPlaybackContainerHint.OTHER` appears at least twice in `VideoPlayerManagerRouteErrorTest.kt` (existing test + new one).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: VideoPlayerManagerRouteErrorTest.kt (modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 6.*` above is `[x] done`.
- [ ] `./gradlew.bat testStandardDebugUnitTest` passes (or `/build` equivalent for unit tests).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Edge cases locked in for file-list sorting, completion detection, and route classification. Phase 07 (docs-catalog-cleanup) finalises the ticket.

---

## Rollback Plan

Revert phase commit(s) — test files only; no production code changed.
