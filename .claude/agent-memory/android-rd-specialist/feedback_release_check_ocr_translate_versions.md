---
name: release-check-ocr-translate-versions
description: Before every release, check upstream for newer OCR/translation library and model versions and pull them into the release if they are worth shipping
metadata:
  type: feedback
---

Before a release ships, check whether newer upstream versions exist for the OCR and translation stack - both the **libraries** and the **models/data** they consume - and decide explicitly whether each update goes into this release.

What to check (verify against `app_v2/build.gradle.kts` at the time - versions below are the point-in-time snapshot, not the truth):
- `com.google.mlkit:translate` (was 17.0.3) - translation engine.
- `com.google.mlkit:language-id` (was 17.0.6) - source-language detection.
- `cz.adaptech:tesseract4android` (was 4.8.0, `-openmp` module excluded) - offline OCR engine, incl. its bundled Tesseract/Leptonica native versions.
- OCR language models: Tesseract `*.traineddata` (tessdata / tessdata_best releases) - fetched at runtime, not in `assets/`, so a model refresh is a data-side change independent of the library bump.
- PaddleOCR deliverables (see `DeliverableDescriptorCatalog` / `DeliveredNativeLibraryLoader`) - engine + model pairs must stay compatible.
- ML Kit translate models are downloaded on demand by the library - a `translate` bump can change the model set/format.

**Why:** owner ask (2026-07-17). OCR/translation quality is mostly upstream-driven - the model and engine improve without any code change here, so a stale pin silently ships worse recognition/translation than is freely available. Release is the natural checkpoint: this is exactly the moment a dependency bump can still be validated on-device before it reaches users.

**How to apply:** during `/release` assessment (before `/spec-prerelease`, so a bump is inside the sweep, not after it), look up current upstream versions, report the deltas with a ship/skip recommendation per item, and let the owner call it. A bump lands as normal ticketed work, not as an untracked edit inside the release flow. Constraints that still bind: engine/model compatibility, flavor matrix (`lite` has no OCR - `src/ocrDisabled`; translation lives in `src/translationMlKit`), minSdk 23 on `legacy`, native `.so` bundling rules ([[native-so-bundle-standard-vs-ondemand-nolegal]], [[s0386-native-attach-broken-api36]]), and [[release-no-coverage-regression]] - a bump that raises minSdk or shrinks device reach is a STOP, not a nice-to-have.
