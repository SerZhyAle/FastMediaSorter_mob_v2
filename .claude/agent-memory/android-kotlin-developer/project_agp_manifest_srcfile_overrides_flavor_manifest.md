---
name: project_agp_manifest_srcfile_overrides_flavor_manifest
description: AGP manifest.srcFile() in a productFlavor sourceSet REPLACES the auto-detected src/<flavor>/AndroidManifest.xml - to add an extra manifest source use androidComponents.onVariants { variant.sources.manifests.addStaticManifestFile(...) }
metadata:
  type: project
---

In `app_v2/build.gradle.kts`, the noLegal flavor sourceSet sets `manifest.srcFile("src/vr/AndroidManifest.xml")` to inherit VR manifest content. This call **replaces** the auto-detected `src/noLegal/AndroidManifest.xml` instead of merging both - AGP `AndroidSourceFile.srcFile()` is a SET operation, not an ADD.

Symptom: a `<uses-permission>` placed in `src/noLegal/AndroidManifest.xml` is silently dropped from the packaged manifest, causing `SecurityException: Need to declare android.permission.X` at runtime. Manifest merger report shows VR + main but not noLegal as a source.

**Why:** S0183 (APK install) added `REQUEST_INSTALL_PACKAGES` to `src/noLegal/AndroidManifest.xml` - the permission never reached the merged manifest because of the srcFile override on the flavor sourceSet. Fix landed in `androidComponents.onVariants { ... variant.sources.manifests.addStaticManifestFile("src/noLegal/AndroidManifest.xml") }` (AGP 8.4+ API).

**How to apply:** When implementing a feature that needs a new `<uses-permission>` / `<queries>` / `<provider>` entry on `noLegal` (or any flavor whose sourceSet uses `manifest.srcFile(...)` to inherit another flavor's manifest), do NOT just edit `src/noLegal/AndroidManifest.xml` and assume it merges - it will be silently dropped. Either:
  1. Add the entry via `addStaticManifestFile()` in `androidComponents.onVariants` inside `app_v2/build.gradle.kts` (preferred - keeps flavor isolation per Rule 15).
  2. Move the entry into the buildType-variant manifest path `src/<flavor><BuildType>/AndroidManifest.xml` (e.g. `src/noLegalDebug/AndroidManifest.xml`), which AGP auto-detects without being affected by the flavor srcFile override.
After the change, build the actual flavor variant and inspect the merged manifest under `app_v2/build/intermediates/merged_manifests/<variant>/AndroidManifest.xml` to confirm the entry survived.
