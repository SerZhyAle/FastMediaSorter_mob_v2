# Phase 02 — Pre-acquire Health Check & SMBClient Purge

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Move the dead-connection detection BEFORE `Connection.authenticate()` and `Session.connectShare()` calls. On detection (or on the first attempt's transport-error retry), purge SMBJ's internal Connection cache by closing the corresponding `SMBClient` so the next attempt establishes a genuinely new TCP socket. Eliminate the bug where retry attempt 2 reuses the same dead Connection.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Backup of `SmbConnectionManager.kt` placed under `temp/` with timestamp.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 950 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt` | Modified | ≤ 320 |

---

## Steps

### Step 02.1 — Backup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy current `SmbConnectionManager.kt` to `temp/SmbConnectionManager.kt.<YYYYMMDD_HHmmss>.phase02.backup` before edits.

**Verification:**

- `Glob` — `temp/SmbConnectionManager.kt.*.phase02.backup` returns at least one match.

**Status:** `[ ]` not done

---

### Step 02.2 — Add `purgeClientForHost(host: String, port: Int)` to manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private function `purgeClientForHost(host: String, port: Int)` that:
> 1. Removes every entry from `pool` whose `ConnectionKey.server == host && port == port` via `pool.removeAndCloseAsync(key)` (use `pool.snapshot()` to iterate).
> 2. Calls `resetClients()` (already exists at line ~850) to close and null `normalClient`/`mediumClient`/`degradedClient`. This forces SMBJ's internal Connection cache to be discarded; the next `getClient()` lazy-init returns a brand-new `SMBClient` whose `connectionTable` is empty.
> Add one Timber.i log: `"SMB purge: host=$host:$port — connection cache cleared"`. The function MUST be idempotent and safe to call concurrently.

**Verification:**

- `Grep` — `private fun purgeClientForHost` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` -A 8 `private fun purgeClientForHost` shows `pool.snapshot()`, `resetClients()`, `Timber.i`.

**Status:** `[ ]` not done

---

### Step 02.3 — Apply pre-acquire health probe in `withConnection`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `withConnection()` (line ~267), after the line `val pooled = connectionPool[key]` (now `pool.get(key)`), insert: if `pooled != null && !healthProbe.isAlive(pooled)` then call `pool.removeAndCloseAsync(key)` and log `Timber.i("SMB pool entry dead — removing, key=${key.server}:${key.port}/${key.shareName}")`, then DO NOT continue with the pooled path — fall through to the fresh-connect path. Also lower the idle force-reset threshold from `CONNECTION_FORCE_RESET_MS` (3 minutes) — add a new constant `IDLE_HEALTH_RECHECK_MS = 30_000L` and after the `timeSinceLastSuccess > IDLE_HEALTH_RECHECK_MS` branch, call `healthProbe.isAlive` on the pooled entry; if dead → drop it as above. Keep the 3-minute `closeAllConnections + resetClients` branch intact for the catastrophic case.

**Verification:**

- `Grep` — `IDLE_HEALTH_RECHECK_MS` matches in `SmbConnectionManager.kt`.
- `Grep` — `healthProbe.isAlive` matches in `SmbConnectionManager.kt` at least twice (one in `withConnection`, one in `getConnectionForExoPlayer` after Step 02.4).
- `Grep` — `SMB pool entry dead — removing` literal matches.

**Status:** `[ ]` not done

---

### Step 02.4 — Apply pre-acquire health probe in `getConnectionForExoPlayer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `getConnectionForExoPlayer()` (line ~887), at the existing pooled-validity check (line ~901), replace `isConnectionValid(pooled)` with `healthProbe.isAlive(pooled) && isConnectionValid(pooled)`. Crucially: in the retry loop (line ~930) where `attempt == 1` fails with transport error, after `try { candidateConnection?.close() } catch (_: Exception) {}` ADD a call to `purgeClientForHost(connectionInfo.server, connectionInfo.port)` BEFORE `continue`. This is the core fix that makes attempt 2 actually open a new TCP socket. Replace the existing `Timber.w(...)` line (~969) with `Timber.i("SMB connect dead, reason=${healthProbe.classify(e)} — purging cache and retrying once")`.

**Verification:**

- `Grep` -B 2 -A 4 `purgeClientForHost\(connectionInfo.server` returns the line inside the `if (isTransportOrBrokenPipe(e) && attempt == 1)` block.
- `Grep` — `SMB connect dead, reason=` literal matches.
- `Grep` — `Broken pipe on ExoPlayer connect attempt` (the old log) returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.5 — Apply pre-acquire health probe in `createFreshConnection`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> In `createFreshConnection()` (line ~401), wrap the `client.connect(...)` + `connection.authenticate(...)` calls in a single try/catch. On catch, if `healthProbe.classify(e)` returns `BROKEN_PIPE`, `SERVER_RESET`, or `SOCKET_CLOSED`, call `purgeClientForHost(connectionInfo.server, connectionInfo.port)` and rethrow. This ensures the suspend `withConnection` retry path also benefits from cache purge between attempts. Do NOT add a local retry loop here — retry remains in `withConnection`'s outer while-loop; this step only adds the purge side-effect.

**Verification:**

- `Grep` — `private suspend fun createFreshConnection` declaration matches.
- `Grep` -A 30 `private suspend fun createFreshConnection` shows `purgeClientForHost` and `healthProbe.classify`.
- `Grep` — `STATUS_LOGON_FAILURE` (auth failure handling at line ~434) is preserved unchanged.

**Status:** `[ ]` not done

---

### Step 02.6 — Build gate (BUILD-REQUIRED)

**Files:** none
**Depends on:** Step 02.5

**Prompt for developer:**

> Run `/build` → standard debug. Build must pass; no new compile warnings beyond pre-phase baseline.

**Verification:**

- `/build` standard debug returns PASS.
- `Grep` — `TODO(phase-02)` returns zero hits in `app_v2/src/main/`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep` for the old log `Broken pipe on ExoPlayer connect attempt` returns zero hits.
- [ ] `purgeClientForHost` is invoked on every transport-error retry path in both `getConnectionForExoPlayer` and `createFreshConnection`.

---

## Handoff Notes to Next Phase

After this phase: dead connections are detected before session-setup, and the SMBJ-internal Connection cache is purged on transport failure so the second attempt actually gets a fresh TCP socket. Retry logic still exists in two places (sync `getConnectionForExoPlayer` and suspend `withConnection`); Phase 03 unifies them.

---

## Rollback Plan

Revert phase commits. The probe additions are surgical; the only behavior-affecting change is the addition of `purgeClientForHost` on the failure path — removing the call returns to pre-fix behavior. No data migration involved.
