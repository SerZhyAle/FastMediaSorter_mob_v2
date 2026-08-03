---
name: photos-flavor-ocr-build-break
description: RESOLVED 2026-06-10 - Tesseract OCR moved to ocrEnabled/ocrDisabled source buckets so photos/lite build
type: project
---

RESOLVED (2026-06-10, branch DEBUG-v013). Was: while S0386's `build.gradle.kts` scoped `cz.adaptech:tesseract4android` to OCR flavors only, `TesseractManager.kt` + `OcrModule.kt` still lived in `src/main` and referenced `TessBaseAPI` unconditionally, so `photos`/`lite` (no OCR dep) failed `compile*Kotlin` with unresolved `TessBaseAPI`/`googlecode`.

**Fix:** mirrored the established `cloudEnabled/cloudDisabled` bucket pattern.
- `src/ocrEnabled/java/` holds `TesseractManager.kt` + `di/OcrModule.kt` (provides Tesseract as default `OfflineOcrEngine`); mounted into standard, noLegal, legacy, vr.
- `src/ocrDisabled/java/` holds `NoOpOfflineOcrEngine.kt` + a same-named `di/OcrModule.kt` providing it; mounted into photos, lite. Needed because `OfflineOcrEngineProvider` (src/main) injects a non-null default `OfflineOcrEngine`.
- `build.gradle.kts` `sourceSets` mounts + catalog `scan.ps1`/`render.ps1` srcRoots updated.

**Why it matters going forward:** OCR is now flavor-isolated like cloud/streaming/translation. Any new code that touches `TessBaseAPI` or Tesseract symbols must live in `src/ocrEnabled`, never `src/main`. A non-OCR flavor needing a new `OfflineOcrEngine` consumer must have a matching binding in `src/ocrDisabled`. See S0386 (on-demand OCR/translation delivery).
