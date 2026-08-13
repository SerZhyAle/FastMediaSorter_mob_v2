# Phase 01 — Status Classifier

**Strategic spec:** [../S0149_enh-sftp-permission-denied-message.md](../S0149_enh-sftp-permission-denied-message.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Introduce typed SFTP write-failure classification and preserve raw protocol status codes for upload, delete, rename, and mkdir paths.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Incident evidence for `3: Permission denied` is reviewed before broadening the mapping scope.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpOperationFailure.kt` | New | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 — Backup the large SFTP write-path files

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create timestamped backups in `temp/` before any edits. Both files already exceed 500 lines and stay on the critical SFTP write path for upload, delete, rename, and mkdir failures.

**Verification:**

- `Glob` — `temp/S0149_SftpClient_*.backup` exists.
- `Glob` — `temp/S0149_SftpOperationStrategy_*.backup` exists.

**Status:** `[ ]` not done

---

### Step 01.2 — Add a typed SFTP operation failure model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpOperationFailure.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `SftpOperationFailure.kt` under `data/remote/sftp/`. Define a small typed model that captures `statusCode`, high-level category, raw server message, operation kind, and `copyCompleted` metadata for move-after-copy failures. Add `fromThrowable(..)` helpers that unwrap nested causes and inspect `SftpException.id` without parsing UI strings.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpOperationFailure.kt` exists.
- `Grep` — `enum class SftpFailureCategory` matches exactly once in that file.
- `Grep` — `fun fromThrowable` present.

**Status:** `[ ]` not done

---

### Step 01.3 — Preserve raw SFTP status codes on write-side failures

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Keep the original `SftpException` cause intact on upload, delete, rename, mkdir, and cross-protocol write helpers. Remove wrapper failures that flatten protocol status `3` into generic text before the handler can classify it. Keep read-path retry behavior for `SSH_FX_FAILURE` and `SSH_FX_BAD_MESSAGE` unchanged.

**Verification:**

- `Grep` — `Result.failure(Exception("Delete file failed:` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`.
- `Grep` — `Result.failure(Exception("Delete directory failed:` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`.
- `Grep` — `ChannelSftp.SSH_FX_PERMISSION_DENIED` present in either `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` or `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpOperationFailure.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Write-side SFTP failures now retain protocol status codes and can be mapped without parsing user-facing text.

---

## Rollback Plan

Revert phase commit(s) and restore the timestamped backups from `temp/` for `SftpClient.kt` and `SftpOperationStrategy.kt`.