---
name: s0386-delivery-pause
description: S0386 on-demand delivery - channel+UX done & inert; debundle core paused at BlockExternal pending /spec-tech refine of the dynamic-feature
type: project
---

S0386 (on-demand OCR/translation/heavy-asset delivery): phases 01-04 + 06 + step 05.1 implemented and `assembleStandardDebug`-green as of 2026-06-09. Delivered: descriptor/manifest/integrity-verifier/downloader (`domain|data/delivery/*`), capability contract, default-OFF, enable-intercept UX (`ui/delivery/*` + settings/player/camera call-sites), set-contributor extension point. All **inert** - nothing stripped from the base yet, so no behavior change.

Paused at **BlockExternal** (owner decision 2026-06-09) on the debundle core (phases 05.2-05.7, 07).

**Why:** The Play dynamic-feature for ML Kit Translate (step 05.2) is tactically under-specified and can't compile naively: ML Kit Translate is used directly in `src/main` (`TranslationBackend`, `PrewarmTranslationModelUseCase`, `PlayerControlsSetupManager`, `TranslationLanguageCodeMapper`), so moving the dependency into a `:translate_feature` module needs that code relocated behind `TextTranslationFacade` + runtime `SplitInstall`. The debundle also breaks live OCR/translation/DTS/audio-viz until Phase 07 attaches the delivered payloads, and needs release/Play/on-device validation + hosting of audio-viz (Set C) and armeabi-v7a `.so` (only arm64 OSS `.so` are hosted in GitHub release `delivery-so-v1`).

**How to apply:** To resume, first `/spec-tech` refine 05.2-07 (dynamic-feature module + OSS native pre-load `System.load` attach), host the remaining payloads, then execute the debundle + Phase 07 attach together and validate on a release build + device. Verify current state against the spec/code before acting - this snapshot is point-in-time.
