# Phase 04 - Tests Validation

**Strategic spec:** [`../S0305_mid-audio-playback-support.md`](../S0305_mid-audio-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 - Fallback Error Flow
**Blocks:** Phase 05 - Docs Catalog Cleanup
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Add static and unit-test coverage for MIDI scope, dependency presence, and non-regression of existing audio classification.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or intentionally dirty with unrelated changes documented.
- [ ] No real user MIDI files are committed to the repository.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/MidiPlaybackPolicyTest.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/MediaExtensionsTest.kt` | Modified | ≤ 35 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/Media3MidiExtensionPresenceTest.kt` | New | ≤ 120 |

---

## Steps

### Step 04.1 - Test MIDI Playback Policy

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/MidiPlaybackPolicyTest.kt`  
**Depends on:** start of phase

**Prompt for developer:**

> Add unit tests for `MidiPlaybackPolicy`. Cover `.mid`, `.midi`, uppercase extensions, query strings, URI fragments, and negative cases for `.kar`, `.rmi`, `.xmf`, and `.mp3`.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/MidiPlaybackPolicyTest.kt` exists.
- `Grep` - `class MidiPlaybackPolicyTest` exists in `MidiPlaybackPolicyTest.kt`.
- `Grep` - `assertTrue.*MID` exists in `MidiPlaybackPolicyTest.kt`.
- `Grep` - `assertFalse.*kar` exists in `MidiPlaybackPolicyTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Added `MidiPlaybackPolicyTest` for supported `.mid`/`.midi`, uppercase, query/fragment paths, and out-of-scope relatives. Dev log recorded.

---

### Step 04.2 - Extend MediaExtensions Tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/MediaExtensionsTest.kt`  
**Depends on:** Step 04.1

**Prompt for developer:**

> Extend existing audio classification tests so `.mid` and `.midi` are both audio and `.kar` / `.rmi` are not audio in S0305 first-version scope. Keep existing assertions for MP3, FLAC, OGG, and MKA.

**Verification:**

- `Grep` - `Expected mid` exists in `MediaExtensionsTest.kt`.
- `Grep` - `Expected midi` exists in `MediaExtensionsTest.kt`.
- `Grep` - `Expected false for kar` exists in `MediaExtensionsTest.kt`.
- `Grep` - `Expected false for rmi` exists in `MediaExtensionsTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Extended `MediaExtensionsTest` so `.mid` and `.midi` are audio while `.kar` and `.rmi` stay out of S0305 first-version scope. Dev log recorded.

---

### Step 04.3 - Test Media3 MIDI Extension Presence

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/Media3MidiExtensionPresenceTest.kt`  
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a small unit test proving the MIDI extension classes are present on the test runtime classpath. Use `Class.forName()` for the Media3 MIDI extractor and renderer classes. If the exact package name differs from the researched README, adjust the class names to the actual 1.2.1 artifact while keeping the test intent unchanged.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/Media3MidiExtensionPresenceTest.kt` exists.
- `Grep` - `Class.forName` exists in `Media3MidiExtensionPresenceTest.kt`.
- `Grep` - `MidiExtractor` exists in `Media3MidiExtensionPresenceTest.kt`.
- `Grep` - `MidiRenderer` exists in `Media3MidiExtensionPresenceTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Researched the actual 1.2.1 artifact and added `Media3MidiExtensionPresenceTest` for `androidx.media3.decoder.midi.MidiExtractor` and `MidiRenderer`. Dev log recorded.

---

### Step 04.4 - Run Focused Validation

**Files:** no source file changes  
**Depends on:** Step 04.1, Step 04.2, Step 04.3

**Prompt for developer:**

> Run focused unit tests and the target build. Capture command, exit code, and expected vs actual result in this phase step log. Do not mark this step done on narration alone.

**Verification:**

- `Command` - S0305-focused `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.model.MidiPlaybackPolicyTest" --tests "com.sza.fastmediasorter.domain.model.MediaExtensionsTest" --tests "com.sza.fastmediasorter.ui.player.Media3MidiExtensionPresenceTest" --no-daemon --max-workers=1` exits 0.
- `Command` - `/build` target selected by build prompt exits 0.
- `Grep` - `UnrecognizedInputFormatException` returns zero matches in the validation log for MID/MIDI happy path if a device smoke log is available.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification BLOCKED. `./gradlew.bat testStandardDebugUnitTest` expected exit 0, actual exit 1: 2045 tests completed, 31 failed, 15 skipped, then Gradle reported test executor exit value 10. Failing classes are outside S0305 scope (`CanonicalPathNormalizerTest`, `GoogleDomainMatcherTest`, `GoogleDriveTokenRefreshTest`, `NetworkErrorMessageMapperTest`, `BaseFileOperationHandlerExtractFileNameTest`, `ProvisionDefaultResourcesUseCaseTest`, `MouseEventHandlerTest`, `SupportIntentFactoryTest`, `CollapsibleSectionHeaderTest`, `StereoVideoProcessorTest`). Focused S0305 command `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.model.MidiPlaybackPolicyTest" --tests "com.sza.fastmediasorter.domain.model.MediaExtensionsTest" --tests "com.sza.fastmediasorter.ui.player.Media3MidiExtensionPresenceTest" --no-daemon --max-workers=1` passed in 17s. `/build` target `./build-debug.PS1` passed in 38s and produced `FastMediaSorter_standard_debug_v2.60.5301.545-DEBUG.apk`. No device MID/MIDI smoke log was available, so `UnrecognizedInputFormatException` grep was not applicable.
- 2026-05-30 - Forced tactical refinement applied from owner `finish S0305` request: the full suite remains recorded as unrelated failure evidence, while S0305 closure uses focused MIDI policy/classification/classpath tests plus the target build. Verification 3/3 PASS: focused tests exit 0, build exit 0, device smoke log unavailable/not applicable.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` or `scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

MIDI scope and dependency presence are covered by unit tests. Build and test commands have recorded exit codes.

---

## Rollback Plan

Revert phase commit(s). Test files are isolated and no runtime behavior depends on them.