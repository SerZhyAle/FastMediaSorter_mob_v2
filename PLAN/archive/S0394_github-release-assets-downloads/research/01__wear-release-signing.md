# Research 01 - Wear OS release signing

**Strategic item:** §6.1
**Status:** Resolved
**Date:** 2026-06-10

## Question

Is the Wear OS release APK signed with the same release key as the main module, or a different one?

## Finding

`wear/build.gradle.kts` declares NO `signingConfigs` block and its `release` buildType sets no `signingConfig`. Therefore `:wear:assembleRelease` emits an **unsigned** release APK (`*-release-unsigned.apk`). An unsigned APK cannot be sideload-installed, so it is not publishable as a usable download asset as-is.

The main module (`app_v2/build.gradle.kts`) defines `signingConfigs.create("release")` sourced from `keystore.properties`, and the `release` buildType binds it. All `app_v2` release flavors (standard, vr, lite, photos, legacy, noLegal) share this one release key - a single expected fingerprint covers all six.

## Decision

Give the wear module a release signing config that reuses the same release keystore (`keystore.properties`), and bind it on the `release` buildType. Once wear is signed with the shared release key, the existing single pinned fingerprint covers the wear asset too - no second fingerprint needed.

The wear module's `compileSdk` / `minSdk` / `targetSdk` / Java-version lines carry "CRITICAL: Do not change" notes; the signing change touches only `signingConfigs` + the `release` buildType and must not alter those lines.

## Impact on plan

- One foundation phase: add release signing to the wear module before any publish work.
- Publisher fingerprint assertion stays single-valued (shared release key).
