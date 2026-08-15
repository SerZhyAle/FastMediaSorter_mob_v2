# Phase 03 — SFTP Gate

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

Add `SftpConnectionGate` over existing `SftpConnectionPool` / `SftpClient`. Health-probe (`isConnected` + `Session.isOpen`) before lease; physical destruction of dead pooled `SshClient` on failure. Closes S0047 (sftp-pool-broken-channel) symptoms.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `SftpClient.kt` and `SftpConnectionPool.kt` exist (verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/SftpConnectionGate.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | Modified | ≤ 250 |

---

## Steps

### Step 03.1 — Add `isAlive` + `invalidate` hooks to `SftpConnectionPool`

**Files:** `data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add to `SftpConnectionPool`:
>
> - `internal fun isAlive(client: net.schmizz.sshj.SSHClient): Boolean` — returns `client.isConnected && client.isAuthenticated`. Catch any `Throwable` and return `false`.
> - `internal fun invalidate(resourceKey: String)` — looks up pooled entry by key, calls `client.disconnect()` ignoring errors, removes from map. Idempotent.
> - `internal fun lastRecreateMs(resourceKey: String): Long?` — returns recreate timestamp tracked in a new `ConcurrentHashMap<String, Long>`; updated whenever `invalidate` removes a live-but-broken client.

**Verification:**

- `Grep -n "fun isAlive" "SftpConnectionPool.kt"` matches once.
- `Grep -n "fun invalidate" "SftpConnectionPool.kt"` matches once.
- `Grep -n "fun lastRecreateMs" "SftpConnectionPool.kt"` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 03.2 — Expose `acquireRaw` for the gate

**Files:** `data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `internal suspend fun acquireRaw(info: SftpConnectionInfo, consumerTag: String): net.schmizz.sshj.SSHClient` that:
>
> 1. Reuses pooled `SSHClient` when present **and** `isAlive(it)` returns `true` and `lastSuccess` < 60 s ago. Otherwise call `invalidate(resourceKey)` first.
> 2. Performs `connect/authenticate` on a fresh client. Tags the client with `consumerTag` for `closeFor` filtering (use a sibling map `ConcurrentHashMap<SSHClient, String>`).
>
> Existing public `acquire(...)` methods stay unchanged.

**Verification:**

- `Grep -n "fun acquireRaw" "SftpConnectionPool.kt"` matches once.
- `Grep -n "consumerTag: String"` in the new function signature.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 03.3 — Implement `SftpConnectionGate`

**Files:** `data/network/lifecycle/SftpConnectionGate.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Define `@Singleton class SftpConnectionGate @Inject constructor(private val pool: SftpConnectionPool, private val diagnostics: ConnectionDiagnostics) : NetworkConnectionGate<SSHClient>`.
>
> - `override val protocol = NetworkProtocol.SFTP`
> - `acquire(consumer, resourceKey)` — extract `SftpConnectionInfo` from a credentials lookup helper (delegate to existing `SftpClient.connectionInfoForKey(resourceKey)`; if no such method exists, add it). Call `pool.acquireRaw(info, consumer.toSftpTag())` where `toSftpTag()` mirrors the SMB extension from Phase 02.
> - `release(connection, success)` — on success: `pool.markUsed(connection)` (add internal hook if missing). On failure: `pool.invalidateClient(connection)` and `diagnostics.recordRecreate(SFTP, resourceKey, reason)`.
> - `withRetry` — use the default interface implementation.
> - `closeFor(consumer)` — `pool.closeMatchingTag(consumer.toSftpTag())` (add internal hook if missing).
> - `lastRecreateMs(resourceKey)` — `pool.lastRecreateMs(resourceKey)`.
>
> Where the prompt says "add internal hook if missing", create the smallest internal method on `SftpConnectionPool` to expose the needed behavior without changing existing semantics.

**Verification:**

- `Glob` — `SftpConnectionGate.kt` exists.
- `Grep -n "class SftpConnectionGate"` matches once.
- `Grep -n "override val protocol = NetworkProtocol.SFTP"` matches once.
- `Grep -n "fun ConsumerType.toSftpTag"` matches once.

**Status:** `[ ]` not done

---

### Step 03.4 — Wire into DI registry

**Files:** `core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Update `provideRegistry` (introduced in Phase 02) to take an additional `sftp: SftpConnectionGate` parameter and call `register(sftp)` after `register(smb)`. Order is irrelevant — registry is a map.

**Verification:**

- `Grep -n "register(sftp)" "NetworkLifecycleModule.kt"` matches once.
- `Grep -n "sftp: SftpConnectionGate"` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 03.5 — Smoke-test S0047 symptom path

**Files:** none modified — verification only
**Depends on:** Step 03.4

**Prompt for developer:**

> Confirm that the symptom path of S0047 (sftp pool broken channel) is structurally addressed:
>
> 1. `Grep` for the two existing call-sites that previously surfaced `Channel is closed`: `SftpClient.readFileBytesRange`, `SshjMediaSource.readAt`. These remain unchanged in this phase — gate adoption is a future migration.
> 2. Confirm that `pool.invalidate` now physically removes a broken client (Step 03.1), so the next `acquire(..)` cannot return the same dead `SSHClient`.
>
> Add a one-line note in `SftpConnectionPool.kt` `// S0067/S0047: dead client physically removed via invalidate(); gate retry guarantees freshness.`

**Verification:**

- `Grep -n "S0067/S0047" "SftpConnectionPool.kt"` matches once.
- Manual S0047 reproduction not required at this phase — see INDEX completion gate (BlockNeedUserTest deferred).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `/build` `standardDebug` PASS.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry per "Files Touched".
- [ ] `Grep -n "Log\\.d\\(" SftpConnectionGate.kt` returns zero hits.

---

## Handoff Notes to Next Phase

After Phase 03 the registry contains `{SMB, SFTP}` gates. Existing `SftpClient` consumers continue to work; the gate is available to opt-in.

---

## Rollback Plan

Revert phase commit. New file is additive; pool changes are backward-compatible (new `internal fun`s, no signature changes on public methods).
