# Phase 02 - Clipboard writer

**Strategic spec:** [`../S0468_screenshot-clipboard.md`](../S0468_screenshot-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 0 / 1
**Started:** -
**Completed:** -

---

## Objective

Introduce a reusable `ImageClipboardWriter` that writes a bitmap to an app-cache PNG and places it on the system clipboard as an `image/png` content URI. No capture wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/clipboard/ImageClipboardWriter.kt` | New | ≤ 120 |

---

## Steps

### Step 02.1 - Create `ImageClipboardWriter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/clipboard/ImageClipboardWriter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class ImageClipboardWriter @Inject constructor(@ApplicationContext private val context: Context)` in package `com.sza.fastmediasorter.core.clipboard`. Expose `suspend fun copyBitmap(bitmap: Bitmap): Boolean`. Implementation per research artifacts 01 and 03:
> - Compress the bitmap to PNG into a stable cache file (e.g. `cacheDir/clipboard/screenshot_clip.png`, overwritten each call) off the main thread (`withContext(Dispatchers.IO)`).
> - Resolve a FileProvider URI via `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)` - the existing `<cache-path>` mapping already covers `cacheDir`.
> - Build `ClipData.newUri(context.contentResolver, label, uri)` and call `ClipboardManager.setPrimaryClip(clip)` (clipboard manager obtained from `context`).
> - Do not recycle the passed bitmap - the caller still needs it for the destination save.
> - Return `true` on success, `false` on failure; log a failure at `Timber.w` with a plain-English subject (no ticket id in the permanent log). Use a narrow `catch (e: Exception)` that returns `false` after logging - no empty/broad swallow.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/clipboard/ImageClipboardWriter.kt` exists.
- `Grep` - `class ImageClipboardWriter` matches once.
- `Grep` - `suspend fun copyBitmap` matches once.
- `Grep` - `setPrimaryClip` matches once.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 5/5 PASS (file exists, class ×1, copyBitmap ×1, setPrimaryClip ×1, Log.d ×0). Files: ImageClipboardWriter.kt (New, 64 LOC). Post-change PASS. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `ImageClipboardWriter.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed: regenerate `dev/CATALOG/app_v2.jsonl` (done centrally in Phase 05).

---

## Handoff Notes to Next Phase

`ImageClipboardWriter.copyBitmap(bitmap)` is constructor-injectable (no Hilt module needed). Phase 03 injects it into the capture service and calls it on the live bitmap before save.

---

## Rollback Plan

Revert phase commit(s) - new file only, no consumer until Phase 03.
