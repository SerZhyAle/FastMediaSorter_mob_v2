---
name: photos-flavor-ocr-build-break
description: While S0386 is paused, non-standard-OCR flavors (photos) fail to compile on TesseractManager
type: project
---

While S0386 (on-demand OCR de-bundling) sits uncommitted in the working tree, the `photos` flavor debug build fails to compile with `TessBaseAPI`/`getUTF8Text` unresolved references in `TesseractManager.kt`.

**Why:** S0386's uncommitted `app_v2/build.gradle.kts` scopes `cz.adaptech:tesseract4android` to `standardImplementation` + `legacyImplementation` only, but `TesseractManager.kt` still lives in `src/main` and references `TessBaseAPI` unconditionally. So any flavor without the OCR dep (photos) can't compile it. `standard`/`legacy` build fine.

**How to apply:** A `photos`/`vr`/`noLegal` compile failure on `TesseractManager` / Tesseract symbols is NOT your regression - it's the in-flight S0386 state (paused at BlockNeedUserTest as of 2026-06-10). Verify your own work on `standard` (shares `src/main`); defer `photos` re-verify until S0386 lands or moves `TesseractManager` into a flavor/feature source set. See [[project_s0386_delivery_pause]].
