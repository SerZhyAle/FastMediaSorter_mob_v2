---
name: s0386-delivery-status
description: S0386 on-demand delivery - ALL code phases done & 4 flavors build-green; ticket at BlockNeedUserTest, only owner device-test + release unpack-verify remain
type: project
---

S0386 (on-demand OCR/translation/heavy-asset delivery): **all 9 code phases done** as of 2026-06-09 (commit `8ed28604` on `DEBUG-v013`), ticket at **BlockNeedUserTest**.

Delivered & build-green on `standard`/`noLegal`/`legacy`/`vr` debug:
- Set A translation: store = Play dynamic-feature `:translate_feature`; sideload/VR = bundled (Google `.so` not re-hosted).
- Set B OCR (Tesseract all flavors; +PaddleOCR on noLegal), Set C audio-viz, Set D FFmpeg DTS: native `.so` stripped from every base via `packaging.jniLibs.excludes`; delivered on demand from GitHub mirror `delivery-so-v1` (all-ABI hosted; hashes in `temp/s0386_so_table.txt`).
- `DeliverableDescriptorCatalog` holds ABI-aware app-pinned SHA-256/size per `.so`.
- `DeliveredNativeLibraryLoader` attaches by **splicing filesDir/delivery/<set> into the classloader native search path** (reflection over `DexPathList.nativeLibraryDirectories`/`makePathElements`, API 23-25 + 26+ variants) - required because Tesseract/Paddle/media3 load their libs via `System.loadLibrary(name)` in a static initializer we cannot edit, which a bare `System.load(absolutePath)` cannot satisfy.

**Why:** This is the keystone discovery. `TessBaseAPI` clinit runs `System.loadLibrary("jpeg"/"pngx"/"leptonica"/"tesseract")`, `PaddleLiteInitializer` runs `loadLibrary("paddle_lite_jni")`, media3 `FfmpegLibrary` loads `ffmpegJNI` - all by name, so the delivered dir must be on the classloader's native path before the wrapper initializes. The prior agent paused 05.3 exactly here.

**How to apply:** To verify/close: the remaining gate is owner-only - on-device enable->download->use (OCR recognize, DTS play), refusal stays unavailable, re-enable skips download, survives update/cache-clear, and `standardRelease`/`noLegalRelease` unpack-verify (release signing from the release worktree). The hidden-API reflection in the loader is the highest-risk part - validate on the target API levels on a real arm64 device (XR emulator unsuited to the 2D UI). Verify current state against the spec header / INDEX before acting - point-in-time snapshot.
