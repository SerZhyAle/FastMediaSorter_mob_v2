# Phase 11 — Store Submission + Sideload Infrastructure

**Status:** Not started · **Depends on:** Phases 1–10 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Prepare the vr flavor for distribution via Meta Horizon Store and sideload (APK drop / Google Play). Requires union AndroidManifest, VR-specific icons, promo assets, build scripts, and a signed release APK.

## Current State

- `app_v2/build.gradle.kts` defines vr flavor.
- No Meta Horizon manifest attributes.
- No VR-branded icon or splash.
- No release build script dedicated to vr flavor.
- `scripts/builders/` has per-flavor builders for standard/lite/photos/legacy only.

## Work

1. **Manifest**: update `app_v2/src/vr/AndroidManifest.xml`:
   - `<uses-feature android:name="android.hardware.vr.headtracking" android:required="true" android:version="1"/>`
   - `<uses-feature android:name="oculus.software.handtracking" android:required="false"/>` (future)
   - `<meta-data android:name="com.oculus.supportedDevices" android:value="quest2|quest3|questpro"/>`
   - `<category android:name="com.oculus.intent.category.VR"/>` on the VR activity.
2. **Icons**: create Meta-spec icons (`512×512` app icon, `2560×1440` cover art) — export from existing brand kit; add to `app_v2/src/vr/res/drawable-nodpi/`.
3. **Splash**: Quest uses a splash texture (`2048×2048` equirect preferred) — add to `app_v2/src/vr/res/raw/`.
4. **Build scripts**:
   - `scripts/builders/build-vr-release.ps1` — signs APK with release keystore, runs `assembleVrRelease`, copies to `artifacts/vr/`.
   - Extend `scripts/builders/build-and-push-all.ps1` to include vr variant.
5. **Signing**: reuse the existing release keystore; document any Meta-specific key requirements in VR_SIDELOAD.md.
6. **Versioning**: follow project's `Y.YM.MDDH.Hmm` format. Meta Horizon requires monotonic `versionCode` — verify.
7. **Dry-run submission**: produce the Meta Developer Hub upload bundle; run Meta's `ovr-platform-util` or `adb install` on a Quest 3 to verify launch.

## Acceptance Criteria

- `./scripts/builders/build-vr-release.ps1` produces a signed, aligned APK.
- `adb install` of the APK on Quest 3 installs and launches into a valid XR session.
- Meta Horizon Developer Hub passes the APK's pre-submission validation (no blocking warnings).
- App icon displays correctly in Quest home screen.
- Phone install of the same APK opens `VrPhoneFallbackActivity` (explains this is the VR edition).

## Files Touched

- `app_v2/src/vr/AndroidManifest.xml`
- `app_v2/src/vr/res/drawable-nodpi/ic_launcher.png` + `cover_art.png`
- `app_v2/src/vr/res/raw/vr_splash.jpg`
- `scripts/builders/build-vr-release.ps1` (new)
- `scripts/builders/build-and-push-all.ps1` (extend)
- `app_v2/build.gradle.kts` — vr flavor versioning logic if needed

## Out of Scope

- Meta Horizon Store listing copy / screenshots (creative task, separate delivery).
- Review appeals / store compliance remediation (post-submission).
- Google Play Store listing for VR (sideload-first strategy).
