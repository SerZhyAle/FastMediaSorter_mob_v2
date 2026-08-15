# Phase 01 - Failure Cache

**Strategic spec:** [`../S1651_sftp-unreachable-host-stacked-timeouts.md`](../S1651_sftp-unreachable-host-stacked-timeouts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Introduce a bounded, clock-testable in-memory record for recent socket-establishment failures without changing SFTP call sites.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCache.kt` | New | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCacheTest.kt` | New | ≤ 260 |

---

## Steps

### Step 01.1 - Add bounded connection-failure cache

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCache.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small SFTP-internal cache keyed by host, port, username, and expected host-key fingerprint. Store the original throwable only for a 15-second cooldown when its cause chain contains `SocketTimeoutException`, `ConnectException`, `NoRouteToHostException`, or `UnknownHostException`; expose lookup, record, endpoint clear, global clear, and success clear operations. Inject a test clock through the constructor, evict expired entries during lookup, enforce a fixed upper bound, and keep passwords and private-key material out of keys and entries.

**Why:**

The first proven connection refusal must prevent the following cleanup, listing, paging, and counting calls from repeating the same full timeout, while authentication and host-key failures must remain immediately retryable.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCache.kt` exists.
- `Grep` - `class SftpConnectionFailureCache` matches exactly once in that file.
- `Grep` - `SocketTimeoutException`, `ConnectException`, `NoRouteToHostException`, and `UnknownHostException` are present in that file.
- `Grep` - `15_000` is present in that file.
- `Grep` - `Log.d(` returns zero hits in that file.

**Result:** file exists; `class SftpConnectionFailureCache` = 1 hit; all four socket causes present; `15_000` = 1 hit; `Log.d(` = 0 hits. `pwsh -NoProfile -File ./a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0.

**Status:** `[x]` done

---

### Step 01.2 - Cover eligibility, expiry, and clearing

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCacheTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add deterministic unit tests using the injected clock. Prove that each allowed socket-establishment cause is returned during the cooldown; an SSH exception without an allowed cause and a protocol exception are not cached; entries expire; endpoint and global clear remove the entry; and one endpoint key does not expose a failure to a distinct host, user, or fingerprint key.

**Why:**

The negative state is safe only if it never masks a credential or trust correction, never outlives its short window, and never crosses secure connection boundaries.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCacheTest.kt` exists.
- `Grep` - `fun ` matches at least five test declarations in that file.
- `Grep` - `SocketTimeoutException` and `SftpException` are present in that file.
- `Grep` - `Log.d(` returns zero hits in that file.

**Result:** file exists; `fun ` = 8 hits (7 tests + `info` factory); `SocketTimeoutException` = 7, `SftpException` = 2; `Log.d(` = 0 hits. Coverage: all four eligible socket causes, cooldown boundary at 14_999 vs 15_000 ms, auth/host-key/protocol rejection, host+port+user+fingerprint isolation, endpoint clear, global clear, and credential exclusion from the key.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1` - **deferred to the owning session's closure run** (`post-change.ps1`); the implementing sub-agent is not permitted to run the closure facade.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Verified that `SftpClient` is `@Singleton` and owns one `SftpConnectionPool`, so temp cleanup, listing, paging and counting genuinely share one cache instance; all cache entry points are `@Synchronized`; no credential material reaches the key or the log.

---

## Handoff Notes to Next Phase

The cache returns only short-lived, socket-establishment failures and has explicit endpoint/global clearing operations for pool lifecycle integration.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
