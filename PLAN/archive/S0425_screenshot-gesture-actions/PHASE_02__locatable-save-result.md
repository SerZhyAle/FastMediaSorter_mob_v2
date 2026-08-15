# Phase 02 - Locatable save result

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Extend `SaveScreenshotUseCase.SaveResult.Success` with a resolved `savedUri: Uri?` so post-capture actions (player / draw / OCR / share) can locate the saved file. No dispatch yet - the field is populated but unused. (ADR-1.)

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveScreenshotUseCase.kt` | Modified | ≤ 220 |

> `res/xml/file_provider_paths.xml` already exposes `external-path` (root ".") + `cache-path`; SelectedResource screenshots in local external folders are FileProvider-shareable without change. No layout files in this phase.

---

## Steps

### Step 02.1 - Add `savedUri` to `SaveResult.Success`

**Files:** `domain/usecase/SaveScreenshotUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val savedUri: Uri?` (nullable) to `data class Success`. Import `android.net.Uri`. Keep `fileName` and `destinationLabel`.

**Verification:**

- `Grep` - `val savedUri: Uri?` present in `Success`.
- `Grep` - `import android.net.Uri` present.

**Status:** `[ ]` not done

---

### Step 02.2 - Populate `savedUri` for the public-collection path

**Files:** `domain/usecase/SaveScreenshotUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `saveToPublicCollection`, `sink.commit()` returns the published MediaStore item URI as a string (`itemUri.toString()`). On success, set `savedUri = Uri.parse(commitResultString)`. Thread the committed string through the `fold` `onSuccess` lambda into `SaveResult.Success(..., savedUri = Uri.parse(it))`.

**Verification:**

- `Grep` - `Uri.parse(` present in `saveToPublicCollection`.
- `Grep` - `savedUri =` present in the `Success(` construction of that function.

**Status:** `[ ]` not done

---

### Step 02.3 - Populate `savedUri` for the selected-resource path

**Files:** `domain/usecase/SaveScreenshotUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `saveToSelectedResource`, after a successful copy the file lives at `File(resource.path, fileName)`. Build a FileProvider URI with `runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(resource.path, fileName)) }.getOrNull()` and pass it as `savedUri` in the `Success`. Null is acceptable when the path is outside provider scope (e.g. a network resource) - downstream actions degrade to silent save. Import `androidx.core.content.FileProvider`.

**Verification:**

- `Grep` - `FileProvider.getUriForFile` present in this file.
- `Grep` - `.fileprovider` authority string present.
- `Grep` - `savedUri =` present in the `Success(` of `saveToSelectedResource`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] Both service call sites still compile (they destructure `Success` by named members - `savedUri` is additive, no break).
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

`SaveResult.Success.savedUri` is a content/file-provider URI for the saved screenshot (nullable). The dispatcher (Phase 04) consumes it.

---

## Rollback Plan

Revert phase commit. Additive field only - no data migration.
