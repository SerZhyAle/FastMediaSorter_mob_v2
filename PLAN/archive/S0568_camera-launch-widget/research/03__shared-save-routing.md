# Research 03 - Shared public-folder save routing

**Strategic §6 item:** 3 (вынос общего шва сохранения)
**Date:** 2026-06-20

## Question

Should the "save a switchable-camera result into a public folder by media kind" routing be extracted into a shared helper, or mirrored into the new widget manager?

## Findings

- `MainCameraCaptureManager.handleResult` currently resolves the destination inline:
  - photo -> `CameraCaptureTarget.CameraFolder`
  - video -> a `CameraCaptureTarget.Resource` over `CaptureDestinationPolicy.resolveVideoDestination(null)` (public Movies)
  - then `cameraCaptureSaver.save(captured, name, target) { _, _, _ -> false }` (no network upload hook for public-folder saves).
- The new launch widget needs the identical routing (same destinations, same saver, no upload hook). Inlining it again would duplicate the target resolution + saver call and risk divergence (strategic Risk 1).
- `CameraCaptureSaver` and `CaptureDestinationPolicy` are already shared engines; only the target-resolution + save call is duplicated glue.
- `MainCameraCaptureManager` carries S0523/S0563 `Timber.d` device-test probes (both tickets `BlockNeedUserTest`). A refactor must preserve those lines verbatim.

## Decision

- Extract a small `SaveCapturedMediaUseCase` (domain/usecase) that takes the captured `File` + captured media kind and persists it to the correct public folder via `CameraCaptureSaver`, returning the existing `SaveResult`. `@Inject constructor(@ApplicationContext context, CameraCaptureSaver)` - no new Hilt module.
- Refactor `MainCameraCaptureManager.handleResult` to delegate destination resolution + save to the use case (behavior-preserving). Keep the existing `Timber.d("S0523:` / `Timber.d("S0563:` probes untouched (both still `BlockNeedUserTest`).
- The new widget manager consumes the same use case, giving a single source of truth for public-folder capture saves.
