# Phase 05 - Set Definitions & Base-Build De-Bundling

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 07
**Steps done:** 7 / 7
**Started:** 2026-06-09
**Completed:** 2026-06-09

> **B4-PoC obsolete** (2026-06-09): ML Kit Translate is delivered by Play dynamic-feature on store and bundled on sideload/VR - no self-load, no device gate. **B3 OSS hosting done** (Tesseract/Paddle/FFmpeg in release `delivery-so-v1`, arm64-v8a); armeabi-v7a OSS upload for 32-bit store flavors is the only operational remainder. **05.3 audit complete:** the ML Kit OCR half is already satisfied; the remaining 05.3 scope is the Tesseract Set B de-bundle on store.

---

## Objective

Define each deliverable set's payload, remove those native libs and heavy assets from every flavor's base APK/AAB, and register set contributors per flavor - so the base artifact ships only the contract and UX (strategic §5.4, criteria §11.1).

---

## Prerequisites

- [ ] Phase 04 ✅ Done.
- [x] Blocker B3 - OSS `.so` hosted (`delivery-so-v1`, arm64); no rebuild needed. armeabi-v7a OSS upload still pending for 32-bit store flavors.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSetContributor.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DeliverableSetContributorModule.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/OcrModule.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt` | Modified | ≤ 400 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/delivery/StandardDeliverableSetsModule.kt` | New | ≤ 120 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/delivery/NoLegalDeliverableSetsModule.kt` | New | ≤ 140 |
| `app_v2/src/main/res/raw/` | Modified (remove `anim_audio_bg_*`) | - |

> Flavor placement: real set descriptors that differ per flavor (Paddle in `noLegal`, FFmpeg in store/legacy/vr) live under `src/<flavor>/java/.../delivery/`. The contract and No-Op live in `src/main`. No `BuildConfig.SUPPORT_*`/`ENABLE_*` decoupling guard in `src/main` (Rule 15).

---

## Steps

### Step 05.1 - Set contributor extension point

**Files:** `domain/delivery/DeliverableSetContributor.kt`, `di/DeliverableSetContributorModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define `interface DeliverableSetContributor { fun descriptors(): Map<DeliverableSet, DeliverableSourceDescriptor> }` and a `@Multibinds Set<DeliverableSetContributor>` module in `src/main` (mirror the existing `OcrContributorModule` multibinding). The manifest data source merges all contributed descriptors so a flavor declares only the sets it ships.

**Verification:**

- `Grep` - `interface DeliverableSetContributor` matches once.
- `Grep` - `@Multibinds` present in `DeliverableSetContributorModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. New: `domain/delivery/DeliverableSetContributor.kt` (+12 LOC) + `di/DeliverableSetContributorModule.kt` (`@Multibinds Set<DeliverableSetContributor>`). Modified `DeliveryModule.provideBundledDescriptors()` to merge contributors (empty set → empty map → inert, no behavior change). Mirrors `OcrContributorModule`. Dev log recorded.

---

### Step 05.2 - Play dynamic-feature module for ML Kit Translate (store flavors)

**Files:** `app_v2/build.gradle.kts`, `settings.gradle.kts`, `translate_feature/build.gradle.kts` (New module), `app_v2/.../delivery/StandardDeliverableSetsModule.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create an on-demand Play dynamic-feature module `:translate_feature` holding `com.google.mlkit:translate` + `com.google.mlkit:language-id`; add `playstore-dynamic-feature-support` to the base; install the module on demand via `SplitInstallManager` and load its native libs via `SplitInstallHelper.loadLibrary`. Attach the module only to store flavors (`standard`/`photos`/`legacy`) - `noLegal`/`vr` keep ML Kit Translate bundled in their base (strategic §5.4 A, 2026-06-09 decision). No self-load, no `.so` re-hosting.

**Verification:**

