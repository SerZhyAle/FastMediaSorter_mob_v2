# Phase 02 - Network Downloader

**Strategic spec:** [`../S0287_tesseract-cyrillic-model-swap-evaluation.md`](../S0287_tesseract-cyrillic-model-swap-evaluation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Implement a safe HTTPS downloader inside `TesseractModelManager.kt` that supports size verification, progress tracking (percentage and bytes written), temporary staging files, and error handling.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt` | Modified | ≤ 250 |

---

## Steps

### Step 02.1 - Implement Download Routine with Progress Callbacks

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt`
**Depends on:** Phase 01 completion

**Prompt for developer:**
> Expand `TesseractModelManager.kt` to include a suspended function `downloadModel(language: String, onProgress: (percent: Int, bytes: Long, total: Long) -> Unit): Boolean`. It must download the model file from `https://github.com/tesseract-ocr/tessdata_best/raw/main/<lang>.traineddata` into a temporary staging file (`<lang>.traineddata.tmp`) inside `tesseract_best/tessdata/`. The loop reading the input stream must write to the output stream and periodically trigger the progress callback.

**Verification:**
- Temporary file is used during download.
- Callback receives correct percent, written bytes, and total content length from the HTTP response.

**Status:** `[x]` done

---

### Step 02.2 - Add Content Length and Size Verification

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**
> Ensure that before the downloaded file is promoted from `.tmp` to its final `.traineddata` name, the downloader validates its final size. The size must match or exceed the minimum threshold defined in constants (14MB for RU, 10MB for UK). If validation fails, delete the `.tmp` file and return `false`. This protects against network interruptions, 404 pages (HTML files instead of models), or other corrupt states.

**Verification:**
- Promotion from `.tmp` to `.traineddata` only occurs if the minimum file size check succeeds.

**Status:** `[x]` done

---

### Step 02.3 - Implement Safe Error Cleanup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**
> Wrap the entire download network stream reading in a fail-safe try-catch block. If any exception occurs (e.g. `IOException`, socket timeout, or HTTP response code is not 200), immediately delete any partially downloaded `.tmp` file, log the error with `Timber.e`, and return `false`.

**Verification:**
- Safe try-catch blocks and cleanups are thoroughly written.

**Status:** `[x]` done

---

### Step 02.4 - Sync Catalog and Compile

**Files:** None (build validation)
**Depends on:** Step 02.3

**Prompt for developer:**
> Run compilation checks and sync the class catalog using: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Verify there are no syntax or type compilation errors in `app_v2`.

**Verification:**
- Compilation and catalog sync script runs and exits with status 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Downloader safely staging files with progress reporting.
- [x] File size validation protects against empty or corrupted files.
- [x] Catalog sync completed successfully.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1` for modified files.
