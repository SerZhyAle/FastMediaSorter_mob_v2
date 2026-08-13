# Phase 04 - Wire the resolver into every connection point

**Strategic spec:** [`../S1006_sftp-multipath-endpoint-fallback.md`](../S1006_sftp-multipath-endpoint-fallback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 6
**Started:** -
**Completed:** -

---

## Objective

Consult `SftpEndpointResolver` at every point where a resource path becomes a live SFTP connection - scan/browse, file operations, transfer, ExoPlayer transport - and make the connection test pass when any candidate is reachable.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (`SftpEndpointResolver.resolve` available for injection).
- [ ] Phase 02 ✅ Done (credential row exists per candidate).
- [ ] Research 01 read (the full consumer list).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt` | Modified | backup first (>500 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt` | Modified | backup first (>500 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt` | Modified | backup first (>700 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | backup first (>500 LOC) - factory in same file |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt` | Modified | connection-test-all |

> Each connection point calls `resolver.resolve(host, port)` immediately AFTER `SftpPathUtils.parseSftpPath` and BEFORE credential lookup, then uses the resolved host:port for both credential resolution and the session. Injection: add `SftpEndpointResolver` to each class's constructor (all are Hilt-constructed data-layer classes).

---

## Steps

### Step 04.1 - Resolve in the media scanner (browse)

**Files:** `data/remote/sftp/SftpMediaScanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `SftpEndpointResolver`. In the private `parseSftpPath(path, credentialsId)` (~L511), after `SftpPathUtils.parseSftpPath` yields host/port, call `resolver.resolve(host, port)` and use the resolved host:port to build the `SftpConnectionInfo` and to resolve credentials. The remote path is unchanged. This makes browse connect to whichever endpoint is reachable.

**Verification:**

- `Grep` - `SftpEndpointResolver` referenced in the file (constructor + call).
- `Grep` - `resolve(` called after `parseSftpPath`.

**Status:** `[ ]` not done

---

### Step 04.2 - Resolve in the file-operation handler

**Files:** `data/network/SftpFileOperationHandler.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inject `SftpEndpointResolver`. In `parseSftpPath(path)` (~L421), resolve host/port via the resolver before `credentialsRepository.getByTypeServerAndPort(..)`, so the credential lookup and connection both target the reachable endpoint (its credential row exists from Phase 02).

**Verification:**

- `Grep` - `SftpEndpointResolver` referenced.
- `Grep` - `resolve(` precedes `getByTypeServerAndPort` in the edited method.

**Status:** `[ ]` not done

---

### Step 04.3 - Resolve in the transfer strategy

**Files:** `data/transfer/strategy/SftpOperationStrategy.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inject `SftpEndpointResolver`. Route the class-local `parseSftpPath(path)` (~L287) result through `resolver.resolve(host, port)` so every copy/move/rename/delete/exists call in this strategy targets the reachable endpoint. A single resolve inside that helper covers all ~15 call sites.

**Verification:**

- `Grep` - `SftpEndpointResolver` referenced.
- `Grep` - `resolve(` inside the `parseSftpPath` helper.

**Status:** `[ ]` not done

---

### Step 04.4 - Resolve before constructing the ExoPlayer SFTP transport

**Files:** `data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Find where `SftpDataSourceFactory` (same file) is handed host/port/username/password. Resolve the endpoint via `SftpEndpointResolver.resolve(host, port)` at the point the factory is built (or immediately before the first `getConnectionForExoPlayer`), so playback streams from the reachable endpoint. Username/password are identical across companion candidates, so only host:port change. Keep the existing transparent-reconnect logic intact.

**Verification:**

- `Grep` - `SftpEndpointResolver` referenced in the file.
- `Grep` - `resolve(` called before `getConnectionForExoPlayer` / factory construction.

**Status:** `[ ]` not done

---

### Step 04.5 - Connection test passes if ANY candidate is reachable

**Files:** `domain/usecase/SmbOperationsUseCase.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> `testSftpConnection` currently tests one host (the field failure: it timed out on the single stored WAN host). Change it to build the candidate list for the resource (primary + `altAccessPaths`) and test them LAN-first, returning success on the FIRST reachable candidate; only return failure when ALL candidates fail. Prefer reusing `SftpEndpointResolver.resolve` to pick a reachable endpoint, then run the existing `SftpConnectionTester.testConnection` against it; report which endpoint succeeded in the log at `Timber.i`. Do not add a `Sxxxx` id to any persistent log line.

**Verification:**

- `Grep` - candidate list / `altAccessPaths` or `resolve(` referenced in `testSftpConnection`.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[ ]` not done

---

### Step 04.6 - Debug verification probe (BlockNeedUserTest)

**Files:** `data/remote/sftp/SftpEndpointResolver.kt`
**Depends on:** Steps 04.1-04.5

**Prompt for developer:**

> Add exactly one `Timber.d("S1006: resolved <host:port> from <n> candidates on <network>")` at the point `resolve()` returns a freshly-probed winner (not on cache hits, to avoid spam). This is the single changed-flow entry probe required while the ticket is `BlockNeedUserTest`; it must be removed when the ticket leaves that status. No other `S1006:` probe anywhere.

**Verification:**

- `Grep` - `Timber.d("S1006:` matches exactly once across `app_v2/src/main`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `Grep` - exactly one `Timber.d("S1006:` in `app_v2/src/main`.
- [ ] Dev log entry added for every modified file.

---

## Handoff Notes to Next Phase

All SFTP connection points resolve the reachable endpoint. Phase 05 does catalog/dev-log closure and flips the ticket to `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit(s). Phases 01-03 leave the resolver unreferenced and inert; the resource model change stays additive.
