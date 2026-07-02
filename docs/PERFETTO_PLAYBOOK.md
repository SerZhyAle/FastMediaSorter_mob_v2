# Macrobenchmark, Baseline Profiles, and Perfetto Playbook

## Scope

- S0722 covers `app_v2` `standard` only in the first iteration.
- Prefer a physical Android 13+ device for representative numbers.
- The benchmark module may run on an emulator for bootstrap smoke, but do not treat emulator timings as a release threshold.

## Local Commands

```powershell
.\a.ps1 mb
.\a.ps1 gbp
```

- `mb` runs `:benchmark:connectedBenchmarkReleaseAndroidTest`.
- `gbp` runs `:benchmark:collectNonMinifiedReleaseBaselineProfile`.

## Expected Outputs

- Gradle-connected benchmark runs copy JSON results and Perfetto traces to `benchmark/build/outputs/connected_android_test_additional_output/<variant>/connected/<device_id>/`.
- In this repo, `.\a.ps1 mb` is expected to use the `benchmarkReleaseAndroidTest` variant.
- The benchmark library also writes JSON on-device into the benchmark APK external media directory and prints the exact file path to Logcat.
- Baseline Profile generator HRF output is produced in the same `benchmark/build/outputs/...` tree before plugin copy.
- The plugin-managed Baseline Profile is expected under `app_v2/src/nonMinifiedRelease/generated/baselineProfiles/`.

## Covered Journeys

- cold start: `StartupBenchmarks`
- browse ready: `TraceSectionMetric("FMS_BROWSE_READY")`
- player ready: `TraceSectionMetric("FMS_PLAYER_READY")`
- player back navigation: `TraceSectionMetric("FMS_PLAYER_BACK_NAVIGATION")`

## Regression Thresholds

Until the first clean device baseline is committed, use the first successful physical-device JSON as the reference run and store it in `temp/` or the spec notes.

Treat the run as regressed when the median grows by more than the larger of:

- cold start (`timeToInitialDisplayMs` or `timeToFullDisplayMs`): `15%` or `120 ms`
- browse ready: `20%` or `80 ms`
- player ready: `20%` or `120 ms`
- player back navigation: `20%` or `60 ms`

Treat a sustained `FrameTimingMetric` jank increase as escalation evidence even when the section metric stays within threshold.

## When to Escalate to Perfetto

- the benchmark delta crosses a threshold
- startup or player feels slower but benchmark numbers are noisy
- frame timing regresses without an obvious code owner
- a release-only slowdown, ANR risk, or unexplained main-thread stall remains

## Perfetto Workflow

1. Prepare the same media state used by the benchmark journey.
2. Capture a 10-15 second System Trace / Perfetto trace while reproducing the affected journey.
3. Align the trace with the app markers:
   - startup -> `reportFullyDrawn()`
   - browse -> `FMS_BROWSE_READY`
   - player -> `FMS_PLAYER_READY`
   - back -> `FMS_PLAYER_BACK_NAVIGATION`
4. Inspect the trace for main-thread stalls, binder bursts, decode churn, repeated thumbnail or media loading, and coroutine wakeups near the marker.
5. If Perfetto still does not explain the delta, move to allocation tracing or a focused code audit.

## Current Blocker

- Without an online device or emulator, `.\a.ps1 mb` and `.\a.ps1 gbp` cannot produce runtime evidence. Keep S0722 in `BlockExternal` until device proof lands.
