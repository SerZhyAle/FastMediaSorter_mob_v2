# S0423 - On-demand translation for store flavors vs empty translate_feature DFM

**Status:** Archived

## 0. Raw capture (auto-parked during /skill-release of v2.60.6141.916)

Symptom
- Release bundle `packageStandardReleaseBundle` failed: `Module 'translate_feature' has no dex files but the attribute 'hasCode' is not set to false in the AndroidManifest.xml`.
- Surfaced only on the release AAB (R8 on). Debug bundles passed because R8 does not run, so the dex-stripping never happened. S0386 status note listed "release unpack-verify" as a deferred owner item - this is that gap.

Immediate unblock applied (this release)
- Added `<application android:hasCode="false" />` to `translate_feature/src/main/AndroidManifest.xml` (commit a54077eb on DEBUG-v013, merged into main).
- Translation keeps working: ML Kit ships bundled in the app_v2 base for standard/noLegal/vr/legacy via flavor-scoped deps + the `src/translationMlKit` source set.

Underlying inconsistency to resolve
- `app_v2/build.gradle.kts` comment (~line 1148): "S0386: ML Kit Translate is on-demand via :translate_feature on store flavors and remains bundled only on sideload/VR flavors where Play dynamic delivery is unavailable."
- Reality contradicts the comment: `standardImplementation("com.google.mlkit:translate")` + `language-id` bundle ML Kit into the standard (store) base. The `translate_feature` DFM declares the same ML Kit deps but has zero source, so R8 strips it to nothing -> empty on-demand split.
- Net: on-demand translation delivery for store flavors is NOT actually implemented; the DFM is dead weight; the comment is misleading.

Evidence
- `translate_feature/` contains only `src/main/AndroidManifest.xml` + `build.gradle.kts` (no .kt/.java).
- `translate_feature/build.gradle.kts` deps: `project(":app_v2")`, mlkit translate 17.0.3, language-id 17.0.6, timber, kotlinx-coroutines-play-services - all redundant/stripped for a code-less module.
- Translation code: `app_v2/src/translationMlKit/java/.../TranslationBackend.kt`, `PrewarmTranslationModelUseCase.kt`; usage in `app_v2/src/main/.../ui/player/helpers/TextTranslationOverlayManager.kt`.
- `app_v2/build.gradle.kts`: `dynamicFeatures += listOf(":translate_feature")` (line ~272), `src/translationMlKit/java` wired into multiple variants (~554-587).
- translate_feature is new since the last release (S0386 commit 9a097b7e "fix translate_feature build"); never shipped in a release AAB before.

## 1. Decision (2026-06-15): Option B selected (owner)

End state: remove the `translate_feature` DFM entirely and bundle ML Kit translate into every translation-capable flavor (standard/legacy/noLegal/vr).

Rationale
- Option A buys only a few MB of store-base shrink for a large multi-phase effort whose acceptance can only be proven on a real Play track (a sideloaded build cannot exercise an on-demand split).
- Option B removes dead weight and both release blockers at once (the empty-DFM `hasCode` failure and the legacy `minSdk 23` vs `26` package-id `0x7e` failure vanish by construction).
- The DFM never delivered anything real: empirical check of the shipped `DOWNLOADS/FastMediaSorter_standard_release.aab` (v2.60.6141.930) shows no `libtranslate_jni.so` / `liblanguage_id_l2c_jni.so` anywhere, and the `translate_feature` split holds only a manifest + a 54-byte `resources.pb`. Translation is therefore non-functional on the store (standard) release today; Option B is also the fix for that defect.

Rejected: Option A (complete on-demand delivery). Parked rationale above; revisit only if store-base size becomes a hard constraint.

## 2. Scope guards
- Do not regress translation on sideload/VR (already bundled).
- Verify on a real Play-delivered build, not just emulator, if choosing A (Play on-demand split download cannot be exercised on a sideloaded APK).
- Cross-check OCR/DTS/audio-bg delivery (S0386 RealDeliverableSetDownloader / URL-based) is a separate mechanism and unaffected.

## 3. Related
- S0386 (ondemand-ocr-translation-delivery) - parent; BlockNeedUserTest.

## 4. Second release-build blocker found (v2.60.6141.930 release, 2026-06-14)
- `build-release-spectrum.ps1` (full GitHub spectrum) failed at `:translate_feature:bundleLegacyReleaseResources`: `AAPT: error: invalid package ID 0x7e. Must be in the range 0x7f-0xff`.
- Cause: translate_feature `defaultConfig.minSdk = 26`, but the legacy app_v2 flavor is `minSdk = 23` (API 23-25). The DFM/feature split resource-package assignment is rejected for the legacy variant.
- The standard AAB (Play) built and shipped fine; the GitHub spectrum (lite/photos/legacy/vr/noLegal/wear) was DEFERRED this release because of this. No GitHub Release was published for v2.60.6141.930 - the website/IzzyOnDroid still serve the previous version until a follow-up GitHub publish.
- Whatever end-state is chosen (complete on-demand vs remove DFM), the legacy (and likely vr/noLegal pass-2) release builds of the DFM must be verified before the follow-up GitHub spectrum.
- Capability note: app code gates translation availability on `SplitInstallManager.installedModules.contains("translate_feature")` (DeliverableCapabilityRepositoryImpl) and downloads it via `RealDeliverableSetDownloader.downloadDfm("translate_feature")`. Removing the DFM (S0423 option B) therefore requires reworking the availability gate to treat translation as bundled, or it will report translation unavailable on store flavors.

