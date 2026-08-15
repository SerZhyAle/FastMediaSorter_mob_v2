**Status:** Archived
**Priority:** 85

<!-- auto-approved by /spec-all - 2026-07-04 -->

# S0923 - OCR native-library attach crashes on real arm64 (Android 16 / API 36)

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0386 (on-demand OCR/translation delivery - introduced the native de-bundle + `DeliveredNativeLibraryLoader`; its "native attach on actual OCR/DTS use" item was never verified on real hardware), S0461 (OCR Paddle payload-missing graceful fallback - same loader), S0288 (noLegal PaddleOCR/PaddleLite bundle), S0794 (edge-gesture camera OCR-translate entry point)
- **Flavor scope:** affects every OCR-capable flavor (standard, legacy, noLegal, vr) and the FFmpeg DTS decoder set; crash was observed on noLegal. Data/UI surface unchanged; this is a runtime-loading + build-packaging fix.
- **Data/API scope:** no Room schema change, no new Hilt scope. Touches `build.gradle.kts` packaging and flavor DI `BundledDeliverableSetsModule`s only if the re-bundle option is chosen (see §6 decision).

## §0 Raw capture

Crash on a real device, not an emulator.

- Build: `2.60.7032.005-NoLegal-DEBUG (260703200)`, session 2026-07-04 00:25:14.
- Device: Samsung `SM-S731B` (Galaxy S24 FE), Android 16, API/SDK 36, `arm64-v8a`. (Not the usual S21+/Android 15 test device.)
- Trigger: camera OCR-translate flow (`cameraOcrTranslationEnabled=true`, `ocrEngineType=TESSERACT`, `enableTranslation=false` -> OCR-only branch).

Crash log (`logs/fastmediasorter_crash_20260704_002514.log`):

```
java.lang.UnsatisfiedLinkError: dlopen failed: library "/system/lib64/libjpeg.so" needed or
  dlopened by "/apex/com.android.art/lib64/libnativeloader.so" is not accessible for the namespace "clns-10"
  at java.lang.Runtime.loadLibrary0(Runtime.java:1111)
  at java.lang.System.loadLibrary(System.java:1765)
  at com.googlecode.tesseract.android.TessBaseAPI.<clinit>(TessBaseAPI.java:57)
  at com.sza.fastmediasorter.ui.player.helpers.TesseractManager$init$2.invokeSuspend(TesseractManager.kt:57)
```

## §1 Problem

S0386 strips the OCR/DTS native `.so` from every base artifact (`app_v2/build.gradle.kts` `jniLibs.excludes`: `libtesseract.so`, `libleptonica.so`, `libpngx.so`, `libjpeg.so`, PaddleOCR pair, `libffmpegJNI.so`) and re-attaches them at runtime from `filesDir` via `DeliveredNativeLibraryLoader`.

The loader does two things before the engine's own loader runs:

1. `injectNativeLibraryDirectory(setDir)` - reflection over `DexPathList` (`nativeLibraryDirectories`, `systemNativeLibraryDirectories`, `makePathElements`, `nativeLibraryPathElements`) to splice `filesDir/delivery/OCR_ENGINES` into the classloader's native search path, so a later `System.loadLibrary(name)` resolves the delivered `.so` by soname.
2. Warm-loads each `.so` by absolute path via `System.load(absolutePath)`.

On this device the injection does not take effect (does not throw, but the search path is not honoured). The warm-load then succeeds anyway, because `System.load(absolutePath)` bypasses name resolution entirely - so `load()` returns "success" and marks the set loaded (`loadedSets.add`). When `TessBaseAPI.<clinit>` subsequently runs `System.loadLibrary("jpeg")`, `classLoader.findLibrary("jpeg")` cannot see `filesDir` and falls through to the platform `/system/lib64/libjpeg.so`, which the app linker namespace (`clns-10`) is forbidden to load -> `UnsatisfiedLinkError`.

Why it crashes instead of degrading:

