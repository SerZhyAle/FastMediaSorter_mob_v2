---
name: r8-mapping-is-per-build
description: Obfuscated symbols in Play Console reports resolve to plausible nonsense against a mapping.txt from any other build - names are assigned per build
metadata:
  type: project
---

R8/Play Console reports name obfuscated symbols (`ev.y`, `k36.b`, `u73.d`). Those names are assigned **per build**. Resolving them against `app_v2/build/outputs/mapping/standardRelease/mapping.txt` from a different build does not fail loudly - it returns confident, plausible-looking, wrong answers.

**Why:** discovered 2026-07-24 on S1156. The three symbols from Play release `2.60.7221.704` resolved against the local 2026-07-20 mapping to `AppLaunchPanelActivity$panelLifecycleCallbacks$1`, `ICustomTabsService$Stub` and `DefaultLivePlaybackSpeedControl$Builder` - none of which download or decode images, which was the reported behaviour. The mismatch was only detectable because the results were semantically absurd; had they landed on any plausible class, the wrong site would have been "confirmed".

**How to apply:** before mapping any Play-reported symbol, confirm the mapping belongs to *that exact release*. A local mapping is only valid for the build that produced it. If the release mapping is unavailable, the deobfuscation step is `BlockExternal` - never substitute a nearby build's mapping. Sanity-check every resolution against the reported behaviour; a resolved class that cannot exhibit the symptom means the mapping is wrong, not that the report is.
