# Phase 01 - Cloud download extraction

**Strategic spec:** [`../S0494_send-to-cloud-http-materialization.md`](../S0494_send-to-cloud-http-materialization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Move the cloud download trio out of `CloudFileOperationHandler` into a dedicated `CloudDownloadUseCase`, leaving the handler delegating and behaviour unchanged.

---

## Prerequisites

- [ ] Strategic §3 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/S0494/` exists for the mandatory backup of the >500 LOC handler.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudDownloadUseCase.kt` | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1080 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt` | Modified | ≤ 150 |

---

## Steps

### Step 01.1 - Back up the handler before editing it

**Files:** `temp/S0494/CloudFileOperationHandler.kt.bak`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` to `temp/S0494/CloudFileOperationHandler_<yyyyMMdd-HHmm>.kt.bak` before any edit in this phase.

**Why:**

The handler is 1080 LOC, above the 500-LOC threshold at which CLAUDE.md Rule 5 requires a timestamped backup under `temp/` before editing.

**Verification:**

- `Glob` - `temp/S0494/CloudFileOperationHandler_*.kt.bak` matches at least one file.

**Status:** `[x]` done

---

### Step 01.2 - Create CloudDownloadUseCase carrying the download trio

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudDownloadUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `CloudDownloadUseCase` as a `@Singleton` `@Inject`-constructed class holding the bodies of `downloadFromCloudToPublic`, `downloadFromCloudTo` and `downloadFromCloud`, moved verbatim from `CloudFileOperationHandler` together with the collaborators those bodies need (cloud clients, `CloudPathParser`, `CloudAuthenticationHelper`, network credential resolver, SMB/SFTP/FTP clients used by the non-local destination branches). Keep `downloadFromCloudTo` and `downloadFromCloud` private to the new class and expose `downloadToPublic(cloudPath, destPath, fileName, progressCallback: ByteProgressCallback? = null)` as the single public entry point. Do not change any logic, log text or error handling while moving.

**Why:**

Strategic §3 resolves the extraction question as yes: the handler already sits above its own 1000-LOC soft cap, the two prior extractions (`CloudToCloudTransferHelper`, `CloudFileOperationPathUtils`) set the pattern, and Phase 02 adds further plumbing to exactly these methods.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudDownloadUseCase.kt` exists.
- `Grep` - `class CloudDownloadUseCase` matches exactly once in that file.
- `Grep` - `suspend fun downloadToPublic(` present in that file.
- `Grep` - `private suspend fun downloadFromCloudTo(` present in that file.

**Status:** `[x]` done

---

### Step 01.3 - Delegate from the handler and repoint the share caller

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inject `CloudDownloadUseCase` into `CloudFileOperationHandler` and replace every internal call to the moved methods with a delegating call, deleting the moved bodies from the handler. Change `MaterializeShareContentUseCase` to inject `dagger.Lazy<CloudDownloadUseCase>` instead of `dagger.Lazy<CloudFileOperationHandler>` and call `downloadToPublic`. Keep the existing `downloadFromCloudToPublic` signature on the handler as a thin delegate so the noLegal silent-APK-install caller is untouched.

**Why:**

Leaving both copies would let the two paths diverge, and the share path must reach the new class directly so Phase 02 can hand it a progress callback without widening the handler's API again.

**Verification:**

- `Grep` - `private suspend fun downloadFromCloudTo(` returns zero hits in `CloudFileOperationHandler.kt`.
- `Grep` - `cloudDownload` present in `MaterializeShareContentUseCase.kt`.
- `Grep` - `CloudFileOperationHandler` returns zero hits in `MaterializeShareContentUseCase.kt`.
- `Grep` - `Log\.d\(` returns zero hits in both modified files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`CloudDownloadUseCase.downloadToPublic` already accepts a `ByteProgressCallback?` parameter that no caller passes yet - Phase 02 fills it in.

---

## Rollback Plan

Revert the phase commit - pure code move, no data migration and no user-facing surface changed.
