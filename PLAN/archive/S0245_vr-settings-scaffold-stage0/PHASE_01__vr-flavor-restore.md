# Phase 01 — Restore `vr` productFlavor + paired source sets

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Recreate the `vr` productFlavor (removed by S0241), introduce the paired source-set pattern (`src/vr/` real vs `src/vrStub/` no-op) mirroring `cloudEnabled/cloudDisabled`, and add a minimal `src/vr/AndroidManifest.xml` declaring XR device features with `required="false"` so a single-APK distribution stays viable per R-10.

---

## Prerequisites

- [x] S0241 (vr-stack-removal-plan) is `Verified` — old VR stack is fully purged.
- [x] S0244 (vr-preliminary-research) is `Verified` — R-05/R-06/R-07/R-09 documented.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 900 |
| `app_v2/src/vr/AndroidManifest.xml` | New | ≤ 80 |
| `app_v2/src/vr/java/.gitkeep` | New | 0 |
| `app_v2/src/vrStub/java/.gitkeep` | New | 0 |

> `build.gradle.kts` is at ~843 lines today; this phase adds ~30 lines → projected ~873 lines, still under 1500 LOC budget. No backup required.

---

## Steps

### Step 01.1 — Add `vr` productFlavor block in `build.gradle.kts`

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts` inside `productFlavors { … }` (right after the `create("legacy") { … }` block and before the closing brace of `productFlavors`), insert a new `create("vr") { … }` block with the BuildConfig flags listed below. Replace the existing comment line `// S0241: vr and vrUnlicensed flavors removed entirely with the OpenXR / immersive stack.` and adjust to reflect S0245 reintroduction. Configuration (per R-05 / R-07 / R-09):
>
> ```kotlin
> // ===== VR (Meta Quest + Android XR, single-APK target) =====
> // S0245 Stage 0: flavor reintroduced as scaffold. SUPPORT_VR_PLAYER stays false until
> // Stage 1 wires the real OpenXR runtime. minSdk=29 per R-07 (Quest requirement).
> // applicationIdSuffix deliberately omitted — shared with standard until Meta Store
> // submission is scoped by a separate spec (S0232 policy: store-published flavors carry
> // a suffix, scaffold-only flavors do not).
> create("vr") {
>     dimension = "version"
>     minSdk = 29
>     versionNameSuffix = "-VR"
>     ndk {
>         abiFilters += listOf("arm64-v8a")
>     }
>     buildConfigField("boolean", "SUPPORT_VIDEO", "true")
>     buildConfigField("boolean", "SUPPORT_AUDIO", "true")
>     buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
>     buildConfigField("boolean", "SUPPORT_IMAGES", "true")
>     buildConfigField("boolean", "SUPPORT_CLOUD", "true")
>     buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
>     buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
>     buildConfigField("boolean", "ENABLE_EPUB", "true")
>     buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
>     buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
>     buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
>     buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // No Wear OS pairing on Quest
>     buildConfigField("boolean", "ENABLE_DTS_DECODER", "true")
>     buildConfigField("boolean", "SUPPORT_CAST", "false")            // Quest has no GMS Cast
>     buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")       // Stage 0: not wired yet
> }
> ```
>
> Also add `buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")` to every other flavor block (`standard`, `noLegal`, `lite`, `photos`, `legacy`) for binding parity. Insert it alongside the existing `SUPPORT_CAST` field.

**Verification:**

- `Grep` — `create\(\"vr\"\)` matches once in `app_v2/build.gradle.kts`.
- `Grep` — `SUPPORT_VR_PLAYER` matches at least 6 times (once per flavor).
- `Grep` — `minSdk = 29` matches once in the `vr` flavor block.

**Status:** `[ ]` not done

---

### Step 01.2 — Map paired source sets in `sourceSets` block

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the existing `sourceSets { … }` block in `app_v2/build.gradle.kts`: each flavor `getByName` entry adds a directory pointing to either `src/vrStub/java` (no-op) or `src/vr/java` (real). The `vr` flavor auto-mounts `src/vr/java/` and `src/vr/res/` and `src/vr/AndroidManifest.xml` by AGP convention — no explicit entry needed. Add only:
>
> ```kotlin
> getByName("standard") {
>     java.directories.add("src/streamingEnabled/java")
>     java.directories.add("src/cloudEnabled/java")
>     java.directories.add("src/vrStub/java")        // NEW (S0245)
> }
> getByName("noLegal") {
>     java.directories.add("src/streamingEnabled/java")
>     java.directories.add("src/cloudEnabled/java")
>     java.directories.add("src/vr/java")            // NEW (S0245): inherits real VR
> }
> getByName("legacy") {
>     java.directories.add("src/streamingEnabled/java")
>     java.directories.add("src/cloudEnabled/java")
>     java.directories.add("src/vrStub/java")        // NEW (S0245)
> }
> getByName("photos") {
>     java.directories.add("src/streamingDisabled/java")
>     java.directories.add("src/cloudEnabled/java")
>     java.directories.add("src/vrStub/java")        // NEW (S0245)
> }
> getByName("lite") {
>     java.directories.add("src/streamingDisabled/java")
>     java.directories.add("src/cloudDisabled/java")
>     java.directories.add("src/vrStub/java")        // NEW (S0245)
> }
> ```
>
> The `noLegal` mount of `src/vr/java/` makes `noLegal` inherit every VR class — symmetric with strategic §1 ("В vr / noLegal блок есть"). No `vr` flavor entry is added because AGP auto-mounts by convention.

