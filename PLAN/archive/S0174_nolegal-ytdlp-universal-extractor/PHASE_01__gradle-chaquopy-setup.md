# Phase 01 — Gradle + Chaquopy Setup

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 5 / 5
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Add Chaquopy Python runtime and yt-dlp wheel to the `noLegal` flavor only; set `extractNativeLibs=true` in the noLegal manifest; verify the project compiles without touching other flavors.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none — foundation)
- [ ] `app_v2/build.gradle.kts` is readable and the `noLegal` flavor block is present.
- [ ] `gradle/libs.versions.toml` is readable.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 600 (currently ~500; add ~20 lines) |
| `app_v2/src/noLegal/AndroidManifest.xml` | New | ≤ 10 |

---

## Steps

### Step 01.1 — Add Chaquopy Gradle plugin to root buildscript

**Files:** `build.gradle.kts` (root)
**Depends on:** — start of phase

**Prompt for developer:**

> Add `id("com.chaquo.python") version "17.0.0" apply false` to the `plugins {}` block of the root `build.gradle.kts`. Use 17.0.0 — first version supporting AGP 9.x / Gradle 9.x. Do not apply it yet — that happens in the app module.

**Verification:**

- `Grep` — `com.chaquo.python` matches in `build.gradle.kts` (root).
- `Grep` — `apply false` is on the same line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. Files: build.gradle.kts (+2 LOC). Dev log recorded.

---

### Step 01.2 — Apply Chaquopy plugin in app_v2 module

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, add `id("com.chaquo.python")` to the `plugins {}` block (without `apply false` — apply it here). The plugin hooks into all variants at the module level; yt-dlp packages are restricted to `noLegal` in Step 01.4.

**Verification:**

- `Grep` — `id("com.chaquo.python")` present in `app_v2/build.gradle.kts` without `apply false`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 1/1 PASS. Files: app_v2/build.gradle.kts (+3 LOC). Dev log recorded.
- 2026-05-12 — PATCHED: `id("com.chaquo.python")` in `plugins {}` causes Chaquopy to validate all variants including `legacy` (minSdk=23 < 24) and `standard`/`lite`/`photos` (armeabi-v7a not supported by Python 3.12). There is no Kotlin-DSL `variantFilter` in Chaquopy 17.x. Solution: `apply(plugin = "com.chaquo.python")` is now called inside `if (isNoLegalBuild)` guard with `beforeVariants { enable = false }` for all non-noLegal flavors. Both constraints resolved without breaking other flavors or their minSdk.

---

### Step 01.3 — Configure default Python version in android block

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inside the `android {}` block in `app_v2/build.gradle.kts`, add the Chaquopy `defaultConfig` Python configuration block:
>
> ```kotlin
> defaultConfig {
>     // existing defaultConfig content unchanged
>     python {
>         version = "3.11"  // Chaquopy 16.x ships CPython 3.8, 3.11
>         buildPython("python3")  // adjust if system Python is elsewhere
>     }
> }
> ```
>
> The `version` pin must be inside `defaultConfig`, not inside a `productFlavor` block (Chaquopy requirement). `buildPython` points to the host Python used by Chaquopy at build time to resolve wheels.

**Verification:**

- `Grep` — `python {` appears inside the `defaultConfig` section of `app_v2/build.gradle.kts`.
- `Grep` — `version = "3.11"` present in that block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. Files: app_v2/build.gradle.kts (+7 LOC). Dev log recorded.
- 2026-05-12 — PATCHED: `python {}` inside `android { defaultConfig {} }` is Groovy-only. With `android.newDsl=false` and Kotlin DSL, Chaquopy DSL must be configured via `configure<ChaquopyExtension>` top-level block. Moved accordingly.

---

### Step 01.4 — Restrict yt-dlp wheel to noLegal flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.3

**Prompt for developer:**

> Inside the `noLegal` product flavor block in `app_v2/build.gradle.kts`, add a `python` DSL block that installs yt-dlp. Use a pinned version to prevent silent breakage on CI:
>
> ```kotlin
> create("noLegal") {
>     // existing noLegal config unchanged
>     python {
>         pip {
>             install("yt-dlp==2025.4.30")
>         }
>     }
> }
> ```
>
> Do not add `pip { install(...) }` to `standard`, `lite`, `photos`, or `legacy` flavor blocks.

**Verification:**

- `Grep` — `yt-dlp==` appears exactly once in `app_v2/build.gradle.kts` and is inside the `noLegal` block.
- `Grep` — `yt-dlp` does NOT appear in `standard`, `lite`, `photos`, or `legacy` blocks.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. Files: app_v2/build.gradle.kts (+6 LOC). Dev log recorded.

---

### Step 01.5 — Add extractNativeLibs=true to noLegal manifest overlay

**Files:** `app_v2/src/noLegal/AndroidManifest.xml` (New)
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `app_v2/src/noLegal/AndroidManifest.xml` with the following content. This overlay sets `android:extractNativeLibs="true"` for the noLegal flavor only — required for Chaquopy to unpack `libpython3.so` from the APK. The `<manifest>` element is empty except for the `<application>` attribute override; AGP merges it with the main manifest.
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <manifest xmlns:android="http://schemas.android.com/apk/res/android"
>     xmlns:tools="http://schemas.android.com/tools">
>
>     <application
>         android:extractNativeLibs="true"
>         tools:replace="android:extractNativeLibs" />
>
> </manifest>
> ```

**Verification:**

- `Glob` — `app_v2/src/noLegal/AndroidManifest.xml` exists.
- `Grep` — `extractNativeLibs="true"` present in that file.
- `Grep` — `tools:replace="android:extractNativeLibs"` present (required by manifest merger to allow override).

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: app_v2/src/noLegal/AndroidManifest.xml (New, 15 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — run `/build` → `noLegalDebug` — BUILD SUCCESSFUL (2026-05-12, `--project-prop chaquopy.enabled=true`).
- [x] `standard` debug build still compiles without Chaquopy errors — BUILD SUCCESSFUL (2026-05-12).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes: Chaquopy plugin applied to `app_v2`; yt-dlp wheel pinned to `noLegal` only; `extractNativeLibs=true` in noLegal manifest overlay. Phase 02 may now modify `LinkExtractionRegistry` to introduce the `"ytdlp"` priority slot.

---

## Rollback Plan

Revert the two Gradle file changes and delete `app_v2/src/noLegal/AndroidManifest.xml`. No data migration or user-facing surface changed.
