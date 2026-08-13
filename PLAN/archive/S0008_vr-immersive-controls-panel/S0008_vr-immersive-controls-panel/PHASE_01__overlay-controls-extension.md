# Phase 01 — Overlay Controls Extension

**Strategic spec:** [`../S0008_vr-immersive-controls-panel.md`](../S0008_vr-immersive-controls-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — this is a foundation phase
**Blocks:** Phase 03
**Steps done:** 0 / 7
**Started:** —
**Completed:** —

---

## Objective

Extend `VrControlOverlayManager` with volume step buttons, brightness step buttons, playback speed picker, stereo format indicator, and audio track selector. Raise auto-hide delay to 10 s. All new controls dispatch existing or new `PlaybackCommand`s through the existing `onCommand` callback — no ViewModel changes in this phase. Phase 03 replaces this 2D View overlay with a native GL panel; keep the API (`show()`, `hide()`, `toggle()`, `dispatchCommand()`) stable.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. _(none — start here)_
- [ ] Working tree is clean or changes are on a feature branch.
- [ ] `VrControlOverlayManager.kt` backed up to `temp/` (currently 151 lines — backup required before edit if projected size exceeds 500 lines; see Step 1.1).
- [ ] `PlaybackCommand` enum/sealed class audited: confirm `SetVolume`, `SetBrightness`, `SetPlaybackSpeed`, `SetAudioTrack`, `CycleAudioTrack` are present. Add missing ones in Step 1.2 before touching the overlay.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommand.kt` | Modified | ≤ current + 10 |
| `app_v2/src/main/res/values/strings.xml` | Modified | add ≤ 14 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | add ≤ 14 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | add ≤ 14 keys |

---

## Steps

### Step 1.1 — Backup VrControlOverlayManager before modification

**Files:** `temp/` (write only)
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt` to `temp/VrControlOverlayManager_BACKUP_<YYYYMMDD_HHmm>.kt` before any edits. Verify the copy exists.

**Verification:**

- `Glob` — at least one file matching `temp/VrControlOverlayManager_BACKUP_*.kt` exists.

**Status:** `[ ]` not done

---

### Step 1.2 — Add missing PlaybackCommand entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommand.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Open `PlaybackCommand` (enum or sealed class). If any of the following are absent, add them:
> - `CycleAudioTrack` — cycle to next available audio track.
> - `SetPlaybackSpeed(speed: Float)` — where `speed` ∈ {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f}.
> - `CycleStereoFormat` — cycle through available stereo format overrides.
>
> Do not change existing entries. Use Timber logging if you add a new branch. Avoid `Log.d`.

**Verification:**

- `Grep` — `CycleAudioTrack` found in `PlaybackCommand.kt`.
- `Grep` — `SetPlaybackSpeed` found in `PlaybackCommand.kt`.
- `Grep` — `CycleStereoFormat` found in `PlaybackCommand.kt`.
- `Grep` — `Log\.d(` returns zero hits in `PlaybackCommand.kt`.

**Status:** `[ ]` not done

---

### Step 1.3 — Add string resources (trilingual)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 1.1

**Prompt for developer:**

> Add the following string keys to all three files. EN values are provided; RU and UK translations must be added to their respective files — do not leave placeholders:
>
> | Key | EN |
> |-----|----|
> | `vr_overlay_btn_vol_up` | `Vol +` |
> | `vr_overlay_btn_vol_down` | `Vol -` |
> | `vr_overlay_btn_bright_up` | `Bright +` |
> | `vr_overlay_btn_bright_down` | `Bright -` |
> | `vr_overlay_btn_speed` | `Speed` |
> | `vr_overlay_btn_track` | `Track` |
> | `vr_overlay_btn_format` | `Format` |
> | `vr_overlay_speed_label` | `Speed: %sx` |
> | `vr_overlay_format_label` | `%s` |
>
> RU: Vol+/Vol- → «Громк +/−», Bright+/− → «Ярк +/−», Speed → «Скор.», Track → «Дорожка», Format → «Формат», Speed label → «Скор.: %sx», Format label → «%s».
> UK: analogous Ukrainian translations.

**Verification:**

- `Grep` — `vr_overlay_btn_vol_up` found in `values/strings.xml`.
- `Grep` — `vr_overlay_btn_vol_up` found in `values-ru/strings.xml`.
- `Grep` — `vr_overlay_btn_vol_up` found in `values-uk/strings.xml`.
- `Grep` — `vr_overlay_btn_format` found in `values/strings.xml`.
- `Grep` — `vr_overlay_btn_format` found in `values-ru/strings.xml`.
- `Grep` — `vr_overlay_btn_format` found in `values-uk/strings.xml`.

**Status:** `[ ]` not done

---

### Step 1.4 — Extend VrControlOverlayManager: auto-hide + row structure

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Change `AUTO_HIDE_DELAY_MS` from `5_000L` to `10_000L`.
>
> Refactor `buildRoot()` to produce two horizontal rows instead of one:
> - **Row 1 (primary):** Prev, SeekBack, Play/Pause, SeekFwd, Next, Settings, Exit. (unchanged)
> - **Row 2 (extended):** Vol−, Vol+, Bright−, Bright+, Speed, Track, Format.
>
> Both rows share the same `LinearLayout.HORIZONTAL` style (dark pill background). The root `FrameLayout` stacks them vertically with Row 2 above Row 1, both centred horizontally at the bottom. Extract row-building to a private `buildRow(vararg buttons: Button): LinearLayout` helper.

**Verification:**

- `Grep` — `10_000L` found in `VrControlOverlayManager.kt`.
- `Grep` — `AUTO_HIDE_DELAY_MS` not equal to `5_000L` in that file.
- `Grep` — `buildRow` private function exists in `VrControlOverlayManager.kt`.
- `Grep` — `Log\.d(` returns zero hits in `VrControlOverlayManager.kt`.

**Status:** `[ ]` not done

---

### Step 1.5 — Wire new Row 2 buttons to PlaybackCommands

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt`
**Depends on:** Step 1.4, Step 1.2, Step 1.3

**Prompt for developer:**

> Inside `buildRoot()`, add the Row 2 buttons with their `onClick` lambdas:
> - Vol−/Vol+ → `dispatchAndReschedule(PlaybackCommand.VolumeDown)` / `PlaybackCommand.VolumeUp` (already existed — reuse).
> - Bright−/Bright+ → `PlaybackCommand.BrightnessDown` / `PlaybackCommand.BrightnessUp` (use screen brightness API if `PlaybackCommand` doesn't have these; add a TODO comment if a full command is out of scope for this phase).
> - Speed → `dispatchAndReschedule(PlaybackCommand.SetPlaybackSpeed(nextSpeed()))` where `nextSpeed()` cycles {0.5f, 1.0f, 1.5f, 2.0f}.
> - Track → `dispatchAndReschedule(PlaybackCommand.CycleAudioTrack)`.
> - Format → `dispatchAndReschedule(PlaybackCommand.CycleStereoFormat)`.
>
> Add a private `var currentSpeed = 1.0f` field. Add `updateSpeedLabel(button)` that sets button text to `getString(R.string.vr_overlay_speed_label, currentSpeed)`.

**Verification:**

- `Grep` — `CycleAudioTrack` found as argument in `VrControlOverlayManager.kt`.
- `Grep` — `CycleStereoFormat` found as argument in `VrControlOverlayManager.kt`.
- `Grep` — `currentSpeed` field exists in `VrControlOverlayManager.kt`.
- `Grep` — `SetPlaybackSpeed` found in `VrControlOverlayManager.kt`.
- `Grep` — `Log\.d(` returns zero hits.

**Status:** `[ ]` not done

---

### Step 1.6 — Add stereo format indicator label

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt`
**Depends on:** Step 1.5

**Prompt for developer:**

> Add a `fun updateStereoLabel(label: String)` method that finds and updates the Format button text (or a dedicated `TextView` placed between the two rows) with the current stereo mode string (e.g. `"360° SBS"`, `"VR180"`, `"2D"`). The label is set to `getString(R.string.vr_overlay_format_label, label)`. Store the `TextView`/`Button` reference as a nullable field; guard against null if the overlay is not visible.

**Verification:**

- `Grep` — `fun updateStereoLabel` exists in `VrControlOverlayManager.kt`.
- `Grep` — `vr_overlay_format_label` referenced inside `updateStereoLabel`.
- File size — `VrControlOverlayManager.kt` ≤ 420 lines (`wc -l` or IDE line count).

**Status:** `[ ]` not done

---

### Step 1.7 — Extend PlaybackCommandSet.forVrPlayback()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandSet.kt` (or wherever `forVrPlayback()` lives)
**Depends on:** Step 1.2

**Prompt for developer:**

> Find `PlaybackCommandSet.forVrPlayback()`. Add `CycleAudioTrack`, `SetPlaybackSpeed`, `CycleStereoFormat` to the `available` set so `VrControlOverlayManager.dispatchCommand()` does not filter them out with a warning.

**Verification:**

- `Grep` — `CycleAudioTrack` inside `forVrPlayback()` function body.
- `Grep` — `CycleStereoFormat` inside `forVrPlayback()` function body.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run the `/build` skill (do not invoke gradle directly).
- [ ] `VrControlOverlayManager.kt` ≤ 420 lines.
- [ ] All three string files contain the 9 new keys from Step 1.3.
- [ ] `AUTO_HIDE_DELAY_MS = 10_000L` in `VrControlOverlayManager.kt`.
- [ ] `Grep` for `Log\.d(` in every touched file returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched":

  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt" "feature" "Phase 01: extend VR control overlay with volume/brightness/speed/track/format controls"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommand.kt" "feature" "Phase 01: add CycleAudioTrack, SetPlaybackSpeed, CycleStereoFormat commands"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "feature" "Phase 01: add VR overlay string keys"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "feature" "Phase 01: add VR overlay string keys (RU)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "feature" "Phase 01: add VR overlay string keys (UK)"
  ```

---

## Handoff Notes to Next Phase

- `VrControlOverlayManager` is now the 2D placeholder with full button set. Phase 03 replaces its internals with a GL quad layer while keeping `show()` / `hide()` / `dispatchCommand()` API identical.
- `PlaybackCommand` now includes the three new commands. Phase 05 wires them to `PlayerViewModel`.
- The auto-hide timer is 10 s. Phase 05 verifies this matches strategic spec §3.1.

---

## Rollback Plan

Revert the phase commits. No database migration, no native code, no user-facing schema changed. `PlaybackCommand` additions are backwards-compatible (unused cases are ignored by existing handlers).

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 1 (MD031 blank line before dev-log powershell block in Phase Done Criteria)
  - REVIEW applied: 1 (R1: Step 1.3 verification — "found in all three files" split into 3 explicit Grep predicates)
  - DISCUSS proposed: 0
