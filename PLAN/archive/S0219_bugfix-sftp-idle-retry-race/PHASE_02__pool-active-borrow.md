# Phase 02 — Active-borrow tracking in SftpConnectionPool (Pillar B)

**Strategic spec:** [`../S0219_bugfix-sftp-idle-retry-race.md`](../S0219_bugfix-sftp-idle-retry-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Prevent `IdleDisconnectPolicy` callback (and any other invalidation path) from disconnecting a `PooledConnection.session` while a FILE_OPS borrower is mid-block. Generalize the existing `activeStreamCount` field to cover both PLAYBACK streams and FILE_OPS borrows under a single counter; in `invalidateSession`, when the counter is non-zero, defer the actual `session.disconnect()` to a `cleanupScope` watchdog that fires once the last borrower releases. The pool's map entry is removed immediately under `poolMutex`, so no new lookup can resurrect the soon-to-die session — satisfying strategic §2.2.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` is 573 LOC → timestamped backup in `temp/` required before edit (rule §5 of CLAUDE.md).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 620 |
| `temp/SftpConnectionPool.kt.2026-05-16__pre-S0219-phase02.bak` | New (backup) | n/a |

---

## Steps

### Step 02.0 — Snapshot the file

**Files:** `temp/SftpConnectionPool.kt.2026-05-16__pre-S0219-phase02.bak`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` to `temp/SftpConnectionPool.kt.2026-05-16__pre-S0219-phase02.bak`. Mandatory pre-edit safeguard for any file >500 LOC.

**Verification:**

- `Glob` — `temp/SftpConnectionPool.kt.2026-05-16__pre-S0219-phase02.bak` exists.
- expected: SHA-256 of backup equals SHA-256 of current source | actual: <fill at execution>.

**Status:** `[ ]` not done

---

### Step 02.1 — Rename activeStreamCount → activeBorrowCount

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 02.0

**Prompt for developer:**

> Rename the `activeStreamCount: AtomicInteger` field on `PooledConnection` (around line 58) to `activeBorrowCount: AtomicInteger`. Update every internal read/write — there are call sites in `getConnectionForExoPlayer` (≈301, 312, 320), `releaseExoPlayerConnection` (≈370, 374), `cleanupIdleConnections` (≈230, 232), and `invalidateSession` (≈210). The semantics widen from "ExoPlayer is streaming" to "any borrower (PLAYBACK stream or FILE_OPS block) holds this pooled session". Update the inline KDoc on the field accordingly: «non-zero while any borrower is active; idle cleanup and invalidation both honor this counter». Update the Timber log lines that mention `active=` to keep printing the same number under the new name — do not change the log shape because operators rely on grep predicates.

**Verification:**

- `Grep` — `pattern: 'activeStreamCount' | -n true` → expected: 0 matches | actual: <fill>.
- `Grep` — `pattern: 'activeBorrowCount' | -n true | -o true` → expected: ≥ 8 matches | actual: <fill>.
- `Grep` — `pattern: 'active=' -A 0 | -n true` → expected: still present in the existing Timber.d lines (log shape preserved) | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 02.2 — Track FILE_OPS borrows in withConnection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `suspend fun <T> withConnection(...)` (≈76-131), wrap the `pc.mutex.withLock { block(pc.channel) }` call so that:
>
> 1. Before entering the channel mutex, increment `pooled.activeBorrowCount`.
> 2. Run the existing try/catch around `pc.mutex.withLock { block(pc.channel) }` unchanged — the dead-transport retry from S0147 must keep working as written. Any retry path that re-enters `getOrCreateSession(...)` for a fresh `newPooled` must increment `newPooled.activeBorrowCount` BEFORE the retried `newPc.mutex.withLock { block(newPc.channel) }` and decrement it in the same finally block as the outer borrow.
> 3. In a `finally` block (between the inner try and the outer `connectionSemaphore.release()`), decrement `pooled.activeBorrowCount` (and `newPooled.activeBorrowCount` if the retry path was taken — track the actual borrowed connection in a local var to handle the swap).
> 4. After decrement, if the pooled connection is no longer present in `pooledSessions` under its key AND `activeBorrowCount` reaches 0, call a new private helper `disconnectOrphan(pooled)` that disconnects all of `pooled.pooledChannels` and `pooled.session`. This is the "last user releases an orphan" path.
> 5. Add `Timber.d("S0219: SftpConnectionPool.withConnection borrow tracked host=${info.host} active=${pooled.activeBorrowCount.get()}")` right after the first increment.
>
> Implement `disconnectOrphan(pooled: PooledConnection)` as a private synchronized helper that wraps both per-channel and session disconnect in their own try/catch and logs `SFTP orphan session disconnected after last borrower released`.

**Verification:**

- `Grep` — `pattern: 'activeBorrowCount\.incrementAndGet' | -n true | -o true` → expected: ≥ 3 matches (FILE_OPS first borrow, FILE_OPS retry borrow, plus the pre-existing PLAYBACK call) | actual: <fill>.
- `Grep` — `pattern: 'activeBorrowCount\.decrementAndGet|updateAndGet' | -n true | -o true` → expected: ≥ 3 matches | actual: <fill>.
- `Grep` — `pattern: 'private fun disconnectOrphan' | -n true` → expected: 1 match | actual: <fill>.
- `Grep` — `pattern: 'S0219: SftpConnectionPool\.withConnection borrow tracked' | -n true` → expected: 1 match | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 02.3 — Defer disconnect in invalidateSession when borrowers active

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `private suspend fun invalidateSession(key: ConnectionKey)` (≈205-223), after `pooledSessions.remove(key)?.let { pooled -> ... }`, branch on `pooled.activeBorrowCount.get()`:
>
> - `== 0`: existing path — disconnect every channel and `pooled.session` synchronously, log `SFTP invalidated session with N channels` (unchanged from current behavior except the field rename).
> - `> 0`: do NOT call `pooled.session.disconnect()`. Log `SFTP invalidate deferred for ${key.host} (activeBorrow=N) — last borrower will disconnect`. The pooled connection is already removed from `pooledSessions` map at this point, so no new caller can see it. Step 02.2's `disconnectOrphan` path is responsible for the actual disconnect once the last borrower releases.
>
> Remove the existing `if (pooled.activeStreamCount.get() == 0) ... else ...` block (it was PLAYBACK-only and the comment about ExoPlayer is now stale) and replace it with the new shared logic above. Add `Timber.d("S0219: SftpConnectionPool.invalidateSession path=${if (deferred) "deferred" else "immediate"} host=${key.host}")` after the branch decision.

**Verification:**

- `Grep` — `pattern: 'SFTP invalidate deferred' | -n true` → expected: 1 match | actual: <fill>.
- `Grep` — `pattern: 'S0219: SftpConnectionPool\.invalidateSession path=' | -n true` → expected: 1 match | actual: <fill>.
- `Grep` — `pattern: 'PLAYBACK active — deferred disconnect' | -n true` → expected: 0 matches (old log line removed) | actual: <fill>.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep` for `activeStreamCount` in `app_v2/` returns zero hits.
- [ ] `Grep` for `disconnectOrphan` returns exactly one declaration and at least one call site.
- [ ] `Grep` for `S0219: SftpConnectionPool\.` returns at least two matches (withConnection borrow tracked, invalidateSession path).
- [ ] Dev log entry added for `SftpConnectionPool.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 02, an in-flight FILE_OPS borrower is never disconnected mid-block by an idle-policy callback. The strategic §2.2 invariant ("idle-policy says inactive ⟹ pool will not hand out the session") holds because invalidation removes the map entry immediately; ongoing borrowers complete their op against the soon-to-be-orphaned session, and the last one disconnects it. Phase 03 builds on this by ensuring the idle timer itself never silently drops out of arm state after a failed op.

---

## Rollback Plan

Restore `temp/SftpConnectionPool.kt.2026-05-16__pre-S0219-phase02.bak`. No public API change — `getConnectionForExoPlayer` / `releaseExoPlayerConnection` / `withConnection` / `invalidate` signatures are preserved. No external caller observes the rename of `activeStreamCount` (private field). Revert the dev log entry.
