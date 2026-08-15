# Phase 02 - Detection configuration

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Make the stereo format detector honor the user-configurable source-trust flags and the ambiguity-behavior flag from Phase 01, without changing any individual detection algorithm.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Read `StereoDetector` cascade order before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerTracksObserver.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 700 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoDetectorConfigTest.kt` | New | ≤ 250 |

> Call-site wiring (VideoPlayerManager + observer + image loader) added to step 02.1 — the detect entry points need the config sourced from settings at the real call sites.

---

## Steps

### Step 02.1 - Pass detection config into the detector

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Introduce a small immutable config value (master enable, trust-filename, trust-metadata, trust-aspect-ratio, ambiguity-best-guess) read from `AppSettings` at detection time and pass it into the detect entry points. Do not store it as mutable state. Source the values from the settings repository at the call site that already loads `AppSettings`.

**Verification:**

- `Grep` - detect entry methods accept or read the config value.
- `Grep` - the four trust flags are referenced in `StereoDetector.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Added `StereoDetectionConfig` (ALL_ENABLED + from(settings)); config param on detectForVideo/detectForImage. Wired call sites: VideoPlayerManager collects config from settings (mirrors panelStereoSingleEye), observer + image loader pass it. Verification PASS.

---

### Step 02.2 - Gate each detection source by its flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> When master enable is false, short-circuit to the unknown/mono result before any source runs. Otherwise skip the filename branch when trust-filename is false, skip the metadata branches (MP4 spatial / Matroska / XMP) when trust-metadata is false, and skip the aspect-ratio heuristic when trust-aspect-ratio is false. When all enabled sources yield nothing, apply ambiguity behavior: best-guess path only when `stereoAmbiguityBestGuess` is true, otherwise return the unknown/2D result.

**Verification:**

- `Grep` - each detection branch is guarded by its corresponding flag.
- `Grep -n "Log\.d\("` in `StereoDetector.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Master-off short-circuits to MONO; each source gated (trustMetadata→mp4+matroska+xmp, trustFilename→filename, trustAspectRatio→AR). AR-off + nothing matched → UNKNOWN (feeds coordinator default); ambiguity best-guess → aggressiveDimensionGuess. ALL_ENABLED reproduces legacy. Note: pre-existing VR_AUDIT Timber.d probes retained (not Log.d). Verification PASS.

---

### Step 02.3 - Unit-test the gated cascade

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoDetectorConfigTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add unit tests: master-off → always unknown/mono; aspect-ratio-off → a panorama that previously tripped the heuristic now returns unknown while a filename-tagged file still detects; metadata-off and filename-on combination; ambiguity best-guess on vs off.

**Verification:**

- `Glob` - `StereoDetectorConfigTest.kt` exists.
- The new test class passes (run it in isolation).

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - 13 tests: master-off→MONO (video+image), AR-on control, AR-off→UNKNOWN, AR-off+filename still wins, filename-off→UNKNOWN, metadata on/off (photo-sphere), ambiguity on/off (video+image). `gradlew testStandardDebugUnitTest --tests StereoDetector*` → BUILD SUCCESSFUL (expected: all stereo tests pass | actual: pass). Bundle/Matroska path avoided (no Robolectric).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `:app_v2:testStandardDebugUnitTest --tests StereoDetector*` → BUILD SUCCESSFUL (compiles main + test).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (5 post-change PASS).

---

## Handoff Notes to Next Phase

Detection now respects user config. The "unknown" result is the trigger for Phase 03's global-default fallback.

---

## Rollback Plan

Revert phase commit(s) - detector falls back to always-on behavior; no schema or UI change.
