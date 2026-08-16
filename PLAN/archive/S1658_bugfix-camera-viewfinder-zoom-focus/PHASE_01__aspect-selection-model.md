# Phase 01 - Aspect selection model

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Introduce `CameraAspectSelection` as the one place that maps the stored `camera_aspect_ratio` int onto a CameraX stream request, and move the persisted default from 4:3 to 16:9. No pipeline, preview or UI behaviour changes yet.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraAspectSelection.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 5 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/CaptureSettingsStore.kt` | Modified | ≤ 5 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraAspectSelectionTest.kt` | New | ≤ 90 |

---

## Steps

### Step 01.1 - Add the `CameraAspectSelection` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraAspectSelection.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class CameraAspectSelection(val storedValue: Int)` with three entries: `RATIO_4_3(AspectRatio.RATIO_4_3)`, `RATIO_16_9(AspectRatio.RATIO_16_9)` and `FULL_SCREEN(FULL_SCREEN_STORED)` where `FULL_SCREEN_STORED = 2`. Give it three members: `cameraXAspectRatio: Int` returning `AspectRatio.RATIO_4_3` for `RATIO_4_3` and `AspectRatio.RATIO_16_9` for the other two; `cropsToScreen: Boolean` true only for `FULL_SCREEN`; and a companion holding `DEFAULT = RATIO_16_9`, `fromStored(value: Int): CameraAspectSelection` falling back to `DEFAULT` on an unknown value, and `photoOptions(available: List<Int>): List<CameraAspectSelection>` mapping probed CameraX ratios onto entries and appending `FULL_SCREEN` when the list contains 16:9. Add a `forMode(videoMode: Boolean)` member returning `RATIO_16_9` in place of `FULL_SCREEN` when `videoMode` is true, every other entry unchanged. Write the two facts a reader cannot see from the code into KDoc: the stored values of the first two entries are deliberately the CameraX constants themselves, so preferences written before this ticket keep their meaning without a migration; and `FULL_SCREEN` is a 16:9 stream shown and saved cropped to the host screen, not a fourth CameraX ratio.

**Why:**

Strategic §3.1 requires the third option to take a stored value that does not collide with the two CameraX constants already in `camera_aspect_ratio`, and to be expanded into "16:9 stream plus a screen crop" in one place rather than across the subsystem.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraAspectSelection.kt` exists.
- `Grep` - `enum class CameraAspectSelection` matches exactly once.
- `Grep` - `FULL_SCREEN` matches in that file.
- `Grep` - `fun fromStored(` and `val DEFAULT` both present.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraAspectSelection added with three entries; AppSettings/CaptureSettingsStore default moved to 16:9 without a ui-package import; 6 JUnit cases pin the stored-value contract

---

### Step 01.2 - Move the persisted default to 16:9

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/CaptureSettingsStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Change `AppSettings.cameraAspectRatio`'s default from `0` to `1`, and change the read fallback in `CaptureSettingsStore.read` from `?: 0` to `?: 1`. Rewrite the S1066 comment above the field to name S1658, the three values the field now takes, and `CameraAspectSelection` as the type that decodes them. Do not import that enum into either file: `AppSettings` is domain and `CaptureSettingsStore` is data, while the enum lives in the camera UI package, so an import would point the dependency up through the layers. Do not touch the key string `camera_aspect_ratio` and do not add a migration: a user who already picked a ratio has a stored value and is unaffected, and a user who never opened the camera settings is exactly who this default is for.

**Why:**

Strategic §3.1 states the default changes from 4:3 to 16:9 by owner ruling, and that this deliberately changes the shape of the photo for everyone who never opened the camera settings.

**Verification:**

- `Grep` - `cameraAspectRatio: Int = 0` returns zero hits in `AppSettings.kt`.
- `Grep` - `cameraAspectRatio: Int = 1` present in `AppSettings.kt`.
- `Grep` - `cameraAspectRatio = preferences[KEY_CAMERA_ASPECT_RATIO] ?: 1` present in `CaptureSettingsStore.kt`.
- `Grep` - `import com.sza.fastmediasorter.ui\.` returns zero hits in both files.
- `Grep` - `KEY_CAMERA_ASPECT_RATIO = intPreferencesKey("camera_aspect_ratio")` still present in `CaptureSettingsStore.kt`.
- `Grep` - `S1658` present in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraAspectSelection added with three entries; AppSettings/CaptureSettingsStore default moved to 16:9 without a ui-package import; 6 JUnit cases pin the stored-value contract

---

### Step 01.3 - Unit-test the selection mapping

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraAspectSelectionTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a JUnit test covering four claims: `fromStored` returns the matching entry for each of the three stored values; `fromStored` of an unrecognised int returns `RATIO_16_9`; `FULL_SCREEN.cameraXAspectRatio` equals `AspectRatio.RATIO_16_9` while `FULL_SCREEN.cropsToScreen` is true and the other two entries' is false; and `photoOptions` appends `FULL_SCREEN` when the probed list carries 16:9 and omits it when the probed list is 4:3 only. Follow the plain-JUnit shape of `CameraRuntimeCapabilitiesTest` in the same package - no Robolectric, no Android runtime.

**Why:**

The stored values of this enum are a persistence contract read back from user preferences, so a silent renumbering would change the meaning of every value already on disk - strategic §3.1 makes the non-colliding third value an explicit requirement.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `class CameraAspectSelectionTest` matches exactly once.
- `Grep` - `@Test` matches at least four times in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraAspectSelection added with three entries; AppSettings/CaptureSettingsStore default moved to 16:9 without a ui-package import; 6 JUnit cases pin the stored-value contract

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` - a new public type ships.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

`CameraAspectSelection` is the only place that knows what the stored int means. Every later phase converts at the boundary (`CameraCaptureActivity` reading `AppSettings`, `CameraCaptureHelperFactory` writing it back) and passes the enum inside the camera subsystem.

---

## Rollback Plan

Revert phase commit(s). No data migration ran: the stored key and its two existing values are untouched, so a revert restores the 4:3 default without leaving unreadable preferences behind.
