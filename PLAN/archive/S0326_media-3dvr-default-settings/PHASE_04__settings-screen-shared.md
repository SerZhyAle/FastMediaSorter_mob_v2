# Phase 04 - Settings screen: shared groups

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Superseded (2026-06-01)
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

> **SUPERSEDED by owner UI-clarify decision (2026-06-01).** There is no separate always-visible 3D/VR settings screen. All 3D/VR settings (including autorecognition and flat-stereo groups A and D) are VR-only and live in the existing VR media section (`src/vr`). These controls fold into Phase 05. Non-VR flavors show no 3D/VR settings. Do not implement this phase as written.

---

## Objective

Add the new "3D/VR" entry inside the media settings section exposing the flavor-independent groups: autorecognition (master toggle, source-trust toggles, ambiguity behavior), default layout, and flat single-eye viewing. VR-only groups are added in Phase 05.

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.
- [ ] Read `MediaSettingsFragment` child-attachment pattern and the existing `panelStereoSingleEye` control in `PlaybackSettingsFragment` before adding the screen.
- [ ] `/ui-clarify` gate passed for screen placement, control order, and default values.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StereoVrSettingsFragment.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/xml/preferences_stereo_vr.xml` | New | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> Declarative preference screen (orientation-independent) - no `res/layout/` / `res/layout-land/` pair needed. Confirm there is no portrait-only layout introduced.

---

## Steps

### Step 04.1 - Add trilingual strings for the shared groups

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add titles/summaries for: screen title "3D/VR", master "Auto-detect 3D/VR formats", "Trust filename markers", "Trust embedded metadata", "Use aspect-ratio guess (may misfire)", "When format is unclear" (2D vs best guess), "Default layout", and the existing flat single-eye toggle label if not already shared. Run every string through `docs/COMMUNICATION_POLICY.md` §2 (formula) and §6 (tone checklist). Use a common key prefix `settings_stereo_vr_`.

**Verification:**

- `Grep` - each new key exists in all three `strings.xml` files (one Grep per file).
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_stereo_vr_"` exits 0.

**Status:** `[ ]` not done

---

### Step 04.2 - Build the preference screen and fragment

**Files:** `app_v2/src/main/res/xml/preferences_stereo_vr.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StereoVrSettingsFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `preferences_stereo_vr.xml` with the shared groups only (autorecognition switches, ambiguity list, default-layout list, flat single-eye toggle). Create `StereoVrSettingsFragment` that binds these preferences to the settings repository fields from Phase 01. No business logic in the fragment beyond read/write delegation. Ensure every control is keyboard/D-pad/mouse reachable and state is distinguishable beyond color (CLAUDE.md Rule 17). Leave a clearly-marked extension point where Phase 05 injects the VR-only group.

**Verification:**

- `Glob` - both new files exist.
- `Grep` - `class StereoVrSettingsFragment` matches exactly once.
- `Grep` - the preference XML references each shared field key.
- `Grep -n "Log\.d\("` in the fragment returns zero hits.

**Status:** `[ ]` not done

---

### Step 04.3 - Wire the screen into the media settings section

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add the navigation entry to the new 3D/VR screen inside the media settings section, in the established order relative to the existing playback/media entries. The entry itself is flavor-independent (shared groups always exist). Do not gate the entry by flavor here - VR-group visibility is handled inside the screen in Phase 05.

**Verification:**

- `Grep` - `MediaSettingsFragment` references `StereoVrSettingsFragment` or its destination.
- Project compiles for the `standardDebug` variant.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `standardDebug` builds - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_stereo_vr_"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new fragment).

---

## Handoff Notes to Next Phase

The screen exists with shared groups and an extension point for VR-only controls. Phase 05 supplies the VR group via the flavor contract; the kill-switch truth table (§6.1) must be resolved first.

---

## Rollback Plan

Revert phase commit(s) - new screen and strings only; removing the media-section entry restores prior settings.
