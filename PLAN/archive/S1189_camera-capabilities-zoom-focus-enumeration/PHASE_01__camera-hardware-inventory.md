# Phase 01 - Camera hardware inventory in System info

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-07-25
**Completed:** 2026-07-25

---

## Objective

Add a read-only "Cameras" section to the System info report listing every camera the platform declares, its physical sub-lenses, zoom range, focus distance, focal lengths and photo output sizes, so a multi-lens device can be diagnosed without a development machine.

---

## Prerequisites

- [ ] Strategic spec `Status:` is `Tactical` or later.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` exists and builds base sections in `buildSections()`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/CameraHardwareInventory.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraHardwareDataSource.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherCameraDiagnosticsUseCase.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 440 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> No layout file is touched - the report renders through the existing grouped-text formatter, so there is no portrait/landscape counterpart to mirror.

---

## Steps

### Step 01.1 - Add the camera inventory model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/CameraHardwareInventory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraHardwareInventory` as a pure data model with no Android imports beyond `android.util.Size` and `android.util.Range`. It holds a list of `CameraHardwareEntry`, each carrying: camera id, lens facing, whether the entry is a physical sub-lens and of which logical camera, focal lengths in mm, zoom ratio range, minimum focus distance in diopters, focus-distance calibration, hardware level, the largest JPEG output size and the largest high-resolution output size. Every numeric field must be nullable or carry an explicit "unknown" sentinel so a device that refuses a characteristic is representable.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/CameraHardwareInventory.kt` exists.
- `Grep` - `data class CameraHardwareInventory` matches exactly once.
- `Grep` - `data class CameraHardwareEntry` matches exactly once.
- `Grep` - `import android.hardware.camera2` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS. Files: domain/model/CameraHardwareInventory.kt (+38 LOC).

---

### Step 01.2 - Read the inventory from Camera2

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraHardwareDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `CameraHardwareDataSource` with an `@Inject constructor(@ApplicationContext context: Context)` that reads `CameraManager.getCameraIdList()` and, for each id, its `CameraCharacteristics`, then walks `CameraCharacteristics.getPhysicalCameraIds()` on API 28+ to add one entry per physical sub-lens. Read `CONTROL_ZOOM_RATIO_RANGE` on API 30+ and fall back to `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM` below it; read high-resolution output sizes unconditionally (available since API 23). Every read is individually guarded so a refusing characteristic yields the model's unknown sentinel and never propagates an exception out of the data source. Return `CameraHardwareInventory`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraHardwareDataSource.kt` exists.
- `Grep` - `class CameraHardwareDataSource @Inject constructor` matches exactly once.
- `Grep` - `physicalCameraIds` present (Kotlin sees the Java `getPhysicalCameraIds()` getter as a property).
- `Grep` - `Build.VERSION.SDK_INT` present at least twice (API 28 / 30 guards).
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 5/5 PASS. Files: data/capture/CameraHardwareDataSource.kt (+124 LOC). Predicate corrected from the Java getter name `getPhysicalCameraIds` to the Kotlin property `physicalCameraIds`.
- 2026-07-25 - AUDIT-FIX (P1): the high-resolution size read was gated behind API 31, which would have hidden the sensor maximum on every API 26..30 device and silently defeated strategic goal 4. `api-versions.xml` (android-35) reports `StreamConfigurationMap.getHighResolutionOutputSizes(I)` as `since=23`, below both flavor minimums, so the guard was removed and the step prompt plus this predicate were corrected from three SDK_INT guards to two.

---

### Step 01.3 - Add the trilingual section and field labels

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the System-info camera labels in one lockstep call per key using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>`, never by editing the three files by hand. Keys: `sysinfo_section_cameras`, `sysinfo_field_camera_id`, `sysinfo_field_camera_facing`, `sysinfo_field_camera_physical_of`, `sysinfo_field_camera_focal`, `sysinfo_field_camera_zoom_range`, `sysinfo_field_camera_focus_distance`, `sysinfo_field_camera_max_photo`, `sysinfo_field_camera_max_photo_high_res`. Match the wording register of the existing `sysinfo_*` keys and check the new strings against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `sysinfo_section_cameras` matches exactly once in each of the three `strings.xml` files.
- `Grep` - `sysinfo_field_camera_zoom_range` matches exactly once in each of the three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sysinfo_field_camera"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS (parity audit: 8/8 keys OK in EN/RU/UK, exit 0). Files: values/values-ru/values-uk strings.xml (+9 keys each). Added via `temp/S1189/add-strings-phase01.ps1` because Cyrillic cannot cross the Bash -> pwsh argument boundary intact.

---

### Step 01.4 - Format the inventory into a report section

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherCameraDiagnosticsUseCase.kt`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Create `GatherCameraDiagnosticsUseCase` with an `@Inject constructor` taking `@ApplicationContext Context` and `CameraHardwareDataSource`. Its `operator fun invoke(): SystemInfoSection` maps the inventory to one section titled by `sysinfo_section_cameras`, emitting one field group per camera entry with the labels added in Step 01.3. Prefix a physical sub-lens field with its logical parent id so the tree is readable in flat text. An entry that reports nothing usable still emits its id and facing rather than being dropped.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherCameraDiagnosticsUseCase.kt` exists.
- `Grep` - `class GatherCameraDiagnosticsUseCase @Inject constructor` matches exactly once.
- `Grep` - `SystemInfoSection` present.
- `Grep` - `R.string.sysinfo_section_cameras` present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS. Files: domain/usecase/GatherCameraDiagnosticsUseCase.kt (+86 LOC).

---

### Step 01.5 - Append the section to the System info report

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Inject `GatherCameraDiagnosticsUseCase` into `GatherSystemInfoUseCase` and append its section to the list returned by `buildSections()`, placed after the hardware section. Wrap the call in the same defensive style the file already uses for extended contributors so a failing camera read degrades the section to empty instead of breaking the whole report.

**Verification:**

- `Grep` - `GatherCameraDiagnosticsUseCase` present in `GatherSystemInfoUseCase.kt`.
- `Grep` - `private val cameraDiagnostics` (or the chosen constructor property name) present in the constructor parameter list.
- `Grep` - `catch (e: Exception)` count in `GatherSystemInfoUseCase.kt` increased by at most one versus before the step.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. Files: domain/usecase/GatherSystemInfoUseCase.kt (+8 LOC, 429 total). `catch (e: Exception)` count unchanged at 3 - the new section reuses the file's existing `safeList` guard rather than adding a fourth catch.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The device can now report its full camera tree without a development machine. Phase 02 consumes the same Camera2 facts but through the capture screen's own probe - do not make the capture path depend on this data source, the two read the platform independently on purpose (the diagnostics section must keep working even when the capture screen refuses to bind).

---

## Rollback Plan

Revert the phase commit(s) - three new files plus one injection point and additive string keys; no data migration and no change to an existing user-facing surface.
