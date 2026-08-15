# Phase 01 — fix-precheck-logic

**Strategic spec:** [`../S0098_bugfix-smb-precheck-false-fail.md`](../S0098_bugfix-smb-precheck-false-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add `hasActiveConnectionForServer` to `SmbConnectionPool` and use it in
`SmbConnectionManager.withConnection` to skip the TCP precheck when a live
connection to the same `server:port` already exists in the pool.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 500 |

---

## Steps

### Step 01.1 — Add `hasActiveConnectionForServer` to `SmbConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> After the existing `snapshot()` method (line 118), add:
>
> ```kotlin
> fun hasActiveConnectionForServer(host: String, port: Int): Boolean =
>     snapshot().any { it.key.server == host && it.key.port == port }
> ```
>
> No other changes to this file.

**Verification:**

- `Grep` — `fun hasActiveConnectionForServer` present in `SmbConnectionPool.kt`.
- `Grep` — `snapshot().any` present in `SmbConnectionPool.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: SmbConnectionPool.kt (+2 LOC). Dev log pending phase end.

---

### Step 01.2 — Update `withConnection` to skip TCP precheck when server is pool-known

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `SmbConnectionManager.withConnection`, find the block (around line 329):
>
> ```kotlin
> // Smart retry: TCP precheck once. If host is unreachable at the TCP layer, skip the
> // degraded retry — the server is dead, prolonging the wait will not help.
> val tcpReachable = checkConnectivity(
>     connectionInfo.server, connectionInfo.port, CONNECTIVITY_CHECK_TIMEOUT_MS
> )
> if (!tcpReachable) {
>     Timber.w("SMB TCP precheck failed: ${connectionInfo.server}:${connectionInfo.port} — fast-fail without retry")
>     return@withPermit handleFreshConnectionFailure(
>         key, connectionInfo,
>         IOException("Server unreachable (${connectionInfo.server}:${connectionInfo.port})")
>     )
> }
> ```
>
> Replace it with:
>
> ```kotlin
> // Smart retry: TCP precheck once. If host is unreachable at the TCP layer, skip the
> // degraded retry — the server is dead, prolonging the wait will not help.
> // Skip the precheck entirely when the pool already holds a live connection to this
> // server:port (any share) — the server is clearly reachable.
> val serverKnown = pool.hasActiveConnectionForServer(connectionInfo.server, connectionInfo.port)
> val tcpReachable = if (serverKnown) {
>     Timber.d("SMB TCP precheck skipped: live pool entry for ${connectionInfo.server}:${connectionInfo.port}")
>     true
> } else {
>     checkConnectivity(connectionInfo.server, connectionInfo.port, CONNECTIVITY_CHECK_TIMEOUT_MS)
> }
> if (!tcpReachable) {
>     Timber.w("SMB TCP precheck failed: ${connectionInfo.server}:${connectionInfo.port} — fast-fail without retry")
>     return@withPermit handleFreshConnectionFailure(
>         key, connectionInfo,
>         IOException("Server unreachable (${connectionInfo.server}:${connectionInfo.port})")
>     )
> }
> ```

**Verification:**

- `Grep` — `hasActiveConnectionForServer` present in `SmbConnectionManager.kt`.
- `Grep` — `SMB TCP precheck skipped` present in `SmbConnectionManager.kt`.
- `Grep` — `SMB TCP precheck failed` still present (unchanged fast-fail path).

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: SmbConnectionManager.kt (+7 LOC). Dev log pending phase end.

---

### Step 01.3 — Build

**Files:** —
**Depends on:** Step 01.2

**Prompt for developer:**

> Run `/build` (debug, any flavor). Build must succeed with zero errors.

**Verification:**

- Build exits with code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Build SUCCESSFUL (exit 0). APK: v2.60.5060.329.

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — `/build` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entries added for both modified files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`SmbConnectionPool.hasActiveConnectionForServer` is available.
`withConnection` skips precheck when any live connection to the target server exists.
Proceed to Phase 02 (docs/catalog cleanup).

---

## Rollback Plan

Revert phase commit — no schema change, no user-facing surface, no data migration.
