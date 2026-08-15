# Phase 01 — Pool Extraction & Health Probe Foundation

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 1 / 7
**Started:** 2026-05-03
**Started:** —
**Completed:** —

---

## Objective

Extract the SMB connection pool data (`ConnectionKey`, `PooledConnection`, the `ConcurrentHashMap`, and the cascade-close sequence) out of `SmbConnectionManager.kt` (currently 1004 LOC, over 1500OC limit) into a new `SmbConnectionPool.kt` helper. Introduce a new `SmbConnectionHealthProbe.kt` helper with a structured `DeadReason` enum and a non-blocking socket-level liveness check. No behavior change yet — extraction must be functionally equivalent.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch dedicated to S0061.
- [ ] Backup of `SmbConnectionManager.kt` placed under `temp/` with timestamp (file is >500 LOC).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionHealthProbe.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 950 (must drop below 1500OC limit) |

---

## Steps

### Step 01.1 — Backup `SmbConnectionManager.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy the current `SmbConnectionManager.kt` to `temp/SmbConnectionManager.kt.<YYYYMMDD_HHmmss>.backup` before making any edits. The file is at the 1000-LOC limit and is being extracted; the backup is mandatory per project rule 5.

**Verification:**

- `Glob` — `temp/SmbConnectionManager.kt.*.backup` returns at least one match.

**Status:** `[ ]` not done

---

### Step 01.2 — Create `SmbConnectionPool` helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new `SmbConnectionPool` class that owns the `ConcurrentHashMap<ConnectionKey, PooledConnection>` (currently lines 153 of `SmbConnectionManager.kt`), the `cleanupScope`, and the cascade-close sequence (`removeConnection`, `closeConnectionAsync`, `closeAllConnections`). Expose: `get(key)`, `put(key, pooled)`, `remove(key)`, `removeAndCloseAsync(key)`, `closeAll()`, `snapshot()`. Keep the `ConnectionConsumer` enum here; do NOT add the `BACKGROUND_WORKER` variant yet (that belongs to Phase 04). Move the `PooledConnection` data class here. The class must be a plain (non-Hilt) helper instantiated by `SmbConnectionManager` in its `init` block — no DI changes.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt` exists.
- `Grep` — `class SmbConnectionPool` matches exactly once in that file.
- `Grep` — `data class PooledConnection` matches exactly once in that file.
- `Grep` — `enum class ConnectionConsumer` matches exactly once in that file.
- `Grep` — `fun closeAll` and `fun removeAndCloseAsync` both present in that file.
- `Grep` -n `Log\.d\(` returns zero hits in `SmbConnectionPool.kt` (Timber-only rule).

**Status:** `[ ]` not done

---

