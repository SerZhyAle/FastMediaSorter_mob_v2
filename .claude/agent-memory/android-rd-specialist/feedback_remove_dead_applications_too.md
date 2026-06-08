---
name: remove-dead-applications-too
description: Dead-code cleanup includes dead build-config (unused plugins, buildscript classpath, config) even when it does not change APK/AAB size
metadata:
  type: feedback
---

When cleaning up dead code/artifacts, also remove dead **applications and build-config** - unused Gradle plugin applications, unused `buildscript { dependencies { classpath(...) } }` entries, dead config blocks - even if removing them does NOT reduce APK/AAB binary size. Build hygiene counts on its own.

**Why:** During S0385 I reported that the `androidx.navigation.safeargs.kotlin` plugin was 100% dead (no nav graphs, no runtime usage) but produced **zero** APK/AAB size win (its codegen runs at build time; navigation classes don't ship). I framed it as "out of the size goal, optional." The owner replied "мёртвые применения нам тоже не нужны" - dead applications aren't wanted either. So the cleanup mandate is broader than binary weight.

**How to apply:** When a `dead-weight` / hygiene task surfaces a dead plugin application, unused buildscript classpath, dead Gradle config, or a no-op build hook, remove it as part of the cleanup rather than flagging it as out-of-scope - even when the size delta is zero. Still verify with a build (plugin removal is a build-config change). Don't pre-filter cleanup candidates by "does it save bytes"; "is it dead" is the test.
