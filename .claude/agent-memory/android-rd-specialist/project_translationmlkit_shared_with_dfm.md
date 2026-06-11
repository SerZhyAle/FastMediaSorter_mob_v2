---
name: translationmlkit-shared-with-dfm
description: src/translationMlKit is compiled by the :translate_feature DFM module (no Hilt/Dagger) - never put a Hilt @Module there
metadata:
  type: project
---

`app_v2/src/translationMlKit/java` is mounted as a **shared source root by the `:translate_feature` dynamic-feature Gradle module** (`translate_feature/build.gradle.kts` adds `../app_v2/src/translationMlKit/java`), in addition to the app's bundled-translation flavors (noLegal/legacy).

**Why:** the `:translate_feature` module does NOT have Dagger/Hilt on its classpath. A Hilt `@Module` / `@Provides` / `@IntoSet` placed in `src/translationMlKit` fails to compile in `:translate_feature:compile<Flavor>DebugKotlin` with `Unresolved reference 'Module'/'Provides'/'InstallIn'` - even though it compiles fine for the app. I hit this on 2026-06-11 trying to contribute `CAP_TRANSLATION_BUNDLED` from translationMlKit.

**How to apply:**
- Need a capability/DI contributor that distinguishes bundled (noLegal/legacy) vs DFM (standard/vr) translation? Put it in `src/translationDynamicFeature/java/.../di/` (app-only, has Hilt) and contribute the **DFM** marker (e.g. `CAP_TRANSLATION_DFM`), inverting the predicate, rather than a bundled marker from translationMlKit.
- General rule: before adding a Hilt module to any `translation*`/feature source set, check whether a DFM module's `build.gradle.kts` mounts that source root - if so, it must stay Hilt-free.
- `translationDynamicFeature` is mounted by standard + vr; `translationMlKit` by noLegal + legacy; lite/photos mount neither.
