# Phase 01 - Eliminate cloud→local double copy (move, not copy)

**Strategic spec:** [`../S0388_cloud-apk-classify-disk-footprint.md`](../S0388_cloud-apk-classify-disk-footprint.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 04 (build)
**Steps:** 3 / 3

---

## Objective

On the shared cloud→local download path, move (rename) the downloaded temp copy into the destination instead of copying it, so a single full copy ever exists on the volume. Fall back to copy for cross-volume destinations. Delete any partially written destination on transfer failure. No flavor-specific code; all flavors using cloud download benefit.

This removes the simultaneous double-footprint peak (`cloud_download_*.tmp` + destination) that fills the Quest cache volume when classifying large APKs (strategic §1, §5.1 pillar "Устранение двойного копирования", §5.1 pillar "Чистка обрезков").

---

## Prerequisites

- [ ] `CloudFileOperationHandler.kt` is 1018 LOC (>500) - timestamped backup mandatory before the first edit.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1040 |

> 1018 LOC (>500) → backup required (Step 01.1). Stays under 1500, no Manager split forced.
>
> **Flavor placement.** Shared `src/main` code. Do NOT add any `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` guard - the move-not-copy behavior is universal for every flavor that downloads cloud→local.

---

## Steps

### Step 01.1 - Back up the file before editing

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The target file is 1018 LOC (>500), so create a timestamped backup before the first edit. Copy it to `temp/CloudFileOperationHandler_<yyyyMMdd_HHmmss>.kt.backup`. Do not write anything to the project root.

**Verification:**

- `Glob` - a file matching `temp/CloudFileOperationHandler_*.kt.backup` exists.

**Status:** `[ ] pending`

---

### Step 01.2 - Replace copy with move in the LOCAL branch of `downloadFromCloudTo`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `downloadFromCloudTo`, in the `ResourceType.LOCAL` branch, the current logic is: `tempFile.exists()` precheck (keep it), then `localFile.parentFile?.mkdirs()`, then `tempFile.copyTo(localFile, overwrite = true)` inside a `try/catch (IOException)`. Replace the copy with a move:
>
> 1. Before transfer, delete a stale destination: `if (localFile.exists()) localFile.delete()`.
> 2. Attempt `val moved = tempFile.renameTo(localFile)`. `renameTo` succeeds only within one volume and is atomic - no truncated destination is possible on the success path.
> 3. If `!moved` (cross-volume destination), fall back to the existing copy inside `try { tempFile.copyTo(localFile, overwrite = true) } catch (e: java.io.IOException) { Timber.w(e, ..); localFile.delete(); return false }`. The added `localFile.delete()` drops any partially written destination so a truncated file is never left behind or mistaken for a valid copy.
>
> Keep the existing `tempFile.exists()` precheck and its `Timber.w(..)` + `return false`. Keep the trailing `Timber.i("downloadFromCloudTo: SUCCESS - wrote to local ..")` and `MediaStoreNotifier.notifyFile(..)`. Do NOT alter the method-level `finally { tempFile.delete() }` - after a successful rename it is a harmless no-op (the temp no longer exists at that path); after the copy fallback it still cleans the temp. No ticket id in any log line. Add a one-line WHY comment explaining move-not-copy avoids the double-footprint peak.

**Verification:**

- `Grep` - `renameTo(localFile)` matches in the file.
- `Grep` - `tempFile.copyTo(localFile, overwrite = true)` still present (the cross-volume fallback) - count unchanged or as expected for the LOCAL branch.
- `Grep` - `localFile.delete()` appears in the LOCAL branch (stale-destination drop and/or failed-copy cleanup).
- `Grep -n "Log\.d\("` - zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-09 - LOCAL branch now: stale-dest delete → `tempFile.renameTo(localFile)` → copy fallback (cross-volume) with `localFile.delete()` on IOException. `exists()` precheck + `finally { tempFile.delete() }` preserved. No `Log.d(`.

---

### Step 01.3 - Confirm the parallel `downloadFromCloud` (File-returning) method is left consistent

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> The sibling private `downloadFromCloud(cloudPath, localFile, ..)` also does `tempFile.copyTo(localFile, overwrite = true)` in its `CloudResult.Success` branch. The classification path does NOT use this method (it uses `downloadFromCloudToPublic` → `downloadFromCloudTo`), so a move change here is optional for this ticket. Leave `downloadFromCloud` as copy to keep blast radius minimal; just confirm it still compiles and retains its `exists()` precheck + `IOException` catch from S0355. Do not introduce a second move path.

**Verification:**

- `Grep` - `downloadFromCloud(` method body still contains `copyTo(localFile, overwrite = true)` and its `exists()` precheck (unchanged from S0355).

**Status:** `[x] done`

**Step Log:**

- 2026-06-09 - `downloadFromCloud` left as copy (not on the classification path). `copyTo(localFile, overwrite = true)` count across file = 2 (this method + the LOCAL cross-volume fallback). Both `exists()` prechecks intact.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] `Grep -n "Log\.d\("` returns zero hits in this file.
- [ ] Build deferred to Phase 04 (`noLegalDebug`).

---

## Handoff Notes to Next Phase

The LOCAL destination transfer no longer holds two full copies at once. The classification path (Phase 02) downloads through this same LOCAL branch, so its transient copy now costs one file, not two. Phase 02 makes the classification copy transient (deleted after read) and adds size validation + out-of-space detection.

---

## Rollback Plan

Revert the phase commit - behavior reverts to copy-then-delete-temp. No data migration, no user-facing surface changed.
