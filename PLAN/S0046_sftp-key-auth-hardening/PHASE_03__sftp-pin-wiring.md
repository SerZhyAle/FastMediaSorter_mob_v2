# Phase 03 — Wire pinned verifier into SFTP connection paths

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Plumb `MediaResource.hostKeyFingerprint` from the resource layer through `SftpClient` / `SftpConnectionPool` / `SftpConnectionTester`, installing `PinnedHostKeyVerifier` when a fingerprint is set and falling back to current permissive behavior when it is null.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 720 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt` | Modified | ≤ 720 |

> `SftpClient.kt` is currently 635 LOC. After change it will exceed 500 LOC — backup step required.

---

## Steps

### Step 03.1 — Backup `SftpClient.kt` and extend connection signature

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> 1. Copy `SftpClient.kt` to `temp/SftpClient.kt.<YYYYMMDD-HHmm>.bak` first.
> 2. In `SftpClient.kt`, locate the SSHJ `SSHClient` configuration call (search for `addHostKeyVerifier` or `PromiscuousVerifier`). Add an optional parameter `expectedFingerprint: String? = null` to the SSHJ session-building method (whatever name it currently has — preserve existing call-sites by defaulting to null). When non-null, replace the permissive verifier with `PinnedHostKeyVerifier(expectedFingerprint)`. When null, keep the current permissive behavior unchanged.

**Verification:**

- `Glob` — `temp/SftpClient.kt.*.bak` matches at least one file with mtime within last 24h.
- `Grep` — `expectedFingerprint: String\? = null` matches in `SftpClient.kt`.
- `Grep` — `PinnedHostKeyVerifier\(expectedFingerprint\)` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits in `SftpClient.kt`.

**Status:** `[ ]` not done

---

### Step 03.2 — Propagate fingerprint through `SftpConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Connection-pool keys must include `expectedFingerprint` so a pinned and an unpinned session for the same host:port:user are pooled separately. Add the field to whatever data class / data holder represents a pool key (search for `data class .*Key` in this file). Propagate `expectedFingerprint: String? = null` through every public method that opens a session, defaulting to null at every call boundary.

**Verification:**

- `Grep` — `expectedFingerprint` matches at least 3 times in `SftpConnectionPool.kt`.
- `Grep` — `data class` matches in this file (key class still present).
- `Grep -n "Log\.d\("` returns zero hits in `SftpConnectionPool.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 — Propagate fingerprint through `SftpConnectionTester`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `expectedFingerprint: String? = null` parameter to `testConnection` and `testConnectionWithPrivateKey` (same default). Pass it through to `SftpClient`. Catch `HostKeyMismatchException` separately from auth failures and return a distinct typed result (extend the existing sealed result type if any, otherwise add a new branch); the UI must be able to distinguish "MITM-style mismatch" from "wrong password / wrong key".

**Verification:**

- `Grep` — `expectedFingerprint` matches at least 4 times in `SftpConnectionTester.kt`.
- `Grep` — `HostKeyMismatchException` matches at least once.

**Status:** `[ ]` not done

---

### Step 03.4 — Surface fingerprint in `SmbOperationsUseCase.testSftpConnection`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Find `testSftpConnection` (around line 327) — it already accepts `privateKey` and `keyPassphrase`. Add `expectedFingerprint: String? = null` parameter (default-null preserves all existing call-sites). Forward it to `SftpClient.testConnectionWithPrivateKey` / `testConnection`. Also find the runtime use-paths in `listSftpFilesWithCredentials`, `checkTrashFolders`, and `cleanupTrash` where SFTP connections are built — pull `MediaResource.hostKeyFingerprint` from the caller or resource and pass it through. Note: you may need to update the method signatures to accept the expected fingerprint, or retrieve the resource first. Existing password-only call-sites continue to work without changes.

**Verification:**

- `Grep` — `expectedFingerprint` matches at least 4 times in `SmbOperationsUseCase.kt`.
- `Grep` — `hostKeyFingerprint` matches at least 2 times in `SmbOperationsUseCase.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Pin-check works end-to-end at the data layer when caller supplies a fingerprint. UI does not yet expose the field — Phase 05 closes that loop.

---

## Rollback Plan

Revert phase commit(s); all new parameters default to null so absence of pinning matches previous behavior bit-for-bit.
