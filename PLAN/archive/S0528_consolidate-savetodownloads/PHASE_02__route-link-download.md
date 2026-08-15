# Phase 02 - Route link download through the shared local writer

**Strategic spec:** [`../S0528_consolidate-savetodownloads.md`](../S0528_consolidate-savetodownloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Replace the private MediaStore `saveToDownloads` in the link-download flow with the shared local destination writer (overwrite mode), and remove the now-dead pre-Q unique-naming helper. No change to resource-copy, fallback, or content-sniff logic.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Shared writer binding exists (already consumed by `SaveGifFirstFrameUseCase`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt` | Modified | ≤ 240 (currently 242 - net shrinks) |

> No layout files touched - landscape parity not applicable.

---

## Steps

### Step 02.1 - Inject the shared writer + classifier into LinkDownloadWriter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `private val destinationWriter: LocalDestinationWriter` to the existing `@Inject` constructor, importing `com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter`. Hilt already provides it (no new `@Module`); the class is Hilt-constructed so there is no manual call site to update. The classifier is NOT needed here: the link flow already knows the caller-supplied MIME, so Step 02.2 builds the `PublicCollection` destination directly (preserving MIME - strategic non-goal: MIME must not change). Param compiles unused until Step 02.2.

**Verification:**

- `Grep` - `destinationWriter: LocalDestinationWriter` matches once in `LinkDownloadWriter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 1/1 PASS. Added `destinationWriter: LocalDestinationWriter` to the `@Inject` constructor (classifier not needed - MIME preserved via manual category in 02.2).

---

### Step 02.2 - Route saveToDownloads through the shared writer; drop the unique-name helper and stale comment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Make `saveToDownloads(tempFile, fileName, mime)` a `private suspend fun` (the shared writer's `open`/`commit` are suspend; callers already run inside `withContext(Dispatchers.IO)`). Rewrite its body: build the destination directly as `LocalDestinationCategory.PublicCollection(collection = ...Kind.DOWNLOADS, relativePath = Environment.DIRECTORY_DOWNLOADS + "/", displayName = fileName, mimeType = mime)` - using the caller's `mime` preserves MIME (strategic non-goal). Then `destinationWriter.open(category, overwrite = true)`, copy temp-file bytes into `sink.outputStream` via `FileInputStream(tempFile).use { it.copyTo(sink.outputStream) }`, `sink.commit()`, and on `Build.VERSION.SDK_INT < Q` call `MediaStoreNotifier.notifyFile(context, savedPath, "link-download")`. Keep the method's `Uri?` return: derive from the committed path - `if (savedPath.startsWith("content:")) Uri.parse(savedPath) else Uri.fromFile(File(savedPath))`. Return `null` on open/write/commit failure after `sink.abort()` where a sink exists. Delete the old `ContentValues`/`MediaStore.Downloads`/`IS_PENDING` block and the pre-Q direct-Downloads branch. KEEP the `uniqueFile` helper - it is still used for the temp-file name (line ~70); only its use inside `saveToDownloads` goes away. Remove the now-stale "Duplicated logic from `SaveVideoFrameManager.saveToDownloads`" KDoc. Drop now-unused imports (`ContentUris`, `ContentValues`, `MediaStore`, `IOException`). Do not touch the resource-copy, fallback-reason, or `sniffMedia` logic.

**Verification:**

- `Grep` - `destinationWriter.open(` matches once in `LinkDownloadWriter.kt`.
- `Grep` - `overwrite = true` present in `LinkDownloadWriter.kt`.
- `Grep` - `Kind.DOWNLOADS` present in `LinkDownloadWriter.kt`.
- `Grep` - `MediaStore.Downloads` returns zero matches in `LinkDownloadWriter.kt`.
- `Grep` - `IS_PENDING` returns zero matches in `LinkDownloadWriter.kt`.
- `Grep` - `resolver.insert` returns zero matches in `LinkDownloadWriter.kt`.
- `Grep` - `Duplicated logic from` returns zero matches in `LinkDownloadWriter.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `LinkDownloadWriter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 8/8 PASS. `saveToDownloads` now suspend; routes through shared writer with a manually-built `PublicCollection(DOWNLOADS, ..., mimeType = mime)` (overwrite=true) + pre-Q MediaStoreNotifier; returns Uri from committed path; MediaStore/ContentValues/IS_PENDING block + stale "Duplicated logic" KDoc removed; `uniqueFile` retained (temp-file use). Build deferred to consolidated build.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Both Downloads-write flows (frame capture, link download) now go through `LocalDestinationWriter` with `overwrite = true`. No private MediaStore Downloads writer remains in either flow. Phase 03 regenerates the catalog and closes the dev log.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface changed.
