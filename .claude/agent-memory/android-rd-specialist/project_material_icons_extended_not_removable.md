---
name: material-icons-extended-not-removable
description: material-icons-extended is NOT dead - Pause/SkipNext/SkipPrevious media icons are extended-only, not in material-icons-core
metadata:
  type: project
---

`androidx.compose.material:material-icons-extended` cannot be removed from `app_v2/build.gradle.kts`: `Icons.Filled.Pause`, `Icons.Filled.SkipNext`, `Icons.Filled.SkipPrevious` (media-control icons in `WearSyncSettingsFragment.kt` and the widget-config Compose activities) live ONLY in the extended set. `Icons.Filled.Close` and `Icons.Filled.PlayArrow` are in `material-icons-core`, but the three transport-control icons are not.

**Why:** During S0385 a dependency-audit subagent claimed all 5 used icons were core members and material-icons-extended was dead. Removing it broke compilation (`Unresolved reference 'Pause'/'SkipNext'/'SkipPrevious'`). The build gate caught it; the dep was restored with a WHY-comment.

**How to apply:** Treat any "material-icons-extended is replaceable by core" claim as unverified until a build proves it - the core set is a small curated subset and most named icons require extended. To actually drop extended you must first migrate Pause/SkipNext/SkipPrevious to vector drawables or different core icons (a UI task, not a dead-dep removal). In release, R8 already strips unused extended icons, so the AAB-size argument for removal is weak. Reinforces [[verify-subagent-build-failures]]: confirm subagent dependency-removal verdicts with an actual build.