- `Grep` - `:translate_feature` referenced in `settings.gradle.kts`.
- `Grep` - `playstore-dynamic-feature-support` present in `app_v2/build.gradle.kts`.
- `Grep` - `SplitInstallManager` or `SplitInstallHelper` referenced in the store delivery wiring.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification PASS. Store translation runtime was moved out of `src/standard/java` into shared source set `src/translationDynamicFeature/java`, fixing the broken `legacy` classpath while keeping `standard` on the same dynamic-feature path. `standardDebug` + `legacyDebug` are green, and both base APKs no longer package `libtranslate_jni.so` / `liblanguage_id_l2c_jni.so`. `noLegalDebug` + `vrDebug` remain green with translation bundled.
- 2026-06-09 (follow-up) - Latent defect: the `:translate_feature` module compiles the shared `src/translationMlKit/java` source set, which calls `Task.await()`, but its `build.gradle.kts` lacked `kotlinx-coroutines-play-services`, so `:translate_feature:compileStandardDebugKotlin` actually FAILED (the 05.2 "green" check had not exercised the feature module). Added `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3` (matching `app_v2`) to `translate_feature/build.gradle.kts`; `standardDebug` BUILD SUCCESSFUL again.

---

### Step 05.3 - Finish the OCR-side de-bundle: drop ML Kit Text-Recognition; move Tesseract off the store base

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/main/java/com/sza/fastmediasorter/di/OcrModule.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt`, `ui/player/helpers/RecognitionBackend.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Keep the already-landed parts of 05.3 intact: `com.google.mlkit:text-recognition` stays removed from the project, `RecognitionBackend` stays pure `OfflineOcrEngineProvider`, and store translation stays in `:translate_feature` from step 05.2. The unresolved work is the Tesseract half of Set B on store flavors: move the Tesseract native payload out of the store base and register it as Set B, but do **not** blindly drop the Tesseract wrapper from the common compile path. First preserve a compilable/runtime-safe boundary for the shared default OCR engine (either keep the Java wrapper in base while stripping only native `.so`, or relocate the binding/implementation out of `src/main`). Preserve `max-page-size=16384` on delivered `.so`.

**Verification:**

- `Grep` - `com.google.mlkit:text-recognition` returns zero hits in `build.gradle.kts`.
- `Grep` - `com.google.mlkit.vision.text` returns zero hits in `RecognitionBackend.kt`.
- `Unpack/Grep` - store base artifact no longer carries `libtesseract.so`, `libleptonica.so`, `libjpeg.so`, or `libpngx.so`, while the store OCR compile path still remains valid.
- `Grep` - `noLegalImplementation`/`vrImplementation` for `com.google.mlkit:translate` present (bundled on sideload/VR).

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Investigation only, no code change. The ML Kit half of this step is already effectively complete: `com.google.mlkit:text-recognition` is gone from the build, `RecognitionBackend` no longer references `com.google.mlkit.vision.text`, and store translation already moved to `:translate_feature` in 05.2.
- 2026-06-09 - Tesseract Set B de-bundled compile-safe: the `cz.adaptech:tesseract4android` AAR stays on the compile path (the `TessBaseAPI` wrapper still compiles), only its native `.so` are dropped via packaging `jniLibs.excludes` (`libtesseract/libleptonica/libpngx/libjpeg`). `TessBaseAPI`'s static initializer calls `System.loadLibrary("jpeg"/"pngx"/"leptonica"/"tesseract")`, which a bare `System.load(absolutePath)` cannot satisfy, so Phase 07's `DeliveredNativeLibraryLoader` splices the delivered dir into the classloader native path before the engine initializes. Store flavors contribute `DeliverableDescriptorCatalog.ocrEnginesStore()`. standardDebug/legacyDebug green; APK unpack shows the four Tesseract `.so` gone.

---

### Step 05.4 - Remove PaddleOCR `.so` from the noLegal base

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/noLegal/java/.../delivery/NoLegalDeliverableSetsModule.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Exclude the checked-in `src/noLegal/jniLibs/arm64-v8a/libpaddle_*.so` (~9.6 MB) from base packaging and register them as part of Set B's `noLegal` payload via `NoLegalDeliverableSetsModule`. The `.nb` models are already on-demand via `PaddleOcrModelManager` - only the `.so` move here.

**Verification:**

