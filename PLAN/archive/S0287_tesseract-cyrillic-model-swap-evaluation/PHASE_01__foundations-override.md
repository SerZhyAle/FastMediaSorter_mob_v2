# Phase 01 - Foundations and Engine Override

**Strategic spec:** [`../S0287_tesseract-cyrillic-model-swap-evaluation.md`](../S0287_tesseract-cyrillic-model-swap-evaluation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Create `TesseractModelManager.kt` to query high-quality model statuses and path configurations. Modify `TesseractManager.kt` to search for and use the `tessdata_best` directory structure, with a fully isolated fallback mechanism to built-in `tessdata_fast` assets on initialization errors.

---

## Prerequisites

- [ ] Strategic spec S0287 is `Approved` or `Tactical`.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt` | Modified | ≤ 420 |

---

## Steps

### Step 01.1 - Create TesseractModelManager skeleton

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**
> Create a new Kotlin class `TesseractModelManager(private val context: Context)` inside `com.sza.fastmediasorter.ui.player.helpers`. It must define constants for paths (`tesseract_best/tessdata`), model URLs, and minimum file size check constraints (>14,000,000 bytes for Russian, >10,000,000 bytes for Ukrainian). Implement functions to check if a specific language model is validly installed on the disk (`isModelInstalled(language: String): Boolean`), and to delete the model file (`deleteModel(language: String): Boolean`).

**Verification:**
- File exists.
- Contains package and class definition.
- `isModelInstalled` checks both existence and size bounds.

**Status:** `[x]` done

**Step Log:**
- 2026-05-21 - Created TesseractModelManager skeleton. Checked size limits and deletion logic.

---

### Step 01.2 - Modify TesseractManager dynamic path check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**
> Modify `TesseractManager.kt` to check with `TesseractModelManager` whether the high-quality model is available. If `TesseractModelManager(context).isModelInstalled(language)` is true, set the initialization `dataPath` to `context.filesDir/tesseract_best/`. Otherwise, fallback to the standard `context.filesDir/tesseract/`.

**Verification:**
- `TesseractManager` references `tesseract_best` when a high-quality model is available.

**Status:** `[x]` done

**Step Log:**
- 2026-05-21 - Modified TesseractManager to check TesseractModelManager and initialize from best model if present.

---

### Step 01.3 - Implement Safe Fallback in TesseractManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**
> Wrap the `tessApi?.init(dataPath.absolutePath, language)` call in a robust try-catch structure. If initialization using the high-quality model fails (returns false or throws an exception), delete the corrupted `tessdata_best` file immediately, log a warning via `Timber.w`, and attempt to initialize again using the fallback built-in path (`context.filesDir/tesseract/`). This guarantees that failure to initialize the heavy model will never block OCR functionality.

**Verification:**
- Safe try-catch blocks are implemented in `init(...)` and handle failure by cleaning up the corrupt file and falling back to `tessdata_fast`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-21 - Implemented full try-catch fallback loop. If best model fails initialization, it is deleted and fast model path is initialized.

---

### Step 01.4 - Sync Catalog and Compile

**Files:** None (build validation)
**Depends on:** Step 01.3

**Prompt for developer:**
> Run compilation checks and sync the class catalog using: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Verify there are no syntax or type compilation errors in `app_v2`.

**Verification:**
- Compilation and catalog sync script runs and exits with status 0.

**Status:** `[x]` done

**Step Log:**
- 2026-05-21 - Successfully compiled and completed catalog sync sequence (exit code 0).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `TesseractModelManager` compiles correctly.
- [x] `TesseractManager` falls back cleanly to `tessdata_fast` if the custom data path fails to initialize.
- [x] Class catalog sync successful.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1` for both files.
