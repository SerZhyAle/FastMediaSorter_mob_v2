# Phase 01 — session-pool-isolation

**Strategic spec:** [`../S0099_sftp-concurrent-access-fix.md`](../S0099_sftp-concurrent-access-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Add a dedicated `playbackConnectionPool` to `SftpConnectionPool` so ExoPlayer sessions are stored in a separate map from FILE_OPS sessions; a failure in one pool can no longer cascade into the other.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Pre-Implementation Blockers in INDEX.md are all ticked.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 560 |

> File is 511 lines — backup required before edit (Step 01.1).

---

## Steps

### Step 01.1 — Backup `SftpConnectionPool.kt`

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
> to `temp/SftpConnectionPool_S0099_backup.kt`.

**Verification:**

- `Glob` — `temp/SftpConnectionPool_S0099_backup.kt` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Files: temp/SftpConnectionPool_S0099_backup.kt (copy). Dev log N/A (temp file).

---

### Step 01.2 — Add `playbackConnectionPool` field; redirect `getConnectionForExoPlayer()` to use it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> **Change 1** — Add a new field immediately after `private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()`:
>
> ```kotlin
>     private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
>     private val playbackConnectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
> ```
>
> **Change 2** — Inside `getConnectionForExoPlayer()`, in the `synchronized(exoPlayerPoolLock)` block, change:
> ```kotlin
>             synchronized(exoPlayerPoolLock) {
>                 val existing = connectionPool[key]
> ```
> to:
> ```kotlin
>             synchronized(exoPlayerPoolLock) {
>                 val existing = playbackConnectionPool[key]
> ```
>
> **Change 3** — In the same `synchronized(exoPlayerPoolLock)` block near the end of `getConnectionForExoPlayer()`, change:
> ```kotlin
>                 connectionPool[key] = pooled
>
>                 Timber.d("SFTP ExoPlayer: New connection added to pool for ${connectionInfo.host}")
> ```
> to:
> ```kotlin
>                 playbackConnectionPool[key] = pooled
>
>                 Timber.d("SFTP ExoPlayer: New connection added to pool for ${connectionInfo.host}")
> ```

**Verification:**

- `Grep` — `private val playbackConnectionPool` present in `SftpConnectionPool.kt`.
- `Grep` — `val existing = playbackConnectionPool\[key\]` — 1 match.
- `Grep` — `playbackConnectionPool\[key\] = pooled` — 1 match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: SftpConnectionPool.kt (+2 LOC). Dev log pending phase end.

---

### Step 01.3 — Redirect `evictExoPlayerChannel()` to `playbackConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `evictExoPlayerChannel()`, change:
> ```kotlin
>         for (pooled in connectionPool.values) {
> ```
> to:
> ```kotlin
>         for (pooled in playbackConnectionPool.values) {
> ```

**Verification:**

- `Grep` — `for (pooled in playbackConnectionPool.values)` present in `SftpConnectionPool.kt`.
- `Grep` — `for (pooled in connectionPool.values)` — 0 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: SftpConnectionPool.kt (1 line changed). Dev log pending phase end.

---

### Step 01.4 — Extend `disconnectAll()` and `cleanupIdleConnections()` to cover `playbackConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> **Change 1** — In `disconnectAll()`, after the `finally { poolMutex.unlock() }` closing brace, append a `synchronized` block to disconnect and clear the playback pool:
>
> Replace the entire `disconnectAll()` function:
> ```kotlin
>     /** Disconnect every pooled session. Call on app shutdown. */
>     suspend fun disconnectAll() {
>         poolMutex.lock()
>         try {
>             connectionPool.values.forEach { pooled ->
>                 try {
>                     pooled.channels.forEach { channel ->
>                         if (channel.isConnected) channel.disconnect()
>                     }
>                     pooled.session.disconnect()
>                 } catch (_: Exception) {}
>             }
>             connectionPool.clear()
>         } finally {
>             poolMutex.unlock()
>         }
>     }
> ```
> with:
> ```kotlin
>     /** Disconnect every pooled session. Call on app shutdown. */
>     suspend fun disconnectAll() {
>         poolMutex.lock()
>         try {
>             connectionPool.values.forEach { pooled ->
>                 try {
>                     pooled.channels.forEach { channel ->
>                         if (channel.isConnected) channel.disconnect()
>                     }
>                     pooled.session.disconnect()
>                 } catch (_: Exception) {}
>             }
>             connectionPool.clear()
>         } finally {
>             poolMutex.unlock()
>         }
>         synchronized(exoPlayerPoolLock) {
>             playbackConnectionPool.values.forEach { pooled ->
>                 try {
>                     pooled.channels.forEach { channel ->
>                         if (channel.isConnected) channel.disconnect()
>                     }
>                     pooled.session.disconnect()
>                 } catch (_: Exception) {}
>             }
>             playbackConnectionPool.clear()
>         }
>     }
> ```
>
> **Change 2** — In `cleanupIdleConnections()`, replace the early-return guard and the existing `cleanupScope.launch` block:
>
> Replace:
> ```kotlin
>         if (keysToRemove.isEmpty()) return
>
>         cleanupScope.launch {
>             poolMutex.withLock {
>                 keysToRemove.forEach { key ->
>                     connectionPool.remove(key)?.let { pooled ->
>                         try {
>                             pooled.channels.forEach { channel ->
>                                 if (channel.isConnected) channel.disconnect()
>                             }
>                             pooled.session.disconnect()
>                             Timber.d("Closed idle SFTP connection to ${key.host} with ${pooled.channels.size} channels")
>                         } catch (e: Exception) {
>                             Timber.w("Error closing idle connection: ${e.message}")
>                         }
>                     }
>                 }
>             }
>         }
> ```
> with:
> ```kotlin
>         val playbackKeysToRemove = playbackConnectionPool.filter { (_, conn) ->
>             now - conn.lastUsed > IDLE_TIMEOUT_MS
>         }.keys
>
>         if (keysToRemove.isEmpty() && playbackKeysToRemove.isEmpty()) return
>
>         cleanupScope.launch {
>             poolMutex.withLock {
>                 keysToRemove.forEach { key ->
>                     connectionPool.remove(key)?.let { pooled ->
>                         try {
>                             pooled.channels.forEach { channel ->
>                                 if (channel.isConnected) channel.disconnect()
>                             }
>                             pooled.session.disconnect()
>                             Timber.d("SFTP [FILE_OPS] Closed idle connection to ${key.host}")
>                         } catch (e: Exception) {
>                             Timber.w("SFTP [FILE_OPS] Error closing idle connection: ${e.message}")
>                         }
>                     }
>                 }
>             }
>             playbackKeysToRemove.forEach { key ->
>                 playbackConnectionPool.remove(key)?.let { pooled ->
>                     try {
>                         pooled.channels.forEach { channel ->
>                             if (channel.isConnected) channel.disconnect()
>                         }
>                         pooled.session.disconnect()
>                         Timber.d("SFTP [PLAYBACK] Closed idle connection to ${key.host}")
>                     } catch (e: Exception) {
>                         Timber.w("SFTP [PLAYBACK] Error closing idle connection: ${e.message}")
>                     }
>                 }
>             }
>         }
> ```

**Verification:**

- `Grep` — `playbackConnectionPool.clear()` present in `SftpConnectionPool.kt`.
- `Grep` — `playbackKeysToRemove` present in `SftpConnectionPool.kt`.
- `Grep` — `SFTP \[PLAYBACK\] Closed idle` present in `SftpConnectionPool.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: SftpConnectionPool.kt (+22 LOC). Dev log pending phase end.

---

### Step 01.5 — Add `[PLAYBACK]` and `[FILE_OPS]` consumer-type tags to Timber calls

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> **Change 1** — Replace all occurrences of `"SFTP ExoPlayer:` with `"SFTP [PLAYBACK]` (use `replace_all: true`). This renames all ExoPlayer-path Timber messages consistently.
>
> **Change 2** — In `withConnection()`, add `[FILE_OPS]` to the two warning messages:
>
> Change `Timber.w("SFTP channel lost, removing from pool: ${e.message}")` to `Timber.w("SFTP [FILE_OPS] channel lost: ${e.message}")`.
>
> Change `Timber.w("SFTP session lost, retrying: ${e.message}")` to `Timber.w("SFTP [FILE_OPS] session lost, retrying: ${e.message}")`.
>
> Change `Timber.e(e, "SFTP operation failed")` to `Timber.e(e, "SFTP [FILE_OPS] operation failed")`.

**Verification:**

- `Grep` — `SFTP ExoPlayer:` — 0 matches in `SftpConnectionPool.kt`.
- `Grep` — `\[PLAYBACK\]` — ≥ 5 matches in `SftpConnectionPool.kt`.
- `Grep` — `\[FILE_OPS\]` — ≥ 3 matches in `SftpConnectionPool.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. [PLAYBACK]=11, [FILE_OPS]=5, old tags=0. Files: SftpConnectionPool.kt. Dev log pending phase end.

---

### Step 01.6 — Build

**Files:** —
**Depends on:** Step 01.5

**Prompt for developer:**

> Run `/build` (debug, any flavor). Build must succeed with zero errors.

**Verification:**

- Build exits with code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Build exit code 0. Dev log recorded.

---

## Phase Done Criteria

- [x] Every step above is `[x] done`.
- [x] Project compiles — `/build` exits 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entries added for `SftpConnectionPool.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`SftpConnectionPool.playbackConnectionPool` is now isolated from `connectionPool`. ExoPlayer sessions survive FILE_OPS invalidation. Proceed to Phase 02 (retry-with-backoff for FILE_OPS path).

---

## Rollback Plan

Revert phase commits — no schema change, no user-facing surface. Restore backup from `temp/SftpConnectionPool_S0099_backup.kt` if needed.
