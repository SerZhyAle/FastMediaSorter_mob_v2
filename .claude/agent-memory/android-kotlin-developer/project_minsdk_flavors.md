---
name: project_minsdk_flavors
description: minSdk and compileSdk values per flavor - avoids re-reading TECH_REQUIREMENTS.md for basic version queries
metadata:
  type: project
---

Build baseline values (source of truth: `app_v2/build.gradle.kts`, `gradle/libs.versions.toml`):

- `compileSdk` = 35 (all flavors)
- `targetSdk` = 35 (all flavors)
- `minSdk` standard = 26 (Android 8.0+)
- `minSdk` lite = 26
- `minSdk` photos = 26
- `minSdk` legacy = 23 (Android 6.0+)
- `minSdk` wear = 28 (Wear OS 2.0+)
- Java toolchain = 17

**Why:** These values are constant per flavor but buried in TECH_REQUIREMENTS.md (26 KB). Pre-caching avoids a large file read on every build/flavor question.

**How to apply:** When writing a class that calls a SDK API gated by version (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.X`), use these values as the baseline of what's available without a check: API 26 is free in standard/lite/photos, API 23 is the floor in legacy, API 28 in wear. Anything above that needs an explicit `SDK_INT` guard. Before relying on these values for a flavor decision, re-confirm against `app_v2/build.gradle.kts` - this memory may lag behind a version bump.

Related: [[project_build_gotchas]]
