# Phase 02 — Repository: markPlaybackCompleted with reason

**Strategic spec:** [`../S0029_bugfix-resume-position-end-of-file.md`](../S0029_bugfix-resume-position-end-of-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Add a new method `markPlaybackCompleted(filePath, reason)` to `PlaybackPositionRepository` (interface + impl) that deletes the saved position and emits a tagged log line `ResumeState: clearState reason=<reason> uri=<filePath>`. No call sites added in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt` | Modified | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImpl.kt` | Modified | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImplMarkCompletedTest.kt` | New | ≤ 90 |

---

## Steps

### Step 02.1 — Extend domain interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new abstract method to the interface:
>
> ```kotlin
> /**
>  * Delete saved position because playback reached the end (natural STATE_ENDED
>  * or explicit user exit inside the near-end zone). Emits a reason-tagged log
>  * marker so the playback-completed clear path is distinguishable in logs from
>  * user-exited / resource-changed clear paths.
>  *
>  * @param reason short tag (e.g. "playback-completed") logged for observability.
>  */
> suspend fun markPlaybackCompleted(filePath: String, reason: String)
> ```
>
> Place it directly after `deletePosition`. Do not modify any other signature.

**Verification:**

- `Grep` — `suspend fun markPlaybackCompleted(filePath: String, reason: String)` matches once.
- File still compiles — `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 1/1 PASS (interface signature matches once). Build deferred. Files: PlaybackPositionRepository.kt (+10 LOC). Dev log recorded.

---

### Step 02.2 — Implement in `PlaybackPositionRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the override directly after `markAsCompleted`:
>
> ```kotlin
> override suspend fun markPlaybackCompleted(filePath: String, reason: String) {
>     try {
>         dao.deletePosition(filePath)
>         Timber.d("ResumeState: clearState reason=%s uri=%s", reason, filePath)
>     } catch (e: Exception) {
>         Timber.e(e, "ResumeState: clearState FAILED reason=%s uri=%s", reason, filePath)
>     }
> }
> ```
>
> Use the existing `dao` field. Do not touch `markAsCompleted` (kept for API compatibility / external callers). The log prefix `ResumeState:` is intentional — it is the marker grep'd from logs in strategic §11 criterion 4.

**Verification:**

- `Grep` — `override suspend fun markPlaybackCompleted(filePath: String, reason: String)` matches once.
- `Grep` — `Timber.d("ResumeState: clearState reason=%s uri=%s"` matches once in the file.
- `Grep -n "Log\.d\("` returns zero hits in this file.
- `/build` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS (override once, log line once, no Log.d). Files: PlaybackPositionRepositoryImpl.kt (+9 LOC). Dev log recorded.

---

### Step 02.3 — Unit test the new method

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImplMarkCompletedTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create JUnit4 test using a `mockk` `PlaybackPositionDao`:
> - `markPlaybackCompleted("smb://x/file.mp4", "playback-completed")` invokes `dao.deletePosition("smb://x/file.mp4")` exactly once.
> - When `dao.deletePosition` throws, the method swallows the exception (no rethrow). Assert `coVerify { dao.deletePosition(any()) }` was attempted.
>
> Use `runTest` from `kotlinx.coroutines.test` and `coEvery` / `coVerify` from `io.mockk`. Mirror the style of any existing repository test under `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/`.

**Verification:**

- `Glob` — test file exists.
- `Grep` — `class PlaybackPositionRepositoryImplMarkCompletedTest` matches once.
- `Grep` — at least 2 `@Test` methods.
- Test passes — run `./gradlew :app_v2:testStandardDebugUnitTest --tests "*.PlaybackPositionRepositoryImplMarkCompletedTest"` (via `/build`).

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Static verification 3/3 PASS (file exists, class once, 2 @Test). Build deferred. Files: PlaybackPositionRepositoryImplMarkCompletedTest.kt (+42 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build`.
- [ ] New tests pass.
- [ ] No `Log.d(` introduced — `Grep` on touched files returns zero hits.
- [ ] Dev log entries added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`markPlaybackCompleted(path, "playback-completed")` is the single API call subsequent phases use to record the completion event. The reason string is a free-form tag — Phase 03 uses `"playback-completed"`, Phase 04 uses `"playback-completed-near-end"`.

---

## Rollback Plan

Revert the two `.kt` source edits and delete the new test file. No data migration; no DI changes.
