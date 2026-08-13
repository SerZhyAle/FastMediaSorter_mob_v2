# Phase 04 - Regression Coverage

**Strategic spec:** [`../S0260_nolegal-ytmusic-audio-share-recovery.md`](../S0260_nolegal-ytmusic-audio-share-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Lock down strategic §11 criteria #3 (YouTube Shorts regression remains green) and #4 (regular YouTube watch-URL regression remains green) with concrete tests. Add one test that exercises the Phase 02 guard against a synthetic non-audio result to prove the negative-criterion guard fires.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done (or `⏭️ Skipped` with the D-out-of-scope branch).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizerTest.kt` | New (or Modified if exists) | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/YtMusicAudioOnlyContractTest.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` | Modified | ≤ 400 |

---

## Steps

### Step 04.1 - Canonicalizer regression net

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizerTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> First check whether the file exists - if it does, extend it; if not, create it. Add the following test cases: (a) `music.youtube.com/watch?v=abc123` → canonical `www.youtube.com/watch?v=abc123` AND `audioOnly == true`. (b) `m.youtube.com/watch?v=abc123` → canonical `www.youtube.com/watch?v=abc123` AND `audioOnly == false`. (c) `youtube.com/shorts/abc123` → canonical `www.youtube.com/watch?v=abc123` AND `audioOnly == false`. (d) `www.youtube.com/shorts/abc123` → canonical `www.youtube.com/watch?v=abc123` AND `audioOnly == false`. (e) `www.youtube.com/watch?v=abc123` → URL unchanged AND `audioOnly == false`. (f) `https://example.com/foo` → URL unchanged AND `audioOnly == false`. (g) Garbage input `not-a-url` → returns the input unchanged AND `audioOnly == false`. These cases collectively prove that the canonicalizer satisfies strategic §11 criteria #3 and #4 (Shorts and regular watch both still produce non-audio-only canonical forms) and that the YTMusic branch is the only one flipping `audioOnly` to true.

**Verification:**

- `Grep -nE 'fun (test|`).+(youtube|YouTube)' LinkUrlCanonicalizerTest.kt` returns at least 5 test functions covering the cases above.
- `.\gradlew testStandardDebugUnitTest --tests "*LinkUrlCanonicalizerTest*"` exit code 0 with the XML report showing every test as `success`.

**Status:** `[ ]` not done

---

### Step 04.2 - `YtMusicAudioOnlyContract` test net

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/YtMusicAudioOnlyContractTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `YtMusicAudioOnlyContractTest`. Test cases: (a) non-YTMusic source + any MIME → `Accept`. (b) YTMusic source (canonicalAudioOnly=true) + `audio/mp4` → `Accept`. (c) YTMusic source + `audio/mpeg` + filename `track.mp3` → `Accept`. (d) YTMusic source + `video/mp4` + filename `clip.mp4` → `Reject("ytmusic_non_audio_artifact", fallbackAllowed = <Q3-defined>)`. (e) YTMusic source + `image/jpeg` + filename `thumb.jpg` → `Reject("ytmusic_thumbnail_artifact", fallbackAllowed = false)` (the thumbnail-artifact reject must always have `fallbackAllowed = false` regardless of Q3 - strategic §11.2 is non-negotiable). (f) YTMusic source + null MIME + filename `unknown.m4a` → `Accept` (extension fallback). (g) YTMusic source + null MIME + null filename → `Reject("ytmusic_non_audio_artifact", ...)`. (h) Original host `music.youtube.com` even when canonicalAudioOnly=false (defensive case) → guard still applies. The reason-code values in (d) and (e) must match the strings used in Phase 02 step 02.1 - they are searchable contract identifiers.

**Verification:**

- `Grep -nE 'fun (test|`).' YtMusicAudioOnlyContractTest.kt` returns at least 7 test functions covering the cases.
- `.\gradlew testStandardDebugUnitTest --tests "*YtMusicAudioOnlyContractTest*"` exit code 0.
- The thumbnail-artifact test asserts `fallbackAllowed == false` explicitly - this is the non-negotiable §11.2 guarantee.

**Status:** `[ ]` not done

---

### Step 04.3 - Coordinator integration: guard rejection flows through to `Result.Failed`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add two new tests to the existing `LinkAutoDownloadCoordinatorTest`. (a) `handle_youTubeMusic_savedJpeg_isRejectedByGuard()`: given `music.youtube.com/watch?v=abc`, mock the strategy chain to return a `Result.Saved` with MIME `image/jpeg` and filename `thumb.jpg`. Mock the guard's `validate` to return `Reject("ytmusic_thumbnail_artifact", fallbackAllowed = false)`. Assert: the final coordinator result is `Result.Failed.Other` whose exception message contains `ytmusic_thumbnail_artifact`. Assert: the saved temp file was deleted (use the existing file-system mock pattern from the test file). (b) `handle_youTubeMusic_savedAudio_passesThroughGuard()`: same setup but mocked result returns MIME `audio/mp4` and filename `track.m4a`; guard returns `Accept`; final coordinator result is the same `Result.Saved` passed in.

**Verification:**

- `Grep -n 'handle_youTubeMusic_savedJpeg_isRejectedByGuard' LinkAutoDownloadCoordinatorTest.kt` returns one hit.
- `Grep -n 'handle_youTubeMusic_savedAudio_passesThroughGuard' LinkAutoDownloadCoordinatorTest.kt` returns one hit.
- `.\gradlew testStandardDebugUnitTest --tests "*LinkAutoDownloadCoordinatorTest.handle_youTubeMusic_*"` exit code 0.
- Per `feedback_build_pre_existing_test_failures.md` - per-test target only; full suite carries ~26 pre-existing failures.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] All three test classes pass via per-class gradle targets.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new test classes appear).

---

## Handoff Notes to Next Phase

Phase 05 closes the spec: catalog regen, functionality log entry, removal of the `S0260:` Timber tags (driven by the spec status transition out of `BlockNeedUserTest`), and final `/spec-check`.

---

## Rollback Plan

Revert the Phase 04 commit. Tests are additive - removal restores the prior coverage gap but does not change runtime behavior.
