# Phase 01 - Resilience primitives

**Strategic spec:** [`../S1513_stream-resilience-testable-core.md`](../S1513_stream-resilience-testable-core.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Create the two shared primitives every later phase consumes - the capped exponential backoff and the failure
classifier - as pure Kotlin with unit tests, changing no caller yet.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamBackoff.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamFailureClass.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamBackoffTest.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamFailureClassTest.kt` | New | ≤ 160 |

> No flavor-scoped file: all three call sites live in `src/main` and compile into every flavor (strategic §3.2).

---

## Steps

### Step 01.1 - Add the capped exponential backoff primitive

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamBackoff.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `internal object StreamBackoff` with one function that computes a retry delay from an attempt index,
> a base delay, a cap and an optional shift ceiling: the shape `(base shl attempt).coerceAtMost(cap)` that
> `AudioPlaybackService.scheduleStreamRetry` and `StreamInlineAudioManager.scheduleLocalRetry` already share
> verbatim, plus the linear shape `(attempt * step).coerceAtMost(cap)` that `StreamPlaybackHelper`'s
> behind-live-window branch uses. Guard the shift against a negative or oversized attempt index so a runaway
> counter cannot overflow the shift. Import nothing from `android.*` or `androidx.*`.

**Why:**

Strategic ADR-1 keeps one policy per site but shares the primitives underneath them, and §4 records that the
service and the inline manager already compute the identical `base shl attempt` capped delay from the same
`RadioStreamBufferConfig` constants, so the formula is duplicated code today rather than a difference.

**Verification:**

- `Glob` - the file exists at the path above.
- `Grep` - `import android` and `import androidx` return zero hits in that file.
- `Grep` - `object StreamBackoff` matches exactly once.

**Status:** `[x]` done

---

### Step 01.2 - Add the failure classifier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/resilience/StreamFailureClass.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `enum class StreamFailureClass` naming the outcomes the three sites distinguish today - behind the
> live window, a transient network failure, a retryable HTTP status, and a failure none of them retries - and
> a pure `classify(errorCode: Int, httpStatus: Int?)` function returning one. Reproduce the existing rules
> exactly: the `ERROR_CODE_IO_UNSPECIFIED..ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE` range the audio sites
> accept, the four codes `StreamPlaybackHelper.isRecoverableStreamError` accepts unconditionally, and its HTTP
> rule that only 429 and 500..599 are retryable. Take the Media3 code values as plain `Int` constants declared
> in this file with a comment naming the `PlaybackException` constant each mirrors - the file must not import
> Media3. `httpStatus` is nullable and null means "no HTTP status was available", never "the status was fine".

**Why:**

Strategic §5.1 puts the error classifier in the shared core with Media3 left at the call site, and ADR-4
requires an absent observation to be its own value rather than collapsing into the healthy case.

**Verification:**

- `Grep` - `import androidx.media3` returns zero hits in that file.
- `Grep` - `fun classify(` matches exactly once.
- `Grep` - `429` and `500..599` both present.

**Status:** `[x]` done

---

### Step 01.3 - Unit-test both primitives

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamBackoffTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/resilience/StreamFailureClassTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Write JVM unit tests. For the backoff: the exact delays the two audio sites produce today for attempts 0,
> 1, 2 and beyond the shift ceiling with base 2000 and cap 8000, and the linear series 0, 1000, .., 5000 the
> video behind-live-window branch produces. For the classifier: one case per branch, including a
> `ERROR_CODE_IO_BAD_HTTP_STATUS` with status 404 classified as not retryable, the same code with 503
> retryable, and the same code with a null status - assert the null case takes the not-retryable answer and
> is distinguishable from a fine status, per ADR-4.

**Why:**

Strategic §11.2 requires a unit test covering the branches of every extracted class, including the "no
evidence" outcome, and these tables are what later phases assert their own behaviour against.

**Verification:**

- `.\a.ps1 fu` (or the targeted `--tests` filter) runs both test classes green.
- `Grep` - both test files contain at least one assertion naming a null HTTP status.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] New unit tests pass.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Phases 02-04 consume `StreamBackoff` and `StreamFailureClass` and must not re-derive either formula locally.
No caller changed in this phase, so the tree behaves exactly as before it.

Two things this phase learned that the plan did not know:

- `classify` cannot make a null HTTP status differ from a 404 without a fifth outcome, because both answer
  `NOT_RETRYABLE` in the code being reproduced. ADR-4's "no evidence is its own value" is honoured where it
  changes an answer - the stall rule in Phase 02 - and recorded here as a deliberate limit, not an oversight.
- `ERROR_CODE_IO_BAD_HTTP_STATUS` sits inside the IO range the audio sites retry unconditionally while
  `classify` routes it to the HTTP branch. Phase 04 carries the resulting trap as a prerequisite note.

Verification run by the parent, not the implementer: `check-standard-fast.ps1 -Mode Unit -Tests
"*StreamBackoffTest,*StreamFailureClassTest"` - BUILD SUCCESSFUL, exit 0. One compile error
(comma-separated conditions in a subjectless `when`) was found and fixed at that point.

---

## Rollback Plan

Revert phase commit(s) - the phase adds files and changes no caller, so reverting cannot affect playback.
