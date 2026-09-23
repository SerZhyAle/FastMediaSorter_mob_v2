# Pointer - `OCR-ACCURACY`

| | |
| --- | --- |
| **Id** | `OCR-ACCURACY` |
| **Version** | 1.0, record. Owner: this product |
| **Home** | `ocr-overlay-accuracy.md` in the `ocr-overlay/` folder of the shared contracts catalog |
| **Role here** | owner of the record - this product's side of the three-sided OCR exchange |

## What this repository must do to stay conformant

- Every constant that cites the record carries `derived here` with its report, or `inherited` with the
  ticket that owns deriving it.
- A new measurement is added to the record in the catalog, as a dated section, not kept in this
  repository.

## Where it lives here

- The constants under `app_v2/.../domain/ocr/` that cite it (`OverlayPlateColorSampler.kt`,
  `OcrLineSplitter.kt`, `OcrLineGap.kt`, `EstimateOcrResolutionUseCase.kt`).
- The corpus runner: `scripts/ocrbench/run-corpus.ps1` and `app_v2/src/test/java/.../ocrbench/`.
