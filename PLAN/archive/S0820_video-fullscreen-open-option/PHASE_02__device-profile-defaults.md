# Phase 02 - Device-profile preset defaults

**Strategic spec:** [`../S0820_video-fullscreen-open-option.md`](../S0820_video-fullscreen-open-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none (Phase 05 waits on all)
**Steps done:** 3 / 3
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Wire `openVideoInFullscreen` into the device-profile preset system so it defaults Off for the "audio player" and "e-book reader" profiles and On for every other named profile, per strategic §5 and research artifact `02__device-profile-defaults-and-migration.md`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `AppSettings.openVideoInFullscreen` exists.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | ≤ 310 (currently ~301) |

---

## Steps

### Step 02.1 - Add the CSV preset row

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** - start of phase

**Prompt for developer:**

> Append a row matching the header order (`option,personal_smartphone,home_tablet,tv_media_box,car_head_unit,media_player,photo_frame,video_player,audio_player,ebook_reader,vr_headset,Other`):
> `"openVideoInFullscreen","TRUE","TRUE","TRUE","TRUE","TRUE","TRUE","TRUE","FALSE","FALSE","TRUE",""`
> On for every named profile except `audio_player` and `ebook_reader` (Off); empty cell for `Other` (no override - falls back to the Phase 01 Kotlin default `true`).

**Verification:**

- `Grep` - `"openVideoInFullscreen","TRUE","TRUE","TRUE","TRUE","TRUE","TRUE","TRUE","FALSE","FALSE","TRUE",""` in the CSV matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 1/1 PASS. Files: app_v2/src/main/assets/device_profile_presets.csv (+1 row).

---

### Step 02.2 - Add the preset-applier branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `"openVideoInFullscreen" -> settings.copy(openVideoInFullscreen = raw.toBool())` to the boolean-branch list inside `applyOverride`, grouped with the other video-related branches (`"showVideoThumbnails"`, `"videoFrameCopyToClipboard"`).

**Verification:**

- `Grep` - `"openVideoInFullscreen" -> settings.copy\(openVideoInFullscreen = raw.toBool\(\)\)` in `DeviceProfilePresetApplier.kt` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 1/1 PASS. Files: DeviceProfilePresetApplier.kt (+1 LOC).

---

### Step 02.3 - Verify CSV/field consistency

**Files:** none - verification-only step

**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` and confirm it reports no missing or orphaned row for `openVideoInFullscreen`.

**Verification:**

- Command exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - `check_device_profile_presets.ps1` exit 0. `openVideoInFullscreen` not among reported gaps (correctly present). Script also surfaced 6 pre-existing, unrelated missing rows (screenRecordingEnabled, screenRecordingDestinationResourceId, screenRecordingDisclosureAccepted, resourceTypeTabCollapsed, programsPanelCollapsed, streamsPanelCollapsed) - out of scope for S0820, parked as S0879 (CLAUDE.md 3.1).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` PASS; force-rerun `compileStandardDebugKotlin --rerun-tasks` confirmed BUILD SUCCESSFUL, 13/13 executed, no errors.
- [x] `Grep` for `Log\.d\(` in `DeviceProfilePresetApplier.kt` returns zero hits.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (batched, see below).

---

## Handoff Notes to Next Phase

New installs and any future device-profile preset re-apply now set `openVideoInFullscreen` correctly per profile. Installs with no applied preset (migration-existing, `Other`) still fall back to the Phase 01 Kotlin default `true`, satisfying strategic §2 goal 3.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - CSV row and one `when` branch, no schema or migration involved.