**Verification:**

- `Grep` — `src/vrStub/java` matches exactly 4 times in `build.gradle.kts` (standard, legacy, photos, lite).
- `Grep` — `src/vr/java` matches exactly once (in the `noLegal` block).

**Status:** `[ ]` not done

---

### Step 01.3 — Create empty source-set placeholder dirs

**Files:** `app_v2/src/vr/java/.gitkeep`, `app_v2/src/vrStub/java/.gitkeep`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create two empty placeholder files so the directory structure can be committed before any Kotlin source lands. Both are zero-byte:
>
> - `app_v2/src/vr/java/.gitkeep`
> - `app_v2/src/vrStub/java/.gitkeep`
>
> Do not add `.gitkeep` files in nested package directories — only at the source-root level.

**Verification:**

- `Glob` — `app_v2/src/vr/java/.gitkeep` exists.
- `Glob` — `app_v2/src/vrStub/java/.gitkeep` exists.

**Status:** `[ ]` not done

---

### Step 01.4 — Author minimal `src/vr/AndroidManifest.xml`

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `app_v2/src/vr/AndroidManifest.xml` with the minimal XR device declarations. Stage 0 deliberately omits the `com.oculus.intent.category.VR` activity registration — that comes in Stage 1 with the real OpenXR runtime. Per R-06 / R-07 / R-10: all XR features are `required="false"` so the single-APK strategy keeps phone-Play visibility.
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <manifest xmlns:android="http://schemas.android.com/apk/res/android"
>     xmlns:tools="http://schemas.android.com/tools">
>
>     <!-- S0245 Stage 0: device feature declarations only. No XR runtime, no immersive
>          activity. The `com.oculus.intent.category.VR` filter and OpenXR loader landing
>          come in Stage 1 with the real XR host. -->
>
>     <!-- Quest -->
>     <uses-feature
>         android:name="android.hardware.vr.headtracking"
>         android:required="false"
>         android:version="1" />
>
>     <!-- Android XR -->
>     <uses-feature
>         android:name="android.software.xr.api.openxr"
>         android:required="false" />
>     <uses-feature
>         android:name="android.hardware.xr.input.controller"
>         android:required="false" />
>     <uses-feature
>         android:name="android.hardware.xr.input.hand_tracking"
>         android:required="false" />
>
>     <application>
>         <meta-data
>             android:name="com.oculus.supportedDevices"
>             android:value="quest3|quest3s" />
>     </application>
> </manifest>
> ```
>
> Do NOT add `<uses-permission>` for hand-tracking or camera yet — those follow the runtime when it lands in Stage 1+.

**Verification:**

- `Glob` — `app_v2/src/vr/AndroidManifest.xml` exists.
- `Grep` — `android.hardware.vr.headtracking` present.
- `Grep` — `com.oculus.supportedDevices` present.
- `Grep` — `com.oculus.intent.category.VR` is **absent** (Stage 0 invariant).

**Status:** `[ ]` not done

---

### Step 01.5 — Build `assembleVrDebug` (compile-only check, no source yet)

**Files:** —
**Depends on:** Step 01.4

**Prompt for developer:**

> Trigger the build via `/build` (do not invoke gradle directly): `./a.ps1 dq` is not sufficient — request a `vr debug` build explicitly. The expected outcome is a green compile with zero VR-specific Kotlin sources yet (only manifest + BuildConfig). If the build fails because Chaquopy refuses non-noLegal variants, verify the `vr` flavor name does not match the `noLegal` substring used by the auto-detection guard (it does not — substring check is case-insensitive `"noLegal"`). If lint complains about empty `src/vr/java/`, accept and proceed.

**Verification:**

- Build exit code 0 (recorded in `dev/CHANGELOG.md` post-build entry).
- `Glob` — `app_v2/build/outputs/apk/vr/debug/FastMediaSorter_vr_debug_v*.apk` exists.

**Status:** `[ ]` not done

---

### Step 01.6 — Build `assembleStandardDebug` (regression guard)

**Files:** —
**Depends on:** Step 01.5

**Prompt for developer:**

> Build standard debug to make sure the new BuildConfig field `SUPPORT_VR_PLAYER` is exposed on every flavor and the `vrStub` source-set mount does not break compilation. The standard APK should compile with no `src/vr/java/` content reachable (because `src/vrStub/java/` is empty at this phase).

**Verification:**

- Build exit code 0.
- `Glob` — `app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_v*.apk` exists.
- Inspect `BuildConfig.java` for the variant: `Grep` `SUPPORT_VR_PLAYER` in `app_v2/build/generated/source/buildConfig/standard/debug/...` returns the constant `= false`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `assembleVrDebug` and `assembleStandardDebug` both succeed.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` does not need regeneration yet (no Kotlin source added).

---

## Handoff Notes to Next Phase

`vr` flavor compiles. Source-set directories are mounted. Phase 02 can now place contract interfaces in `src/main/java/.../core/xr/` and immediately start populating `src/vr/java/` and `src/vrStub/java/`.

---

## Rollback Plan

Revert the `build.gradle.kts` block + delete the four new `.gitkeep` / manifest files. No data migration, no user-visible surface.