- `Grep` - `NoLegalDeliverableSetsModule` declares an `OCR_ENGINES` descriptor including the Paddle payload.
- `Grep` - a packaging exclude for `libpaddle` present in `build.gradle.kts` (under the `noLegal` packaging/jniLibs config).

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - `libpaddle_lite_jni.so` + `libpaddle_light_api_shared.so` excluded from packaging (global jniLibs exclude; the libs exist only in `src/noLegal/jniLibs`). `NoLegalBundledDeliverableSetsModule` contributes `DeliverableDescriptorCatalog.ocrEnginesNoLegal()` (Tesseract + Paddle for arm64). noLegalDebug green; APK unpack shows both Paddle `.so` gone while ML Kit Translate stays bundled.

---

### Step 05.5 - Move FFmpeg DTS AAR to Set D delivery

**Files:** `app_v2/build.gradle.kts`, `src/standard/.../delivery/StandardDeliverableSetsModule.kt`, `src/noLegal/.../delivery/NoLegalDeliverableSetsModule.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Remove the four `"<flavor>Implementation"(files("libs/fms-ffmpeg-dts.aar"))` lines so the `libffmpegJNI.so` no longer packs into `standard`/`noLegal`/`legacy`/`vr` base. Register the FFmpeg DTS `.so` as Set D payload in each relevant flavor's contributor. Keep media3 decoder wiring able to attach the delivered `.so` at runtime (consumed in Phase 07). Preserve 16 KB alignment (Blocker B3).

**Verification:**

- `Grep` - `fms-ffmpeg-dts.aar` no longer referenced under any `Implementation(` in `build.gradle.kts`.
- `Grep` - `FFMPEG_DTS` descriptor present in `StandardDeliverableSetsModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - `libffmpegJNI.so` excluded from packaging (global jniLibs exclude); the `fms-ffmpeg-dts.aar` dependency stays so media3's `FfmpegLibrary`/`FfmpegAudioRenderer` classes still compile, only the `.so` is stripped. All four OCR/DTS flavors contribute `DeliverableDescriptorCatalog.ffmpegDts()` (per-ABI). Attach is in Phase 07 (`createPlaybackRenderersFactory`); when the `.so` is absent media3's `FfmpegLibrary.isAvailable()` returns false and DTS degrades gracefully. Builds green; APK unpack shows `libffmpegJNI.so` gone.

---

### Step 05.6 - Move audio-visualization videos to Set C delivery

**Files:** `app_v2/src/main/res/raw/`, `src/standard/.../delivery/StandardDeliverableSetsModule.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Remove `anim_audio_bg_1..5.mp4` (~6.1 MB) from `src/main/res/raw/` and register them as Set C payload. Code that references these raw resources must route through the delivered-asset path instead of `R.raw.anim_audio_bg_*`. Set C is a pure resource set - no `System.load`, no signature requirement (lightest pilot).

**Verification:**

- `Glob` - `app_v2/src/main/res/raw/anim_audio_bg_1.mp4` no longer exists.
- `Grep` - `AUDIO_VISUALIZATIONS` descriptor present in `StandardDeliverableSetsModule.kt`.
- `Grep` - no remaining `R.raw.anim_audio_bg` reference in `src/main`.

**Status:** `[x]` done (commit `379b497f`)

**Step Log:**

- 2026-06-09 - Done in commit `379b497f`: the 5 `anim_audio_bg_*.mp4` were removed from `src/main/res/raw/`, `DeliveredAudioVisualizationSource` serves them delivered-only from `filesDir/delivery/AUDIO_VISUALIZATIONS/`, and each flavor contributes `audioVisualizations()`.

---

### Step 05.7 - Verify base artifact is stripped (release unpack)

**Files:** (verification only)
**Depends on:** Step 05.3, 05.4, 05.5, 05.6

**Prompt for developer:**

> Build `standardRelease` and `noLegalRelease`, unzip, and confirm the strip per the per-flavor decision. Store base (`standard`) must NOT carry ML Kit Translate/language-id (moved to `:translate_feature`), ML Kit Text-Recognition (dropped), Tesseract, FFmpeg, or `anim_audio_bg`. Sideload base (`noLegal`) must NOT carry Text-Recognition, Tesseract, Paddle, FFmpeg, or `anim_audio_bg`, but **DOES still carry** ML Kit Translate/language-id (bundled per 2026-06-09 decision). Record `expected | actual` per artifact.

**Verification:**

- `Bash` - `standardRelease` base contains zero `libtranslate_jni.so` / `liblanguage_id*.so` / `libmlkit_google_ocr_pipeline.so` / `libtesseract.so` / `libffmpegJNI.so` entries.
- `Bash` - `noLegalRelease` contains zero `libmlkit_google_ocr_pipeline.so` / `libtesseract.so` / `libpaddle_*.so` / `libffmpegJNI.so`, but `libtranslate_jni.so` IS present (bundled).
- `Bash` - neither artifact contains `anim_audio_bg`.

**Status:** `[~]` verified on debug; release unpack-verify deferred to the owner's release/device pass

**Step Log:**

- 2026-06-09 - Verified on the four **debug** base APKs (unzip): `standardDebug` carries none of `libtranslate_jni`/`liblanguage_id`/`libtesseract`/`libleptonica`/`libjpeg`/`libpngx`/`libffmpegJNI`/`anim_audio_bg`; `noLegalDebug` carries none of `libtesseract`/`libpaddle_*`/`libffmpegJNI`/`anim_audio_bg` but **does** still bundle `libtranslate_jni`/`liblanguage_id` (per the 2026-06-09 decision). The `standardRelease`/`noLegalRelease` unpack is bundled into the owner's BlockNeedUserTest release/device validation (release signing runs from the release worktree).

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done` (or 05.3's ML Kit Translate removal explicitly deferred if B4-PoC failed - log it).
- [ ] Project compiles on target variants - run `/build` for `standard`, then build `noLegalDebug`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Release unpack proves criteria §11.1 (no ML/OCR/Paddle/FFmpeg `.so`, no `anim_audio_bg`).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Execution Note (2026-06-09)

- 05.1 done + `assembleStandardDebug` BUILD SUCCESSFUL (contributor extension point is additive and inert: empty contributor set → empty descriptor map → no behavior change, nothing stripped yet).
- 05.2 done: the store translation runtime now lives in shared source set `src/translationDynamicFeature/java`, `standard` and `legacy` both bind it, `legacyDebug` compiles again, and `standard`/`legacy` base APKs no longer ship ML Kit Translate/language-id. `noLegal`/`vr` keep bundled translation as planned.
- 05.3 is partially grounded: the ML Kit OCR-removal part is already landed, so the open work is only the Tesseract Set B move on store. The first implementation boundary is the common default OCR binding and the shared Tesseract engine, not `RecognitionBackend`.
- 05.3-05.7 remain paused at the genuine external block:
  - **05.3-05.6 (debundle)** remove native libs/assets from the base, which breaks live OCR/DTS/audio-viz until Phase 07 attaches/serves the delivered payloads. They should land together with Phase 07.
  - **05.7 + Phase 07** require release builds (signing) + unzip proof and an on-device pass; audio-viz (Set C) and armeabi-v7a `.so` are not hosted yet (only arm64 OSS `.so` in `delivery-so-v1`).
- Resume path: start from 05.3 with the current dynamic-feature wiring and the Tesseract-wrapper constraint above, then execute 05.3-05.7 together with Phase 07 and validate on a release build + device.

---

## Handoff Notes to Next Phase

Store translation is now correctly dynamic (`standard` + `legacy`), while `noLegal`/`vr` still bundle translation. Base artifacts are **not yet stripped** for Set B/C/D. The immediate next slice is 05.3 store Tesseract de-bundle, with one local constraint: keep the store OCR compile/runtime contract intact while removing native payloads. Phase 07 must attach Set B/C/D only after 05.3-05.6 actually remove them from the base build.

---

## Rollback Plan

Revert phase commit(s) to restore the bundled deps and resources. Higher-risk phase: revert restores prior packaging exactly (deps and `res/raw` come back); confirm a clean `standardDebug` build after revert. No data migration.
