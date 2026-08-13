# Phase 03 - paddle-lite-runtime

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Integrate the compiled Paddle-Lite native libraries (`libpaddle_light_api_shared.so` with 16 KB page alignment) and Java API wrappers strictly inside the `noLegal` sourceSet, achieving full compile-time and runtime isolation.

---

## Prerequisites

- [ ] Pre-implementation Blocker: Model conversion to `.nb` is completed.
- [ ] Compiled 16 KB page-aligned `libpaddle_light_api_shared.so` under `arm64-v8a` is verified and available.
- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/jniLibs/arm64-v8a/libpaddle_light_api_shared.so` | New | (binary) |
| `app_v2/src/noLegal/jniLibs/arm64-v8a/libpaddle_lite_jni.so` | New | (binary) |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/ConfigBase.java` | New | ≤ 50 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/CxxConfig.java` | New | ≤ 60 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/MobileConfig.java` | New | ≤ 100 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/PaddlePredictor.java` | New | ≤ 150 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/Tensor.java` | New | ≤ 150 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/Place.java` | New | ≤ 100 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/PowerMode.java` | New | ≤ 50 |
| `app_v2/src/noLegal/java/com/baidu/paddle/lite/PaddleLiteInitializer.java` | New | ≤ 50 |
| `app_v2/build.gradle.kts` | Verified | ≤ 1180 |

---

## Steps

### Step 03.1 - Import JNI shared libraries for arm64-v8a into noLegal

**Files:** `app_v2/src/noLegal/jniLibs/arm64-v8a/libpaddle_light_api_shared.so`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy the precompiled, 16 KB page-aligned `libpaddle_light_api_shared.so` shared library file into the `app_v2/src/noLegal/jniLibs/arm64-v8a/` directory.
> This library is only packaged into `noLegal` APK slices and remains absent in standard/photos/lite flavors.
> Validate page alignment by running: `dumpbin /headers libpaddle_light_api_shared.so` or verify matching tools (e.g. readelf) in `temp/` to ensure LOAD Align equals `0x4000` (16 KB page boundary).

**Verification:**

- `Glob` - `app_v2/src/noLegal/jniLibs/arm64-v8a/libpaddle_light_api_shared.so` exists.
- `VerificationPredicate` - Library has `16 KB` alignment.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: app_v2/src/noLegal/jniLibs/arm64-v8a/libpaddle_light_api_shared.so (+binary). Alignment expected: >=0x4000 | actual: 0x10000. Source: Paddle-Lite v2.14-rc Android armv8 clang c++_static with_extra with_cv.

---

### Step 03.2 - Copy Paddle-Lite Java API wrappers under noLegal sourceSet

**Files:** `app_v2/src/noLegal/java/com/baidu/paddle/lite/MobileConfig.java`, `app_v2/src/noLegal/java/com/baidu/paddle/lite/PaddlePredictor.java`, `app_v2/src/noLegal/java/com/baidu/paddle/lite/Tensor.java`, `app_v2/src/noLegal/java/com/baidu/paddle/lite/Place.java`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add official Paddle-Lite Java wrappers (`MobileConfig.java`, `PaddlePredictor.java`, `Tensor.java`, `Place.java`) to package `com.baidu.paddle.lite` inside `src/noLegal/java/`.
> These classes provide the JNI interface to configure, input, infer, and retrieve output from Paddle-Lite models.
> Placing these classes inside `src/noLegal/` ensures standard compilation does not see them and prevents class path pollution.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/baidu/paddle/lite/PaddlePredictor.java` exists.
- `Grep` - `package com.baidu.paddle.lite` matches.
- `Grep` - `public class PaddlePredictor` matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/noLegal/java/com/baidu/paddle/lite/*.java (+8 Java wrappers), app_v2/src/noLegal/jniLibs/arm64-v8a/libpaddle_lite_jni.so (+binary). Added the full official Java wrapper set because `PaddlePredictor.java` depends on `ConfigBase`, `CxxConfig`, `PowerMode`, and `PaddleLiteInitializer`. JNI alignment expected: >=0x4000 | actual: 0x10000.

---

### Step 03.3 - Configure build.gradle.kts for native JNI packaging

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 03.2

**Prompt for developer:**

> Verify the `app_v2/build.gradle.kts` source sets configuration.
> Ensure that `src/noLegal/jniLibs` is included as a JNI library location exclusively for the `noLegal` variant compilation.
> Verify that the build pipeline packs `libpaddle_light_api_shared.so` for `noLegal` flavor and completely ignores it for other flavors.

**Verification:**

- `Grep` - `productFlavors` or dependencies block containing variant configurations.
- `Grep` - `noLegal` targets arm64-v8a and packages files correctly.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: app_v2/build.gradle.kts (verified, no edit). Expected: noLegal flavor includes arm64-v8a and default `src/noLegal/jniLibs` packaging | actual: `abiFilters += listOf("arm64-v8a", "x86_64")`, `getByName("noLegal")`, and `jniLibs.useLegacyPackaging = false` present.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`. Expected: `:app_v2:assembleNoLegalDebug` exit 0 and `:app_v2:assembleStandardDebug` exit 0 | actual: both exit 0. Logs: `temp/S0288_assembleNoLegalDebug.log`, `temp/S0288_assembleStandardDebug.log`.
- [x] Native library presence in the `noLegal` artifact and absolute absence in `standard` artifact is validated. Expected: noLegal APK contains Paddle `.so`, standard APK has zero Paddle entries | actual: noLegal contains `lib/arm64-v8a/libpaddle_light_api_shared.so` and `lib/arm64-v8a/libpaddle_lite_jni.so`; standard has `NONE`.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Paddle-Lite dynamic runtime and Java API wrappers are successfully integrated into the `noLegal` flavor codebase. Compilation remains isolated.
We are ready to implement `PaddleOcrEngine` using these classes in Phase 04.

---

## Rollback Plan

Revert phase commits. Delete files in `src/noLegal/jniLibs/arm64-v8a/` and `src/noLegal/java/com/baidu/paddle/lite/`.
