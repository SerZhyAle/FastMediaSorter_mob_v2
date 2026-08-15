# Phase 01 - Re-OCR orchestration

**Strategic spec:** [`../S0361_result-screen-ocr-language-reocr.md`](../S0361_result-screen-ocr-language-reocr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Extend `CameraOcrFlowManager` so a changed OCR source language re-runs recognition (and translation) over the retained `orientedBitmap`; no dialog or layout changes yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt` | Modified | ≤ 430 |

---

## Steps

### Step 01.1 - Add a retained-image query

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun hasRetainedSourceImage(): Boolean` returning `true` when `orientedBitmap` is non-null and not recycled. This lets the dialog disable the OCR-language control when the source image is gone (process death).

**Verification:**

- `Grep` - `fun hasRetainedSourceImage` matches exactly once in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 1/1 PASS. Files: CameraOcrFlowManager.kt (+3 LOC). Dev log recorded.

---

### Step 01.2 - Extend `applyLanguageSettings` with the OCR source language and re-OCR branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Change the signature to `fun applyLanguageSettings(sourceLang: String, targetLang: String, ocrOnly: Boolean)`. Read current settings first (capture the old source = `current.translationSourceLanguage`), then persist `translationSourceLanguage = sourceLang`, `translationTargetLanguage = targetLang`, `cameraOcrOnly = ocrOnly`. Branch:
> - If `sourceLang != oldSource` and `hasRetainedSourceImage()` is true: call the existing `runRecognition(orientedBitmap!!)` so OCR re-runs over the retained (cropped) image with the new source language and then re-translates per the freshly-persisted settings. `runRecognition` already reads settings via `getSettings().first()` and drives loading/empty/result callbacks - do not duplicate that logic.
> - If `sourceLang != oldSource` and the image is unavailable: do not re-OCR; leave the current results shown (defensive - this state is unreachable while results are visible).
> - If `sourceLang == oldSource`: keep the current behaviour exactly (re-translate the existing recognized text to the new target, or just `showResults` when translation is unavailable / no recognized text).
> Update the KDoc on the method to describe the OCR-language re-recognition branch and drop the S0354 "OCR source language is intentionally not part of this dialog" wording.

**Verification:**

- `Grep` - `fun applyLanguageSettings(sourceLang: String, targetLang: String, ocrOnly: Boolean)` matches exactly once.
- `Grep` - `runRecognition(` appears inside `applyLanguageSettings` (re-OCR branch wired).
- `Grep -n "Log\.d\("` - zero hits in the file (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS (new signature ×1, runRecognition wired, zero Log.d). Files: CameraOcrFlowManager.kt (+~25 LOC). Dev log recorded.

---

### Step 01.3 - Compile the module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> The only caller of `applyLanguageSettings` is `CameraOcrTranslateActivity` (Phase 02 updates it). To keep this phase independently buildable, expect a compile error at that single call site until Phase 02; if building Phase 01 alone, temporarily pass `settings.translationSourceLanguage` at the call site, or sequence Phase 01+02 in one build. Confirm `CameraOcrFlowManager.kt` has no unresolved references within itself.

**Verification:**

- `/build` standardDebug compiles once Phase 02 call site is updated (record `expected: SUCCESS | actual: <...>`). If validating Phase 01 in isolation, a single unresolved-call-site error in `CameraOcrTranslateActivity` is the only acceptable failure.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Phase 01 + Phase 02 built together (intentional coupling per phase note): `.\a.ps1 dq` BUILD SUCCESSFUL. expected: SUCCESS | actual: SUCCESS.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `CameraOcrFlowManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`applyLanguageSettings` now takes `sourceLang` first and re-OCRs over `orientedBitmap` when the source language changes. Phase 02 wires the dialog control and updates the single call site.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed (orchestration only).
