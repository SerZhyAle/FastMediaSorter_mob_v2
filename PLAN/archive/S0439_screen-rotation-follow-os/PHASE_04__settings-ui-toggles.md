# Phase 04 - Settings UI toggles

**Strategic spec:** [`../S0439_screen-rotation-follow-os.md`](../S0439_screen-rotation-follow-os.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Rename the existing toggle to program scope, repurpose the duplicate playback-group toggle into a new player-scope toggle shown only when the program toggle is off, and supply trilingual strings.

> Resolves the live duplicate (`@+id/rowFollowSystemRotation` exists in both `fragment_settings_destinations.xml` and `fragment_settings_playback.xml`): destinations stays the program toggle, playback becomes the player toggle.

---

## Prerequisites

- [ ] Phase 01 and Phase 03 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | - |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 1500 |

> Program toggle in `fragment_settings_destinations.xml` (+ `layout-land`) needs no layout edit: it references `@string/setting_follow_system_rotation_title` by key, and `OperationsSettingsFragment` already persists `programFollowSystemRotation` after Phase 01. Only the string value changes (Step 04.1).

---

## Steps

### Step 04.1 - Trilingual strings: program rename + new player title

**Files:** `res/values/strings_settings.xml`, `res/values-ru/strings_settings.xml`, `res/values-uk/strings_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the value of the existing key `setting_follow_system_rotation_title` to the program-scoped wording (keep the key). Add a new key `setting_follow_system_rotation_player_title` for the player toggle. Use `scripts/utils/set-android-string.ps1`: `-Action set -Key setting_follow_system_rotation_title` per locale with `-ExpectedOldValue` guard, then `-Action add -Key setting_follow_system_rotation_player_title -En .. -Ru .. -Uk ..` (lockstep). Suggested values (final wording subject to UX): program EN "Rotate program screen with OS auto-rotate" / RU "Поворачивать экран программы вслед за ОС" / UK "Повертати екран програми услід за ОС"; player EN "Rotate player screen with OS auto-rotate" / RU "Поворачивать экран плеера вслед за ОС" / UK "Повертати екран плеєра услід за ОС". Keep the new key grouped in `strings_settings.xml` (hand-move if the tool appends to `strings.xml`).

**Verification:**

- `Grep` - `setting_follow_system_rotation_player_title` present in all three `strings_settings.xml` locales.
- `Grep` - `Поворачивать экран программы вслед за ОС` present in `values-ru/strings_settings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_follow_system_rotation"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS (parity OK for all 4 keys EN/RU/UK; Cyrillic intact). Reworded program title/summary (whole-app scope) and added player title/summary; both title AND summary keys handled (toggle uses str_title + str_subtitle). Hand-edited XML to avoid bash->pwsh Cyrillic mojibake.

---

### Step 04.2 - Repurpose the playback toggle row into the player toggle (portrait + landscape)

**Files:** `res/layout/fragment_settings_playback.xml`, `res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> In both the portrait and landscape `fragment_settings_playback.xml`, the toggle currently at `@+id/rowFollowSystemRotation` (container `@+id/layoutFollowSystemRotation`, inside the "Player interface and commands" group `@+id/containerPlayerUI`; landscape places it inside `@+id/containerFullscreenAndRotation`) becomes the player toggle: rename its id to `@+id/rowFollowSystemRotationPlayer` and the container to `@+id/layoutFollowSystemRotationPlayer`, and change its title attribute to `@string/setting_follow_system_rotation_player_title`. Keep it in the same group/position. Do not introduce hardcoded colors - reuse the existing `SettingsToggleRow` styling.

**Verification:**

- `Grep` - `rowFollowSystemRotationPlayer` present in both `layout/` and `layout-land/` `fragment_settings_playback.xml`.
- `Grep` - `setting_follow_system_rotation_player_title` referenced in both layout files.
- `Grep` - `rowFollowSystemRotation"` (old id, exact) returns zero hits in both playback layout files.
- `Grep` - no `="#` hex color added in the edited rows.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS. Portrait + landscape playback layouts: row/layout ids renamed to *Player, str_title/str_subtitle repointed to player keys. Old exact ids gone from playback. No hardcoded colors.

---

### Step 04.3 - Bind the player toggle and couple its visibility

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Update `PlaybackSettingsFragment` to drive the renamed binding fields `layoutFollowSystemRotationPlayer` / `rowFollowSystemRotationPlayer`. The checked-change listener persists the player flag: `viewModel.updateSettings(current.copy(playerFollowSystemRotation = isChecked))`. The settings observer sets `binding.rowFollowSystemRotationPlayer.setCheckedSilently(settings.playerFollowSystemRotation)` and couples visibility: `binding.layoutFollowSystemRotationPlayer.isVisible = hasAccelerometer && !settings.programFollowSystemRotation`. Keep the accelerometer-gated listener registration. Confirm `OperationsSettingsFragment` still owns the program toggle (`programFollowSystemRotation`) with `hasAccelerometer`-only visibility - no change needed there.

**Verification:**

- `Grep` - `playerFollowSystemRotation = isChecked` present in `PlaybackSettingsFragment.kt`.
- `Grep` - `hasAccelerometer && !settings.programFollowSystemRotation` present (visibility coupling).
- `Grep` - `rowFollowSystemRotationPlayer` and `layoutFollowSystemRotationPlayer` present.
- `Grep -n "Log\.d\("` returns zero hits in `PlaybackSettingsFragment.kt`.

**Status:** `[ ]` not done

---

### Step 04.4 - Compile gate

**Files:** -
**Depends on:** Step 04.1, 04.2, 04.3

**Prompt for developer:**

> Build the standard debug variant to prove the renamed binding ids, new string, and visibility logic resolve. Use `/build`.

**Verification:**

- `/build` - standard debug compiles, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - `a.ps1 fc` PASS (code + resources). Renamed ViewBinding fields resolve, new string keys present, visibility coupling compiles. Program toggle (destinations) persists programFollowSystemRotation; player toggle (playback) persists playerFollowSystemRotation; landscape edited in lockstep.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Program toggle (destinations) persists `programFollowSystemRotation`; player toggle (playback) persists `playerFollowSystemRotation`; the old shared id no longer exists in the playback layouts.
- [ ] Landscape `fragment_settings_playback.xml` edited in lockstep with portrait.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_follow_system_rotation"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- The feature is functionally complete: program toggle governs all windows, player toggle overrides only when program is off and is hidden otherwise.
- Phase 05 handles FEATURES trilingual, catalog regen, dev log closure.

---

## Rollback Plan

Revert phase commit(s). The playback row reverts to the (duplicate) program toggle; strings revert. No data surface touched.