### Step 01.3 — Create `SmbConnectionHealthProbe` helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionHealthProbe.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new `SmbConnectionHealthProbe` class with: (1) a public `enum class DeadReason { TIMEOUT, BROKEN_PIPE, SERVER_RESET, AUTH_FAILED, SOCKET_CLOSED, UNKNOWN }`; (2) a public `fun isAlive(pooled: PooledConnection): Boolean` that returns `false` if any of the underlying objects are closed (`pooled.connection.isConnected == false`, `pooled.session` or `pooled.share` already closed); (3) a public `fun classify(throwable: Throwable): DeadReason` that maps `SocketException("Broken pipe")` → `BROKEN_PIPE`, `SocketException("Connection reset")` → `SERVER_RESET`, `TimeoutException` and `kotlinx.coroutines.TimeoutCancellationException` → `TIMEOUT`, `SMBApiException` with `STATUS_LOGON_FAILURE` → `AUTH_FAILED`, `IOException("Socket closed")` → `SOCKET_CLOSED`, anything else → `UNKNOWN`. The class must NOT make any network I/O — it is a pure inspector. Use `com.hierynomus.protocol.transport.TransportException` and `com.hierynomus.smbj.common.SMBRuntimeException` `cause` chain when classifying.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionHealthProbe.kt` exists.
- `Grep` — `class SmbConnectionHealthProbe` matches exactly once in that file.
- `Grep` — `enum class DeadReason` matches exactly once in that file.
- `Grep` — `fun isAlive` and `fun classify` both present.
- `Grep` — `BROKEN_PIPE`, `SERVER_RESET`, `AUTH_FAILED`, `TIMEOUT`, `SOCKET_CLOSED`, `UNKNOWN` all present.
- `Grep` -n `Log\.d\(` returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.4 — Migrate `SmbConnectionManager` to use `SmbConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the inline `connectionPool: ConcurrentHashMap<...>`, `cleanupScope`, and pool helper functions with delegation to a private `pool: SmbConnectionPool` instance. Remove the local `data class PooledConnection` and the `enum class ConnectionConsumer` — import them from `SmbConnectionPool`. Replace direct `connectionPool[key]` lookups with `pool.get(key)`, `connectionPool.remove` with `pool.remove`, `connectionPool[key] = newPooled` with `pool.put(key, newPooled)`, and the `closeAllConnections()` body with `pool.closeAll()`. Keep the existing public/internal API of `SmbConnectionManager` byte-identical from callers' perspective. Remove the `TODO(phase-decompose-smb)` comment at line 3 — extraction is now done. The file MUST drop below 1500OC after this step.

**Verification:**

- `Grep` — `private val pool: SmbConnectionPool` matches in `SmbConnectionManager.kt`.
- `Grep` — `connectionPool` (the old field name) returns zero hits in `SmbConnectionManager.kt`.
- `Grep` — `data class PooledConnection` returns zero hits in `SmbConnectionManager.kt` (moved to pool).
- `Grep` — `enum class ConnectionConsumer` returns zero hits in `SmbConnectionManager.kt` (moved to pool).
- `Grep` — `TODO(phase-decompose-smb)` returns zero hits in `SmbConnectionManager.kt`.
- Run `(Get-Content SmbConnectionManager.kt).Count` — expected value < 1000.

**Status:** `[ ]` not done

---

### Step 01.5 — Wire `SmbConnectionHealthProbe` into manager (no behavior change)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 01.3, Step 01.4

**Prompt for developer:**

> Add `private val healthProbe = SmbConnectionHealthProbe()` to `SmbConnectionManager`. Do NOT call its methods anywhere yet — wiring only. The probe will be used in Phase 02. This step exists so the import surface is established before logic changes.

**Verification:**

- `Grep` — `private val healthProbe: SmbConnectionHealthProbe` OR `private val healthProbe = SmbConnectionHealthProbe(` present in `SmbConnectionManager.kt`.
- `Grep` — `healthProbe\.` returns zero hits in `SmbConnectionManager.kt` (probe is not yet invoked — Phase 02).

**Status:** `[ ]` not done

---

### Step 01.6 — Update consumer references in dependent files

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt` (and any other callers of `ConnectionConsumer` / `PooledConnection`).
**Depends on:** Step 01.4

**Prompt for developer:**

> Run `Grep -r "SmbConnectionManager.ConnectionConsumer"` and `Grep -r "SmbConnectionManager.PooledConnection"` across `app_v2/src/`. For every match, change the import from `com.sza.fastmediasorter.data.network.SmbConnectionManager.ConnectionConsumer` (and `.PooledConnection`) to `com.sza.fastmediasorter.data.network.ConnectionConsumer` (and `.PooledConnection`). Do not change usage sites — only imports.

**Verification:**

- `Grep` — `SmbConnectionManager\.ConnectionConsumer` returns zero hits in `app_v2/src/`.
- `Grep` — `SmbConnectionManager\.PooledConnection` returns zero hits in `app_v2/src/`.
- `Grep` — `import com.sza.fastmediasorter.data.network.PooledConnection` OR equivalent qualified name resolves in `SmbDataSource.kt`.

**Status:** `[ ]` not done

---

### Step 01.7 — Build gate (BUILD-REQUIRED)

**Files:** none
**Depends on:** Step 01.6

**Prompt for developer:**

> Run `/build` → standard debug. The extraction must compile without warnings beyond what existed before; tests (if any in build) must pass.

**Verification:**

- `/build` standard debug returns PASS.
- `Grep` — `TODO(phase-01)` returns zero hits in `app_v2/src/main/`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `SmbConnectionManager.kt` line count < 1000.
- [ ] No call sites of `SmbConnectionManager.PooledConnection` / `SmbConnectionManager.ConnectionConsumer` remain.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase: `SmbConnectionPool` owns the in-memory pool and cascade-close; `SmbConnectionHealthProbe` exists with `isAlive` / `classify` API but is not yet invoked anywhere. `SmbConnectionManager` is below the 1000-LOC limit. Phase 02 will wire the probe into the acquire path.

---

## Rollback Plan

Revert phase commits — extraction is purely structural with no behavior change. The timestamped backup in `temp/` is the safety net.
