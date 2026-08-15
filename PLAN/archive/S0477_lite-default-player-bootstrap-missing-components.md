# Strategic spec: S0477 - DefaultPlayer bootstrap crashes on flavor-removed components (lite)

**Ticket:** S0477
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-17

> Draft skeleton. Raw capture verbatim; no research/approval/spec-tech chaining yet.

---

## 0. Raw capture

Discovered during S0448 device-test on the `lite` flavor (emulator-5554, Android 17/SDK37, fresh first-run onboarding).

On startup the `DefaultPlayerStateBootstrapper` (run from `DeferredStartupWorker`) calls `DefaultPlayerManager.setComponentState(..)` for player/share components that the `lite` flavor manifest removes via `tools:node="remove"`. `PackageManager.setComponentEnabledSetting` then throws `IllegalArgumentException` because the component class does not exist in the installed package, and a red ERROR toast is shown to the user on the welcome screen.

Observed (logcat, `com.sza.fastmediasorter.lite.debug`):

```
E DefaultPlayerManager: DefaultPlayerManager: failed to set state for com.sza.fastmediasorter.StandaloneAudioSender
E DefaultPlayerManager: java.lang.IllegalArgumentException: Component class com.sza.fastmediasorter.StandaloneAudioSender does not exist in com.sza.fastmediasorter.lite.debug
E DefaultPlayerManager:     at com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager.setComponentState(DefaultPlayerManager.kt:119)
E DefaultPlayerManager:     at com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager.applyShareReceiverState(DefaultPlayerManager.kt:107)
E DefaultPlayerManager:     at com.sza.fastmediasorter.core.init.DefaultPlayerStateBootstrapper.apply(DefaultPlayerStateBootstrapper.kt:30)
E DefaultPlayerManager:     at com.sza.fastmediasorter.worker.DeferredStartupWorker$doWork$5.invokeSuspend(DeferredStartupWorker.kt:46)
```

A second variant fires for `com.sza.fastmediasorter.ui.player.MediaButtonRestartReceiver` (also removed from the `lite` manifest), via `applyPrimaryPlayerState` (`DefaultPlayerManager.kt:84`).

Symptom: user-visible red ERROR toast during onboarding on `lite`. Likely also affects other flavors that remove player components (`photos` removes video/audio components; needs checking).

Evidence: `temp/S0448_devtest/oos_defaultplayer_error.log`.

Probable cause: bootstrapper unconditionally toggles a fixed component list without guarding on flavor capability / component existence.

Out of scope for S0448 (network-source gating); parked for its own research + fix.

---

## 1. Root cause

- `MediaCapabilities.supportsDefaultPlayer` is the established gate for the whole default-player feature (system aliases + `MediaButtonRestartReceiver`).
- The `lite` flavor sets `SUPPORTS_DEFAULT_PLAYER=false` and its manifest overlay strips every `Standalone*` activity-alias and `MediaButtonRestartReceiver` via `tools:node="remove"`.
- Note: `lite` keeps `SUPPORT_AUDIO/VIDEO/IMAGES=true`, so the per-type alias filtering in `DefaultPlayerManager` still builds a non-empty alias list - the type flags do not protect against the stripped components.
- Every UI entry point already checks `supportsDefaultPlayer` before touching the manager: `WelcomeActivity`, `WelcomeEnableAllManager`, `DefaultPlayerSettingsManager`, `OperationsSettingsFragment`.
- `DefaultPlayerStateBootstrapper.apply` (run from `DeferredStartupWorker`) was the only caller that toggled the components unconditionally. On `lite` it tries to enable/disable classes the installed package does not declare, so `PackageManager.setComponentEnabledSetting` throws `IllegalArgumentException`, surfaced to the user as a red ERROR toast during onboarding.

## 2. Fix

- Guard `DefaultPlayerStateBootstrapper.apply` with an early return when `!caps.supportsDefaultPlayer`, matching every other call site.
- Resolve capabilities before reading settings so the unsupported-flavor path short-circuits without touching DataStore.
- `photos` is unaffected: its manifest keeps the image/text/typeless aliases and `MediaButtonRestartReceiver`, and its `supportsAudio/Video=false` already filters the audio/video aliases out.

## 3. Verification

- Build: `.\a.ps1 fk` - PASS (change is flavor-agnostic `src/main` Kotlin).
- Device (lite): fresh install + first-run onboarding on emulator must show no `DefaultPlayerManager` ERROR toast and no `IllegalArgumentException` for `Standalone*` / `MediaButtonRestartReceiver` in logcat.
