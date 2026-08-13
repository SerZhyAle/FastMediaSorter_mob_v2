# Phase 03 - SFTP session hardening

**Strategic spec:** [`../S0624_bugfix-sftp-scan-hang-network.md`](../S0624_bugfix-sftp-scan-hang-network.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (shares `SftpConnectionPool.kt`)
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Make a dead SFTP transport drop itself proactively via JSch keep-alive, and remove the dead duplicate socket-timeout constant (strategic Pillar C / FIX #3 keep-alive + Pillar D / FIX #5 hygiene; goal G4).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (avoids merge churn in `SftpConnectionPool.kt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 695 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 760 |

> Both exceed 500 LOC - timestamped backup into `temp/` before editing.

---

## Steps

### Step 03.1 - Enable JSch keep-alive on every pooled session

> **Step Log:** 2026-06-22 - Verification PASS (setServerAliveInterval/CountMax ×2 each, consts declared, both before session.connect). Files: SftpConnectionPool.kt.

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `getOrCreateSession` set `session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)` and `session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)` immediately before `session.connect(CONNECTION_TIMEOUT)` (`:225`), right after the existing `session.timeout = SOCKET_TIMEOUT` line. Apply the identical pair before `session.connect` in `getOrCreateSessionBlocking` (`:421-422`) so the PLAYBACK path is covered too. Add the two consts to the companion object (`:628`): `private const val SERVER_ALIVE_INTERVAL_MS = 15_000` and `private const val SERVER_ALIVE_COUNT_MAX = 2` (≈30 s to drop a dead transport, comfortably under the Phase 02 watchdog). One WHY comment: keep-alive lets JSch's own thread detect a half-open socket and fail the session, unblocking a parked `ls` without waiting on external invalidation. Conservative interval/count keeps the cost negligible for healthy sessions (strategic §3.2 performance constraint).

**Verification:**

- `Grep` - `setServerAliveInterval` and `setServerAliveCountMax` each match exactly twice in `SftpConnectionPool.kt` (both session-creation paths).
- `Grep` - `SERVER_ALIVE_INTERVAL_MS` and `SERVER_ALIVE_COUNT_MAX` declared once each in the companion object.
- Each keep-alive pair appears before its `session.connect(` call.

**Status:** `[x] done`

---

### Step 03.2 - Remove the dead SOCKET_TIMEOUT constant from SftpClient

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the unused `private const val SOCKET_TIMEOUT = 30000` from `SftpClient`'s companion object (`:88`). The live socket timeout is `SftpConnectionPool.SOCKET_TIMEOUT` (`:630`), applied where sessions are actually created; the `SftpClient` copy is dead and creates false confidence that the listing is protected (strategic Pillar D). Before deleting, confirm no remaining reference inside `SftpClient` (current grep shows only the declaration line). Leave the other companion consts untouched unless an identical zero-reference check proves them dead too - this step removes only `SOCKET_TIMEOUT`.

**Verification:**

- `Grep` - `SOCKET_TIMEOUT` returns zero hits in `SftpClient.kt`.
- `Grep` - `SOCKET_TIMEOUT` still present in `SftpConnectionPool.kt` (the live one is untouched).
- `/build` - `standard debug` compiles.

**Status:** `[x] done`

> **Step Log:** 2026-06-22 - Verification PASS (SOCKET_TIMEOUT removed from SftpClient, still present in pool). Files: SftpClient.kt.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL (consolidated build).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [~] Dev log entry - batched at ticket finalization.

---

## Handoff Notes to Next Phase

Final functional phase. Phase 04 is catalog/changelog cleanup only.

---

## Rollback Plan

Revert the phase commit(s) - keep-alive params and a const deletion only; no data migration or user-facing surface changed.
