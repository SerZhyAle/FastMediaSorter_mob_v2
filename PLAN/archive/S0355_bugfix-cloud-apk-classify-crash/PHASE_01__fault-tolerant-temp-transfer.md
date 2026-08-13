# Phase 01 - Fault-tolerant temp-copy transfer

**Strategic spec:** [`../S0355_bugfix-cloud-apk-classify-crash.md`](../S0355_bugfix-cloud-apk-classify-crash.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Make the shared cloud download path return `false` (with diagnostic logging) instead of throwing when the downloaded temp copy is missing or unreadable at transfer time; cover every destination branch (LOCAL / SMB / SFTP / FTP) and both download methods. No flavor-specific code.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `CloudFileOperationHandler.kt` is 868 LOC (>500) - the backup step below is mandatory before any edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 920 |

> This file is 868 LOC (>500) → backup required (Step 01.1). It stays under 1500, so no Manager split is forced.
>
> **Flavor placement.** This file is shared `src/main` code. Do NOT introduce any `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` guard here - the defensive behavior is universal for all flavors that use cloud download.

---

## Steps

### Step 01.1 - Back up the file before editing

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The target file is 868 LOC (>500), so create a timestamped backup before the first edit. Copy it to `temp/CloudFileOperationHandler_<yyyyMMdd_HHmmss>.kt.backup`. Do not write anything to the project root.

**Verification:**

- `Glob` - a file matching `temp/CloudFileOperationHandler_*.kt.backup` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 1/1 PASS. Backup: temp/CloudFileOperationHandler_20260604_225917.kt.backup.

---

### Step 01.2 - Guard and wrap the LOCAL destination transfer in `downloadFromCloudTo`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `downloadFromCloudTo`, in the `ResourceType.LOCAL` branch (currently the unguarded `tempFile.copyTo(localFile, overwrite = true)`), before copying, check `tempFile.exists()`; if it is missing, log via `Timber.w(..)` describing that the downloaded temp copy vanished before the local transfer and `return false`. Wrap the `copyTo` call in `try { .. } catch (e: java.io.IOException) { .. }`; on catch, log the failure via `Timber.w(e, ..)` and `return false`. The log text must name the operation (cloud→local transfer) and must NOT embed `S0355` or any ticket id. Do not remove or alter the existing `finally { tempFile.delete() }` - temp cleanup already exists and must remain.

**Verification:**

- `Grep` - `tempFile.exists()` matches at least once in the file.
- `Grep` - `catch (e: java.io.IOException)` (or imported `IOException`) matches in `downloadFromCloudTo`.
- `Grep` - count of `finally {` followed by `tempFile.delete()` is unchanged (still present for both download methods).
- `Grep -n "Log\.d\("` - returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 4/4 PASS. LOCAL branch guarded: exists()-precheck + IOException catch around copyTo, both return false. Log.d count: expected 0 | actual 0.

---

### Step 01.3 - Guard the SMB / SFTP / FTP destination branches in `downloadFromCloudTo`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> The SMB, SFTP, and FTP branches consume the temp copy via `tempFile.inputStream()` and `tempFile.length()`, which throw the same `FileNotFoundException`/`IOException` if the temp copy vanished. Add the same `tempFile.exists()` precheck (log `Timber.w(..)` + `return false`) at the entry of each of the three network branches, and ensure the `tempFile.inputStream()` upload call in each branch is inside an `IOException` catch that logs `Timber.w(e, ..)` and `return false`. Reuse the existing per-branch failure logging style. Do not change the existing FTP `finally { ftpClient.disconnect() }` or the method-level `finally { tempFile.delete() }`. No ticket id in any log line.

**Verification:**

- `Grep` - `tempFile.exists()` matches at least 4 times in the file (LOCAL + SMB + SFTP + FTP entry guards).
- `Grep` - `ftpClient.disconnect()` still present exactly once (existing `finally` preserved).
- `Grep -n "Log\.d\("` - returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. tempFile.exists() expected >=4 | actual 4. ftpClient.disconnect() expected unchanged | actual 2 (download+upload methods, both preserved). Log.d expected 0 | actual 0. SMB/SFTP/FTP each: exists()-precheck + IOException catch around upload, return false.

---

### Step 01.4 - Guard the parallel `downloadFromCloud` (File-returning) method

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> The sibling private method `downloadFromCloud(cloudPath, localFile, ..)` has the same unguarded `tempFile.copyTo(localFile, overwrite = true)` in its `CloudResult.Success` branch. Apply the identical protection: `tempFile.exists()` precheck → `Timber.w(..)` + `return null`; wrap `copyTo` in `catch (e: IOException)` → `Timber.w(e, ..)` + `return null`. Keep the existing `finally { tempFile.delete() }`. No ticket id in log text.

**Verification:**

- `Grep` - `return null` appears in the `downloadFromCloud` Success path guarded by an `exists()`/`catch` (manual confirm the new lines sit inside `downloadFromCloud`, not only `downloadFromCloudTo`).
- `Grep` - `tempFile.copyTo(localFile, overwrite = true)` still matches exactly twice (both methods retain their copy call, now guarded).
- `Grep -n "Log\.d\("` - zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. downloadFromCloud Success branch: exists()-precheck + IOException catch around copyTo, both return null. copyTo(localFile, overwrite=true) expected 2 | actual 2. Log.d expected 0 | actual 0.

---

### Step 01.5 - Insert the `BlockNeedUserTest` debug probe on the cloud download entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", S0355 enters `BlockNeedUserTest` after implementation, so the changed flow needs exactly one entry-point probe. Add `Timber.d("S0355: cloud download temp-transfer guard reached")` once at the top of `downloadFromCloudTo` (the entry of the changed flow). This is the only place a ticket id is allowed in log text; do not scatter additional `S0355:` tags across the per-branch guards. The `/spec-check` that moves the ticket to `Verified` (or `/spec-update` on re-open) is responsible for grep-deleting this line.

**Verification:**

- `Grep` - `Timber.d("S0355:` matches exactly once across all `.kt` files.
- `Grep` - that single match is inside `CloudFileOperationHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 2/2 PASS. Probe at line 463, entry of downloadFromCloudTo. Timber.d("S0355: expected 1 | actual 1 across all .kt; inside CloudFileOperationHandler.kt. (Tag pre-staged per tactical plan; ticket finishes this run in BlockNeedUserTest, restoring the IFF invariant at rest.)

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` for the `noLegalDebug` variant. **DEFERRED** - user requested "no build". Must be run before `/spec-check`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits. (expected 0 | actual 0)
- [x] Dev log entry added for `CloudFileOperationHandler.kt` via post-change.ps1.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`downloadFromCloudTo` and `downloadFromCloudToPublic` now return `false` (never throw) when the temp copy is missing or unreadable at transfer time, for every destination type. Phase 02 layers caller-side isolation on top of this guarantee: `VrApkArchiveResolver.resolveCloudArchive` already treats a `false` return as "no cache file", but must additionally catch any non-`IOException` thrown earlier in the cloud pipeline.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface changed; behavior reverts to the pre-fix throw-on-missing-temp path.
