# Phase 04 — FTP Gate

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Add `FtpConnectionGate` over `FtpClient` / `FtpExoPlayerPool`. Health-probe via `FTPClient.isConnected` (always) and `NOOP` (only when idle ≥ 60 s, per §6.3). Physical destruction of dead pooled `FTPClient` on broken-pipe.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `FtpClient.kt` and `FtpExoPlayerPool.kt` exist (verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/FtpConnectionGate.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | Modified | ≤ 300 |

---

## Steps

### Step 04.1 — Add `isAlive(noopIfIdle)` + `invalidate` to `FtpExoPlayerPool`

**Files:** `data/remote/ftp/FtpExoPlayerPool.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following internal hooks:
>
> - `internal const val IDLE_HEALTH_RECHECK_MS = 60_000L` (top-level companion).
> - `internal fun isAlive(client: org.apache.commons.net.ftp.FTPClient, idleSinceLastSuccessMs: Long): Boolean`:
>   1. If `!client.isConnected` → false.
>   2. If `idleSinceLastSuccessMs < IDLE_HEALTH_RECHECK_MS` → return `true` immediately (cheap path).
>   3. Otherwise execute `client.sendNoOp()` inside a `try/catch`. Return its boolean result; on any exception return `false`.
> - `internal fun invalidate(resourceKey: String)` — close pooled client (`disconnect()`), remove from map, update `lastRecreateMs`.
> - `internal fun lastRecreateMs(resourceKey: String): Long?` — same as SFTP.
> - `internal fun lastSuccessMs(resourceKey: String): Long?` — read existing tracking; if not present add `ConcurrentHashMap<String, Long>` updated by existing success paths.

**Verification:**

- `Grep -n "fun isAlive" "FtpExoPlayerPool.kt"` matches once.
- `Grep -n "client.sendNoOp"` matches once.
- `Grep -n "IDLE_HEALTH_RECHECK_MS = 60_000L"` matches once.
- `Grep -n "fun invalidate" "FtpExoPlayerPool.kt"` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 04.2 — Expose `acquireRaw` to gate

**Files:** `data/remote/ftp/FtpExoPlayerPool.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `internal suspend fun acquireRaw(info: FtpConnectionInfo, consumerTag: String): FTPClient`. Behavior:
>
> 1. Look up pooled client by `resourceKey`. If present and `isAlive(it, now - lastSuccess)` → return it.
> 2. Otherwise `invalidate(key)` and connect a fresh `FTPClient` (login, passive mode, control encoding) — reuse existing connect helper if present.
> 3. Tag client with `consumerTag` in `ConcurrentHashMap<FTPClient, String>`.

**Verification:**

- `Grep -n "fun acquireRaw" "FtpExoPlayerPool.kt"` matches once.
- `Grep -n "isAlive(" "FtpExoPlayerPool.kt"` matches >= 1.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 04.3 — Implement `FtpConnectionGate`

**Files:** `data/network/lifecycle/FtpConnectionGate.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Define `@Singleton class FtpConnectionGate @Inject constructor(private val pool: FtpExoPlayerPool, private val diagnostics: ConnectionDiagnostics) : NetworkConnectionGate<FTPClient>`.
>
> - `override val protocol = NetworkProtocol.FTP`
> - `acquire(consumer, resourceKey)` — derive `FtpConnectionInfo` via `FtpClient.connectionInfoForKey(resourceKey)` (add helper if missing). Call `pool.acquireRaw(info, consumer.toFtpTag())`.
> - `release(connection, success)` — on success: `pool.markUsed(connection)`. On failure: `pool.invalidateClient(connection)` + `diagnostics.recordRecreate(FTP, resourceKey, reason)`.
> - `withRetry` — default interface implementation.
> - `closeFor(consumer)` — `pool.closeMatchingTag(consumer.toFtpTag())`.
> - `lastRecreateMs(resourceKey)` — `pool.lastRecreateMs(resourceKey)`.

**Verification:**

- `Glob` — `FtpConnectionGate.kt` exists.
- `Grep -n "class FtpConnectionGate"` matches once.
- `Grep -n "override val protocol = NetworkProtocol.FTP"` matches once.
- `Grep -n "fun ConsumerType.toFtpTag"` matches once.

**Status:** `[ ]` not done

---

### Step 04.4 — Wire into DI registry

**Files:** `core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Update `provideRegistry` to take `ftp: FtpConnectionGate` parameter and call `register(ftp)`.

**Verification:**

- `Grep -n "register(ftp)" "NetworkLifecycleModule.kt"` matches once.
- `Grep -n "ftp: FtpConnectionGate"` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 04.5 — Add S0061-style cascade-prevention comment

**Files:** `data/remote/ftp/FtpExoPlayerPool.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Add a one-line note near `invalidate`: `// S0067: physical destruction prevents Broken-pipe cascade across siblings (S0061-pattern).`

**Verification:**

- `Grep -n "S0067" "FtpExoPlayerPool.kt"` matches once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `/build` `standardDebug` PASS.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry per "Files Touched".

---

## Handoff Notes to Next Phase

Registry now holds `{SMB, SFTP, FTP}`. Phase 05 adds Cloud (token-only). Phase 06 wires lifecycle observer.

---

## Rollback Plan

Revert phase commit — additive changes only.
