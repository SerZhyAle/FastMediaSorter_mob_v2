# Phase 02 - Stall rule extracted from the watchdog

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

Move the stall decision out of `StreamStallWatchdog` into a pure rule that takes `now` as a parameter and
answers with three outcomes, leaving the watchdog to sample the player and apply the answer.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamStallRule.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamStallRuleTest.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Pin today's stall behaviour in a characterization test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamStallRuleTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write the test first, against the rule signature planned in step 02.2, encoding the behaviour that is in
> `StreamStallWatchdog.kt` today: a poll every 3000 ms; three consecutive polls with no progress declare a
> stall; for video "progress" means the rendered-frame counter advanced, for audio-only it means the position
> advanced by at least 500 ms; a buffering timeout of 15000 ms that does NOT fire while the buffered duration
> is still growing and the player reports loading. Read the constants out of the current file rather than
> retyping them from this plan.

**Why:**

The INDEX ordering rationale makes the characterization test binding on this phase, because strategic §2
forbids a behaviour change and a test written after the move can only prove the new code agrees with itself.

**Verification:**

- `Grep` - the test file names all four thresholds: `3000`, `3`, `500`, `15000`.
- The test compiles and fails only because the rule does not exist yet (or passes once step 02.2 lands).

**Status:** `[x]` done

---

### Step 02.2 - Add the pure stall rule with a three-valued outcome

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamStallRule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a pure `StreamStallRule` class holding the poll counters and the buffering deadline as its own state,
> with every entry point taking `now: Long` as a parameter. It receives an observation of plain values -
> position, rendered-frame count as a nullable `Int`, buffered duration, whether the player reports loading,
> whether the item is live - and returns a sealed outcome with exactly three cases: progressing, stalled, and
> no-evidence. A null rendered-frame count means the engine reported no counter, so the rule falls back to
> position progress and answers no-evidence rather than progressing when it cannot tell. Import nothing from
> `android.*` or `androidx.*`; do not read a clock inside the class.

**Why:**

Strategic ADR-2 makes `now` a call parameter so a test can play ten minutes in ten lines, and ADR-4 requires
the third outcome so an engine that reports no frame counter cannot silently disarm the watchdog.

**Verification:**

- `Grep` - `import android` and `import androidx` return zero hits in that file.
- `Grep` - the sealed outcome declares three cases, one of them naming no-evidence.
- `Grep` - `System.currentTimeMillis` and `SystemClock` return zero hits in that file.
- Step 02.1's test passes.

**Status:** `[x]` done

---

### Step 02.3 - Rewire the watchdog onto the rule and put it on a monotonic clock

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Replace the watchdog's own counters and deadline arithmetic with calls into `StreamStallRule`, passing
> `SystemClock.elapsedRealtime()` as `now` at every entry. Keep `Handler.postDelayed` scheduling, the player
> sampling and every effect (`stop`, `prepare`, `seekTo`, the callbacks) exactly where they are. Replace the
> `System.currentTimeMillis()` stamp in `armStreamBufferingTimeout` with `SystemClock.elapsedRealtime()` so
> the whole path uses one monotonic clock. Log the no-evidence outcome distinctly from the progressing one.
> Leave `StreamStallRecoveryWindow` where it is and keep calling it - it is already pure and already tested.

**Why:**

Strategic §4 records `armStreamBufferingTimeout` as the one real clock anomaly on this path, where a wall
clock change can extinguish the threshold early or suspend it indefinitely, and §2.3 requires a rule that
could not run to sound different in the archive from one that ran and declined to fire.

**Verification:**

- `Grep` - `System.currentTimeMillis` returns zero hits in `StreamStallWatchdog.kt`.
- `Grep` - `StreamStallRule` present in that file.
- `Grep` - `StreamStallRecoveryWindow` still present and still called.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.4 - Cover the S1467 scenario in the rule test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamStallRuleTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Read S1467's §2 and add the case it describes to the rule test, so the question that ticket is waiting on a
> car head unit log to answer is answered by the test instead. If S1467's §2 does not pin the scenario
> precisely enough to encode, write the closest encodable case and state in the test's KDoc which part of
> S1467 remains unanswered - do not guess a threshold it never states.

**Why:**

Strategic §11.5 makes this an explicit completion criterion, because S1467 sits on `BlockExternal` only for
want of a way to check this rule without a device.

**Verification:**

- `Grep` - `S1467` present in the test file.
- The test class passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The watchdog now samples and applies but decides nothing. Whether S1467 can leave `BlockExternal` is a
judgement for that ticket, not this one - record what the test answered and let S1467's own audit rule on it.

**What the S1467 test does and does not settle.** Encoded: a live stream whose position never advances while
the rendered-frame counter climbs must answer progressing on every poll - S1467 §2's stated root cause, and
the only part of it that is a function of this rule's inputs. Still unanswered, and recorded in the test's
KDoc rather than papered over: whether the re-anchors in the attached car-head-unit log came from the false
signal or from a genuinely failing link, which §2 says that log cannot separate and which needs release-log
fields this rule never sees. S1467 stays where its own audit puts it; this phase did not touch its status.

**Deliberate deviations from the steps as written**, each defended in the implementation:

- `StreamStallObservation` carries `hasVideo`, which step 02.2's input list omitted. Without it the
  frames-versus-position split cannot be reproduced, and strategic §2 forbids changing it.
- `onBufferingDeadline` additionally requires the elapsed time to have reached the timeout. In production
  the `Handler` already guaranteed that, so nothing changes; it is what puts the deadline on the monotonic
  clock and makes it playable in a test.
- The rule instance lives on `VideoPlayerManager` beside `StreamStallRecoveryWindow`, because the watchdog is
  a set of extension functions with no instance of its own and that is where its state already lived. Four
  now-dead manager fields were removed after a repo-wide grep proved no other file read them.

Verification run by the parent: `check-standard-fast.ps1 -Mode Unit -Tests
"*StreamStallRuleTest,*StreamStallRecoveryWindowTest"` - BUILD SUCCESSFUL, exit 0.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
