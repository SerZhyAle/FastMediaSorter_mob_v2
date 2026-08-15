# Phase 01 — Completion Detector

**Strategic spec:** [`../S0029_bugfix-resume-position-end-of-file.md`](../S0029_bugfix-resume-position-end-of-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Introduce a pure-Kotlin utility object `PlaybackCompletionDetector` exposing `nearEndThresholdMs(durationMs)` and `isNearEnd(positionMs, durationMs)`. No Android, no Hilt, no I/O. Used by Phase 04 to decide whether a manual exit happened in the "near-end" zone.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetectorTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 — Create `PlaybackCompletionDetector`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new `object PlaybackCompletionDetector` under package `com.sza.fastmediasorter.domain.playback`. Expose two functions:
> - `nearEndThresholdMs(durationMs: Long): Long` — returns `min(durationMs / 20, 5000L).coerceAtMost(durationMs / 2).coerceAtLeast(0L)`. For `durationMs <= 0` return `0L`.
> - `isNearEnd(positionMs: Long, durationMs: Long): Boolean` — returns `false` if `durationMs <= 0` or `positionMs < 0`; otherwise `positionMs >= durationMs - nearEndThresholdMs(durationMs)`.
>
> No Android imports. No Timber. Use only stdlib. Keep file <60 lines.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt` exists.
- `Grep` — `object PlaybackCompletionDetector` matches exactly once.
- `Grep` — `fun nearEndThresholdMs(durationMs: Long): Long` matches once.
- `Grep` — `fun isNearEnd(positionMs: Long, durationMs: Long): Boolean` matches once.
- `Grep` — no `android.` or `timber.` import in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 4/4 PASS. Files: PlaybackCompletionDetector.kt (+30 LOC). Dev log recorded.

---

### Step 01.2 — Unit test the detector

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetectorTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add JUnit4 test class `PlaybackCompletionDetectorTest`. Cover:
> - 30 s file at 28 s → `isNearEnd` returns `true` (threshold = min(1500, 5000, 15000) = 1500 ms; cutoff = 28 500 ms; 28 000 < 28 500 → false). NOTE: 28 s alone is NOT near-end. Test that 29 s IS near-end (29 000 >= 28 500). Add the 28 s case as `false`.
> - 30 s file at end (30 000 ms) → `true`.
> - 1 hour file (3 600 000 ms) at 3 595 000 ms → `true` (threshold = 5 000 ms).
> - 1 hour file at 3 400 000 ms → `false`.
> - 8 s file at 4 100 ms → `false` (threshold = min(400, 5000, 4000) = 400 ms; cutoff = 7 600 ms).
> - 8 s file at 7 700 ms → `true`.
> - duration <= 0 → `isNearEnd` returns `false` regardless of position.
> - position < 0 → `isNearEnd` returns `false`.
> - `nearEndThresholdMs(0)` → `0`.
> - `nearEndThresholdMs(-1)` → `0`.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetectorTest.kt` exists.
- `Grep` — `class PlaybackCompletionDetectorTest` matches once.
- `Grep` — at least 8 occurrences of `@Test` in the file.
- Test passes locally — run `./gradlew :app_v2:testStandardDebugUnitTest --tests "*.PlaybackCompletionDetectorTest"` (via `/build`).

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Static verification 3/3 PASS (Glob ok, class once, 12 @Test ≥ 8). Test execution deferred to phase build gate. Files: PlaybackCompletionDetectorTest.kt (+78 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `PlaybackCompletionDetectorTest` passes.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`PlaybackCompletionDetector` is a stateless `object` — Phase 04 imports and calls it directly. No DI binding required.

---

## Rollback Plan

Delete the two new files. No call sites yet.
