# Research 02 - Spectrum version reconciliation

**Strategic item:** §6.2
**Status:** Resolved
**Date:** 2026-06-10

## Question

Each per-flavor release builder self-stamps `versionCode`/`versionName` from the launch timestamp, so flavors built minutes apart embed different versions and cannot share one release tag cleanly. How to make every asset of one release carry a single version?

## Finding

`scripts/builders/build-and-push-all.ps1` already assembles the whole spectrum in one run:
- Pass 1 (`-Pchaquopy.enabled=false`): standard/lite/photos/legacy/vr release + `:wear:assembleRelease`.
- Pass 2 (`-Pchaquopy.enabled=true`): noLegal release.

Crucially it does NOT rewrite `versionCode`/`versionName` at all - it builds whatever is currently in `app_v2/build.gradle.kts`. So all `app_v2` flavors built in that single run share one identical version automatically. The only per-flavor self-stamping happens in the individual `build-<flavor>-release.ps1` scripts, which the spectrum run does not call.

The wear module keeps its own `versionCode`/`versionName` in `wear/build.gradle.kts` (a `build-with-version.ps1` is referenced as the sync mechanism), so a uniform spectrum version must stamp BOTH `app_v2` and `wear` once before the spectrum build.

## Decision

Stamp the version ONCE (into both `app_v2` and `wear` build.gradle.kts) before the orchestrated spectrum build, then build all release flavors + wear in one run so they share that version. The publisher reads the (now uniform) `versionName` and uploads all assets under one `v<version>` tag.

Chosen over "normalize version at publish time by renaming files": the version is embedded inside each APK manifest (`versionCode`/`versionName`), not just the filename - renaming the asset would leave the in-APK version skewed. Stamp-once keeps file name and in-APK version consistent.

## Impact on plan

- One phase introduces a single-version stamp covering app_v2 + wear and an orchestrated release-spectrum build that reuses the existing two-pass (Chaquopy) build logic.
- The publisher consumes a directory of uniform-version APKs; no per-asset version juggling.
