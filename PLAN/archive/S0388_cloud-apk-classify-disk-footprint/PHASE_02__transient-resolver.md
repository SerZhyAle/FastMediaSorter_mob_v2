# Phase 02 - Transient classification copy + size validation + stale purge

**Strategic spec:** [`../S0388_cloud-apk-classify-disk-footprint.md`](../S0388_cloud-apk-classify-disk-footprint.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (compiles together with Phase 03)
**Blocks:** Phase 03, Phase 04
**Steps:** 4 / 4

---

## Objective

Make `VrApkArchiveResolver.resolveCloudArchive` produce a transient copy instead of a persistent keyed disk-cache file: download once, validate against the expected size, hand it to the caller for one read, signal whether the caller must delete it. Detect out-of-space and report it distinctly so the caller can stop classification for the session. Purge legacy/leaked copies once per session (strategic §5.1 pillars + §6.1/§6.2 conclusions).

All changes live in the noLegal source set.

---

## Prerequisites

- [ ] `VrApkArchiveResolver.kt` is < 500 LOC - no backup required.
- [ ] Phase 01 understood (the LOCAL transfer is now a move).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt` | Modified | ≤ 180 |

> Flavor-only file under `src/noLegal/java/`. Keep it there. Source-set placement isolates it to noLegal - no `BuildConfig` guard.

---

## Steps

### Step 02.1 - Introduce the `VrArchiveResolution` return type

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `sealed interface VrArchiveResolution` in this file (same package, so the cache sees it without an import). Three cases:
>
> - `class Available(val file: File, val cleanup: () -> Unit) : VrArchiveResolution` - the archive is ready to classify; the caller must invoke `cleanup()` after reading it. `cleanup` is a no-op for original on-disk files (local source) and deletes the transient copy for cloud/downloaded files.
> - `data object OutOfSpace : VrArchiveResolution` - the cache volume cannot hold the copy; the caller should stop classifying for the session.
> - `data object Unavailable : VrArchiveResolution` - no usable archive; degrade to `NOT_VR`.
>
> Use a plain `class` (not `data class`) for `Available` because it carries a function reference. `data object` is fine on Kotlin 2.2.

**Verification:**

- `Grep` - `sealed interface VrArchiveResolution` matches.
- `Grep` - `class Available(`, `data object OutOfSpace`, `data object Unavailable` all match.

**Status:** `[x] done`

---

### Step 02.2 - Change `resolve` to return `VrArchiveResolution`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Change `suspend fun resolve(mediaFile: MediaFile): File?` to `suspend fun resolve(mediaFile: MediaFile): VrArchiveResolution`. Map each branch:
>
> - Local path: `File(mediaFile.path)` if `exists() && isFile` → `VrArchiveResolution.Available(file) {}` (no-op cleanup - never delete the user's original file); otherwise `VrArchiveResolution.Unavailable`.
> - Network path (`smb://`/`sftp://`/`ftp://`): `networkDownloader.downloadToTemp(..)` → if non-null `VrArchiveResolution.Available(temp) {}` else `VrArchiveResolution.Unavailable`. Per strategic non-goals, do NOT change network temp-file behavior (no auto-delete here - leave cleanup a no-op).
> - Cloud path: delegate to `resolveCloudArchive(mediaFile)` which now returns `VrArchiveResolution` (Step 02.3).
> - `else` → `VrArchiveResolution.Unavailable`.

**Verification:**

- `Grep` - `suspend fun resolve(mediaFile: MediaFile): VrArchiveResolution` matches.
- `Grep` - `VrArchiveResolution.Available` appears at least twice (local + network branches).

**Status:** `[x] done`

---

### Step 02.3 - Rewrite `resolveCloudArchive` for transient copy, size validation, out-of-space, stale purge

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Rewrite `resolveCloudArchive(mediaFile)` to return `VrArchiveResolution`:
>
> 1. Build `cacheDir = File(context.cacheDir, CLOUD_CACHE_DIR_NAME).apply { mkdirs() }`.
> 2. **One-time stale purge.** Guard with an `AtomicBoolean` `staleCopiesPurged`. On the first cloud resolve, list `cacheDir` and delete every file whose `lastModified() < sessionStartMillis` (a `private val sessionStartMillis = System.currentTimeMillis()` field captured at construction). This removes legacy persistent copies from older builds and leaked transients from a previous session, while never touching files created this session (in-flight transients have `lastModified >= sessionStartMillis`). The `compareAndSet(false, true)` makes it run once and race-free.
> 3. **Pre-flight space check.** If `cacheDir.usableSpace < spaceNeeded(mediaFile.size)` → return `VrArchiveResolution.OutOfSpace` without attempting the download. `spaceNeeded` = `mediaFile.size.coerceAtLeast(MIN_FREE_SPACE_FLOOR_BYTES)` (floor e.g. 32 MiB to cover unknown/zero sizes).
> 4. Build the transient scratch file `File(cacheDir, "${mediaFile.path.hashCode()}_${mediaFile.size}_${mediaFile.name}")`. Do NOT reuse an existing file by `length() > 0` - the persistent-reuse branch is removed (the memory result cache covers re-binds; strategic §6.1).
> 5. Download via `cloudFileOperationHandler.downloadFromCloudToPublic(cloudPath = mediaFile.path, destPath = cacheDir.absolutePath, fileName = scratch.name)`.
> 6. **Validate.** `val sizeOk = if (mediaFile.size > 0L) scratch.length() == mediaFile.size else scratch.length() > 0L`. On `downloadOk && scratch.exists() && sizeOk` → return `VrArchiveResolution.Available(scratch) { scratch.delete() }` (transient; caller deletes after read).
> 7. **Failure.** Otherwise delete the scratch (drop any truncated copy) and decide: if `cacheDir.usableSpace < spaceNeeded(mediaFile.size)` → `OutOfSpace`, else `Unavailable`.
>
> Keep the whole body inside the existing `try { .. } catch (e: Exception) { Timber.w(e, ..); VrArchiveResolution.Unavailable }` guard (S0355 belt-and-braces). Log text must describe the operation and must NOT embed `S0388` or any ticket id (the single debug probe is added separately in Phase 04). Add `MIN_FREE_SPACE_FLOOR_BYTES` to the companion object.

**Verification:**

- `Grep` - `usableSpace` matches in the file.
- `Grep` - `scratch.length() == mediaFile.size` (or the equivalent size-equality check) matches.
- `Grep` - `VrArchiveResolution.OutOfSpace` returned at least once.
- `Grep` - `AtomicBoolean` and `sessionStartMillis` both present.
- `Grep` - no `cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L` persistent-reuse early-return remains (the old reuse branch is gone).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x] done`

---

### Step 02.4 - Imports and companion constants

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `import java.util.concurrent.atomic.AtomicBoolean`. Confirm `MIN_FREE_SPACE_FLOOR_BYTES` is defined in the companion object next to `CLOUD_CACHE_DIR_NAME`. Confirm `timber.log.Timber` import is present (from S0355).

**Verification:**

- `Grep` - `import java.util.concurrent.atomic.AtomicBoolean` present.
- `Grep` - `MIN_FREE_SPACE_FLOOR_BYTES` defined in companion.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Build deferred to Phase 04 - this file only compiles in the noLegal source set and only together with Phase 03's caller update.

---

## Handoff Notes to Next Phase

`resolve` now returns `VrArchiveResolution`. The sole caller `VrApkClassificationCache.classifyForCache` (Phase 03) must switch on it: read + `cleanup()` for `Available`, set a session disk-full flag for `OutOfSpace`, degrade to `NOT_VR` for `Unavailable`. The `testNoLegal` test that mocks `resolve(..) returns archive` must be updated to return `VrArchiveResolution.Available(archive) {}`.

---

## Rollback Plan

Revert the phase commit - resolver reverts to persistent keyed disk-cache returning `File?`. Flavor-only, no migration.
