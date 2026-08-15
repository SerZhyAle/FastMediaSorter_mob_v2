# Phase 03 — player-instrumentation-tests

**Strategic spec:** [`../S0095_integration-test-review.md`](../S0095_integration-test-review.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Add two instrumentation test classes covering player file-move and orientation-change flows; both run entirely in the app's cache directory without real media files.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] S0094 (player-move-currently-playing) is at least `In Progress` — the `FileOperationsHandler` move API must exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/PlayerFileOperationsInstrumentationTest.kt` | New | ≤ 200 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/PlayerOrientationInstrumentationTest.kt` | New | ≤ 160 |

---

## Steps

### Step 3.1 — Add PlayerFileOperationsInstrumentationTest

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/PlayerFileOperationsInstrumentationTest.kt`
**Depends on:** Phase 02 complete

**Prompt for developer:**

> Create `PlayerFileOperationsInstrumentationTest` in package `com.sza.fastmediasorter.ui.player` (androidTest). Use `ApplicationProvider.getApplicationContext()` to get a `Context`. In `@Before`, create a source temp file via `TestFixtures.createTempFile(context.cacheDir, "player_test_source.mp4")` and a destination directory `File(context.cacheDir, "player_test_dest")`. Write two `@Test` methods: (a) `moveFile_succeeds_fileAppearsInDest` — call `sourceFile.copyTo(File(destDir, sourceFile.name))` then `sourceFile.delete()`; assert destination exists and source is gone; (b) `moveFile_toSameDirectory_isNoOp` — attempt to copy the file to the same path; assert the file is unchanged. Clean up in `@After`. Do **not** launch `PlayerActivity` — this test validates the file-system invariant only, not UI.

**Verification:**

- `Glob` — `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/PlayerFileOperationsInstrumentationTest.kt` exists.
- `Grep` — `class PlayerFileOperationsInstrumentationTest` present exactly once.
- `Grep` — `moveFile_succeeds_fileAppearsInDest` present.
- `Grep` — `TestFixtures.createTempFile` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: PlayerFileOperationsInstrumentationTest.kt (new, 54 LOC). Dev log recorded.

---

### Step 3.2 — Add PlayerOrientationInstrumentationTest

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/PlayerOrientationInstrumentationTest.kt`
**Depends on:** Phase 02 complete

**Prompt for developer:**

> Create `PlayerOrientationInstrumentationTest` in package `com.sza.fastmediasorter.ui.player` (androidTest). Use `ActivityScenario.launch<PlayerActivity>` with an Intent that includes `Intent.FLAG_ACTIVITY_NEW_TASK`; use `ActivityScenario.recreate()` to simulate a configuration change. Add a `@Test` method `recreate_doesNotCrash` that: launches `PlayerActivity` with no extras (the Activity should show an empty/error state, not crash), calls `scenario.recreate()`, then checks `scenario.state == Lifecycle.State.RESUMED`. Annotate the test with `@LargeTest`. Wrap the entire launch in a try-finally that calls `scenario.close()`. Do not assert playback position — asserting non-crash and resumed state is sufficient for this phase.

**Verification:**

- `Glob` — `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/PlayerOrientationInstrumentationTest.kt` exists.
- `Grep` — `class PlayerOrientationInstrumentationTest` present exactly once.
- `Grep` — `scenario.recreate()` present.
- `Grep` — `@LargeTest` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: PlayerOrientationInstrumentationTest.kt (new, 32 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Player-area instrumentation tests established. Phase 04 adds settings-area tests following the same structural pattern (AndroidTest + `TestFixtures` imports).

---

## Rollback Plan

Revert phase commit(s) — new test files only; no production code or migration changed.
