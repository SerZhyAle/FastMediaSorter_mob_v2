---
name: android-xr-play-distribution
description: Android XR ships from ONE Google Play product (same package as standard); vr goes to a dedicated XR track, NOT a second listing - do not swap vr for standard (arm64-only reach regression)
metadata:
  type: project
---

Distribution model for Android XR on Google Play (owner strategy call, 2026-07-11; Meta Quest deliberately deferred):

- **`standard` already covers Android XR today.** `src/main/AndroidManifest.xml` has zero `required="true"` hardware/XR features, so Google auto-lists it as an "Android XR compatible mobile app" - it shows on Samsung Galaxy XR in a floating spatial panel with no work. Base Android XR reach is free via the existing `standard` mobile-track AAB.
- **One product, not two programs.** Same package name serves phones + Android XR from a single Play Console listing. Two distribution options within that one product:
  - Option A: XR features inside the same AAB, `uses-feature required="false"`, stays on mobile track.
  - Option B: separate XR AAB on the **dedicated Android XR release track**, `required="true"`; visible only to XR devices; **same package name + same listing** as the mobile bundle. Google calls this the path for "significantly different XR variants" - which our `vr` flavor is (arm64-only, native OpenXR, separate immersive activities). This is the natural fit.
- **Store listing differentiation:** XR-specific **screenshots/video** yes (4-8 assets, 8:5, rec 3840x2400 / min 1920x1200; ratings+reviews shown per-form-factor). Full **description text** is basically shared - no per-form-factor body text (only Custom Store Listings by country/audience/keyword, not by form factor). A truly separate VR marketing text / price / name would require a second product (different package) - we have no such need.

**Why:** owner asked (2026-07-11) whether Android XR needs a second Google Play program or fits one product with multiple bundles, while the immersive player (epic S0773) is still being finished.

**How to apply:**
- Publish only `standard` on the mobile track until S0773 lands. When the immersive player is ready, add the `vr` bundle to the **same** product on a dedicated XR track - never a second listing.
- For Google Play XR, `vr` must **share the package** with `standard` = NO `applicationIdSuffix` (already the case, S0232). The `.vr` suffix comment in build.gradle applies ONLY to a future Meta Horizon Store submission, not Google Play.
- Multi-bundle-under-one-package needs **distinct versionCodes** for the `standard` vs `vr` AABs - the one real build-config task before XR publish.
- NEVER publish `vr` in place of `standard`: `vr` is `arm64-v8a`-only ([build.gradle.kts](../../../app_v2/build.gradle.kts) L500) vs standard's 4 ABIs -> drops armeabi-v7a/x86/x86_64 -> device-reach regression -> release-gate STOP. See [[feedback_release_no_coverage_regression]].
- Android XR (Google Play) != Meta Quest (Horizon Store). `XrEnvironmentDetector.detect()` already distinguishes `ANDROID_XR` vs `VR_QUEST`. See [[project_vr_inclusion_hierarchy]] and [[supportsvrplayer_nolegal_only]].
