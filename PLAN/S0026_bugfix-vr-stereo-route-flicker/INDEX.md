# Tactical plan: S0026 — VR stereo route flicker

**Ticket:** S0026
**Strategic spec:** [`PLAN/S0026_bugfix-vr-stereo-route-flicker.md`](../S0026_bugfix-vr-stereo-route-flicker.md)
**Status:** Tactical
**Date:** 2026-04-29

> **Scope of this folder:** TACTICAL. Concrete classes, paths, code patches, build commands, verification predicates.

## Root cause (from code research)

Two coupled bugs feed the user-visible flicker `VrPlayerActivity onCreate → forceStopVrPlayback reason=standard-player-fallback:player-state`:

1. **B1 — Browse ignores `vrAutoImmersive`.**
   `BrowseEventHandler.shouldLaunchStandardPlayer` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt:177-235`) decides `shouldUseStandard` by checking only `settings.vrAutoDetectFormat` and the detected stereo mode. The setting `settings.vrAutoImmersive` ("automatically enter immersive on stereo content") is **not** consulted. Result: with auto-immersive OFF, a detected stereo file still routes through `VrPlayerActivity`, only to fall back inside.

2. **B2 — `resolveLaunchStereoMode` short-circuits when requested is `MONO`.**
   `VrPlayerActivity.resolveLaunchStereoMode` (`app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt:1529-1560`) has an early-return: `if (requestedStereoMode != AUTO && requestedStereoMode != UNKNOWN) return requestedStereoMode`. When `PlayerStereoModeCoordinator.applySettings` initialises before the file is loaded, it falls back to `MONO` (log line 494: `suppressed effective=UNKNOWN reason=apply-settings (kept=MONO)`). The early-return then skips re-detection, even though the filename clearly encodes the format. `effectiveMode = MONO` → `requestsImmersive = false` → route `plain-2d-video` → fallback fires.

Fixing B1 alone closes the user-reported flicker for `vrAutoImmersive = false`. Fixing B2 also closes the latent regression that affects the `vrAutoImmersive = true` path: when an external entry-point (deep-link, share intent) lands directly on `VrPlayerActivity` without the coordinator being primed, the same MONO short-circuit kicks in.

## Phases

| # | File | Goal | Build gate |
|---|------|------|:----------:|
| 01 | [`F01_browse-honors-auto-immersive.md`](F01_browse-honors-auto-immersive.md) | Patch `BrowseEventHandler.shouldLaunchStandardPlayer` to honor `vrAutoImmersive`. Stereo files stay on standard player when toggle is off. | standard + vr |
| 02 | [`F02_intent-extra-detected-stereo.md`](F02_intent-extra-detected-stereo.md) | Add intent-extra `EXTRA_DETECTED_STEREO_MODE`. Browse fills it on launch; `VrPlayerActivity` reads it before settings apply. | vr |
| 03 | [`F03_resolve-launch-stereo-uses-extra.md`](F03_resolve-launch-stereo-uses-extra.md) | `resolveLaunchStereoMode` consumes the extra (and re-detection still works as last-resort fallback). Fixes B2. | vr |
| 04 | [`F04_unit-tests.md`](F04_unit-tests.md) | Unit tests covering matrix `(detected × autoImmersive × autoDetect × forceImmersive)`. | tests |
| 05 | [`F05_manual-acceptance.md`](F05_manual-acceptance.md) | Manual on-device acceptance on Quest 3. Non-blocking. | manual |

## Acceptance gate

- ✅ `:app_v2:assembleStandardDebug` — PASS
- ✅ `:app_v2:assembleVrDebug` — PASS
- ✅ `:app_v2:testVrDebugUnitTest` — including new tests — PASS
- ⏳ Manual on-device — Quest 3, defer to user

## Non-goals (re-stated)

- Не пересматривается алгоритм `StereoDetector.detectFromFilename`.
- Не правится `VrRouteDecisionHelper` — он уже корректен (S0018), правки только на ВХОДЕ в helper.
- Не объединяются Browse и VR routing decisions в один общий helper. Browse honors a subset of rules (auto-immersive gate); inner helper остаётся the source of truth внутри VR-активити.
