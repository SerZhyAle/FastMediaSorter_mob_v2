# Phase 02 - Per-profile default for the Streams flag

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

## Step Log

- 2026-06-21 - Steps 02.1-02.4 Verification PASS. `check_device_profile_presets.ps1` exit 0 (171 fields = 171 CSV rows). Targeted `ApplyProfilePresetUseCaseTest` BUILD SUCCESSFUL (new test `applies enableStreams per device profile` green; PHOTO_FRAME enum confirmed). Dev logs batched at Phase 07.

---

## Objective

Make the first-run device profile choose the `enableStreams` default: ON for every profile except `photo_frame` and `ebook_reader`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`AppSettings.enableStreams` exists, applied by the preset applier).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | ≤ 280 |
| `dev/DEVICE_PROFILE_PRESET_MATRIX.md` | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyProfilePresetUseCaseTest.kt` | Modified | ≤ 400 |

---

## Steps

### Step 02.1 - Add the `enableStreams` matrix row

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one row keyed `enableStreams`. Column order is `option,personal_smartphone,home_tablet,tv_media_box,car_head_unit,media_player,photo_frame,video_player,audio_player,ebook_reader,vr_headset,Other`. Set `TRUE` for every named profile EXCEPT `photo_frame` and `ebook_reader`, which are `FALSE`; leave the trailing `Other` column empty. Match the existing quoting style (every cell quoted). Place the row near the other streaming rows (`streamingCacheCleanupMode`, `streamingCacheTtlDays`).

**Verification:**

- `Grep` - in `device_profile_presets.csv`, a line starting `"enableStreams"` exists with exactly the pattern `"TRUE","TRUE","TRUE","TRUE","TRUE","FALSE","TRUE","TRUE","FALSE","TRUE",""`.

**Status:** `[x]` done

---

### Step 02.2 - Add the applier branch

**Files:** `data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `applyOverride()`, add `"enableStreams" -> settings.copy(enableStreams = raw.toBool())` next to the existing `"enableTranslation"` / `"enableOcr"` boolean branches. Without this branch the CSV cell would fall through to `else -> skip()` and silently no-op (the exact class of bug S0576 fixed).

**Verification:**

- `Grep` - `"enableStreams" -> settings.copy(enableStreams = raw.toBool())` matches once in `DeviceProfilePresetApplier.kt`.

**Status:** `[x]` done

---

### Step 02.3 - Document the row in the matrix doc

**Files:** `dev/DEVICE_PROFILE_PRESET_MATRIX.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the `enableStreams` field to the matrix documentation in the same format as the surrounding rows: the per-profile ON/OFF values (ON everywhere except photo_frame and ebook_reader) and a one-line meaning ("Streams feature master switch default"). Keep it grouped with the other streaming preset entries.

**Verification:**

- `Grep` - `enableStreams` matches in `dev/DEVICE_PROFILE_PRESET_MATRIX.md`.

**Status:** `[x]` done

---

### Step 02.4 - Extend the preset applier test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyProfilePresetUseCaseTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a test asserting `enableStreams` is applied from the matrix: a streaming-oriented profile (e.g. `tv_media_box`) yields `enableStreams = true`, and `photo_frame` yields `enableStreams = false`. Reuse the existing test harness/fixtures in this file; do not introduce a new test framework.

**Verification:**

- `Grep` - `enableStreams` matches in `ApplyProfilePresetUseCaseTest.kt`.
- Run `./gradlew.bat testStandardDebugUnitTest --tests "*ApplyProfilePresetUseCaseTest"` - the targeted class report shows 0 failures.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- First-run default for `enableStreams` is now profile-driven; nothing downstream re-derives it.

---

## Rollback Plan

Revert the phase commit(s) - the CSV row and applier branch are additive; existing installs are unaffected (the flag only applies on first-run profile selection).
