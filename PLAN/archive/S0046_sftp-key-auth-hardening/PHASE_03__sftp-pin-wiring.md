# Phase 03 — Wire pinned verifier into SFTP connection paths

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-09
**Completed:** 2026-06-10

---

## Objective

Plumb `MediaResource.hostKeyFingerprint` from the resource layer through `SftpClient` / `SftpConnectionPool` / `SftpConnectionTester`, installing the JSch `PinnedHostKeyRepository` (Phase 02) on the JSch `Session` when a fingerprint is set and falling back to current permissive behavior (`StrictHostKeyChecking="no"`) when it is null. The SFTP stack is **JSch** (`com.jcraft.jsch.*`) — there is no SSHJ in this project.

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
> 2. The SFTP stack is **JSch** (`com.jcraft.jsch.*`), not SSHJ. Add a field `expectedFingerprint: String? = null` to the `SftpConnectionInfo` data class. Also add an `expectedFingerprint: String? = null` parameter to the `testConnection` and `testConnectionWithPrivateKey` wrapper methods in `SftpClient` (they delegate to `SftpConnectionTester`), forwarding it to the tester. All defaults are null so existing call-sites compile unchanged.
> 3. No verifier is installed in this step. The JSch `Session` is created in `SftpConnectionPool` (Step 03.2) and `SftpConnectionTester` (Step 03.3); those steps install `PinnedHostKeyRepository` on the session.

**Verification:**

