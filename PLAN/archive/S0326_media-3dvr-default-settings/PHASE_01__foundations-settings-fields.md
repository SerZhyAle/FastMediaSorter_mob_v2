# Phase 01 - Foundations: settings fields

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Extend the app settings model and its persistence/backup with the new global 3D/VR fields (detection-source flags, ambiguity behavior, default stereo layout, default projection). No detector, coordinator, or UI changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Read existing VR field block in `AppSettings` and `SettingsRepositoryImpl` before adding new keys.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 500 |

> `SettingsRepositoryImpl` is large - take a timestamped backup in `temp/` before editing if it exceeds 500 lines.

---

## Steps

### Step 01.1 - Add global 3D/VR fields to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add new fields to `AppSettings` with safe defaults: `stereoAutoDetectEnabled` (default true), `stereoTrustFilename` (true), `stereoTrustMetadata` (true), `stereoTrustAspectRatio` (false - heuristic off by default, it is the false-positive source), `stereoAmbiguityBestGuess` (false → open as 2D on ambiguity), `stereoDefaultLayout` (the canonical "mono" value of the existing stereo-mode model), `stereoDefaultProjection` (the canonical "flat" value). Reuse the existing stereo-mode model type for layout/projection defaults; do not introduce new enums if the existing model already expresses Mono/SBS/OU and Flat/180/360/Cylinder.

**Verification:**

- `Grep` - `stereoAutoDetectEnabled` present in `AppSettings.kt`.
- `Grep` - `stereoTrustAspectRatio` present and defaulted to `false`.
- `Grep` - `stereoDefaultLayout` and `stereoDefaultProjection` present.
- `Grep -n "Log\.d\("` in `AppSettings.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. Added 7 fields (5 detection flags + stereoDefaultLayout/Projection as StereoMode=MONO). Files: AppSettings.kt (+13 LOC). Dev log recorded.

---

### Step 01.2 - Persist new fields in SettingsRepositoryImpl

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add DataStore keys for every field from 01.1 in the same store as the existing VR settings block. Wire read (map into `AppSettings`) and write (persist on update) following the existing `panelStereoSingleEye` pattern. Keep defaults identical to 01.1.

**Verification:**

- `Grep` - a `KEY_` constant exists for each new field.
- `Grep` - each new field is both read and written (two references per field minimum).
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 3/3 PASS. Added 7 DataStore keys + read + write; StereoMode import; layout/projection parsed via fromKey with AUTO/UNKNOWN→MONO guard. Files: SettingsRepositoryImpl.kt (+27 LOC). Dev log recorded.

---

### Step 01.3 - Round-trip new fields through backup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Include the new fields in backup/restore mapping so they survive export/import, mirroring the existing VR fields. Restored values must fall back to the 01.1 defaults when absent in an older backup.

**Verification:**

- `Grep` - each new field name appears in `BackupMapper.kt`.
- Affected unit test for backup mapping passes (run the backup-mapper unit test class).

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 2/2 PASS. Added 7 nullable fields to BackupData DTO + toBackup (.name) + fromBackup (fromKey, AUTO/UNKNOWN→current) mapping; StereoMode import. `gradlew :app_v2:testStandardDebugUnitTest --tests BackupMapperTest` → BUILD SUCCESSFUL (expected: pass | actual: pass). Files: BackupData.kt (+8 LOC), BackupMapper.kt (+19 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `:app_v2:testStandardDebugUnitTest` compiled standardDebug main + test source sets → BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (model surface changed).

---

## Handoff Notes to Next Phase

New global fields exist and persist, but nothing reads them yet. Phase 02 consumes the detection flags; Phase 03 consumes the default layout/projection.

---

## Rollback Plan

Revert phase commit(s) - additive fields with defaults, no migration, no user-facing surface yet.
