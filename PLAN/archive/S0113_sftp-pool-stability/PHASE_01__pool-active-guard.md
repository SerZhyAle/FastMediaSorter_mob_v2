# Phase 01 — Pool Active Guard

**Strategic spec:** [`../S0113_sftp-pool-stability.md`](../S0113_sftp-pool-stability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Add an active-stream reference counter to `SftpConnectionPool.PooledConnection`; make `cleanupIdleConnections()` skip PLAYBACK entries whose counter is non-zero; increment/decrement the counter in the ExoPlayer acquire/release path.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] No open research items block this phase.
- [ ] Working tree is clean or on a feature branch.
- [ ] Backup of `SftpConnectionPool.kt` created in `temp/` (file is 539 lines → backup required).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 600 |

---

## Steps

### Step 01.1 — Backup SftpConnectionPool.kt

**Files:** `temp/SftpConnectionPool_<timestamp>.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` to `temp/SftpConnectionPool_<YYYYMMDD_HHmmss>.kt` before any edits. The file is 539 lines — backup is mandatory per project rules.

**Verification:**

- `Glob` — `temp/SftpConnectionPool_*.kt` returns at least one match.

**Status:** `[ ]` not done

---

### Step 01.2 — Add activeStreamCount field to PooledConnection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `SftpConnectionPool.kt`, add `val activeStreamCount: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0)` to `PooledConnection` as a `val` field (not part of the primary constructor — declare it as a property inside the data class body, or change `data class` to a plain `class` if needed to accommodate a mutable non-constructor field). If `PooledConnection` is a `data class`, convert it to a regular `class` while preserving all existing fields; remove the `data` modifier. `activeStreamCount` must not be included in `equals`/`hashCode` — only the session identity matters.

**Verification:**

- `Grep` — `activeStreamCount` appears in `SftpConnectionPool.kt`.
- `Grep` — `AtomicInteger` imported in `SftpConnectionPool.kt`.
- `Grep` — `data class PooledConnection` does NOT appear (must be plain `class`).

**Status:** `[ ]` not done

---

### Step 01.3 — Increment counter on ExoPlayer acquire

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `getConnectionForExoPlayer()`, immediately before every `return ExoPlayerConnection(...)` call (there are three: reuse-connected-channel, new-channel-on-existing-session, new-session path), add `pooled.activeStreamCount.incrementAndGet()`. Do not call `incrementAndGet()` on the reference inside `ExoPlayerConnection` — always call it on the `PooledConnection` instance that is in `playbackConnectionPool`. Also add a `Timber.d("S0113: SFTP [PLAYBACK] acquired — active=${pooled.activeStreamCount.get()} host=${connectionInfo.host}")` line at each acquire point.

**Verification:**

- `Grep` — `activeStreamCount.incrementAndGet()` appears exactly 3 times in `SftpConnectionPool.kt`.
- `Grep` — `Timber.d("S0113:` appears in `SftpConnectionPool.kt`.

**Status:** `[ ]` not done

---

### Step 01.4 — Decrement counter on ExoPlayer release

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `releaseExoPlayerConnection()`, at the top of the method (before the `if (broken && channel != null)` branch), add a decrement: iterate `playbackConnectionPool.values` to find the `PooledConnection` that contains `channel` (or any pooled connection if `channel == null`), call `pooled.activeStreamCount.decrementAndGet()` clamped to 0 (`maxOf(0, current - 1)` using `updateAndGet`). Guard against going negative: use `updateAndGet { maxOf(0, it - 1) }`. If `channel` is null, decrement the first pooled connection for the key (this covers the "release without a channel reference" path).

**Verification:**

- `Grep` — `activeStreamCount` and `decrementAndGet\|updateAndGet` co-appear in `releaseExoPlayerConnection` body in `SftpConnectionPool.kt`.

**Status:** `[ ]` not done

---

### Step 01.5 — Guard idle cleanup against active PLAYBACK connections

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `cleanupIdleConnections()`, change the `playbackKeysToRemove` filter from:
> ```kotlin
> playbackConnectionPool.filter { (_, conn) -> now - conn.lastUsed > IDLE_TIMEOUT_MS }.keys
> ```
> to:
> ```kotlin
> playbackConnectionPool.filter { (_, conn) ->
>     now - conn.lastUsed > IDLE_TIMEOUT_MS && conn.activeStreamCount.get() == 0
> }.keys
> ```
> Also add a `Timber.d` when a PLAYBACK entry is skipped due to `activeStreamCount > 0`:
> ```kotlin
> playbackConnectionPool.filter { (_, conn) ->
>     val shouldEvict = now - conn.lastUsed > IDLE_TIMEOUT_MS && conn.activeStreamCount.get() == 0
>     if (!shouldEvict && now - conn.lastUsed > IDLE_TIMEOUT_MS) {
>         Timber.d("SFTP [PLAYBACK] idle cleanup skipped — active=${conn.activeStreamCount.get()}")
>     }
>     shouldEvict
> }.keys
> ```

**Verification:**

- `Grep` — `activeStreamCount.get() == 0` appears in `cleanupIdleConnections` body.
- `Grep` — `idle cleanup skipped` appears in `SftpConnectionPool.kt`.
- `Grep -n "Log\.d\("` — returns zero hits in `SftpConnectionPool.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `SftpConnectionPool.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `PooledConnection.activeStreamCount` is now a live counter; always non-negative.
- All PLAYBACK acquire/release paths update it atomically.
- Idle cleanup for PLAYBACK pool respects the guard.
- Phases 03 and 04 may now proceed (03 independently, 04 after research).

---

## Rollback Plan

Revert `SftpConnectionPool.kt` to the `temp/SftpConnectionPool_*.kt` backup. No data migration, no user-facing surface changed.
