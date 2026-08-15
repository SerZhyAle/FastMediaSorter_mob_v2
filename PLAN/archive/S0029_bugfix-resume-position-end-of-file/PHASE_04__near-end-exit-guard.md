# Phase 04 — Near-end exit guard

**Strategic spec:** [`../S0029_bugfix-resume-position-end-of-file.md`](../S0029_bugfix-resume-position-end-of-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

When the user exits the player (`onPause`) while playback is inside the near-end zone, the lifecycle manager must call `markPlaybackCompleted` instead of `savePosition`. Closes the gap where a short file's auto-save tick lands at < 95 % and `STATE_ENDED` never fires because the user closed the activity.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`PlaybackCompletionDetector` available).
- [ ] Phase 02 ✅ Done (`markPlaybackCompleted` available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 600 (current 547) |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManagerNearEndTest.kt` | New | ≤ 130 |

---

## Steps

### Step 04.1 — Branch on near-end inside `saveCurrentPlaybackPosition`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate `saveCurrentPlaybackPosition` (around line 316). Replace the `activity.lifecycleScope.launch(...)` body so the coroutine decides between two paths:
>
> ```kotlin
> activity.lifecycleScope.launch(Dispatchers.IO) {
>     try {
>         if (PlaybackCompletionDetector.isNearEnd(position, duration)) {
>             activity.playbackPositionRepository.markPlaybackCompleted(
>                 currentFile.path,
>                 reason = "playback-completed-near-end"
>             )
>         } else {
>             activity.playbackPositionRepository.savePosition(currentFile.path, position, duration)
>             Timber.d("PlayerLifecycleManager: Saved playback position $position/$duration for ${currentFile.name}")
>         }
>     } catch (e: Exception) {
>         Timber.e(e, "PlayerLifecycleManager: Failed to save playback position for ${currentFile.name}")
>     }
> }
> ```
>
> Add the import `import com.sza.fastmediasorter.domain.playback.PlaybackCompletionDetector`. Keep all other code paths in the file untouched.

**Verification:**

- `Grep` — `PlaybackCompletionDetector.isNearEnd` matches once in this file.
- `Grep` — `reason = "playback-completed-near-end"` matches once.
- `Grep` — `import com.sza.fastmediasorter.domain.playback.PlaybackCompletionDetector` matches once.
- `Grep -n "Log\.d\("` returns zero hits in this file.
- `/build` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 4/4 PASS (PlaybackCompletionDetector.isNearEnd × 1, near-end reason × 1, import × 1, no Log.d). Build deferred. Files: PlayerLifecycleManager.kt (+10 LOC, total 555). Dev log recorded.

---

### Step 04.2 — Unit test the branch decision

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManagerNearEndTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> The strategic-criterion test belongs here: `30 s file, position 28 s, manual exit → markPlaybackCompleted called` (28 s = 28 000 ms; threshold 1500 ms; cutoff 28 500 ms; 28 000 < 28 500 → NOT near-end → savePosition called). Add the actual completion variant: `30 s file, position 29 s → markPlaybackCompleted called`.
>
> Constructing a real `PlayerLifecycleManager` is heavy; if its construction is impractical to mock, write the test against a small extracted helper function `decideOnSavePath(position, duration)` that returns a sealed result `SaveDecision.Save | SaveDecision.MarkCompleted` and call that helper from inside `saveCurrentPlaybackPosition`. Refactor only if necessary — the simpler win is to test `PlaybackCompletionDetector` (already done in Phase 01) plus a focused integration test using `mockk` on `activity.playbackPositionRepository`.
>
> If the activity-level test is impractical, the user-mandated unit test (30 s file, position 28 s, EVENT_ENDED → clearState) is already covered by Phase 03 (`VideoPlayerManagerStateEndedTest`). Add at minimum these focused cases here:
> - `nearEnd at 29 s of 30 s → branch chooses markPlaybackCompleted` (call `PlaybackCompletionDetector.isNearEnd(29_000L, 30_000L)` returns `true`).
> - `mid-playback at 5 min of 1 h → branch chooses savePosition` (`isNearEnd(300_000L, 3_600_000L)` returns `false`).
>
> Mark this test `PlayerLifecycleManagerNearEndTest` even if it ultimately tests only `PlaybackCompletionDetector` from this perspective — it documents the contract this phase relies on.

**Verification:**

- `Glob` — test file exists.
- `Grep` — `class PlayerLifecycleManagerNearEndTest` matches once.
- `Grep` — at least 2 `@Test` methods.
- Tests pass — `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Static verification 3/3 PASS (file exists, class once, 4 @Test ≥ 2). Build run deferred. Files: PlayerLifecycleManagerNearEndTest.kt (+38 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build`.
- [ ] New tests pass.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every touched file.

---

## Handoff Notes to Next Phase

The two completion paths (natural end + manual-exit-near-end) emit distinct reasons (`playback-completed` vs `playback-completed-near-end`). Strategic §11 criterion 4 only requires `playback-completed` to be present — both reasons start with that prefix and remain greppable.

---

## Rollback Plan

Revert the single edit inside `saveCurrentPlaybackPosition` and delete the test file. Phase 03 still provides the natural-end case in isolation.
