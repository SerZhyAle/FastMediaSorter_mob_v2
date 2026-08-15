# PHASE 01 - Scan core (dependency, analyzer, session, activity)

Goal: a self-contained camera QR-scan screen that returns the decoded payload string via activity result. No wiring into add-resource yet (Phase 02).

## Steps

1. Add ZXing decoder dependency.
   - File: `app_v2/build.gradle.kts`, dependencies block near the CameraX lines.
   - Add: `implementation("com.google.zxing:core:3.5.3")` (single shared implementation, all flavors).
   - Verification: dependency resolves in the Phase-03 build; no `zxing-android-embedded`.

2. ZXing frame analyzer.
   - New: `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/QrCodeAnalyzer.kt`.
   - `ImageAnalysis.Analyzer`; `MultiFormatReader` with `DecodeHintType.POSSIBLE_FORMATS = [QR_CODE]`.
   - Read Y plane -> `PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)` -> `BinaryBitmap(HybridBinarizer(...))` -> `decodeWithState`.
   - First successful decode -> single `onDecoded(text)` callback (guard `done` flag so later frames no-op); always `image.close()` in finally; swallow `NotFoundException` (expected per frame, no log spam).
   - Verification: compiles; single-hit guard present.

3. Lightweight scan session.
   - New: `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/QrScanSessionManager.kt`.
   - Owns a minimal CameraX session: bind `Preview` + `ImageAnalysis` (STRATEGY_KEEP_ONLY_LATEST, single analyzer executor) to a `PreviewView` + `LifecycleOwner`, back lens.
   - Expose `bind(previewView, onDecoded, onError)`, `setTorch(enabled)`, `hasFlash()`, `unbind()` (unbindAll + shutdown analyzer executor).
   - Keeps camera logic out of the Activity (Rule 3).
   - Verification: compiles; `unbind()` releases provider + executor.

4. Scan Activity.
   - New: `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/CompanionQrScanActivity.kt` (`@AndroidEntryPoint`).
   - Runtime CAMERA permission via `registerForActivityResult(RequestPermission)`; granted -> bind session; denied -> rationale string + finish.
   - On decode -> `setResult(RESULT_OK, Intent().putExtra(EXTRA_PAYLOAD, text))` + finish; guard against double-result.
   - Torch toggle button (visible only when `hasFlash()`); cancel/back finishes with RESULT_CANCELED.
   - `onDestroy` -> `session.unbind()`.
   - `companion object { EXTRA_PAYLOAD; fun createIntent(context) }`.
   - Verification: compiles; camera released in onDestroy.

5. Scan layout.
   - New: `app_v2/src/main/res/layout/activity_companion_qr_scan.xml`.
   - `androidx.camera.view.PreviewView` full-bleed; framing hint overlay; bottom control row (torch toggle + cancel) anchored with `systemBars`+`displayCutout` insets (Rule 17); hint TextView.
   - Orientation-agnostic (full-bleed preview + anchored controls) - no separate `layout-land` needed; controls focusable/clickable for D-pad/mouse (Rule 16). No hardcoded hex (Rule 19) - use `?attr`/`@color`.
   - Verification: `fr` resources pass.

6. Manifest registration.
   - File: `app_v2/src/main/AndroidManifest.xml`.
   - Register `CompanionQrScanActivity` `android:exported="false"`, portrait-friendly (no forced orientation, or `fullSensor`), theme consistent with app.
   - Verification: manifest merges; target build passes (Phase 03).

7. Strings (declare keys; values in Phase 03).
   - Keys: `companion_qr_scan_button`, `companion_qr_scan_title`, `companion_qr_scan_hint`, `companion_qr_scan_torch`, `companion_qr_camera_denied`, `companion_qr_invalid`.
   - Added via `scripts/utils/set-android-string.ps1 -Action add` across EN/RU/UK in Phase 03.
