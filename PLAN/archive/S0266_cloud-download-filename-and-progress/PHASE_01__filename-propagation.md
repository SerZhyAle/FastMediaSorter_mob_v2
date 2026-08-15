# Phase 01 — Filename Propagation

**Strategic spec:** [`../S0266_cloud-download-filename-and-progress.md`](../S0266_cloud-download-filename-and-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Carry the cloud display-name (e.g. `MyVideo.mp4`) from `CloudMediaScanner` through every cloud `File`-wrapper into `BaseFileOperationHandler.extractFileName`, and add a defensive `getFileMetadata` fallback inside `downloadFromCloudTo` for paths that arrive without a name (recovered last-viewed, raw `cloud://` URLs).

---

## Prerequisites

- [ ] Strategic spec read.
- [ ] Working tree clean or on `DEBUG-v004`.
- [ ] §6.1 and §6.2 resolutions noted (see INDEX.md).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudFileHandle.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 800 |

> All listed files are below the 500-line backup threshold except `BrowseManagerInitializer.kt` and `CloudFileOperationHandler.kt`. Phase 01 final step in `docs-catalog-cleanup` (Phase 05) covers catalog regen.

---

## Steps

### Step 01.1 — Create `CloudFileHandle` subclass

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudFileHandle.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `CloudFileHandle.kt` in `data/transfer/` package. Declare `class CloudFileHandle(cloudPath: String, val displayName: String, private val size: Long = 0L) : File(cloudPath)`. Override `getAbsolutePath()`, `getPath()` to return the original `cloudPath` (so the existing `cloud://provider/<fileId>` string survives across passes). Override `getName()` to return `displayName`. Override `length()` to return `size` (preserves the cached cloud-file size for progress UI - aligns with the existing anonymous File-wrapper pattern in `BrowseFileOperationsManager.showCopyDialog`). Add a single-line KDoc: `/** java.io.File wrapper that preserves the cloud display-name and cached size alongside the cloud:// path. */`. No Hilt, no other state.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudFileHandle.kt` exists.
- `Grep` — `class CloudFileHandle` matches exactly once in that file.
- `Grep` — `override fun getName(): String` present in that file.
- `Grep` — `override fun getAbsolutePath(): String` present in that file.
- `Grep` — `: File\(` present (extends `java.io.File`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudFileHandle.kt` (+16 LOC, new). Dev log recorded.

---

### Step 01.2 — Teach `BaseFileOperationHandler.extractFileName` about `CloudFileHandle`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `BaseFileOperationHandler.extractFileName(path: String, fallbackName: String)`, the cloud branch currently returns `fallbackName`. Change behaviour so that when `path.startsWith("cloud://") || path.startsWith("cloud:/")`, if `fallbackName` looks like a display-name (contains `.` extension OR length < 20 OR contains chars outside `[A-Za-z0-9_-]`), return `fallbackName` as-is. Otherwise (looks like a bare fileId) return `fallbackName` unchanged — the cloud handler will defensively re-fetch metadata in Step 01.4. Do NOT call any network here. Comment the heuristic explicitly: `// S0266: fallbackName is the displayName when caller passed a CloudFileHandle; raw cloud:// URIs land here with fileId as fallbackName and Step 01.4 re-fetches metadata.`

**Verification:**

- `Grep` — `S0266: fallbackName is the displayName` present in `BaseFileOperationHandler.kt`.
- `Grep` — `cloud://` still matches in `BaseFileOperationHandler.kt` (the cloud branch was not deleted).
- `Grep -n "Log\\.d\\("` — zero hits in `BaseFileOperationHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 3/3 PASS. Files: `BaseFileOperationHandler.kt` (+1 LOC comment). Dev log recorded.

---

### Step 01.3 — Build `CloudFileHandle` at the source-collection sites

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` — **not modified** (cached share files are always local, never cloud).

**Depends on:** Step 01.1

**Prompt for developer:**

> Every place that builds `sourceFiles: List<File>` from `MediaFile` / `MediaResource` / raw paths must construct a `CloudFileHandle(path, displayName, size)` for cloud paths and a plain `File(path)` for everything else. Where the call site has access to `MediaFile.name`: when `path.startsWith("cloud://")` use `CloudFileHandle(path, mediaFile.name, mediaFile.size)`. When only a string path is available without a known display-name (e.g. recovered last-viewed): fall back to plain `File(path)` — the defensive metadata fetch in Step 01.4 covers it. Add `import com.sza.fastmediasorter.data.transfer.CloudFileHandle` at each touched file. Sites touched: `BrowseFileOperationsManager.showCopyDialog` + `showMoveDialogInternal`, `BrowseManagerInitializer.showArchiveDestinationPicker`, `PlayerDialogHelper.showCopyDialog` + `showMoveDialogInternal`. `ReceiveShareActivity` skipped — share-intent inputs are already locally cached `File` objects, never `cloud://`.

**Verification:**

- `Grep` — `CloudFileHandle(` matches at least 4 times across the modified files.
- `Grep` — `import com.sza.fastmediasorter.data.transfer.CloudFileHandle` present in the 3 modified files (`BrowseFileOperationsManager`, `BrowseManagerInitializer`, `PlayerDialogHelper`).
- `Grep -n "Log\\.d\\("` — zero hits introduced in any of the 3 modified files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 3/3 PASS. Files: `BrowseFileOperationsManager.kt` (showCopy + showMove, +2 CloudFileHandle), `BrowseManagerInitializer.kt` (archive, +1), `PlayerDialogHelper.kt` (showCopy + showMove, +2). `ReceiveShareActivity.kt` not touched — share intent files are local. Dev log recorded.

---

### Step 01.4 — Defensive metadata fetch in `downloadFromCloudTo`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> In `CloudFileOperationHandler.downloadFromCloudTo(cloudPath, destPath, fileName, progressCallback)`, after the `pathInfo` is resolved and before the temp file is created: if `fileName` matches the regex `^[A-Za-z0-9_-]{20,}$` (bare fileId, no extension) — call `cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client -> client.getFileMetadata(pathInfo.fileId) }`. On `CloudResult.Success` replace `fileName` with `metadata.name`. On any failure or null name, log via `Timber.w("S0266: getFileMetadata fallback failed for $cloudPath - using original fileName")` and continue with the original `fileName`. Wrap the resolved name in a local `val resolvedFileName = ...` and use it consistently downstream (the existing `File(normalizedDestPath, fileName)` call must use `resolvedFileName` instead).

**Verification:**

- `Grep` — `S0266: getFileMetadata fallback` present in `CloudFileOperationHandler.kt`.
- `Grep` — `val resolvedFileName` present in `CloudFileOperationHandler.kt`.
- `Grep` — `\\^\\[A-Za-z0-9_-\\]\\{20,\\}\\$` regex literal present in `CloudFileOperationHandler.kt`.
- `Grep -n "Log\\.d\\("` — zero hits in `CloudFileOperationHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 4/4 PASS (regex literal `^[A-Za-z0-9_-]{20,}$` present at line 488 — manual confirm). Files: `CloudFileOperationHandler.kt` (+19 LOC fallback + 5 site rename `fileName` → `resolvedFileName`). Dev log recorded.

---

### Step 01.5 — Unit-test guard: extractFileName cloud branch

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandlerExtractFileNameTest.kt` (New)
**Depends on:** Step 01.2

**Prompt for developer:**

> Create a minimal JUnit4 test that exercises `extractFileName` with: (a) cloud path + display-name fallback → returns display-name; (b) cloud path + bare-fileId fallback → returns the fileId unchanged (the network fallback is in `CloudFileOperationHandler`, not here); (c) `content://` path → returns last segment; (d) plain local path → returns last segment. Use Robolectric only if the existing test base requires it; otherwise pure JUnit. Class name: `BaseFileOperationHandlerExtractFileNameTest`. Method names: `extractFileName_cloudPath_withDisplayName_returnsDisplayName`, `..._withBareFileId_returnsFileId`, `..._contentUri_returnsLastSegment`, `..._localPath_returnsLastSegment`. Use `BaseFileOperationHandler`'s extractFileName via a minimal anonymous subclass since the method is `protected`.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandlerExtractFileNameTest.kt` exists.
- `Grep` — `class BaseFileOperationHandlerExtractFileNameTest` matches exactly once.
- `Grep` — `fun extractFileName_cloudPath_withDisplayName_returnsDisplayName` present.
- `Build` — `./a.ps1 dq` compiles successfully (test runs in Phase 05).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 3/3 PASS (build check folded into Step 01.6). Files: `BaseFileOperationHandlerExtractFileNameTest.kt` (+53 LOC, new). Dev log recorded.

---

### Step 01.6 — Compile gate

**Files:** —
**Depends on:** Step 01.1 .. Step 01.5

**Prompt for developer:**

> Run `./a.ps1 dq` to compile `standardDebug`. Treat BUILD SUCCESSFUL as the gate. If compile errors appear: fix in the appropriate step above (typically Step 01.3 missed call site), re-run.

**Verification:**

- `Bash` — `./a.ps1 dq` exits 0.
- `Grep` — `BUILD SUCCESSFUL` matches in the captured build output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. BUILD SUCCESSFUL in 54s, version 2.60.5201.218.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `./a.ps1 dq` exits 0 (standardDebug compiles).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Catalog regen deferred to Phase 05.

---

## Handoff Notes to Next Phase

- `CloudFileHandle` is the canonical wrapper for cloud source files. Phase 02 may rely on `source.name` returning the display-name (because `File.getName()` is overridden).
- `downloadFromCloudTo` now resolves a real filename internally — Phase 02 just plugs in the progress callback without touching the name path.

---

## Rollback Plan

Revert Phase 01 commit(s). No data migration; only new file `CloudFileHandle.kt` to delete and edits to revert. No user-facing surface beyond the (still broken) bug-fix path.
