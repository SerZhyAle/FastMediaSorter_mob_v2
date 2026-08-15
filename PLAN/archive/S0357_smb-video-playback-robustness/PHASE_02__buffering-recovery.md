# Phase 02 - Buffering Recovery

**Strategic spec:** [`../S0357_smb-video-playback-robustness.md`](../S0357_smb-video-playback-robustness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Detect a stuck-buffering stall on a network stream and attempt bounded session-level reopen/retry recovery before escalating to a named user error, instead of immediately marking the file failed (errorCode 1004 / `ERROR_CODE_FAILED_RUNTIME_CHECK`).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the URI contract holds, so a reopen path can re-satisfy it.
- [ ] Pre-Implementation Blocker R2 (strategic §6.2) is Resolved - the stall root cause and whether a session-level reopen recovers it on the 2026-06-04 failing files are known.
- [ ] Scope-Overlap Note in INDEX.md is confirmed by the owner - Phase 02 adds session-level recovery as a delta over S0344's escalation-with-name path.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/BufferingStallRecoveryPolicy.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 760 |

> `VideoPlayerManager.kt` is 711 lines and stays under 1500 - no Manager split. It is >500 lines, so a timestamped backup in `temp/` is required before editing (Step 02.3 includes it). The recovery policy lives in `core/playback` so the retry count/interval are not hard-wired into a protocol (strategic §5.3 extensibility point).

---

## Steps

### Step 02.1 - Define the bounded buffering-stall recovery policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/BufferingStallRecoveryPolicy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BufferingStallRecoveryPolicy` in `core.playback`. It holds the recovery budget for a stuck-buffering stall: a hard maximum number of reopen attempts and a per-attempt backoff, plus per-session state to count attempts for the current path and report when the budget is exhausted. Expose `fun shouldRetry(path: String): Boolean`, `fun onRetryConsumed(path: String)`, and `fun reset(path: String)` (reset on STATE_READY of that path). Place the numeric constants (max attempts, backoff ms) in a `companion object` with WHY-comments tying them to interactive-playback latency, not in `VideoPlayerErrorHandler`. Keep it thread-confined to the main thread (documented), matching the existing retry handler usage. No flavor `BuildConfig` guard.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/BufferingStallRecoveryPolicy.kt` exists.
- `Grep` - `class BufferingStallRecoveryPolicy` matches exactly once.
- `Grep` - `fun shouldRetry` and `fun onRetryConsumed` both present.
- `Grep -n "Log\.d\("` on the new file returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 - Route the buffering-hang branch through recovery before escalation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `VideoPlayerErrorHandler.handlePlayerError`, the `ERROR_CODE_FAILED_RUNTIME_CHECK` branch currently marks the file failed and shows a named message (the S0344 escalation path). Before that escalation, consult `BufferingStallRecoveryPolicy`: when the path is on a network source (`smb://` / `sftp://` / `ftp://`) and the budget is not exhausted, consume one retry, re-prepare and resume playback at the last position (mirror the existing seek-index recovery pattern), and return `true`. Only when the budget is exhausted fall through to the existing mark-failed + named-message escalation (do not regress S0344). Keep honest log levels: a recovery attempt logs at `warn`, not `error`. Network detection must reuse the existing path-prefix check already in this file - do not add a flavor `BuildConfig` guard.

**Verification:**

- `Grep` - `BufferingStallRecoveryPolicy` referenced in `VideoPlayerErrorHandler.kt`.
- `Grep` - `shouldRetry` call present in the `ERROR_CODE_FAILED_RUNTIME_CHECK` branch.
- `Grep` - `VideoPlaybackFailureSessionCache::markFailed` still present (escalation path preserved).
- `Grep -n "Log\.d\("` on `VideoPlayerErrorHandler.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.3 - Wire the recovery policy into VideoPlayerManager and reset on STATE_READY

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Before editing, create a timestamped backup of `VideoPlayerManager.kt` in `temp/` (>500 lines). Add a `BufferingStallRecoveryPolicy` instance to `VideoPlayerManager` so `VideoPlayerErrorHandler` (which already holds a `manager` reference) can reach it, mirroring how `decoderFailureTracker` is exposed. In the `Player.STATE_READY` branch of `playerListener` (alongside the existing `playbackRetryCount = 0` / failure-cache clear), reset the recovery policy for the current path so a successful load re-arms the budget. Do not add a flavor `BuildConfig` guard.

**Verification:**

- `Grep` - `BufferingStallRecoveryPolicy` present in `VideoPlayerManager.kt`.
- `Grep` - a `reset(` call on the policy is present inside the `STATE_READY` handling (same block as `playbackRetryCount = 0`).
- `Glob` - a `temp/VideoPlayerManager*.kt*` backup file exists.
- `Grep -n "Log\.d\("` on `VideoPlayerManager.kt` returns zero new hits beyond pre-existing `Timber.d` calls (no `Log.d`).

**Status:** `[ ]` not done

---

### Step 02.4 - Insert BlockNeedUserTest verification tag at the recovery entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> At the point in the `ERROR_CODE_FAILED_RUNTIME_CHECK` branch where a recovery retry is consumed (the changed flow entry for this phase), insert exactly one `Timber.d("S0357: buffering stall recovery attempt")`. One tag per changed flow entry. Do not add `S0357:` to any `Timber.i/w/e` line.

**Verification:**

- `Grep` - `Timber.d("S0357: buffering stall recovery attempt")` matches exactly once in `VideoPlayerErrorHandler.kt`.
- `Grep` - the tag sits inside the buffering-hang branch (within `-C 8` context of `ERROR_CODE_FAILED_RUNTIME_CHECK`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed (new policy class) - `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Stuck-buffering on a network source now attempts bounded reopen/retry before the S0344 named-error escalation; the budget re-arms on STATE_READY.
- No dependency flows into Phase 03 (decoder refusal touches a different error class); Phase 03 may already be in flight.

---

## Rollback Plan

Revert the phase commit(s). The new policy class is additive; reverting restores the immediate S0344 escalation behaviour with no data migration or schema change.
