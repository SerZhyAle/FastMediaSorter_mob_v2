---
name: minsdk-flavors
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

**How to apply:** For quick minSdk/compileSdk queries, use these values directly. Verify against `app_v2/build.gradle.kts` before answering questions about flavor support gaps or minSdk bumps - this memory may lag behind a version bump.

Related: [[build-gotchas]]
