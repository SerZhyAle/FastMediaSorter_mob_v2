# Phase 03 — Wire `STATE_ENDED` → markPlaybackCompleted

**Strategic spec:** [`../S0029_bugfix-resume-position-end-of-file.md`](../S0029_bugfix-resume-position-end-of-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (Step 03.3 still MANUAL-REQUIRED — on-device smoke check by user)
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 3
**Started:** 2026-04-29
**Completed:** 2026-04-29 (code-side; on-device manual check pending)

---

## Objective

Hook the new `markPlaybackCompleted` call into `VideoPlayerManager.playerListener`'s `STATE_ENDED` branch. Idempotent (one clear per `(file × completion)` regardless of how many times `STATE_ENDED` fires). Covers panel and VR — both use `VideoPlayerManager` (VR via `PlayerActivity` inheritance).

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`markPlaybackCompleted` available on `PlaybackPositionRepository`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 (current ~1300) |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerStateEndedTest.kt` | New | ≤ 130 |

> `VideoPlayerManager.kt` is large — confirm size with `wc -l` before edit; if total would exceed 1500 LOC, write a backup to `temp/` first.

---

## Steps

### Step 03.1 — Add idempotency field + emit completion in `STATE_ENDED`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> 1. Near the existing `currentFilePath` field declaration in `VideoPlayerManager`, add:
>    ```kotlin
>    @Volatile
>    private var lastCompletedPath: String? = null
>    ```
> 2. In `playerListener` (search for `Player.STATE_ENDED -> {`), inside that branch, BEFORE the existing `playerCallback.onPlaybackEnded()` call, add:
>    ```kotlin
>    val completedPath = currentFilePath
>    if (completedPath != null && completedPath != lastCompletedPath) {
>        lastCompletedPath = completedPath
>        managerScope.launch(Dispatchers.IO) {
>            try {
>                playbackPositionRepository.markPlaybackCompleted(
>                    completedPath,
>                    reason = "playback-completed"
>                )
>            } catch (e: Exception) {
>                Timber.e(e, "VideoPlayerManager: markPlaybackCompleted failed")
>            }
>        }
>    }
>    ```
> 3. In any function that loads a new media file (search for assignments `currentFilePath = `), reset `lastCompletedPath = null` immediately after — so a re-open of the same file re-arms the completion path. The reset MUST happen on every `currentFilePath` assignment to a non-null value.
> 4. Verify `managerScope` and `Dispatchers.IO` are already imported in this file. If not, add the imports.

**Verification:**

- `Grep` — `private var lastCompletedPath: String?` matches once.
- `Grep` — `markPlaybackCompleted(` matches at least once in `VideoPlayerManager.kt`.
- `Grep` — `reason = "playback-completed"` matches once.
- `Grep -n "currentFilePath\s*=\s*[^n]"` — every match (excluding `= null`) is followed within ≤ 5 lines by `lastCompletedPath = null`.
- `Grep -n "Log\.d\("` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 5/5 PASS (private var once, markPlaybackCompleted call once, reason once, currentFilePath assignment guarded, no Log.d). Build deferred. Files: VideoPlayerManager.kt (+22 LOC, total 911). Dev log recorded.

---

### Step 03.2 — Unit test: `STATE_ENDED` triggers exactly one clear

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerStateEndedTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Write a JUnit4 test that constructs a `VideoPlayerManager` with mocked dependencies (`mockk(relaxed = true)` for all constructor params; `PlaybackPositionRepository` is the one we assert on). Use `Robolectric` if a `Looper` / `Handler` is needed (mirror existing tests under `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/`).
>
> Test cases:
> 1. `STATE_ENDED triggers markPlaybackCompleted once`:
>    - Set `currentFilePath` to `"smb://server/short.mp4"` via reflection or via a helper that simulates `loadVideo()`.
>    - Invoke `playerListener.onPlaybackStateChanged(Player.STATE_ENDED)`.
>    - Advance the test dispatcher.
>    - Assert: `coVerify(exactly = 1) { repo.markPlaybackCompleted("smb://server/short.mp4", "playback-completed") }`.
> 2. `Repeated STATE_ENDED for same file calls clear once`:
>    - Same setup, fire `STATE_ENDED` twice.
>    - Assert: `coVerify(exactly = 1) { repo.markPlaybackCompleted(any(), "playback-completed") }`.
> 3. `STATE_ENDED with null currentFilePath is a no-op`:
>    - `currentFilePath` left `null`.
>    - Fire `STATE_ENDED`.
>    - Assert: `coVerify(exactly = 0) { repo.markPlaybackCompleted(any(), any()) }`.
>
> Use `kotlinx.coroutines.test.TestScope` / `runTest`. Inject the test dispatcher into `managerScope` if construction allows; otherwise mark this test `@Ignore` only as a last resort and log the gap in Blockers Log.
>
> The strategic-criterion test (30 s file, position 28 s, EVENT_ENDED → clearState) is implicitly satisfied: `STATE_ENDED` is the trigger, the position value is irrelevant inside this listener branch.

**Verification:**

- `Glob` — test file exists.
- `Grep` — `class VideoPlayerManagerStateEndedTest` matches once.
- `Grep` — `coVerify(exactly = 1)` matches at least once.
- `Grep` — `Player.STATE_ENDED` matches at least once (test trigger).
- All three tests pass — run via `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Static verification 4/4 PASS (file exists, class once, `coVerify(exactly = 1)` × 2, `Player.STATE_ENDED` × 6 — both ≥ 1). Build run deferred. Files: VideoPlayerManagerStateEndedTest.kt (+99 LOC). Dev log recorded.

---

### Step 03.3 — Manual smoke check on logcat (Quest 3 or panel)

**Files:** none (verification only).
**Depends on:** Step 03.2

**Prompt for developer:**

> Build and install onto a device (or capture logcat from the user's Quest 3 session per `dev/PROJECT_OPERATIONS_INDEX.md`). Open a short video file (≤ 30 s) and let it play to natural end. Confirm logcat contains exactly one line:
>
> ```text
> ResumeState: clearState reason=playback-completed uri=<path>
> ```
>
> If the user is unavailable for device testing, mark this step `[~] in progress` and add a Blockers Log entry — do NOT mark `[x]`. Set spec status to `BlockNeedUserTest` via `update.ps1`.

**Verification:**

- Logcat contains the expected line exactly once per file completion (visual / grep verification on captured log).
- No duplicate `ResumeState: clearState reason=playback-completed` for the same `uri` within a single file lifetime.

**Status:** `[~] in progress` — MANUAL-REQUIRED (on-device smoke check by user)

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build`.
- [ ] All new tests pass.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every modified file.
- [ ] If smoke check is blocked, journal status flipped to `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

After this phase, natural end-of-file (`STATE_ENDED`) clears the saved position. Phase 04 covers the manual-exit-near-end case where the user closes the player ≤ threshold before the natural end.

---

## Rollback Plan

Revert the `VideoPlayerManager.kt` edit and delete the test file. The repo method from Phase 02 stays — it is callable but unused; a follow-up commit can prune it if Phase 03 is permanently abandoned.
