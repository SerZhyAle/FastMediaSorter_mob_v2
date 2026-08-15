# Phase 02 - Failure backoff in the snapshot engine

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Add a per-url failure cooldown to `StreamFrameSnapshotManager.request` so a stream whose last capture failed is not re-captured until an exponential backoff window (60 s -> 5 min cap) elapses - the second, engine-level guard that stops a dead visible tile from re-probing on every incidental rebind, independent of who triggers the request.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `StreamFrameSnapshotManager.request(url, force)` and `capture()` unchanged from main.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotBackoffTest.kt` | New | ≤ 160 |

---

## Steps

### Step 02.1 - Per-url exponential failure cooldown

**Files:** `ui/streams/helpers/StreamFrameSnapshotManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a guarded `HashMap<String, Long>` of "next-eligible-elapsed-time" per url and a consecutive-failure counter per url. In `request(url, force)`: after the existing `!CAPTURE_ENABLED` / `cache.isFresh` guard, when `!force`, skip the url if `SystemClock.elapsedRealtime() < nextEligible[url]`. In the completion path (`drainOne`, where `onOutcome(url, bitmap != null)` fires): on failure (`bitmap == null`) increase the url's failure count and set `nextEligible[url] = now + min(BACKOFF_BASE_MS shl (count-1), BACKOFF_CAP_MS)`; on success clear the url's backoff entry and counter. A `force` capture (pull-to-refresh) ignores and clears the cooldown. Constants: `BACKOFF_BASE_MS = 60_000L`, `BACKOFF_CAP_MS = 300_000L`. Keep all map access inside the existing `synchronized(pending)` monitor or a dedicated lock; keep log/probe lines <=120 chars. Use `TimeUnit`/named consts, no bare numeric literals beyond the two named ones. WHY comment: a dead visible tile otherwise re-captures on every rebind (S1169).

**Verification:**

- `Grep` - `BACKOFF_CAP_MS` present exactly once (declaration).
- `Grep` - `nextEligible` (or the chosen field name) referenced in both `request` and the failure branch.
- `Grep -n "Log\.d\("` in the file returns zero hits (Timber only).
- `.\a.ps1 fk` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Step 02.1: Verification 4/4 PASS (BACKOFF_CAP_MS x1, nextEligibleAt x5, zero Log.d, compileStandardDebugKotlin EXIT=0). File: StreamFrameSnapshotManager.kt.

### Step 02.2 - Backoff unit test

**Files:** `ui/streams/helpers/StreamFrameSnapshotBackoffTest.kt` (new)
**Depends on:** Step 02.1

**Prompt for developer:**

> Extract the backoff decision into a pure, testable helper (e.g. `internal fun backoffDelayMs(consecutiveFailures: Int): Long`) and unit-test it: failures 1/2/3 -> 60_000 / 120_000 / 240_000; failure 5+ clamps to 300_000. Do not spin up ExoPlayer in the test - the capture path is device-only; test only the delay math and that a success resets the counter to 0. Pure-JVM test, no Robolectric.

**Verification:**

- `Glob` - `StreamFrameSnapshotBackoffTest.kt` exists.
- `Grep` - `backoffDelayMs` present in both the manager and the test.
- `--tests *StreamFrameSnapshotBackoffTest` green.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Step 02.2: Verification 3/3 PASS (test file exists, backoffDelayMs in manager+test, tests=3 failures=0). File: StreamFrameSnapshotBackoffTest.kt.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] `/build` passes.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `--tests *StreamFrameSnapshotBackoffTest` green.
- [ ] Dev log entry for both files.
- [ ] Phase-boundary audit: concurrency - map access guarded; no new listener without symmetric removal (none added).

---

## Handoff Notes to Next Phase

Re-capture of a failed url is now rate-limited at the engine regardless of trigger (bind, scroll, timer, or a Phase 01-surviving genuine re-emit). `force` (pull-to-refresh) still bypasses it.

---

## Rollback Plan

Revert the phase commit(s) - additive guard, no schema or user-facing surface changed.
