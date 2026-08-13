# Phase 01 - SFTP network invalidation

**Strategic spec:** [`../S0624_bugfix-sftp-scan-hang-network.md`](../S0624_bugfix-sftp-scan-hang-network.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03 (shares `SftpConnectionPool.kt`)
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Subscribe the SFTP stack to `NetworkStateMonitor` and force-close the connection pool on a network change, reaching behavioural parity with `SmbConnectionManager` (strategic Pillar A / FIX #1 / ADR-1).

---

## Prerequisites

- [ ] Strategic §6.5 (forced-reset lease safety) is Resolved - see [`research/05__forced-reset-lease-safety.md`](research/05__forced-reset-lease-safety.md).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 690 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 765 |

> Both files exceed 500 LOC (665 / 751). Take a timestamped backup of each into `temp/` before editing.

---

## Steps

### Step 01.1 - Add a non-suspend force-reset entry on the pool

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a public non-suspend method `fun disconnectAllOnNetworkChange()` to `SftpConnectionPool` that launches the existing `suspend fun disconnectAll()` on the pool's own `cleanupScope` (already declared as `CoroutineScope(SupervisorJob() + Dispatchers.IO)`). Do NOT block the caller (the network callback runs on a `ConnectivityManager` binder thread) and do NOT use `runBlocking`. The method must close sockets unconditionally - it delegates to `disconnectAll()`, never to the borrow-deferring `invalidate()` (see research/05 Part A: the parked scan never reaches its `finally` until the socket is closed). Add one concise WHY comment tying it to network-change parity with SMB.

**Verification:**

- `Grep` - `fun disconnectAllOnNetworkChange` matches exactly once in `SftpConnectionPool.kt`.
- `Grep` - the method body references `cleanupScope.launch` and `disconnectAll(`.
- `Grep` - the method body does NOT contain `runBlocking`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Verification 4/4 PASS. Files: SftpConnectionPool.kt (+11 LOC, `disconnectAllOnNetworkChange()`). Dev log recorded.

---

### Step 01.2 - Subscribe SftpClient to NetworkStateMonitor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inject `NetworkStateMonitor` into `SftpClient`'s `@Inject constructor` (add `private val networkStateMonitor: com.sza.fastmediasorter.core.network.NetworkStateMonitor`). In an `init { }` block register a `NetworkStateMonitor.NetworkChangeCallback` whose `onNetworkChanged()` and `onNetworkLost()` both call `pool.disconnectAllOnNetworkChange()`. Mirror the pattern in `SmbConnectionManager.kt:57-68`. `SftpClient` is `@Singleton`, so the subscription is registered once. `NetworkStateMonitor` depends only on `@ApplicationContext`, so injecting it introduces no DI cycle. Keep it a no-arg fire-and-forget like SMB's reconnect handling.

**Verification:**

- `Grep` - `networkStateMonitor: ` present in `SftpClient`'s constructor parameter list.
- `Grep` - `networkStateMonitor.registerCallback` matches once in `SftpClient.kt`.
- `Grep` - `disconnectAllOnNetworkChange()` is called from both `onNetworkChanged` and `onNetworkLost` (two call sites).
- `/build` - `standard debug` compiles (DI graph resolves).

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Static verification 4/4 PASS (ctor param, registerCallback ×1, two call sites, two overrides). Build validated at Phase Done Criteria. Files: SftpClient.kt (+15 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (compile-only symbol change; DI graph resolved).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [~] Dev log entry - batched at ticket finalization (CLAUDE.md §12: one entry per logical change).
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 04 (public API change: new pool method).

---

## Handoff Notes to Next Phase

- `SftpConnectionPool.disconnectAllOnNetworkChange()` is now the canonical force-reset hook; Phase 02's watchdog reuses `sftpClient.disconnectAll()` (the suspend variant) for its on-timeout force-close.
- Phase 03 also edits `SftpConnectionPool.kt` (keep-alive in `getOrCreateSession`); ensure no merge churn with Step 01.1's new method.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or user-facing surface changed. The subscription is additive; removing it restores prior (no-invalidation) behaviour.