- `UnsatisfiedLinkError` is an `Error`, not an `Exception`. The `catch (e: Exception)` blocks in `TesseractManager.init` and the unguarded engine call in `RecognitionBackend.recognizeText` (line ~111) do not catch it, so it escapes to the top and kills the process.
- The warm-load-by-absolute-path masks the injection failure, so the loader never reports the set as unloadable; `RecognitionBackend`'s existing graceful-degradation path (`DeliveredNativeLibraryIncompatibleException` -> "OCR unavailable") is never entered.

Root-cause confirmation: this is exactly the S0386 remaining item recorded on its archived ticket - "native attach on actual OCR/DTS use" was only ever verified that files land in `filesDir` on an **API 33 x86_64 emulator**; the real `System.loadLibrary` name resolution on a real arm64 device was never exercised. No existing catalog ticket covers the symptom (searched `UnsatisfiedLinkError`, `camera crash`, `injectNativeLibraryDirectory` - empty).

## §2 Goals / non-goals

Goals:

- G1 (crash): the camera OCR-translate flow (and every other OCR/DTS entry) must never crash the process when the delivered native libraries cannot be attached. Failure degrades to "feature unavailable" with an actionable log, on every device and Android version.
- G2 (diagnosis): the loader must detect and log when name-based resolution of a delivered `.so` does not resolve into the delivered `filesDir` (rather than silently believing the set is attached), turning this class of failure from a mystery crash into a diagnosable, catchable condition.
- G3 (feature restore): OCR/translate and DTS decoding must actually work on real arm64 / API 36 devices. Approach for G3 is an owner decision (§6) - it may need on-device iteration.

Non-goals:

- Not changing OCR/translation UX, engine selection, or language handling.
- Not touching the pure-resource delivery (Set C audio-visualization videos) - those never call `System.load`, so they are unaffected and their de-bundle savings stay.

## §3 Approach (layered)

Layer 1 - Anti-crash guard + diagnosis (deterministic; this pipeline run). Fixes G1 + G2 with no product trade-off, correct regardless of the exact reflection failure mode:

- In `DeliveredNativeLibraryLoader.load()`, after `injectNativeLibraryDirectory` + warm-load, verify each delivered `.so` resolves by name: `(classLoader as BaseDexClassLoader).findLibrary(soname)` must return a path inside `setDir`. If any does not, log the actually-resolved path at WARN and throw `DeliveredNativeLibraryIncompatibleException` so the set is not marked attached and callers degrade gracefully (the existing `RecognitionBackend` catch already maps this to "OCR unavailable").
- Defense-in-depth at the engine boundary: wrap the engine static-initializer trigger (`TessBaseAPI()` in `TesseractManager.init`, and the PaddleLite predictor creation in `PaddleOcrEngine`) so a `LinkageError`/`UnsatisfiedLinkError` from `<clinit>` cannot escape as a crash - treat it as init-failure (`return false` / `null`).

Layer 2 - Feature restoration (owner decision, §6; likely a follow-up gated on the API 36 device). Two mutually-exclusive options:

- Option 2a (re-bundle): remove the `jniLibs.excludes` for the native sets and mark `OCR_ENGINES` / `FFMPEG_DTS` as bundled in the flavor `BundledDeliverableSetsModule`s, so the engines load via the standard, guaranteed APK `nativeLibraryDir` path. Reliable on all devices; reverses S0386's native size saving and requires unwinding the delivery/Extensions surface for those sets (capability-installed reporting, download offers, upgrade reconciliation). Best for noLegal (sideload, no Play size limit).
- Option 2b (fix the reflection): make `injectNativeLibraryDirectory` effective on API 36 and add the Layer-1 `findLibrary` post-condition as a hard gate. Preserves S0386's size saving but keeps a fragile, undocumented reflection dependency that must be re-validated per Android release; needs on-device confirmation on the S24 FE.

Recommendation: ship Layer 1 now (removes the P0 crash and produces the on-device diagnostic that will confirm the injection failure). Decide Layer 2 with the owner once the device log confirms root cause; default lean is 2a for noLegal, and re-evaluate 2b for the Play flavors on the size trade-off.

