# Phase 01 - Foundations: CameraX deps, CAMERA permission, registry entry, strings

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Add the CameraX dependencies, declare the CAMERA permission and optional camera hardware feature in the manifest, register a CAMERA `PermissionEntry` in the registry, and add the foundational strings. No capture behaviour changes yet.

---

## Prerequisites

- [ ] Strategic §6 resolved (Variant 1).
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ +8 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ +4 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ +14 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +6 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +6 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +6 |

---

## Steps

### Step 01.1 - Add CameraX dependencies

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> In the dependencies block, next to the ML Kit cluster (around line 1065), add the four CameraX artifacts: `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`. Use the current stable `1.4.x` line (research baseline `1.4.2`) - verify the latest stable patch on the AndroidX releases page before pinning. No version catalog exists; declare inline as `implementation("androidx.camera:camera-...:<version>")`.

**Verification:**

- `Grep` - `androidx.camera:camera-view` matches once in `app_v2/build.gradle.kts`.
- `Grep` - `androidx.camera:camera-lifecycle` matches once.
- Build: `/build` standardDebug assembles (dependency resolution succeeds). expected: BUILD SUCCESSFUL | actual: <fill>.

**Status:** `[x] done`

---

### Step 01.2 - Declare CAMERA permission and optional camera feature

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `<uses-permission android:name="android.permission.CAMERA" />`. Add `<uses-feature android:name="android.hardware.camera" android:required="false" />` and `<uses-feature android:name="android.hardware.camera.any" android:required="false" />` so camera-less devices (e.g. Quest 3) stay installable. Place near the existing `<uses-feature>` block (around lines 26-34). WHY-comment: required=false keeps Play Store availability for camera-less devices.

**Verification:**

- `Grep` - `android.permission.CAMERA` matches once in `AndroidManifest.xml`.
- `Grep` - `android.hardware.camera` with `required="false"` present.

**Status:** `[x] done`

---

### Step 01.3 - Register CAMERA permission entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 01.4 (strings must exist for the res ids to resolve)

**Prompt for developer:**

> Add a `PermissionEntry` for `Manifest.permission.CAMERA` to `allEntries`: `id = "camera"`, `titleRes = R.string.perm_title_camera`, `descriptionRes = R.string.perm_desc_camera`, `group = PermissionGroup.CAMERA`, `optional = true`, no flavorGates (camera capture exists in all flavors), default minSdk. Add the `PermissionGroup.CAMERA -> R.string.perm_group_camera` branch in `getGroups()` `when(group)` (replaces the silent `else -> 0` fallthrough for CAMERA). `perm_group_camera` is a fallback header; the visual "Optional" grouping is applied in Phase 07.

**Verification:**

- `Grep` - `id = "camera"` matches once in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `PermissionGroup.CAMERA -> R.string.perm_group_camera` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x] done`

---

### Step 01.4 - Add foundational strings (EN/RU/UK lockstep)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three keys in lockstep across EN/RU/UK using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one call per key, parity-enforced `-En -Ru -Uk`): `perm_title_camera`, `perm_desc_camera` (detailed usage description matching the tone of `perm_desc_record_audio`), `perm_group_camera`. Author Style applies (`..` not `...`, `ё`/`Ё`). Strings must pass COMMUNICATION_POLICY §6 tone checklist. Author RU/UK text via the script (UTF-8), do not pass Cyrillic as inline CLI args.

**Verification:**

- `Grep` - `perm_title_camera` in all three `strings.xml` (3 hits).
- `Grep` - `perm_desc_camera` in all three (3 hits).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_title_camera"` exit 0. expected: exit 0 | actual: <fill>.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog regenerated if public API changed (PermissionRegistry signature unchanged - skip unless new symbol added).

---

## Handoff Notes to Next Phase

CAMERA is declared and registered (optional). CameraX is on the classpath. Phase 02 builds the in-app capture screen against these deps.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted state changed. Removing the CAMERA `<uses-permission>` restores the prior permission-free `ACTION_IMAGE_CAPTURE` behaviour.
