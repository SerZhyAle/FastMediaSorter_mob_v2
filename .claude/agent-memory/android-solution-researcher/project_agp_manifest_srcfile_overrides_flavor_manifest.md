---
name: agp-manifest-srcfile-replaces-flavor-manifest
description: AGP manifest.srcFile() in a productFlavor sourceSet REPLACES the auto-detected src/<flavor>/AndroidManifest.xml - cite this trap in any multi-flavor manifest research
metadata:
  type: project
---

In `app_v2/build.gradle.kts`, the `noLegal` flavor sourceSet sets `manifest.srcFile("src/vr/AndroidManifest.xml")` to inherit VR manifest content. This call **replaces** the auto-detected `src/noLegal/AndroidManifest.xml` instead of merging both - AGP `AndroidSourceFile.srcFile()` is a SET operation, not an ADD.

Symptom: a `<uses-permission>` placed in `src/noLegal/AndroidManifest.xml` is silently dropped from the packaged manifest, causing `SecurityException: Need to declare android.permission.X` at runtime. The manifest merger report shows VR + main but not noLegal as a source.

**Why:** S0183 (APK install) added `REQUEST_INSTALL_PACKAGES` to `src/noLegal/AndroidManifest.xml` - the permission never reached the merged manifest because of the srcFile override on the flavor sourceSet. Fix landed in `androidComponents.onVariants { .. variant.sources.manifests.addStaticManifestFile("src/noLegal/AndroidManifest.xml") }` (AGP 8.4+ API).

**How to apply:** When the research scope touches noLegal/VR manifest behaviour, multi-flavor permission gating, or any feature that depends on a flavor-specific `<uses-permission>` / `<queries>` / `<activity>` entry:

- Always read `app_v2/build.gradle.kts` for `manifest.srcFile(..)` calls in flavor sourceSet blocks AND for the `addStaticManifestFile(..)` calls in `androidComponents.onVariants`. Both shape the merged manifest.
- Cite the trap explicitly in the report under "Risks Identified" or "Android API Level Constraints" (whichever fits the scope): "AGP srcFile override drops `src/noLegal/AndroidManifest.xml` from the merge - any flavor-only manifest entry must be added via `addStaticManifestFile` instead." Severity Med (silent runtime crash).
- Cite the actual line range in `build.gradle.kts` where the override and the static-manifest fix live; do not paraphrase.
- The alternative `src/<flavor><BuildType>/AndroidManifest.xml` path (e.g. `src/noLegalDebug/AndroidManifest.xml`) is auto-detected and not affected by the flavor srcFile override - mention as an option in the report's "Proposed Solution Patterns Found in Codebase" section if relevant.
