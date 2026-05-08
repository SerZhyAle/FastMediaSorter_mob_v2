# Phase 04 — Unified Session

**Strategic spec:** [`../S0113_sftp-pool-stability.md`](../S0113_sftp-pool-stability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Collapse `connectionPool` and `playbackConnectionPool` in `SftpConnectionPool` into a single `pooledSessions` map; route PLAYBACK and FILE_OPS channel requests through a shared SSH session per (host, port, user) triple; eliminate the two-session problem that exceeds server per-user session limits.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Research #1 (max JSch channels per session) is **Resolved** — safe `MAX_CHANNELS_PER_SESSION` confirmed.
- [ ] Research #2 (JSch `Session.openChannel()` thread-safety) is **Resolved** — locking strategy confirmed.
- [ ] `SftpConnectionPool.kt` backed up in `temp/` (file will be ≥500 lines after Phase 01).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 680 |

---

## Steps

### Step 04.1 — Add ChannelPurpose enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Phase 01 ✅ Done; Research #1 and #2 Resolved

**Prompt for developer:**

> Add a top-level (file-level) enum inside `SftpConnectionPool.kt` (or inside the class as a nested enum):
> ```kotlin
> enum class ChannelPurpose { PLAYBACK, FILE_OPS }
> ```
> Also add a `data class PooledChannel(val channel: ChannelSftp, val mutex: Mutex, val purpose: ChannelPurpose)` to replace the current parallel `channels: MutableList<ChannelSftp>` + `channelMutexes: MutableList<Mutex>` structure. Do not change functional behaviour yet — this step is structural scaffolding only.

**Verification:**

- `Grep` — `enum class ChannelPurpose` appears in `SftpConnectionPool.kt`.
- `Grep` — `data class PooledChannel` appears in `SftpConnectionPool.kt`.

**Status:** `[ ]` not done

---

### Step 04.2 — Migrate PooledConnection to use PooledChannel list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `PooledConnection`, replace:
> ```kotlin
> val channels: MutableList<ChannelSftp>
> val channelMutexes: MutableList<Mutex>
> ```
> with:
> ```kotlin
> val pooledChannels: MutableList<PooledChannel> = mutableListOf()
> ```
> Update all internal usages: `getOrCreateChannel()`, `removeChannel()`, `evictExoPlayerChannel()`, `cleanupIdleConnections()`, `disconnectAll()`, `withConnection()`. Where code references `channels[index]` and `channelMutexes[index]`, replace with `pooledChannels[index].channel` and `pooledChannels[index].mutex`. Keep `activeStreamCount` from Phase 01 unchanged.

**Verification:**

- `Grep` — `pooledChannels` appears in `SftpConnectionPool.kt`.
- `Grep` — `channelMutexes` does NOT appear in `SftpConnectionPool.kt`.
- Project compiles — run `/build`.

**Status:** `[ ]` not done

---

### Step 04.3 — Merge playbackConnectionPool into connectionPool

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Remove the `playbackConnectionPool` field. Rename `connectionPool` to `pooledSessions` to signal the unified purpose. In `getConnectionForExoPlayer()`:
> - Look up the key in `pooledSessions` (the unified map) instead of `playbackConnectionPool`.
> - When acquiring a channel: filter `pooledChannels` for existing channels with `purpose == ChannelPurpose.PLAYBACK` and `isConnected`; if found, reuse. If not found, create new with `purpose = PLAYBACK`.
>
> In `withConnection()` / `getOrCreateChannel()`:
> - Filter `pooledChannels` for `purpose == ChannelPurpose.FILE_OPS` channels.
> - Create new channels with `purpose = FILE_OPS`.
>
> Maintain `MAX_CHANNELS_PER_SESSION` as the combined total across both purposes (or split: `MAX_PLAYBACK_CHANNELS = 1`, `MAX_FILE_OPS_CHANNELS = 4` — use whichever Research #1 recommends).
>
> Apply the locking strategy confirmed by Research #2 around `session.openChannel()` calls.

**Verification:**

- `Grep` — `playbackConnectionPool` does NOT appear in `SftpConnectionPool.kt`.
- `Grep` — `pooledSessions` appears as the unified map field.
- `Grep` — `ChannelPurpose.PLAYBACK` and `ChannelPurpose.FILE_OPS` each appear at least once.

**Status:** `[ ]` not done

---

### Step 04.4 — Update cleanupIdleConnections for unified pool

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> `cleanupIdleConnections()` previously iterated two maps. Update it to iterate only `pooledSessions`. The PLAYBACK guard from Phase 01 (`activeStreamCount.get() == 0`) still applies: skip a session entirely if any PLAYBACK channel is active. When evicting, disconnect all `pooledChannels` (both purposes) and the session.

**Verification:**

- `Grep` — `playbackKeysToRemove` does NOT appear in `SftpConnectionPool.kt`.
- `Grep` — `pooledSessions` appears in `cleanupIdleConnections` body.

**Status:** `[ ]` not done

---

### Step 04.5 — Update disconnectAll for unified pool

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> `disconnectAll()` previously cleared two maps. Update to clear only `pooledSessions`, disconnecting all `pooledChannels` per session. Remove the `synchronized(exoPlayerPoolLock)` block that operated on `playbackConnectionPool` — it is no longer needed (the unified map uses `poolMutex`).

**Verification:**

- `Grep` — `playbackConnectionPool` does NOT appear anywhere in `SftpConnectionPool.kt`.
- `Grep` — `pooledSessions.clear()` appears in `disconnectAll`.

**Status:** `[ ]` not done

---

### Step 04.6 — Add S0113 diagnostic tag for unified session

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Add `Timber.d("S0113: SftpConnectionPool unified session — host=${info.host} playback=${pooled.pooledChannels.count { it.purpose == ChannelPurpose.PLAYBACK }} fileOps=${pooled.pooledChannels.count { it.purpose == ChannelPurpose.FILE_OPS }}")` at the point where a session is returned from the unified pool (once in `getConnectionForExoPlayer` and once in `getOrCreateChannel`).

**Verification:**

- `Grep` — `S0113: SftpConnectionPool unified session` appears in `SftpConnectionPool.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- One SSH session per (host, port, user) triple is now enforced.
- PLAYBACK and FILE_OPS channels share the session; server-side session-count limit can no longer be exceeded.
- The `exoPlayerPoolLock` object is removed (unified pool uses `poolMutex` consistently).

---

## Rollback Plan

Revert `SftpConnectionPool.kt` to the backup created in the prerequisite step. No data migration; no user-facing surface changed.
