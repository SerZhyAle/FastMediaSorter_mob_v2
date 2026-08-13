# Phase 04 - Audio retry policies

**Strategic spec:** [`../S1513_stream-resilience-testable-core.md`](../S1513_stream-resilience-testable-core.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Extract the two audio ladders - the foreground service and the inline mini-player - into two pure policies
that keep their differing gates and differing give-up consequences.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

> **Trap surfaced while implementing Phase 01 - read before writing step 04.3 or 04.4.**
> `ERROR_CODE_IO_BAD_HTTP_STATUS` (2004) lies inside the IO block both audio sites retry unconditionally,
> but `StreamFailureClass.classify` sends that code to the HTTP branch, and the audio sites never extract a
> response code, so they will pass `httpStatus = null` and get `NOT_RETRYABLE`. Mapping `NOT_RETRYABLE` to
> "give up" in the audio adapters would therefore stop retrying a failure both sites retry today - a
> behaviour change strategic §2 forbids. Each audio adapter must treat a bad-HTTP-status code with no
> observed status as its own retryable case, and step 04.1's characterization test must contain that case.



---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamServiceRetryPolicy.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamInlineRetryPolicy.kt` | New | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamServiceRetryPolicyTest.kt` | New | ≤ 240 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamInlineRetryPolicyTest.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | Modified | ≤ 500 |

---

## Steps

### Step 04.1 - Pin both audio ladders in characterization tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamServiceRetryPolicyTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamInlineRetryPolicyTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write both tests first. For the service: the entry gate accepts a failure when the stream has ever played
> OR the connection is younger than 15000 ms; the delay is `2000 shl attempt` capped at 8000 with the shift
> clamped at 2; the fire-time gate gives up after 15000 ms when the stream never played and after 300000 ms
> from the first retry when it has; a paused user drops the retry. For the inline manager: the same entry
> gate and the same delay, one gate shape used at both schedule and fire time, and - the asymmetry strategic
> §4 names - no upper bound at all once the stream has played once. Assert that unbounded case explicitly
> rather than omitting it.

**Why:**

Strategic §1 lists this very asymmetry as a symptom, and §2 forbids fixing it here, so the test has to record
it as the current truth rather than quietly assert the behaviour the reader would expect.

**Verification:**

- `Grep` - the service test names `15000` and `300000`; the inline test names a case asserting no upper bound.

**Status:** `[x]` done

---

### Step 04.2 - Add the two pure policies

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamServiceRetryPolicy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamInlineRetryPolicy.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add both classes as pure Kotlin holding their own counters and timestamps, every entry point taking
> `now: Long`. The service policy answers retry-after, give-up or ignore and keeps its two life-stage windows
> distinct. The inline policy answers retry-after, give-up or not-mine, the last one for the `usingService`
> case where the foreground service owns the same stream. Both reuse `StreamBackoff` and `StreamFailureClass`
> from Phase 01. Import nothing from `android.*` or `androidx.*`.

**Why:**

Strategic ADR-1 keeps one policy per site because the sites differ in the shape of the decision - the inline
manager's `usingService` input and the service's two-window life-stage split have no counterpart in the other.

**Verification:**

- `Grep` - `import android` and `import androidx` return zero hits in both files.
- `Grep` - the inline policy declares a not-mine outcome naming the service case.
- Step 04.1's tests pass.

**Status:** `[x]` done

---

### Step 04.3 - Rewire `AudioPlaybackService`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace `canRetryStream`, `scheduleStreamRetry` and `giveUpStreamRetry`'s decision arithmetic with calls
> into `StreamServiceRetryPolicy`, passing `SystemClock.elapsedRealtime()` as `now`. Keep the `Handler`
> scheduling, the `playWhenReady` re-check that drops a retry the user paused, and the give-up effect
> (`stopSelf()` plus clearing the now-playing snapshot) exactly where they are. Keep `resetStreamRecovery`
> and `recordCurrentStreamSuccess` as the policy's reset entry points rather than duplicating their fields.

**Why:**

Strategic ADR-3 keeps the effect at the call site precisely because this site's give-up consequence is
stopping a foreground service, which a pure policy must never be able to do.

**Verification:**

- `Grep` - `StreamServiceRetryPolicy` present in the service.
- `Grep` - `stopSelf()` still present on the give-up path.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.4 - Rewire `StreamInlineAudioManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace `canRetry`, `canContinueRetrying` and `scheduleLocalRetry`'s arithmetic with calls into
> `StreamInlineRetryPolicy`, passing `SystemClock.elapsedRealtime()` as `now` and the `usingService` flag as
> an observation. Keep the `Handler` scheduling and the `stop()` plus `callbacks.onError` tail. Leave
> `scheduleToleranceTimeout` and `handleToleranceTimeout` alone - that is a stall timeout, not this ladder,
> and it is out of this phase's scope.

**Why:**

Strategic §4 records that this site's `canRetry` is also a routing decision - when the service owns the
stream the manager deliberately schedules nothing - so the flag has to reach the policy as an input rather
than being re-derived.

**Verification:**

- `Grep` - `StreamInlineRetryPolicy` present in the manager.
- `Grep` - `scheduleToleranceTimeout` unchanged and still present.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

All three sites and the watchdog now decide in pure code. The three policies' differences are visible as three
test tables, which is the instrument the follow-up ticket needs before anyone picks a reference behaviour.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
