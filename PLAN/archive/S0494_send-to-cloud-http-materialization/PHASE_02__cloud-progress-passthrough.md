# Phase 02 - Cloud progress passthrough

**Strategic spec:** [`../S0494_send-to-cloud-http-materialization.md`](../S0494_send-to-cloud-http-materialization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Report real byte progress while a cloud file is materialized for «Отправить в..», replacing the indeterminate spinner.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudDownloadUseCase.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt` | Modified | ≤ 160 |

---

## Steps

### Step 02.1 - Delete the dead private downloadFromCloud

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudDownloadUseCase.kt`, `config/detekt/baseline-app_v2.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the private `downloadFromCloud(cloudPath, localFile, progressCallback)` function together with the `@Suppress("UnusedPrivateMember")` Phase 01 put on it, and drop the matching `UnusedPrivateMember:CloudFileOperationHandler.kt$..$private suspend fun downloadFromCloud(..)` line from the detekt baseline. Verify first that a repo-wide grep for `downloadFromCloud\b` finds no call site outside the function's own body and log lines.

**Why:**

Planning assumed this function was the un-plumbed local-destination path, but reading the moved code shows `downloadFromCloudTo` already builds `adaptCloudProgress` and passes it to `client.downloadFile` for every destination, and nothing calls `downloadFromCloud` at all - which is exactly why detekt had baselined it as an unused private member. CLAUDE.md Rule 20 requires deleting orphaned code with the change that surfaces it rather than threading new parameters through it.

**Verification:**

- `Grep` - `private suspend fun downloadFromCloud(` returns zero hits in `CloudDownloadUseCase.kt`.
- `Grep` - `UnusedPrivateMember` with `downloadFromCloud` returns zero hits in `config/detekt/baseline-app_v2.xml`.
- `Grep` - `@Suppress("UnusedPrivateMember")` returns zero hits in `CloudDownloadUseCase.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.2 - Convert cloud byte progress to the share dialog's percent contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `downloadTo`, build a `ByteProgressCallback` that maps `(bytesTransferred, totalBytes)` to `0..100` and forwards it to `onProgress`, using the same conversion idiom as `DownloadNetworkFileUseCase`, and pass it to `cloudDownload.get().downloadToPublic(..)`. Guard against `totalBytes <= 0` by emitting nothing rather than a bogus percentage. Update the class KDoc sentence that states cloud has no progress hook.

**Why:**

The use case's own contract documents `onProgress` as 0..100 for every materialized source, and the cloud branch was the one source silently violating it, which is what left the share dialog on an indeterminate spinner.

**Verification:**

- `Grep` - `ByteProgressCallback` present in `MaterializeShareContentUseCase.kt`.
- `Grep` - `Cloud has no progress hook` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`downloadTo` now owns a reusable byte-to-percent adapter that the http(s) branch in Phase 03 consumes instead of writing its own.

---

## Rollback Plan

Revert the phase commit - progress reporting only, no persisted state and no new surface.
