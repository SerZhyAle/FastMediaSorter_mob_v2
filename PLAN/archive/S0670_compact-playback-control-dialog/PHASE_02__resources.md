# Phase 02 - Strings and icon resources

**Strategic spec:** [`../S0670_compact-playback-control-dialog.md`](../S0670_compact-playback-control-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Provide the user-visible resources the dialog needs: the renamed "Audio track" tab label, three speed-preset button labels, and a distinct volume icon - all before layout/logic reference them.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_video_player.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/values-ru/strings_video_player.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/values-uk/strings_video_player.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_volume_up.xml` | New | ≤ 15 |

---

## Steps

### Step 02.1 - Rename the Audio tab label to "Audio track"

**Files:** `strings_video_player.xml` (values / values-ru / values-uk)
**Depends on:** - start of phase

**Prompt for developer:**

> Change the existing key `playback_control_tab_audio` value in all three locales using the byte-preserving editor (one `-Action set` call per locale with `-ExpectedOldValue`): EN `Audio` -> `Audio track`; RU `Аудио` -> `Аудиодорожка`; UK `Аудіо` -> `Аудіодоріжка`. Keep it short - it is a vertical-rail tab caption (`COMMUNICATION_POLICY` §6: concise, neutral). Do not touch `playback_control_tab_volume`.

**Verification:**

- `Grep` - `<string name="playback_control_tab_audio">Audio track</string>` in `values/`.
- `Grep` - `Аудиодорожка` in `values-ru/`, `Аудіодоріжка` in `values-uk/`.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. set-android-string set EN/RU/UK in strings_video_player.xml; localization audit OK.

---

### Step 02.2 - Add three speed-preset button labels

**Files:** `strings_video_player.xml` (values / values-ru / values-uk)
**Depends on:** - start of phase

**Prompt for developer:**

> Add three keys via `scripts/utils/set-android-string.ps1 -Action add` (one lockstep call per key, parity-enforced across EN/RU/UK). Labels are numeric and locale-neutral, so use the same value in all three locales: `playback_control_speed_0_5x` = `0.5x`; `playback_control_speed_1_5x` = `1.5x`; `playback_control_speed_2x` = `2x`.

**Verification:**

- `Grep` - each of the three keys present in `values/`, `values-ru/`, `values-uk/` (9 hits total).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "playback_control_speed_"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Added 3 keys via set-android-string add (parity-enforced); audit exit 0.

---

### Step 02.3 - Add a distinct volume icon

**Files:** `app_v2/src/main/res/drawable/ic_volume_up.xml` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ic_volume_up.xml` as a 24dp Material `volume_up` vector (speaker-with-soundwaves), `android:tint="?attr/colorControlNormal"`, fill `@color/white`, matching the style of `ic_audio.xml`. This visually separates the Volume tab from the Audio-track tab (which keeps `ic_audio_track`). No hardcoded layout hex - tint via attr.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_volume_up.xml` exists.
- `Grep` - `pathData` present once; `?attr/colorControlNormal` present.
- `.\a.ps1 fr` (resources/manifest) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. ic_volume_up.xml created (Material volume_up, ?attr tint); `.\a.ps1 fr` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `.\a.ps1 fr` passes (resources compile).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries batched in Phase 05.

---

## Handoff Notes to Next Phase

Phase 03 references `@drawable/ic_volume_up` (Volume tab icon) and `@string/playback_control_speed_0_5x|_1_5x|_2x` (preset buttons) in both layouts. The Audio-tab label change applies automatically through the unchanged `@string/playback_control_tab_audio` reference.

---

## Rollback Plan

Revert phase commit(s) - additive strings + one new drawable; the audio-label rename reverts via the editor's `-ExpectedOldValue` round-trip.