## 5. Follow-up GitHub publish for v2.60.6141.930
- Once the DFM release builds are fixed: run `build-release-spectrum.ps1 -ReuseVersion` (or a fresh version) from the release worktree on main, then `publish-github-release.ps1`.
- If a fresh version is stamped, re-align WHATS_NEW/README/tag to it (the GitHub publisher matches the `**Current release:**` header verbatim).
- Owner note (2026-06-15): the deferred GitHub spectrum publish for .930 is not being pursued; this section is kept for history only.

## 6. Tactical plan (Option B)

Phase 01 - gradle/module teardown
- [x] `settings.gradle.kts`: drop `include(":translate_feature")`.
- [x] `app_v2/build.gradle.kts`: drop `dynamicFeatures += listOf(":translate_feature")`.
- [x] `app_v2/build.gradle.kts`: drop the `jniLibs.excludes` of `libtranslate_jni.so` / `liblanguage_id_l2c_jni.so` for standard/legacy so the engine `.so` re-bundles into the base.
- [x] `app_v2/build.gradle.kts`: remove the unused `com.google.android.play:feature-delivery-ktx` dependency (no DFM and no SplitCompat remain).
- [x] `app_v2/build.gradle.kts`: fix the misleading S0386 on-demand comment over the ML Kit deps.
- [x] Delete the `translate_feature/` module directory.

Phase 02 - bundled-state + delivery code
- [x] `StandardBundledDeliverableSetsModule` + `LegacyBundledDeliverableSetsModule`: add `TRANSLATION` to `bundledSets()`; drop the `TRANSLATION -> translation()` descriptor (mirrors noLegal/vr).
- [x] `DeliverableCapabilityRepositoryImpl`: drop the TRANSLATION SplitInstall branch in `isInstalledBlocking`; drop the `deferredUninstall("translate_feature")` block in `uninstall` (the now-unused `@ApplicationContext context` param was also dropped).
- [x] `RealDeliverableSetDownloader`: drop the TRANSLATION DFM branch, `downloadDfm`, `dfmFailureReason`, and the now-unused SplitInstall imports; fix the stale `isNativeCodeSet` comment.
- [x] `DeliverableDescriptorCatalog`: remove the now-unused `translation()` function and its `TRANSLATION` lib map.
- [x] `CapabilityAvailability`: remove the dead `CAP_TRANSLATION_DFM` + `isTranslationViaDynamicFeature()` (no contributor, no caller).

Phase 03 - manifests + comments
- [x] `src/standard/AndroidManifest.xml` + `src/legacy/AndroidManifest.xml`: drop the `tools:node="remove"` of `MlKitInitProvider` / `MlKitComponentDiscoveryService` (ML Kit is bundled, the provider must run); fix the comments.
- [x] `TranslationBackend.kt` + `PrewarmTranslationModelUseCase.kt`: correct the on-demand comments to bundled reality.

Phase 04 - verify
- [x] standard compile + resources (`fc`) green; legacy + vr compile + resources green.
- [x] standard debug APK re-bundles the translate `.so` (all four ABIs: `libtranslate_jni.so` + `liblanguage_id_l2c_jni.so`); merged manifest re-includes `MlKitInitProvider`.
- [x] release-bundle graph has no `translate_feature` / feature-module tasks, so the `hasCode` and legacy `0x7e` blockers are structurally gone (`packageStandardReleaseBundle` now runs with zero feature modules). Signed-AAB proof deferred to the release worktree (keystore not present on the debug branch).
- [x] neuroslop gate passes (all deltas 0).
- [~] unit gate blocked by a pre-existing `:app_v2:compileStandardDebugUnitTestKotlin` failure in three unrelated test files (parked S0434); `RealDeliverableSetDownloaderGateTest` is logically unaffected by this change.

## 7. Acceptance
- A standard build initializes and runs in-player translation (engine `.so` present in base).
- The Extensions screen shows Translation as Installed on standard/legacy/noLegal/vr (no download row).
- No `translate_feature` module remains in the tree, settings, or any release artifact.
- standard + legacy release bundles build without the `hasCode` and `0x7e` failures.

## 8. Device verification (2026-06-15)
- Verified on a standard debug build (`2.60.6152.342`/`.243-DEBUG`) and a noLegal debug build, emulator API 37 x86_64.
- Installed standard APK bundles `libtranslate_jni.so` + `liblanguage_id_l2c_jni.so` for both packaged ABIs (arm64-v8a, x86_64) - confirmed by unzipping the on-device `base.apk`.
- In-player image translation renders Russian block overlays from English source ("Hello world", "Good morning friend", "The weather is nice today"), so OCR plus the bundled ML Kit translator run end to end - not "engine not installed" and not blank.
- Extensions screen lists "Translation Module" as Installed with no download row (only Delete).
- Loader confirms the bundled path at runtime: `DeliveredNativeLibraryLoader: set TRANSLATION is bundled in base, no filesDir attach needed`.
- The standard fix is the meaningful one: noLegal already bundled ML Kit and was never broken; standard (store) was the flavor the empty DFM had left non-functional.
