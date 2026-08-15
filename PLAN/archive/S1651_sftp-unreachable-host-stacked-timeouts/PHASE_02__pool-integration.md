# Phase 02 - Pool Integration

**Strategic spec:** [`../S1651_sftp-unreachable-host-stacked-timeouts.md`](../S1651_sftp-unreachable-host-stacked-timeouts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Integrate the recent-failure cache at the serialized SFTP session-creation boundary and prove that recovery paths clear it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Take the pre-edit backup CLAUDE.md Rule 5 requires for a source file over 500 LOC.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 850 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPoolTest.kt` | Modified | ≤ 300 |

---

## Steps

### Step 02.1 - Gate session creation by recent eligible failures

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Take the Rule 5 backup before editing. Own one failure-cache instance in the pool and query it while holding the existing per-connection creation lock, before a new SSH handshake. Record only a qualifying failed handshake under that same lock, remove the record after a successful handshake, and rethrow the original cached or fresh failure so the existing `Result.failure` and caller messaging remain unchanged. Do not cache failures from opening or operating an already established SFTP channel.

**Why:**

The pool is the only boundary shared by cleanup, listings, paging, and counting, so placing the guard elsewhere would leave one or more full connection timeouts intact.

**Verification:**

- `Grep` - `SftpConnectionFailureCache` is present in `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`.
- `Grep` - the cache lookup occurs before `session.connect(CONNECTION_TIMEOUT)` in that file.
- `Grep` - the cache record is inside the session-creation failure path in that file.
- `Grep` - `Log.d(` returns zero hits in that file.

**Result:** Rule 5 backup taken before the edit. `SftpConnectionFailureCache` owned at line 97. Guard `failFastIfRecentlyUnreachable` at line 258 precedes `session.connect(CONNECTION_TIMEOUT)` at 271, and at 505 precedes the PLAYBACK handshake at 518. `recordUnreachable` sits in the `catch` of both handshakes (275, 520); `clearUnreachable` follows both successes (278, 523). `Log.d(` = 0 hits.

**Two corrections against the plan text, both kept:**

1. The guard now runs **after** the live-session reuse check rather than before it. As first written it could reject a healthy pooled session because of a negative record left by an earlier attempt, which contradicts strategic goal 3.
2. The pool has **two** handshake sites, not one - the suspend FILE_OPS path and the blocking PLAYBACK path (`getOrCreateSessionBlocking`). Only the first was wired. The blocking path recorded no failure and, worse, cleared none on success, so a successful PLAYBACK handshake left a stale negative record able to fast-fail a subsequent FILE_OPS call. Both sites now share the same three hooks.

**Status:** `[x]` done

---

### Step 02.2 - Clear negative state on pool recovery boundaries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Clear the matching cached failure from explicit endpoint invalidation and clear all cached failures from the existing all-session and network-handover reset paths. Keep these operations aligned with the existing pool synchronization rules so a handover cannot retain an unreachable result from the former network.

**Why:**

An endpoint that becomes reachable after a network change or explicit reset must receive a normal new connection attempt instead of waiting for stale negative state.

**Verification:**

- `Grep` - endpoint cache clear is present in the pool invalidation path.
- `Grep` - global cache clear is present in the all-session reset path.
- `Grep` - `disconnectAllOnNetworkChange` still delegates to the all-session reset path.
- `Grep` - `Log.d(` returns zero hits in that file.

**Result:** `invalidate(info)` calls `clearUnreachable(info)` before `invalidateSession` (line 312); `disconnectAll()` calls `connectionFailureCache.clearAll()` (line 649); `disconnectAllOnNetworkChange` still delegates to `disconnectAll` (line 670). `Log.d(` = 0 hits. Note: the scanner watchdog also force-closes through `disconnectAll`, so a fired watchdog drops the cooldown as well - consistent with the research decision that the all-session reset is a recovery boundary.

**Status:** `[x]` done

---

### Step 02.3 - Prove pool-level cooldown and recovery wiring

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPoolTest.kt`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Extend the pool tests through a narrowly scoped internal test seam or deterministic collaborator. Prove a qualifying session-creation failure prevents a second handshake during the cooldown, explicit invalidation permits a new handshake, global reset clears all endpoint failures, and a successful handshake clears the prior failure. Keep production APIs unchanged outside the SFTP package.

**Why:**

The standalone cache tests cannot prove that all pool consumers share the guard or that recovery boundaries restore normal connection behaviour.

**Verification:**

- `Grep` - `SftpConnectionFailureCache` is present in `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPoolTest.kt`.
- `Grep` - tests for invalidation and global reset are present in that file.
- `Grep` - `Log.d(` returns zero hits in that file.

**Result:** two internal seams added to the pool - `applyHandshakeOutcomeForTest` (replays the handshake outcome hooks the creation paths call) and `failFastIfRecentlyUnreachableForTest` (the pre-handshake guard). They follow the file's existing `*ForTest` convention and expose nothing outside the SFTP package. Six new tests: qualifying failure short-circuits the next handshake, a distinct endpoint is unaffected, `invalidate` permits a new handshake, `disconnectAll` clears every endpoint, a successful handshake clears the prior failure, and the pool delegates eligibility to `SftpConnectionFailureCache` (an auth refusal never arms the guard). `Log.d(` = 0 hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0. Full unit suite `pwsh -NoProfile -File ./a.ps1 fu` -> `BUILD SUCCESSFUL in 3m 8s`, exit 0; both new SFTP test classes report `tests=7 failures=0 errors=0 skipped=0`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1` - **deferred to the owning session's closure run**.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (exit 0, 2847 records); `role`/`status` filled for the new class via `dev/CATALOG/scripts/set.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Two P1 defects were found and fixed inside this phase (guard ordering vs. a live pooled session; the unwired second handshake site) - see Step 02.1. Remaining observation, accepted: the 60 s scanner watchdog force-closes through `disconnectAll`, which also drops the cooldown.

---

## Handoff Notes to Next Phase

All file-operation consumers now share one short cooldown for qualifying unreachable-host failures; the public SFTP client contract is unchanged.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
