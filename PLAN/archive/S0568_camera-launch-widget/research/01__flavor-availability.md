# Research 01 - Flavor availability of the camera launch widget

**Strategic §6 item:** 1 (точный список вариантов сборки и места удаления ресивера)
**Date:** 2026-06-20

## Question

In which flavors should the new camera launch widget appear, and where (if anywhere) must its receiver be removed from the merged manifest?

## Findings

- The existing camera widgets are registered only in `app_v2/src/main/AndroidManifest.xml`:
  - `CameraQuickCaptureWidgetProvider` (S0369/S0371) - quick capture to a bound target.
  - `CameraPhotosWidgetProvider` - opens the Camera local resource.
  - `CameraOcrTranslateWidgetProvider`, `CaptureOcrPanelWidgetProvider` - OCR/translate widgets.
- Flavor manifest overlays remove only a subset:
  - `lite` and `photos` remove `CameraOcrTranslateWidgetProvider` + `CaptureOcrPanelWidgetProvider` (no translation: `ENABLE_TRANSLATION=false`).
  - No flavor manifest removes `CameraQuickCaptureWidgetProvider` or `CameraPhotosWidgetProvider` (grep over `src/{photos,legacy,vr,noLegal,lite,standard}/AndroidManifest.xml` returned no matches for either provider).
- Therefore the quick-capture and camera-photos widgets ship in every flavor via the main manifest. `docs/ALL_FEATURES.jsonl` lists `widgets.quick-capture-widget` as `["standard","lite","photos","legacy"]`; the record predates vr/noLegal and is not authoritative for source-set presence - the manifest is.
- The in-app camera host (`CameraCaptureActivity`) lives in `src/main` and is the capture target for the quick-capture widget in every flavor, so the host is available wherever the quick-capture widget is.

## Decision

- Register the new camera launch widget receiver + its transparent trampoline activity only in `app_v2/src/main/AndroidManifest.xml`, mirroring `CameraQuickCaptureWidgetProvider`.
- Do NOT add removal overlays in any flavor manifest. Availability degrades at runtime via the injectable `MediaCapabilities` surface and capture settings (see research 02), not via per-flavor manifest surgery.
- This keeps the widget present in every flavor that has at least image capture (all current flavors have IMAGES) and lets the degenerate single-mode gating handle flavors without video.
