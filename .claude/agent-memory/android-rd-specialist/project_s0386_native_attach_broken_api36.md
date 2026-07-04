---
name: s0386-native-attach-broken-api36
description: S0386 runtime native-lib attach (injectNativeLibraryDirectory) fails on real arm64/API36; OCR/DTS crash guarded by S0923, Layer 2 pending
type: project
metadata:
  type: project
---

S0386's de-bundled native `.so` (Tesseract OCR, PaddleOCR, FFmpeg DTS) are stripped from every APK (`build.gradle.kts` jniLibs excludes) and runtime-attached from `filesDir` by `DeliveredNativeLibraryLoader.injectNativeLibraryDirectory()` (DexPathList reflection). On a real **Samsung SM-S731B (S24 FE), Android 16 / API 36, arm64-v8a** this injection does not take effect: `System.loadLibrary("jpeg")` in `TessBaseAPI.<clinit>` falls through to the forbidden `/system/lib64/libjpeg.so` -> `UnsatisfiedLinkError` crash (camera OCR-translate). S0386 only ever verified file download on an API 33 x86_64 emulator, never the real-hw attach.

**Why:** the crash is a runtime/hardware behavior not derivable from code; the emulator (API 33 x86_64) structurally cannot reproduce it (S0461 precedent). The device that surfaced it (S24 FE / API 36) may be the owner's real phone, not the usual S21+/Android 15 test device.

**How to apply:** when touching OCR / delivery / native-lib loading, know that the runtime-attach path is unreliable on modern arm64. S0923 (BlockNeedUserTest, 2026-07-04) shipped Layer 1 = anti-crash guard: loader verifies `findLibrary(soname)` resolves into `filesDir` (else WARN + `DeliveredNativeLibraryIncompatibleException`), engines catch `LinkageError`. Layer 2 (feature restoration) is an owner decision deferred until the S24 FE device log confirms the diagnosis: 2a re-bundle `.so` (reliable, +~15-25MB, partial S0386 revert, best for noLegal) vs 2b fix the DexPathList reflection for API 36 (keeps size, fragile). Owner chose device-log-first (2026-07-04). Verify live status via select.ps1 before acting.
