# Phase 02 - Restore and probe policy in the controller

**Strategic spec:** [`../S1511_stream-quality-rung-memory-and-probe-up.md`](../S1511_stream-quality-rung-memory-and-probe-up.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Teach the policy class to start from a remembered ceiling and to decide when to probe upward, entirely as pure functions with unit tests, holding no Android type.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt` | Modified | ≤ 130 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownControllerTest.kt` | Modified | ≤ 250 |

> The controller is 119 LOC today and stays well inside the file-size ceiling; keep it that way by adding policy, not plumbing.

---

## Steps

### Step 02.1 - Accept a remembered ceiling

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an optional remembered rung argument to `setRenditions(..)`: after the ladder is sorted, place the ceiling on the rung matching the remembered bitrate, falling back to the nearest rung at or below it, and leaving the ceiling at the top when the remembered rung is absent from this ladder. Return which rung was actually adopted so the caller can log it. Keep the stall-window reset as it is.

**Why:**

Strategic section 2 goal 1 and section 6 Q1 require the learned rung to be applied when the channel is opened, and `setRenditions` is the single point where the ladder becomes known, which strategic section 4 records as the earliest moment a ceiling can be expressed.

**Verification:**

- `Grep` - `setRenditions` signature carries the remembered-rung parameter.
- `Grep` - `import android` returns zero hits in the controller.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - setRenditions now takes an optional remembered rung and returns the rung the ceiling landed on; exact-bitrate match, fallback to the nearest rung at or below, top when the remembered rung is above the whole ladder. Grep: signature carries the parameter, import android 0 hits.

---

### Step 02.2 - Decide when to probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a query that answers, for a supplied monotonic timestamp, whether a probe upward is due: never when the ceiling is already at the top or the ladder has one rung, otherwise once the wait for the rung above has elapsed since the last step or the last failed probe of that rung. The wait doubles once per recorded failure of that same rung and is capped. Take the clock as an argument, exactly as `registerStall` does.

**Why:**

Strategic section 2 goal 2 requires a path upward, and section 3.2 forbids the policy from holding an Android type, which is why the caller supplies the clock rather than the class reading it.

**Verification:**

- `Grep` - the new query takes a `nowMs: Long` parameter.
- `Grep` - `System.currentTimeMillis` and `SystemClock` return zero hits in the controller.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - isProbeDue(nowMs) added with a per-rung doubling wait capped at PROBE_MAX_WAIT_MS; first call only anchors the wait because setRenditions is offered no clock. Grep: query takes nowMs: Long, System.currentTimeMillis/SystemClock 0 hits.

---

### Step 02.3 - Record the probe and its verdict

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a call that starts a probe - raising the ceiling one rung and returning the `Cap` to apply - and a call that closes it with a verdict. A stall registered while a probe is open fails that probe: the ceiling returns to the rung it came from and the failure is counted against the probed rung. Surviving the full observation window succeeds and forgives only that rung's failure count. Make the probe state explicit rather than inferred from the ceiling index.

**Why:**

Strategic section 2 goals 2 and 3 require the probe to be a real switch judged by a survived window, and section 7 names the case where the verdict is recorded for a rung that never rendered - which is why success must be confirmable against what actually played rather than assumed.

**Verification:**

- `Grep` - a probe-state property or sealed state exists in the controller.
- `Grep` - the failure counter is per rung, not a single field.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Explicit ProbeState (Idle/Open) added; startProbe raises the ceiling one rung and returns the Cap, closeProbeIfSurvived succeeds only on a survived window confirmed against the playing rung, and a stall arriving while a probe is open fails it - restoring the previous ceiling and charging the failure to the probed rung. Grep: sealed ProbeState present, failures held in probeFailuresByRung keyed per rung rather than one field.

---

### Step 02.4 - Expose what must be persisted

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Expose the current learned rung and its failure count as a small immutable snapshot the caller can hand to the store, and accept the same shape back in `setRenditions`. Do not let the controller know what a database is.

**Why:**

Strategic ADR-1 keeps storage outside the policy class so it stays testable off-device, and a snapshot type is what lets the two sides meet without the controller importing a data layer.

**Verification:**

- `Grep` - the snapshot type is declared in the controller file.
- `Grep` - `androidx.room` returns zero hits in the controller.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Memory snapshot type added (learned rung plus the failure count of the rung above it) and setRenditions now takes it back. Grep: data class Memory declared in the controller, androidx.room 0 hits - the file still imports nothing at all.

---

### Step 02.5 - Cover the policy with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownControllerTest.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Extend the existing suite: a remembered rung present in the ladder is adopted; a remembered rung absent from it leaves the ceiling at the top; a probe is not due before its wait elapses and is due after; a failed probe doubles that rung's wait and leaves neighbouring rungs' waits alone; the wait stops doubling at the cap; a stall during an open probe fails it and restores the previous ceiling; a survived window succeeds. Keep every test off-device with a supplied clock.

**Why:**

The class exists in this shape specifically so its decisions are provable without a device, and strategic section 7 rates a wrong probe cadence as the highest risk in the ticket.

**Verification:**

- `.\a.ps1 fu` - the controller suite passes with the new cases present.
- `Grep` - each named case above has a matching `@Test`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Controller suite extended to 26 cases; XML report tests=26 failures=0 errors=0. New cases: remembered rung adopted, remembered rung below the ladder leaves the top, lost rung falls back to the nearest below, probe not due before the wait and due after, top ceiling never due, stall during an open probe fails it and restores the ceiling, survived window succeeds, survived window without the picture climbing does not, failed probe doubles only its own rung's wait, wait stops doubling at the cap. All off-device with a supplied clock.
- 2026-08-13 - Phase-boundary audit (Layer 1; Layers 3-4 not applicable, the file holds no listener and no Room type). Layer 1: two notes, both P2 and both recorded rather than churned. (1) isProbeDue mutates ceilingSinceMs on its first call, so a query named as a question has one side effect; it is stated in the KDoc and pinned by a test, and the alternative - a separate anchor call from the media layer - would put the invariant where it is easier to forget. (2) isProbeOpen currently has no production caller; it is the signal phase 04 step 04.4 needs, and the phase handoff already says nothing is wired until then. Carried to phase 04: probeFailuresByRung and the probe state are plain unsynchronised fields, exactly like the ceiling and stall deque that shipped with S1508 - phase 04 must confirm the stats tick and the Media3 callbacks reach them from one thread, which its own Done Criteria already demands.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The policy is complete and proven off-device, but its wait constants are still placeholders - Phase 03 measures what they should be, and nothing is wired into playback until Phase 04.

---

## Rollback Plan

Revert phase commit(s) - the added calls have no callers until Phase 04, so the shipped step-down behaviour is unchanged.
