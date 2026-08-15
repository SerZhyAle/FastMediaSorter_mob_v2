# Research §6.1 - Perf metrics, tools, thresholds

**Strategic item:** §6.1
**Status:** Resolved
**Date:** 2026-06-17

## Question

What performance checkpoints to measure, with what tool, and what PASS thresholds on an emulator.

## Findings / decisions

Measure four checkpoints, each with a tool that needs no app code change (ADR-2 intact):

1. **Cold start** - `adb shell am start -W -n <pkg>/.ui.main.MainActivity` after `am force-stop`. Parse `TotalTime` (ms) from the `am start -W` output. This is the standard, app-code-free cold-start metric.
2. **List scroll smoothness** - `adb shell dumpsys gfxinfo <pkg> reset` before the scroll, then `adb shell dumpsys gfxinfo <pkg> framestats` after. Derive janky-frame percentage and worst frame time from the `Janky frames` line / histogram. No app instrumentation needed.
3. **Player open (time to first frame)** - wall-clock around the launch: timestamp before `am start` of the standalone/in-app player and the log line confirming first frame / ready. Use the existing `StandalonePlayer[debug]:` launch marker (see research 04) as the start anchor and the player-ready log as the end anchor; fall back to `am start -W TotalTime` when no ready marker exists.
4. **Network / SFTP listing open** - wall-clock from resource open to the `BrowseLoadingManager: COMPLETE - N files loaded and displayed` marker (see research 02). This is the definitive listing-done signal.

## Tooling

- All four use `adb` + `dumpsys` + existing log markers; consumed by `scripts/devtest/prerelease-measure.ps1` (Phase 03).
- `search-log.ps1` extracts the `COMPLETE` and player-ready markers for checkpoints 3-4 (reuse, do not reparse).

## Thresholds (emulator-aware starter set)

Emulators are markedly slower than hardware, so thresholds are relaxed vs strategic §3.3 device-oriented numbers. These are starter limits; refine after the first baseline run and keep them in `prerelease.config.psd1` `Thresholds`.

- `cold-start`: TotalTime ≤ 5000 ms
- `list-scroll`: janky frames ≤ 20 % AND no single frame > 700 ms
- `player-open`: ≤ 4000 ms to first frame / ready
- `network-listing`: ≤ 15000 ms to `COMPLETE` (network + emulator overhead)

A first calibration run records actual values into the run report; the owner ratifies final limits from observed baselines.

## Impact on plan

- Phase 03 `Thresholds` block uses the four keys above.
- Phase 03 `prerelease-measure.ps1` implements the four tool invocations; no app code change.
