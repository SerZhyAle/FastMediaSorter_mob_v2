# S0465 — Camera/video capture save fails with EACCES on public collections (API 29+ scoped storage)

**Status:** Archived
**Priority:** 50

## §0 — Raw evidence (auto-captured, 2026-06-16 — discovered while fixing S0464)

While fixing S0464 (mic recording EACCES), found the same latent bug in the sibling capture flows that share `util/CaptureDestinationPolicy`:

- `CaptureDestinationPolicy.resolveCameraDestination()` returns a public `File` (DCIM/Camera, else Downloads).
- `CaptureDestinationPolicy.resolveVideoDestination()` returns a public `File` (Movies).
- Callers write to these via direct `File`/`FileOutputStream`, which fails with `EACCES` on API 29+ when `WRITE_EXTERNAL_STORAGE` is not granted / is restricted by OEM policy (same failure mode as S0464 on SPRD ums512 car head units).

S0464 fixed only the microphone path (`BrowseMicRecordingManager`) by routing on-device writes through the MediaStore-aware `LocalDestinationWriter` (`LocalDestinationClassifier.classify(path)` → `writer.open(category)` → stream → commit). The camera and video capture save sites need the same treatment.

Evidence pointers:
- `util/CaptureDestinationPolicy.kt` — `resolveCameraDestination` / `resolveVideoDestination`.
- Camera capture save site(s) (e.g. `data/capture/CameraCaptureSaver.kt` and the camera/video capture managers under `ui/browse`).

## §1 — Problem

Camera photo and video recording saves to public device collections (DCIM/Movies/Downloads) use direct filesystem writes and fail with EACCES on API 29+ restrictive-storage devices, mirroring the fixed S0464 mic bug. Reuse the same MediaStore-backed write path for these flows.

## §2 — Цель и объём

Сохранение camera-фото и video-записи в публичную коллекцию (DCIM/Camera, Movies, Downloads-fallback) не падает с EACCES на API 29+ - публичные коллекции пишутся через MediaStore.

**В объёме:**

- `CameraCaptureSaver` - единственный on-device save backend для обоих потоков (фото и видео; Browse и quick-capture widget делегируют сюда). Два прямых `tempFile.copyTo` в публичную коллекцию: `saveToDcim` (DCIM/Camera) и `saveLocal` (LOCAL root, в т.ч. Movies-fallback видео с `id=-1`).

**Вне объёма:**

- `BrowseCameraCaptureManager` / `CameraQuickCaptureLaunchManager` - только маршрутизация таргета; сам write уже централизован в `CameraCaptureSaver`, менять не нужно.
- Сетевые / облачные таргеты (`upload` hook) - не затронуты.
- `CaptureDestinationPolicy` - резолвинг директорий остаётся, меняется только способ записи.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0464 (mic EACCES fix - pattern source), S0369 (CameraCaptureSaver extraction), S0231/S0280 (MediaStore local writer)

## §3 — Реализация (фазы)

### PHASE_01 — route CameraCaptureSaver on-device writes through the MediaStore-aware writer

- [x] Inject `LocalDestinationClassifier` and `LocalDestinationWriter` into `CameraCaptureSaver` (constructor `@Inject` params; both Hilt-bound, all call sites are field/constructor-injected - no manual construction in `main`).
- [x] Add `writeToDevice(tempFile, absolutePath)` (IO dispatcher): `classifier.classify(path)` → `writer.open(category, overwrite = true)` → stream `tempFile` bytes into `sink.outputStream` → `sink.commit()`; on failure `sink.abort()` and return `false`. Mirror of `BrowseMicRecordingManager.writeToDevice` (S0464).
- [x] Replace `saveToDcim` body: `writeToDevice(tempFile, File(cameraDir(), name).absolutePath)`; keep the legacy media-scanner broadcast only on success (still needed on pre-Q where the writer routes through `FileOutputStream`).
- [x] Replace `saveLocal` body: `writeToDevice(tempFile, File(rootPath, name).absolutePath)`.
- [x] Keep `resolveSavedPath`, the `upload` (network/cloud) branch, and temp-file deletion unchanged.

**Verification:**

- [x] `.\a.ps1 fc` (standard debug compile + resources) - PASS.
- [x] No remaining `tempFile.copyTo(` to a public collection in `CameraCaptureSaver`.
- [x] `CameraCaptureSaverTest` updated (filesystem-backed fake `LocalDestinationWriter` so on-disk routing assertions hold) and green.
- [ ] On-device (BlockNeedUserTest): a camera photo and a video recording saved to a public collection succeed on an API 29+ device with restrictive storage (no EACCES); appear in DCIM/Movies via MediaStore.

### PHASE_02 — build + docs

- [x] Standard debug build PASS (`.\a.ps1 d`).
- [x] Dev log + functionality log (FIX, user-visible).

## §4 — Links

- Blocked-by / pattern source: S0464 (mic recording EACCES fix — reuse `LocalDestinationClassifier` + `LocalDestinationWriter`).
