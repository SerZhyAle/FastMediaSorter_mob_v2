# Phase 01 - Progress Contract

**Status:** Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2

## Objective

Create a thread-safe domain component that derives stable progress, a smoothed byte rate, and consumer-specific emission eligibility from monotonic byte samples.

## Files Touched

| File | New / Modified |
|---|:---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/TransferProgressReporter.kt` | New |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/transfer/TransferProgressReporterTest.kt` | New |

## Steps

### Step 01.1 - Add the transfer progress reporter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/TransferProgressReporter.kt`

**Prompt for developer:**

> Add an `@Singleton` reporter with a per-operation monotonic sample window. Its public API accepts operation id, transferred bytes, total bytes, and a consumer key; returns a byte-derived percent when known, a smoothed rate calculated from the oldest valid sample in a bounded time window, and whether that consumer may publish. Terminal samples and explicit clear remove the operation state.

**Verification:**

- `TransferProgressReporter.kt` exists.
- `class TransferProgressReporter` appears exactly once.
- No `android.view`, `Context`, or UI import appears.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Contract narrowed at the Phase 03 audit: the percent this step specified shipped with no production consumer, so it was removed rather than left as a second percent formula. The reporter now owns rate and publish eligibility only; percent stays in `ui/browse/transfer/TransferProgressPercent.kt`, which every rendering site already calls.

### Step 01.2 - Test time, smoothing, throttle, and cleanup

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/transfer/TransferProgressReporterTest.kt`

**Depends on:** Step 01.1

**Prompt for developer:**

> Add deterministic unit tests using an injected monotonic clock. Cover first sample, rate over a multi-sample window, per-consumer publish intervals, terminal cleanup, and independent simultaneous operation ids.

**Verification:**

- `class TransferProgressReporterTest` appears exactly once.
- Tests cover terminal cleanup and two consumer keys.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every step is done.
- [x] Focused unit tests pass - `a.ps1 fu`, exit 0.
- [x] Kotlin compilation passes - `a.ps1 fk`, exit 0.

## Rollback Plan

Revert the phase commit; no persistence or user-facing surface changes.
