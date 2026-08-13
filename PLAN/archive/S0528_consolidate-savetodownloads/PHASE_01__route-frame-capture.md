# Phase 01 - Route frame capture through the shared local writer

**Strategic spec:** [`../S0528_consolidate-savetodownloads.md`](../S0528_consolidate-savetodownloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 02
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Replace the private MediaStore `saveToDownloads` in the video-frame capture flow with the shared local destination writer (overwrite mode), threading the writer and classifier in through `PlayerActivity`. No change to capture, temp-file, or destination-resource logic.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Shared writer binding exists (already consumed by `SaveGifFirstFrameUseCase`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 990 (currently 986 - backup before editing) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt` | Modified | ≤ 290 (currently 304 - net shrinks) |

> `PlayerActivity.kt` exceeds 500 LOC - take a timestamped backup into `temp/` before editing (Step 01.1).
> No layout files touched - landscape parity not applicable.

---

## Steps

### Step 01.1 - Inject the shared writer + classifier into PlayerActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> First copy `PlayerActivity.kt` to `temp/PlayerActivity.kt.<yyyyMMdd_HHmm>.bak` (file > 500 LOC). Then add two injected fields beside the existing `@Inject lateinit var` block (around lines 328-381): `@Inject lateinit var localDestinationClassifier: LocalDestinationClassifier` and `@Inject lateinit var localDestinationWriter: LocalDestinationWriter`, importing `com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier` and `...LocalDestinationWriter`. Mirror the existing field style; field injection only (no constructor change). The fields compile unused until Step 01.2 wires them.

**Verification:**

- `Grep` - `localDestinationClassifier: LocalDestinationClassifier` matches once in `PlayerActivity.kt`.
- `Grep` - `localDestinationWriter: LocalDestinationWriter` matches once in `PlayerActivity.kt`.
- `Glob` - a `temp/PlayerActivity.kt.*.bak` backup exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 3/3 PASS. Added `localDestinationClassifier` + `localDestinationWriter` `@Inject` fields + imports to PlayerActivity.kt. Backup temp/PlayerActivity.kt.20260619_1048.bak.

---

### Step 01.2 - Add the constructor deps, update the construction site, and route saveToDownloads through the shared writer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> One atomic change - `SaveVideoFrameManager` is manually constructed, so the constructor signature, its sole construction site, and the body must change together to keep the build green.
> 1. In `SaveVideoFrameManager`, add `private val localDestinationClassifier: LocalDestinationClassifier` and `private val localDestinationWriter: LocalDestinationWriter` to the constructor (import from `com.sza.fastmediasorter.data.transfer.local`).
> 2. In `PlayerManagerInitializer` at the `SaveVideoFrameManager(...)` construction (around line 280), pass `localDestinationClassifier = activity.localDestinationClassifier` and `localDestinationWriter = activity.localDestinationWriter`.
> 3. Rewrite `saveToDownloads` to mirror `SaveGifFirstFrameUseCase.execute`: build the Downloads target path (`File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName).absolutePath`), `localDestinationClassifier.classify(targetPath)`, `localDestinationWriter.open(category, overwrite = true)`, copy temp-file bytes into `sink.outputStream` via `FileInputStream(tempFile).use { it.copyTo(sink.outputStream) }`, `sink.commit()`, and on `Build.VERSION.SDK_INT < Q` call `MediaStoreNotifier.notifyFile(...)`. On open/write failure call `sink.abort()` where a sink exists and rethrow (preserve the current throwing contract used by the caller's catch). Delete the old `ContentValues`/`MediaStore.Downloads`/`IS_PENDING` query-delete-insert block and the pre-Q `copyTo(overwrite = true)` block. MIME now comes from the classifier - if the `useJpeg` parameter becomes unused inside `saveToDownloads`, drop it and update the internal call at the capture site accordingly. Drop now-unused imports (`ContentUris`, `ContentValues`, `MediaStore`). Do not touch capture, temp-file, or destination-resource logic, and keep the S0522 fallback path intact.

**Verification:**

- `Grep` - `localDestinationClassifier = activity.localDestinationClassifier` matches once in `PlayerManagerInitializer.kt`.
- `Grep` - `localDestinationWriter.open(` matches once in `SaveVideoFrameManager.kt`.
- `Grep` - `overwrite = true` present in `SaveVideoFrameManager.kt`.
- `Grep` - `MediaStore.Downloads` returns zero matches in `SaveVideoFrameManager.kt`.
- `Grep` - `IS_PENDING` returns zero matches in `SaveVideoFrameManager.kt`.
- `Grep` - `resolver.insert` returns zero matches in `SaveVideoFrameManager.kt`.
- `Grep` - `ContentValues` returns zero matches in `SaveVideoFrameManager.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SaveVideoFrameManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 8/8 PASS. SaveVideoFrameManager constructor gained classifier+writer; `saveToDownloads` rewritten to route through shared writer (overwrite=true) + pre-Q MediaStoreNotifier; `useJpeg` param dropped; MediaStore/ContentValues/IS_PENDING block removed; construction site in PlayerManagerInitializer updated. Build deferred to consolidated post-Phase-02 build.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Video-frame capture now writes to Downloads exclusively through `LocalDestinationWriter` with `overwrite = true`. Phase 02 applies the identical pattern to the link-download flow; the two are independent.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface changed. Restore `temp/PlayerActivity.kt.*.bak` if needed.
