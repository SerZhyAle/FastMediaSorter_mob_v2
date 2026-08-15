# Phase 01 — Pool eviction API

**Strategic spec:** [`../S0047_bugfix-sftp-pool-broken-channel.md`](../S0047_bugfix-sftp-pool-broken-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Extend the ExoPlayer release contract on `SftpConnectionPool` (and its `SftpClient` forwarder) so callers can return a specific `ChannelSftp` and signal whether it is broken; on a broken signal the pool disconnects and removes that channel from the pooled session. No DataSource changes in this phase — the new parameters get default values that preserve current behavior so the project still compiles.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (None — foundation phase.)
- [ ] Strategic §6 research items blocking this phase are Resolved. (All three resolved.)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 700 |

> Both files are well under 500 LOC; no backup step required.

---

## Steps

### Step 01.1 — Add `evictExoPlayerChannel` private method to `SftpConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new private method `evictExoPlayerChannel(channel: ChannelSftp)` placed right above `releaseExoPlayerConnection`. It must run under `synchronized(exoPlayerPoolLock)` to keep the same locking discipline as `getConnectionForExoPlayer`. Inside the lock, walk `connectionPool.values`; for the first `PooledConnection` whose `channels` list contains the given channel reference, call `channel.disconnect()` inside its own `try/catch` (log `Timber.w` on failure with the message), then remove the channel from `pooled.channels` and the parallel entry from `pooled.channelMutexes` at the same index. Log `Timber.d("SFTP ExoPlayer: Evicted broken channel from pool")` on success. If the channel is not found in any pool entry, log `Timber.d("SFTP ExoPlayer: Eviction skipped — channel not in pool")` and return without throwing.

**Verification:**

- `Grep` — `private fun evictExoPlayerChannel\(channel: ChannelSftp\)` matches once.
- `Grep` — `synchronized\(exoPlayerPoolLock\)` matches at least twice in the file (existing usage in `getConnectionForExoPlayer` plus the new one).
- `Grep` — `Evicted broken channel from pool` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt (+18 LOC). Dev log recorded.

---

### Step 01.2 — Extend `releaseExoPlayerConnection` signature with `channel` and `broken`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the current `fun releaseExoPlayerConnection()` with `fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false)`. Body order must be: (1) if `broken && channel != null` → call `evictExoPlayerChannel(channel)`; (2) `connectionSemaphore.release()`; (3) `cleanupIdleConnections()`. The default values (`null`, `false`) preserve the current behavior for any call site not yet migrated, so the build stays green between this step and Phase 02.

**Verification:**

- `Grep` — `fun releaseExoPlayerConnection\(channel: ChannelSftp\? = null, broken: Boolean = false\)` matches once.
- `Grep` — `connectionSemaphore.release\(\)` still matches inside that function (read with `-A 8` after the function header).
- `Grep` — `cleanupIdleConnections\(\)` still present in that function.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt (+3 LOC). Dev log recorded.

---

### Step 01.3 — Update `SftpClient` forwarder signature

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the existing one-line `fun releaseExoPlayerConnection() = pool.releaseExoPlayerConnection()` with `fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false) = pool.releaseExoPlayerConnection(channel, broken)`. Keep it adjacent to `getConnectionForExoPlayer`. Do not add new imports beyond `com.jcraft.jsch.ChannelSftp` (already imported).

**Verification:**

- `Grep` — `fun releaseExoPlayerConnection\(channel: ChannelSftp\? = null, broken: Boolean = false\)` matches once in `SftpClient.kt`.
- `Grep` — `pool\.releaseExoPlayerConnection\(channel, broken\)` matches once in `SftpClient.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SftpClient.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt (~1 LOC modified). Dev log recorded.

---

### Step 01.4 — Compile gate

**Files:** (no edits — verification only)
**Depends on:** Step 01.3

**Prompt for developer:**

> Trigger a full debug build via `/build` to confirm the default-parameter rollout did not break any existing call site of `releaseExoPlayerConnection`. Do not invoke gradle directly. Fix any compilation error before declaring the phase done.

**Verification:**

- `/build` reports `BUILD SUCCESSFUL` for the standard debug variant.
- `Grep -n "releaseExoPlayerConnection\(\)"` returns hits only in `SftpDataSource.kt` (which still uses the no-arg form via defaults — to be migrated in Phase 02).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. assembleStandardDebug → BUILD SUCCESSFUL in 1m. Sole no-arg call site at SftpDataSource.kt:194 (expected, defaults compile).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — `/build` clean for debug.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `SftpConnectionPool.kt` and `SftpClient.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Catalog regen deferred to Phase 03 (the public API surface is finalized only after Phase 02 binds it).

---

## Handoff Notes to Next Phase

- `SftpConnectionPool.releaseExoPlayerConnection(channel, broken)` and `SftpClient.releaseExoPlayerConnection(channel, broken)` are the only sanctioned release entry points. The DataSource will adopt them in Phase 02.
- Eviction does not touch the session — only the channel and its mutex slot. The next `getConnectionForExoPlayer` call will open a fresh channel on the same session via the existing `existing.channels.size < MAX_CHANNELS_PER_SESSION` branch.

---

## Rollback Plan

Revert this phase's commits — no on-disk migration, no protocol change, no user-visible surface. The pool reverts to the original "release without signal" contract.
