# Phase 03 - Video retry policy

**Strategic spec:** [`../S1513_stream-resilience-testable-core.md`](../S1513_stream-resilience-testable-core.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Extract the two-branch error ladder of `StreamPlaybackHelper.onPlayerError` into a pure policy, leaving the
helper to read the Media3 exception and apply the answer.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamVideoRetryPolicy.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamVideoRetryPolicyTest.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 600 |

---

## Steps

### Step 03.1 - Pin today's video ladder in a characterization test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamVideoRetryPolicyTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write the test first, encoding what `StreamPlaybackHelper.onPlayerError` does today: a behind-live-window
> failure on non-RTSP content recovers up to 3 times with a linear delay of `attempt * 1000` capped at 5000;
> a transient failure retries up to 4 times with `2000 shl (attempt - 1)` capped at 8000; RTSP content takes
> no behind-live-window branch; both counters reset only on a confirmed ready state and never on buffering;
> the fifth transient failure surfaces the error instead of retrying. Read the constants out of the current
> source rather than retyping them from this plan.

**Why:**

The INDEX ordering rationale makes the characterization test binding here, and strategic §11.3 requires the
unchanged behaviour to be shown by tests written before the move rather than asserted afterwards.

**Verification:**

- `Grep` - the test names both budgets (`3`, `4`) and both caps (`5000`, `8000`).
- `Grep` - a case naming RTSP is present.

**Status:** `[x]` done

---

### Step 03.2 - Add the pure video retry policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamVideoRetryPolicy.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a pure `StreamVideoRetryPolicy` owning the two counters, taking `now: Long` and an observation of plain
> values - the `StreamFailureClass` from Phase 01, whether the source is RTSP - and returning a sealed
> decision: re-anchor to the live edge after N ms, retry after N ms, or surface the error. Reuse
> `StreamBackoff` for both delay shapes. Expose the ready-state reset as its own entry point so the caller
> can keep the existing "reset only on confirmed ready" rule. Import nothing from `android.*` or `androidx.*`.

**Why:**

Strategic ADR-3 keeps the effect on the call site so the policy holds no `Player`, which is what makes the
ladder testable at all, and ADR-1 keeps this policy separate from the audio ones because the shapes differ.

**Verification:**

- `Grep` - `import android` and `import androidx` return zero hits in that file.
- `Grep` - `StreamBackoff` referenced.
- Step 03.1's test passes.

**Status:** `[x]` done

---

### Step 03.3 - Rewire `StreamPlaybackHelper` onto the policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `onPlayerError`, extract the Media3 error code and, when the failure is a bad HTTP status, the response
> code from the `cause` chain, classify with Phase 01's classifier, ask the policy, and apply its decision
> with the existing effects - the delayed `seekToDefaultPosition()`/`prepare()` on `managerScope` and the
> `playerCallback.onPlaybackError` tail. Keep the stale-player guard that drops the recovery when the user
> navigated away. Delete the now-dead local counters, budget constants and `isRecoverableStreamError` /
> `isRetryableHttpStatus` once nothing calls them; leave `LiveConfiguration` untouched.

**Why:**

Strategic §2.2 reduces the Media3 layer to taking an observation and applying an answer, and §20 of the
project rules forbids leaving the superseded helpers behind once their only caller is gone.

**Verification:**

- `Grep` - `isRecoverableStreamError` returns zero hits across `app_v2/src`.
- `Grep` - `StreamVideoRetryPolicy` present in the helper.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The video site now reads, classifies, asks and applies. Phase 04 repeats the shape for the two audio sites,
whose policies differ in gate shape and give-up consequence - do not reuse this policy there.

**A behaviour change this phase had to actively prevent, and Phase 04 must not undo.** `classify` maps the
whole IO block `2000..2008` to a transient failure, because that is what the two audio sites accept. The
video site never did: it listed exactly four codes. Branching on the class alone would have started retrying
`IO_FILE_NOT_FOUND`, `IO_NO_PERMISSION` and `IO_CLEARTEXT_NOT_PERMITTED` four times across roughly 22 seconds
before the user was told anything. The policy therefore carries the raw error code beside the class and keeps
its own four-code set - an addition to the step's stated observation list, made because strategic §2 requires
the code sets to move across verbatim.

**Settled from the source, not assumed:** `transientRetries` is incremented before the delay is computed, so
the shift is zero-based on the first failure and the series is 2000, 4000, 8000, 8000. An off-by-one here
would have been a silent behaviour change.

**Deviation:** the policy takes no `now`. Strategic §4 records that this ladder counts attempts and not
seconds, so a clock parameter would advertise a time window it does not have. The two Phase 04 policies do
have one and must take it.

Verification run by the parent: `check-standard-fast.ps1 -Mode Unit -Tests "*StreamVideoRetryPolicyTest"` -
BUILD SUCCESSFUL, exit 0. `post-change.ps1 -ScopeToFile`: PASS.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
