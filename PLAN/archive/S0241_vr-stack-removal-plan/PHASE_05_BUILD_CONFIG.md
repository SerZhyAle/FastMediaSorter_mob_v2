# S0241 Phase 05 — Build Configuration Cleanup

Ticket: S0241
Phase status: Done
Goal: collapse the build system to the 5 surviving flavors — no VR product flavors, no OpenXR loader, no native CMake target, no VR build aliases.

## Scope — `app_v2/build.gradle.kts`

- Deleted `productFlavors { create("vr") }` block (was lines 263..317 pre-edit).
- Deleted `productFlavors { create("vrUnlicensed") }` block (was lines 325..370 pre-edit).
- Removed `sourceSets { getByName("vr") }` and `getByName("vrUnlicensed")` entries; removed `manifest.srcFile("src/vr/AndroidManifest.xml")` from `noLegal` source-set.
- Removed `externalNativeBuild { cmake { path = file("src/vr/cpp/CMakeLists.txt") } }` (top-level android block).
- Removed `externalNativeBuild { cmake { targets += "openxr_native" } }` block from the `noLegal` flavor.
- Removed `buildFeatures { prefab = true }` — no surviving consumer of the OpenXR AAR prefab headers.
- Removed `buildConfigField("boolean", "SUPPORT_VR_PLAYER", ...)` from all surviving flavors.
- Removed `buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", ...)` from all surviving flavors (was only on standard and noLegal historically — both removed).
- Removed `buildConfigField("String", "PLAYER_ACTIVITY_CLASS", ...)` from all surviving flavors.
- Removed `vrImplementation` / `vrUnlicensedImplementation` dependency entries (Media3 HLS/DASH, OpenXR loader, custom DTS FFmpeg AAR).
- Removed `noLegalImplementation("org.khronos.openxr:openxr_loader_for_android:1.1.48")`.
- Renamed helper `disableNativeBuild()` → `standardDistributionAbis()` and rewrote its KDoc — every surviving flavor uses it for the standard 4-ABI distribution filter; the native-build-disable side-effect is gone because no flavor ships native code now.
- Rewrote stale comments at the top of `productFlavors` referencing VR-only CMake targeting.
- Removed `androidComponents.onVariants` injection of `addStaticManifestFile("src/noLegal/AndroidManifest.xml")` — no longer needed since `src/vr/AndroidManifest.xml` redirection is gone.

## Scope — `a.ps1`

- Removed aliases: `vr`, `vrd`, `ivr`, `ivrd` from the `$scripts` hashtable, parameter docs, error-handler menu, chaquopy switch, and `$releaseCommands` array.
- Verified script still parses via `[Parser]::ParseFile`.

## Scope — `scripts/builders/` + `scripts/utils/`

Deleted (10 files):

- `scripts/builders/build-vr-aab.ps1`
- `scripts/builders/build-vr-debug.ps1`
- `scripts/builders/build-vr-device.ps1`
- `scripts/builders/build-vr-release.ps1`
- `scripts/builders/install-vr-debug-to-device.ps1`
- `scripts/builders/install-vr-release-to-device.ps1`
- `scripts/utils/fetch_organic_vr_samples.ps1`
- `scripts/utils/generate-vr-icons.ps1`
- `scripts/utils/setup_test_media_vr.ps1`
- `scripts/utils/synth_vr_test_media.py`

## Scope — `src/debug/`

Two debug-source-set files held VR-specific test integration code:

- `app_v2/src/debug/java/com/sza/fastmediasorter/ui/settings/IntegrationTestDialog.kt` — `chipThreeDVr` visibility gated by `BuildConfig.SUPPORT_VR_PLAYER`. Now permanently `View.GONE`.
- `app_v2/src/debug/java/com/sza/fastmediasorter/domain/usecase/IntegrationTestRunner.kt` — `THREE_D_VR` sweep group entirely; ~243 lines deleted (manifest path, `ThreeDVrEntry` data class, `loadThreeDVrManifest`, `expectedRouteFor`, and 5 test functions). `TestGroup.THREE_D_VR` enum value retained for binary compatibility with any persisted test history but renamed display name to "3DVR Sweep — removed" and never produces new entries.

## Validation

- PASS: `./gradlew.bat -Pchaquopy.enabled=false :app_v2:assembleStandardDebug :app_v2:assembleLiteDebug :app_v2:assemblePhotosDebug :app_v2:assembleLegacyDebug` — BUILD SUCCESSFUL in 14m 46s.
- PASS: `./gradlew.bat :app_v2:assembleNoLegalDebug` — BUILD SUCCESSFUL in 1m 15s (chaquopy enabled).
- `Grep "SUPPORT_VR_PLAYER\|VR_UI_COMPOSITION_LAYER_ENABLED\|PLAYER_ACTIVITY_CLASS" app_v2/src` — expected: 0; actual: 4 (comment-only mentions in IntegrationTestRunner.kt, PlayerEntryCoordinator.kt KDoc, plus historical S0241 markers).
- `find app_v2/src -type d -name "vr*"` — expected empty; actual empty.
- `productFlavors` count in `build.gradle.kts` — expected: 5; actual: 5 (standard, noLegal, lite, photos, legacy).

## Notes

- After this phase the catalog goes from 1137 files / 1384 records → 1085 files / 1322 records (52 files / 62 records removed).
- `archive/vr-stack-2026-05` branch + tag `vr-stack-2026-05-final` in `origin` retain the last working configuration of the deleted stack.
- Phase 06 (resources / manifest cleanup) and Phase 07 (finalization: archive `S0203`, `S0240`, flip S0241 to Verified) remain.