## §4 Affected areas

- `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt` - Layer 1 verification + logging.
- `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt` - engine-boundary `LinkageError` guard.
- `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt` - engine-boundary guard (Paddle uses the same loader/model path).
- `RecognitionBackend.kt` - already catches `DeliveredNativeLibraryIncompatibleException`; confirm no code path invokes an engine before `libraryLoader.load` (audit only).
- Layer 2 only: `app_v2/build.gradle.kts` packaging, `Standard/Legacy/NoLegal/Vr BundledDeliverableSetsModule.kt`, capability-repo "installed" reporting for bundled sets.

## §5 Risks

- `BaseDexClassLoader.findLibrary(name)` is public and returns the resolved path or null; verifying against `setDir` is side-effect-free. Low risk.
- The engine-boundary catch must catch `LinkageError` (superclass of `UnsatisfiedLinkError` and `ExceptionInInitializerError` wrapping) without swallowing genuine `Exception`s that already have dedicated handling. Keep the existing `catch (Exception)` and add a narrow `catch (LinkageError)`.
- Layer 2a materially grows APK size for Play flavors and touches the Extensions/delivery UX - do not implement blind; gate on owner decision.

## §6 Open items / decisions

- D1 (owner, blocks G3): choose Layer 2a (re-bundle) vs 2b (fix reflection), per flavor. Needs the S24 FE (API 36) on-device Layer-1 log to confirm the injection-ineffective diagnosis first. Until decided, OCR/translate + DTS remain gracefully unavailable on affected devices (no crash).
- D2 (device): reproduce on the API 36 device with the Layer-1 build and capture the loader's `findLibrary` resolved-path WARN line to confirm root cause and inform D1.

## Last Audit

**Date:** 2026-07-04
**Mode:** spec-all F3 (Layer 1 implemented) + build gate; on-device deferred
**Flags:** only device online is `emulator-5554` (`sdk_gphone64_x86_64`, Android 13 / API 33) - structurally cannot reproduce the arm64 / API 36 injection failure, and noLegal x86_64 is Tesseract-only
**Outcome:** BlockNeedUserTest (Layer 1 crash-guard shipped; definitive verification needs the S24 FE)

### Done (Layer 1)

- [x] Loader post-attach `findLibrary` verification + WARN diagnostic (`DeliveredNativeLibraryLoader.kt`).
- [x] Engine-boundary `LinkageError` guards (`TesseractManager.kt`, `PaddleOcrEngine.kt`).
- [x] Build gate: standard `fk` PASS, noLegal `nd` BUILD SUCCESSFUL (device APK `v2.60.7040.*-NoLegal-DEBUG`).
- [x] detekt scoped PASS (no new findings among changed files); fast static gates PASS (probe accepted as BlockNeedUserTest).

### Manual / on-device (definitive - requires Samsung SM-S731B, Android 16 / API 36, arm64)

- [ ] Install noLegal debug, trigger camera OCR-translate.
  - expected: no crash (was `UnsatisfiedLinkError` at `TessBaseAPI.<clinit>`) -> actual: ...
  - expected: logcat WARN `DeliveredNativeLibraryLoader: ... injection ineffective (API 36)` naming `libjpeg.so` and its `/system/lib64` resolution -> actual: ... (confirms §1 root cause, feeds decision D1)
  - expected: probe `S0923: camera OCR-translate recognition entry` present -> actual: ...
  - Note: OCR output may be absent (graceful degrade) - this ticket is crash-fix only. If the WARN is absent and OCR works, injection is effective on that device - re-open root-cause before deciding Layer 2.
- Emulator (API 33 x86_64) not attempted for the arm64 path (S0461 precedent: structurally unreachable). Launch smoke clean, no FATAL.

## §10 Related work

- S0386 (Archived) - origin of the native de-bundle + `DeliveredNativeLibraryLoader`; its unfinished "real-hw native attach" item is this crash.
- S0461 (BlockNeedUserTest) - graceful fallback when a Paddle payload file is missing; shares the loader and the graceful-degradation contract Layer 1 extends.
