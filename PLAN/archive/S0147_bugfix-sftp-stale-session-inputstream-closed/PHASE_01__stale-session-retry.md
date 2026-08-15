# Phase 01 — Stale Session Retry

**Strategic spec:** [`../S0147_bugfix-sftp-stale-session-inputstream-closed.md`](../S0147_bugfix-sftp-stale-session-inputstream-closed.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Extend `SftpConnectionPool.withConnection()` to detect dead-transport `IOException`s (such as `"inputstream is closed"`) that bypass the existing `isConnected` checks, then force session invalidation and a single transparent retry — matching the already-implemented session-lost recovery path.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] INDEX Pre-Implementation Blockers §6.1 and §6.2 are resolved (Step 01.1 covers this).
- [x] Working tree is clean or on a feature branch.
- [x] `SftpConnectionPool.kt` is read before editing — existing `withConnection` exception handler (lines ~83–95) is understood.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 480 |

> File is currently 447 lines — no backup required (under 500). Adding ~25 lines brings it to ~472.

---

## Steps

### Step 01.1 — Resolve JSch dead-transport signal set (research)

**Files:** `SftpConnectionPool.kt` (companion constant only)
**Depends on:** — start of phase

**Prompt for developer:**

> Read JSch source `Channel.java` and `Session.java` (available in the JSch jar sources or on GitHub at `mwiede/jsch`). Find every `IOException` message that indicates a broken underlying transport (not an SFTP-protocol error). Known confirmed signal from field log: `"inputstream is closed"`. Typical additional signals: `"channel is not opened"` (from `Channel.checkConnected`), `"Broken pipe"` (from `OutputStream`). Verify that `Session.isConnected()` is a simple field read (`return _isConnected`) and does NOT call `socket.isConnected()` — confirming it stays `true` after a silent TCP drop. Record the final set as a `companion object` constant list `DEAD_TRANSPORT_MESSAGES` of lowercase substrings inside `SftpConnectionPool`. Mark INDEX blockers §6.1 and §6.2 `[x]` resolved.

**Verification:**

- `Grep` — `DEAD_TRANSPORT_MESSAGES` matches exactly once in `SftpConnectionPool.kt` (companion declaration).
- `Grep` — `"inputstream is closed"` present in `SftpConnectionPool.kt` (as part of the constant).
- `Grep` — `Log\.d\(` returns zero hits in `SftpConnectionPool.kt`.

**Status:** `[x] done`
Resolved: `DEAD_TRANSPORT_MESSAGES` = ["inputstream is closed", "channel is not opened", "broken pipe"]. Session.isConnected() is a simple field read (`return _isConnected`) — confirmed via JSch source; does not re-check socket, stays true after silent TCP drop.

---

### Step 01.2 — Add `isDeadTransportException` private predicate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a private member function `isDeadTransportException(e: Exception): Boolean` to `SftpConnectionPool`. It returns `true` iff:
> 1. `e` is an `IOException` (not a `SftpException` — those are SFTP-protocol errors, not transport failures), AND
> 2. the lowercase of `e.message` contains any substring from `DEAD_TRANSPORT_MESSAGES`.
>
> Place the function near the bottom of the class body, before any companion object. Do not throw or catch inside the predicate — it is a pure Boolean check.

**Verification:**

- `Grep` — `fun isDeadTransportException` matches exactly once in `SftpConnectionPool.kt`.
- `Grep` — `e !is IOException` (or equivalent `e is IOException` guard) present in the predicate body.
- `Grep` — `DEAD_TRANSPORT_MESSAGES` referenced inside `isDeadTransportException`.

**Status:** `[x] done`

---

### Step 01.3 — Extend `withConnection` exception handler with dead-transport retry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `SftpConnectionPool.withConnection()`, inside the `catch (e: Exception)` block (currently lines ~83–95), add a third branch **before** `throw e`:
>
> ```kotlin
> if (isDeadTransportException(e)) {
>     Timber.w("SFTP [FILE_OPS] dead transport detected (${e.message}), reconnecting")
>     removeChannel(pooled, pc.channel)
>     invalidateSession(key)
>     val newPooled = getOrCreateSession(key, info)
>     newPooled.lastUsed = System.currentTimeMillis()
>     val newPc = getOrCreateFileOpsChannel(newPooled, info)
>     return@withContext newPc.mutex.withLock { block(newPc.channel) }
> }
> ```
>
> Keep the existing `!pc.channel.isConnected` and `!pooled.session.isConnected` branches untouched above this addition. The new branch fires only when `isDeadTransportException` returns `true` AND the existing `isConnected`-based checks did not already handle the reconnect. Do not wrap the retry in another try/catch — if the retry fails, the exception propagates naturally.

**Verification:**

- `Grep` — `isDeadTransportException` referenced inside `withConnection` in `SftpConnectionPool.kt`.
- `Grep` — `"SFTP [FILE_OPS] dead transport detected"` present in `SftpConnectionPool.kt` (Timber log line).
- `Grep` — `throw e` still present after the new branch (not removed — it remains the default for non-dead-transport errors).
- `Grep` — `!pc.channel.isConnected` still present (existing channel-lost branch not disturbed).

**Status:** `[x] done`

---

### Step 01.4 — Insert S0147 BlockNeedUserTest debug tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> At the entry of `withConnection` (first line inside `withContext(Dispatchers.IO) {`, before `connectionSemaphore.acquire()`), insert:
>
> ```kotlin
> Timber.d("S0147: withConnection host=${info.host}")
> ```
>
> This is the on-device verification probe. It is removed when the ticket reaches `Verified`.

**Verification:**

- `Grep` — `Timber.d("S0147: withConnection` matches exactly once in `SftpConnectionPool.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `SftpConnectionPool.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scan.ps1 -Module app_v2` (no public API change expected; verify).
- [x] Spec status advanced to `BlockNeedUserTest` — `update.ps1 -Id S0147 -Status BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

`SftpConnectionPool.withConnection` now detects `"inputstream is closed"` (and peer dead-transport signals) as a special case, invalidates the session, and retries once. The `throw e` final fallback remains for all other exception types. Phase 02 handles docs and catalog cleanup.

---

## Rollback Plan

Revert the Phase 01 commit — removes `DEAD_TRANSPORT_MESSAGES`, `isDeadTransportException`, and the new branch from `withConnection`. No data migration; no user-facing surface changed. Prior behaviour (exception propagated to caller) is restored.