- `Glob` — `temp/SftpClient.kt.*.bak` matches at least one file with mtime within last 24h.
- `Grep` — `expectedFingerprint: String\? = null` matches at least twice in `SftpClient.kt` (data-class field + wrapper params).
- `Grep` — `data class SftpConnectionInfo` matches in `SftpClient.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SftpClient.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 — Verification 4/4 PASS. Files: SftpClient.kt (SftpConnectionInfo.expectedFingerprint field + testConnection/testConnectionWithPrivateKey param). Backup temp/SftpClient.kt.20260609-2348.bak. Dev log recorded.

---

### Step 03.2 — Propagate fingerprint through `SftpConnectionPool`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> The JSch `Session` is created and configured in this file — `applyAuth(session, info)` sets `config["StrictHostKeyChecking"]` in both the private-key and password branches. Two changes:
>
> 1. The pool key must include `expectedFingerprint` so a pinned and an unpinned session for the same host:port:user are pooled separately. Add the field to the data class / data holder that represents a pool key (search for `data class .*Key` in this file). Propagate `expectedFingerprint: String? = null` through every public method that opens a session, defaulting to null at every call boundary.
> 2. In `applyAuth`, before `session.connect`, branch on `info.expectedFingerprint`: compute `val canonical = SshFingerprintNormalizer.canonical(info.expectedFingerprint)`. When `canonical != null`, call `session.setHostKeyRepository(PinnedHostKeyRepository(canonical))` and set `config["StrictHostKeyChecking"] = "yes"` (NOT `"no"`) so JSch consults the pinned repository and aborts on a `CHANGED` verdict. When `info.expectedFingerprint == null`, keep the current `"no"` permissive behavior unchanged. When a fingerprint is set but `canonical == null` (unparseable config), log `Timber.w` (no key bytes) and fall back to the permissive `"no"` path — do not crash.

**Verification:**

- `Grep` — `expectedFingerprint` matches at least 3 times in `SftpConnectionPool.kt`.
- `Grep` — `setHostKeyRepository(PinnedHostKeyRepository(` matches at least once.
- `Grep` — `SshFingerprintNormalizer.canonical` matches at least once.
- `Grep` — `data class` matches in this file (key class still present).
- `Grep -n "Log\.d\("` returns zero hits in `SftpConnectionPool.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 — Verification 5/5 PASS. Files: SftpConnectionPool.kt (ConnectionKey.expectedFingerprint + 4 call-sites; installPinnedHostKeyOrPermissive in applyAuth: PinnedHostKeyRepository + StrictHostKeyChecking="yes" when pinned, "no" fallback, Timber.w on unparseable). Dev log recorded.

---

### Step 03.3 — Propagate fingerprint through `SftpConnectionTester`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `expectedFingerprint: String? = null` parameter to `testConnection` and `testConnectionWithPrivateKey` (same default). Each method builds its own JSch `Session` and currently sets `config["StrictHostKeyChecking"] = "no"`. When `SshFingerprintNormalizer.canonical(expectedFingerprint)` yields a non-null `canonical`: call `testSession.setHostKeyRepository(PinnedHostKeyRepository(canonical))` before `connect` and set `config["StrictHostKeyChecking"] = "yes"` instead of `"no"`. When `connect` fails with a JSch host-key rejection (`JSchException` whose message indicates a host-key / `reject HostKey` problem) while pinned, map it to `HostKeyMismatchException` and return it as a distinct typed failure (extend the existing sealed result type if any, otherwise surface the typed exception in the `Result.failure`); the UI must be able to distinguish "MITM-style mismatch" from "wrong password / wrong key". The unpinned (null) path stays bit-for-bit unchanged.

**Verification:**

- `Grep` — `expectedFingerprint` matches at least 4 times in `SftpConnectionTester.kt`.
- `Grep` — `HostKeyMismatchException` matches at least once.
- `Grep` — `setHostKeyRepository(PinnedHostKeyRepository(` matches at least once in `SftpConnectionTester.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 — Verification 3/3 PASS. Files: SftpConnectionTester.kt (expectedFingerprint param on both test methods; applyHostKeyPin installs PinnedHostKeyRepository + StrictHostKeyChecking="yes"; mapTestFailure maps JSch host-key rejection to HostKeyMismatchException). Dev log recorded.

---

### Step 03.4 — Surface fingerprint in `SmbOperationsUseCase.testSftpConnection`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Find `testSftpConnection` (around line 327) — it already accepts `privateKey` and `keyPassphrase`. Add `expectedFingerprint: String? = null` parameter (default-null preserves all existing call-sites). Forward it to `SftpClient.testConnectionWithPrivateKey` / `testConnection`. Also find the runtime use-paths in `listSftpFilesWithCredentials`, `checkTrashFolders`, and `cleanupTrash` where SFTP connections are built — pull `MediaResource.hostKeyFingerprint` from the caller or resource and pass it through. Note: you may need to update the method signatures to accept the expected fingerprint, or retrieve the resource first. Existing password-only call-sites continue to work without changes.

**Verification:**

- `Grep` — `expectedFingerprint` matches at least 4 times in `SmbOperationsUseCase.kt`.
- `Grep` — `hostKeyFingerprint` matches at least 2 times in `SmbOperationsUseCase.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 — Verification 2/2 PASS. Files: SmbOperationsUseCase.kt (expectedFingerprint on testSftpConnection + listSftpFiles forwarded to SftpClient; hostKeyFingerprint on listSftpFilesWithCredentials/checkTrashFolders/cleanupTrash threaded into SftpConnectionInfo.expectedFingerprint). All new params default null - existing call-sites unchanged. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` BUILD SUCCESSFUL (3m05s, 2026-06-10).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Pin-check works end-to-end at the data layer when caller supplies a fingerprint. UI does not yet expose the field — Phase 05 closes that loop.

---

## Rollback Plan

Revert phase commit(s); all new parameters default to null so absence of pinning matches previous behavior bit-for-bit.

---

## Revision History

- **2026-06-03** — by `/spec-update` (`--tactical --phase 03 --force-locked`, focus: consistency, verifiability)
  - **Override reason:** journal status was `Partial` (formally locked), but `Partial` here means "implementation incomplete" (tactical INDEX = In Progress), not a closed historical record. Refinement is legitimate; only the tactical phase file is touched, not the strategic record.
  - Realigned Steps 03.1–03.3 from a stale **SSHJ** formulation to the actual **JSch** transport (`com.jcraft.jsch.*`). Phase 02 was rewritten to JSch on 2026-05-18; Phase 03 (dated 2026-05-05) still referenced SSHJ APIs (`addHostKeyVerifier`, `PromiscuousVerifier`) and a non-existent class `PinnedHostKeyVerifier`.
  - Step 03.1: now extends the `SftpConnectionInfo` data class + `SftpClient` test wrappers with `expectedFingerprint`; no verifier install here.
  - Step 03.2: install `PinnedHostKeyRepository` in `SftpConnectionPool.applyAuth` (`setHostKeyRepository` + `StrictHostKeyChecking="yes"` when pinned; permissive `"no"` fallback otherwise; `Timber.w` + fallback on unparseable fingerprint).
  - Step 03.3: same JSch wiring in `SftpConnectionTester`; map JSch host-key rejection to `HostKeyMismatchException`.
  - Verification predicates updated to grep the real class (`PinnedHostKeyRepository`) and JSch call (`setHostKeyRepository(PinnedHostKeyRepository(`) instead of the non-existent `PinnedHostKeyVerifier`.
  - Applied: 4 edits. Proposed (DISCUSS): 0.
