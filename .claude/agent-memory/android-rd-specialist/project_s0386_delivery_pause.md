---
name: s0386-delivery-status
description: S0386 on-demand delivery - ALL 13 phases done & 6 flavors build-green; debundle + Extensions restructure + upgrade-reconciliation device-verified on emulator; at BlockNeedUserTest (only owner native-attach OCR/DTS device check + release unpack remain)
type: project
---

**2026-06-10 update (Phases 12.3 + 13, commits `bf283ab8` + `90e9c869`):** 12.3 - removed the duplicate inline OCR-best-models (rus/ukr) download UI from the Translation/OCR settings group (it now lives only in the Extensions screen OCR section); kept runtime toggles/selectors. Phase 13 (ADR-2 variant 1.1, owner-chosen) - `S0386UpgradeReconciliation` run-once migration in `FastMediaSorterApp.onCreate` (mirrors `S0200AuthStateWipe`): on the de-bundle upgrade, force a code-download toggle OFF if it is ON but `isInstalledBlocking`=false (never downloads - rejected variant 1.2 auto-download); `DeliveryPromptDialogFragment` title now names the capability via `ext_*_title`. Device-verified on emulator (API33): upgrade flipped Translation OFF (not installed) while OCR stayed ON (installed); re-enable prompt titled "Translation Module". Note: the in-use enable-intercept (`TranslationButtonManager`/`CameraOcrFlowManager`) was already the working safety net before Phase 13. Still owner-only: native attach on REAL OCR/DTS use (the System.loadLibrary path is exercised only by actual recognition/playback, not by download), release unpack-verify.

---

**2026-06-10 update (Phases 10-12, commit `d2fe8d53`):** owner added an Extensions-screen restructure, implemented + emulator-verified (API 33 x86_64): the primary "Downloadable Extensions" entry now lives on the Settings **General** tab (non-full-width, after groups, before permission/about buttons; layout-land mirrored); the Translation/OCR group keeps a contextual "OCR & translation downloads" shortcut; the screen is grouped into **OCR / Translation / Media Playback** sections (multi-view-type adapter + `ExtensionSection` enum on `ExtensionItem`), with the rus/ukr OCR models listed under OCR. Deferred: 12.3 (remove the now-redundant inline OCR-best-models from `OtherMediaSettingsFragment` - ~230-line cross-file deletion), 11.3 (manifest-404 red banner - comes from the shared global OkHttp interceptor). **Concurrency note:** done on a working tree carrying another agent's uncommitted `ocrEnabled`/`ocrDisabled` OCR-source-set refactor (TesseractManager/OcrModule moved to `src/ocrEnabled`); only S0386 files were committed (selective `git add`, unstage foreign staged renames first).

---

S0386 (on-demand OCR/translation/heavy-asset delivery): **all 9 code phases done** as of 2026-06-09 (commit `8ed28604` on `DEBUG-v013`), ticket at **BlockNeedUserTest**.

Delivered & build-green on `standard`/`noLegal`/`legacy`/`vr` debug:
- Set A translation: store = Play dynamic-feature `:translate_feature`; sideload/VR = bundled (Google `.so` not re-hosted).
- Set B OCR (Tesseract all flavors; +PaddleOCR on noLegal), Set C audio-viz, Set D FFmpeg DTS: native `.so` stripped from every base via `packaging.jniLibs.excludes`; delivered on demand from GitHub mirror `delivery-so-v1` (all-ABI hosted; hashes in `temp/s0386_so_table.txt`).
- `DeliverableDescriptorCatalog` holds ABI-aware app-pinned SHA-256/size per `.so`.
- `DeliveredNativeLibraryLoader` attaches by **splicing filesDir/delivery/<set> into the classloader native search path** (reflection over `DexPathList.nativeLibraryDirectories`/`makePathElements`, API 23-25 + 26+ variants) - required because Tesseract/Paddle/media3 load their libs via `System.loadLibrary(name)` in a static initializer we cannot edit, which a bare `System.load(absolutePath)` cannot satisfy.

**Why:** This is the keystone discovery. `TessBaseAPI` clinit runs `System.loadLibrary("jpeg"/"pngx"/"leptonica"/"tesseract")`, `PaddleLiteInitializer` runs `loadLibrary("paddle_lite_jni")`, media3 `FfmpegLibrary` loads `ffmpegJNI` - all by name, so the delivered dir must be on the classloader's native path before the wrapper initializes. The prior agent paused 05.3 exactly here.

**How to apply:** To verify/close: the remaining gate is owner-only - on-device enable->download->use (OCR recognize, DTS play), refusal stays unavailable, re-enable skips download, survives update/cache-clear, and `standardRelease`/`noLegalRelease` unpack-verify (release signing from the release worktree). The hidden-API reflection in the loader is the highest-risk part - validate on the target API levels on a real arm64 device (XR emulator unsuited to the 2D UI). Verify current state against the spec header / INDEX before acting - point-in-time snapshot.
