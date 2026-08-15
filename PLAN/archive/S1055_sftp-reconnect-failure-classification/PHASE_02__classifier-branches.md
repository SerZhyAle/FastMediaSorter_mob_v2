# Phase 02 - Classifier branches for auth-reject and host-key-change

**Strategic spec:** [`../S1055_sftp-reconnect-failure-classification.md`](../S1055_sftp-reconnect-failure-classification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-07-15

---

## Objective

Teach `NetworkErrorClassifier` to recognise a JSch host-key-change and a JSch authentication failure and map them to the correct non-transient types, ahead of the generic message heuristics.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`NetworkHostKeyChangedException` exists).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt` | Modified | ≤ 320 |

---

## Steps

### Step 02.1 - Add host-key-change and auth-fail branches

**Files:** `data/network/exceptions/NetworkErrorClassifier.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `classifyInternal`, add two branches BEFORE the generic message-heuristic branches (before the `messageContains("access denied", "permission denied", "authentication", ...)` branch) and after the typed-exception short-circuits. Reuse the existing `messageContains` helper.
> - Host-key-change: `throwable.messageContains("hostkey", "host key", "host-key")` -> `ClassificationResult(NetworkHostKeyChangedException("Server host key changed: ${throwable.message}", throwable), usedFallback = false)`. This matches both the raw JSch verdict ("reject HostKey", "HostKey has been changed") and the typed `HostKeyMismatchException` message ("host-key mismatch"). Place it FIRST among the new branches so a mismatch never falls through to auth or connection-lost.
> - Auth-reject: `throwable.messageContains("auth fail", "auth cancel", "userauth")` -> `ClassificationResult(NetworkAccessDeniedException("SFTP auth failed: ${throwable.message}", throwable), usedFallback = false)`. Use these specific JSch tokens, NOT a broad "auth" substring, so a normal SFTP "permission denied" file status is not swallowed (see strategic §7 risk).
> Do not change `isTransient` - both `NetworkHostKeyChangedException` and `NetworkAccessDeniedException` are already non-transient. Keep the cause-chain recursion below the new branches so a wrapped `IOException("...", JSchException("Auth fail"))` still reaches them.

**Verification:**

- `Grep` - `NetworkHostKeyChangedException(` matches once in `NetworkErrorClassifier.kt`.
- `Grep` - `"auth fail"` and `"hostkey"` both present in `NetworkErrorClassifier.kt`.
- `Grep` - the two new branches appear above the existing `"access denied", "permission denied"` branch (line order).

**Status:** `[x]` done

---

### Step 02.2 - Confirm isTransient excludes both outcomes

**Files:** `data/network/exceptions/NetworkErrorClassifier.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Verify (no code change expected) that `isTransient` returns `false` for `NetworkHostKeyChangedException` and `NetworkAccessDeniedException`. `isTransient` only returns true for `NetworkTimeoutException`, `NetworkConnectionLostException`, `NetworkRateLimitException`, `NetworkServerErrorException`. If a future edit widened it, narrow it back. This step is a guard, not new work; if nothing needs changing, mark it done after the grep.

**Verification:**

- `Grep` - `isTransient` body lists exactly the four transient types; neither new type appears there.
- `/build` standard debug compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `NetworkErrorClassifier.kt`.

---

## Handoff Notes to Next Phase

`classify()` now returns `NetworkHostKeyChangedException` / `NetworkAccessDeniedException` for the characteristic messages, and both are non-transient. Phase 03 stops the streaming data source from a futile reconnect on these.

---

## Rollback Plan

Revert the phase commit - classifier reverts to prior behaviour, no persisted state touched.
