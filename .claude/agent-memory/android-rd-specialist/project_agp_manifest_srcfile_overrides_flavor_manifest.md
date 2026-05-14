---
name: agp-manifest-srcfile-replaces-flavor-manifest
description: AGP manifest.srcFile() in a productFlavor sourceSet REPLACES the auto-detected src/<flavor>/AndroidManifest.xml — to add an extra manifest source use androidComponents.onVariants { variant.sources.manifests.addStaticManifestFile(...) }
metadata:
  type: project
---

In `app_v2/build.gradle.kts`, the noLegal flavor sourceSet sets `manifest.srcFile("src/vr/AndroidManifest.xml")` to inherit VR manifest content. This call **replaces** the auto-detected `src/noLegal/AndroidManifest.xml` instead of merging both — AGP `AndroidSourceFile.srcFile()` is a SET operation, not an ADD.

Symptom: a `<uses-permission>` placed in `src/noLegal/AndroidManifest.xml` is silently dropped from the packaged manifest, causing `SecurityException: Need to declare android.permission.X` at runtime. Manifest merger report shows VR + main but not noLegal as a source.

**Why:** S0183 (APK install) added `REQUEST_INSTALL_PACKAGES` to `src/noLegal/AndroidManifest.xml` — the permission never reached the merged manifest because of the srcFile override on the flavor sourceSet. Fix landed in `androidComponents.onVariants { ... variant.sources.manifests.addStaticManifestFile("src/noLegal/AndroidManifest.xml") }` (AGP 8.4+ API).

**How to apply:** Any time noLegal needs a manifest entry that VR does NOT need (or vice versa for any flavor that uses srcFile override), do NOT add it to the file pointed to by srcFile (would leak across flavors) and do NOT add it to the overridden file (would be ignored). Either:
  1. Use `addStaticManifestFile()` in `androidComponents.onVariants` (preferred — keeps flavor isolation per Rule 15).
  2. Use the buildType-variant manifest path: `src/<flavor><BuildType>/AndroidManifest.xml` (auto-detected, not affected by flavor srcFile override).
