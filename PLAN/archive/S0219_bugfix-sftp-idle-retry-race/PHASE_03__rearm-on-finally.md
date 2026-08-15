# Phase 03 — Rearm idle timer in finally (Pillar C)

**Strategic spec:** [`../S0219_bugfix-sftp-idle-retry-race.md`](../S0219_bugfix-sftp-idle-retry-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Move `armTransport(connectionInfo)` out of the "result.isSuccess" branch in `SftpClient.withConnection` and `SftpClient.openInputStream` so that the idle timer is rearmed on every completion path — success, plain failure, and pool-side dead-transport recovery — except `CancellationException` (preserving the S0205 invariant that user-initiated cancellation does not trigger background work). The rearm runs only if the transport is still in `trackedTransportKeys` at the point of finally execution, so a `disconnectAll()` path naturally skips it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (the previous backup `temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak` is sufficient as a rollback anchor; Phase 03 takes a fresh snapshot for its own diff range).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 760 |
| `temp/SftpClient.kt.2026-05-16__pre-S0219-phase03.bak` | New (backup) | n/a |

---

## Steps

### Step 03.0 — Snapshot the file

**Files:** `temp/SftpClient.kt.2026-05-16__pre-S0219-phase03.bak`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` to `temp/SftpClient.kt.2026-05-16__pre-S0219-phase03.bak`. Mandatory pre-edit safeguard.

**Verification:**

- `Glob` — `temp/SftpClient.kt.2026-05-16__pre-S0219-phase03.bak` exists.
- expected: SHA-256 of backup equals SHA-256 of current source | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 03.1 — Move armTransport to finally in withConnection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 03.0

**Prompt for developer:**

> Rewrite `private suspend fun <T> withConnection(info, block): Result<T>` (≈108-121) so the body is:
>
> ```kotlin
> lifecycleBootstrapper.get().ensureInitialized()
> reachabilityGate.requireAnyNetwork("SFTP")
> val transportKey = rememberTransportKey(info)
> idleDisconnectPolicy.touch(transportKey)
> Timber.d("S0219: SftpClient.withConnection enter transport=$transportKey")
> var cancelled = false
> try {
>     return pool.withConnection(info, block)
> } catch (e: CancellationException) {
>     cancelled = true
>     throw e
> } finally {
>     if (!cancelled && trackedTransportKeys.contains(transportKey)) {
>         armTransport(info)
>     }
> }
> ```
>
> The two surface-level changes: (1) the rearm decision no longer depends on `result.isSuccess`; (2) `CancellationException` is observed via a guard flag so the finally can skip rearm. The behavior for the happy path is unchanged (success → rearm). The behavior for a `Result.failure(...)` returned by the pool is now also rearm (previously: silent drop-off — root cause of strategic §1 paragraph 3). The behavior for `CancellationException` is unchanged: no rearm, exception propagates.
>
> Note: `pool.withConnection` returns `Result<T>` and does not throw for ordinary failures, so this try/catch sees only `CancellationException` (and any unexpected programming error, which also bypasses rearm — acceptable).

**Verification:**

- `Grep` — `pattern: 'if \(result\.isSuccess\) \{[\s\n]+armTransport' | -n true | multiline: true` → expected: 0 matches in `withConnection` body | actual: <fill>.
- `Grep` — `pattern: 'S0219: SftpClient\.withConnection enter transport=' | -n true` → expected: 1 match | actual: <fill>.
- `Grep` — `pattern: 'cancelled = true' -B 1 -A 1 | -n true` → expected: 1 match inside `withConnection` (the CancellationException guard) | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 03.2 — Apply the same pattern to openInputStream

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Rewrite `suspend fun openInputStream(connectionInfo, remotePath): Result<InputStream>` (≈698-709) so it mirrors Step 03.1: touch → try/finally with `cancelled` guard around `pool.openInputStream(connectionInfo, remotePath)`. Rearm in finally only when `!cancelled && trackedTransportKeys.contains(transportKey)`. Add `Timber.d("S0219: SftpClient.openInputStream enter transport=$transportKey path=$remotePath")` at the start.
>
> Note: `openInputStream` returns `Result<InputStream>` — the underlying `InputStream` lifetime extends past the function return, but the idle-disconnect concern is about the SFTP transport's pool state, not the stream itself. The pool's `cleanupIdleConnections` already honors `activeBorrowCount` (after Phase 02), so a long-lived input stream keeps the session alive independently of the idle timer.

**Verification:**

- `Grep` — `pattern: 'if \(result\.isSuccess\) \{[\s\n]+armTransport' | -n true | multiline: true` → expected: 0 matches anywhere in `SftpClient.kt` | actual: <fill>.
- `Grep` — `pattern: 'S0219: SftpClient\.openInputStream enter' | -n true` → expected: 1 match | actual: <fill>.
- `Grep` — `pattern: 'cancelled = true' | -n true | -o true` → expected: 2 matches (one in withConnection, one in openInputStream) | actual: <fill>.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep` for `if \(result\.isSuccess\)[\s\n]+armTransport` (multiline) returns zero hits in `SftpClient.kt`.
- [ ] Dev log entry added for `SftpClient.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 03, the idle policy is rearmed on every non-cancellation completion of every SFTP operation. Strategic §2.3 ("неуспешная SFTP-операция оставляет idle-учёт транспорта в корректном состоянии") is fully satisfied. The trio A+B+C now closes all three sources of the original symptom. Phase 04 finalizes catalog, log, and ticket status.

---

## Rollback Plan

Restore `temp/SftpClient.kt.2026-05-16__pre-S0219-phase03.bak`. No public API change. Revert the dev log entry.
