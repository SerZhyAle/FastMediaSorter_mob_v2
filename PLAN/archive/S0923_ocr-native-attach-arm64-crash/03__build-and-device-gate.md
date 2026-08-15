# Phase 03 - Build gate + BlockNeedUserTest device verification

## Steps

1. Insert the BlockNeedUserTest probe tag (only when status is being set to `BlockNeedUserTest`): one `Timber.d("S0923: camera OCR-translate recognition entry")` at the camera-OCR recognition entry in `RecognitionBackend.recognizeText` (start of the method, after the `ocrEnginesInstalled()` gate), so the device run can confirm the exercised flow. This is the single changed-flow entry point for the crash.

2. Build gate (this is the last code-touching phase, so this build validates code + probe tag in one pass):
   - `.\a.ps1 dq` -> standard debug (assembleStandardDebug) - primary.
   - `.\a.ps1 nd` -> noLegal debug - the crashing flavor; the device build under test.
   - Both must be BUILD SUCCESSFUL.

3. Set status `BlockNeedUserTest` with a `-StatusNote` describing the device test (below). Device-test gate then auto-runs when a device is online.

## Device acceptance (API 36 arm64)

- Install noLegal debug on the S24 FE (API 36) and trigger camera OCR-translate.
- expected: no process crash (no `UnsatisfiedLinkError` FATAL) -> actual: ...
- expected: logcat WARN `DeliveredNativeLibraryLoader: ... injection ineffective on this device (API 36)` naming `libjpeg.so` and its `/system/lib64` resolution -> actual: ... (this confirms the strategic §1 root cause and feeds decision D1).
- expected: `S0923: camera OCR-translate recognition entry` probe present (flow exercised) -> actual: ...
- If the WARN does NOT appear and OCR works: injection is effective on this device after all - re-open root-cause analysis before deciding Layer 2.

## On leaving BlockNeedUserTest

- Delete the `Timber.d("S0923:` probe line from `RecognitionBackend.kt`.
- Record the device log outcome under strategic §6 D2; that outcome drives the Layer 2 owner decision (D1).
