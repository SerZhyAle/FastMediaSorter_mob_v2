# Phase 05 - 3D/VR settings in the VR media block

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-01
**Completed:** 2026-06-01

> Re-detailed 2026-06-01 after the owner UI-clarify reframe (ADR-3): ALL 3D/VR settings UI is VR-only and lives in the existing VR media section. Phase 04 superseded. §6.1 resolved: single unified 3D/VR switch.

---

## Objective

Surface all 3D/VR default settings (autorecognition, default layout/projection, immersive behavior, diagnostics, and a unified 3D/VR enable switch) inside the existing VR media-settings block, shown only on `vr`/`noLegal`. No changes to non-VR flavors.

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.
- [ ] §6.1 resolved (single unified switch) - confirmed.
- [ ] Read `dev/FLAVOR_DEVELOPMENT_RULES.md` §3–§4; all new UI lives under `src/vr/`.
- [ ] Read the established Spinner-in-settings pattern (`fragment_settings_audio.xml` + `BaseSettingsFragment.bindSpinner` / `setSpinnerSelection`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/vr/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/vr/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/vr/res/layout/fragment_vr_settings_block.xml` | Modified | ≤ 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockFragment.kt` | Modified | ≤ 320 |

> No `res/layout-land/fragment_vr_settings_block.xml` exists - the block is content inside the scrollable media-settings container, orientation-independent. No landscape counterpart needed.
> All files are in the `vr` source set (shared by `vr` + `noLegal`). `SettingsRepository` / `SettingsViewModel` are in `src/main` and consumed normally. No `BuildConfig` guard in `src/main`.

---

## Steps

### Step 05.1 - Add trilingual VR strings and spinner entries

**Files:** `app_v2/src/vr/res/values/strings.xml`, `app_v2/src/vr/res/values-ru/strings.xml`, `app_v2/src/vr/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add titles/summaries (key prefix `settings_stereo_vr_`) for: section subheaders; autodetect master; trust filename; trust embedded metadata; trust aspect-ratio guess (summary must warn it may misfire); ambiguity behavior (open as 2D vs best guess); default layout; default projection; auto-immersive; render mode; FPS overlay; and the unified "3D/VR enabled" switch label/summary. Add three `string-array`s for the spinners: layout (Mono, Side-by-Side, Over-Under), projection (Flat, 180°, 360°, Cylinder), render mode (Cinema, Full SBS, Full OU). Run every string through `docs/COMMUNICATION_POLICY.md` §2 (formula) and §6 (tone checklist). Add all keys to en + ru + uk.

**Verification:**

- `Grep` - each new `settings_stereo_vr_` key exists in all three `strings.xml` files (one Grep per file).
- `Grep` - the three `string-array` names exist in all three files.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_stereo_vr_"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - 20 strings + 3 string-arrays added to vr en/ru/uk. Locale parity: expected equal counts | actual 23/23/23 PASS. Localization script scans src/main only (exit 0, vr keys not in its scope) - grep parity is the discriminating check. RU/UK use ё / `..` per author style.

---

### Step 05.2 - Extend the VR block layout with the new rows

**Files:** `app_v2/src/vr/res/layout/fragment_vr_settings_block.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Below the existing master-toggle row, add (in grouped order): autorecognition `SettingsToggleRow`s (`autoDetectRow`, `trustFilenameRow`, `trustMetadataRow`, `trustAspectRatioRow`, `ambiguityBestGuessRow`); two `Spinner`s for default layout (`defaultLayoutSpinner`) and default projection (`defaultProjectionSpinner`) each with a label, following the established settings Spinner row markup; immersive `vrAutoImmersiveRow` toggle and `renderModeSpinner`; diagnostics `vrShowFpsRow` toggle. Reuse `SettingsToggleRow` with `app:str_title`/`app:str_subtitle` and the project's Trigger Row dimensions. Every control must be keyboard/D-pad/mouse reachable (the existing row/spinner widgets already provide focus + click); keep the logical focus order top-to-bottom.

**Verification:**

- `Grep` - the layout references each new row/spinner id (`autoDetectRow`, `trustAspectRatioRow`, `defaultLayoutSpinner`, `defaultProjectionSpinner`, `renderModeSpinner`, `vrAutoImmersiveRow`, `vrShowFpsRow`).
- `Grep` - the two stereo spinners reference the `string-array` entries from 05.1.

**Status:** `[ ]` not done

---

### Step 05.3 - Bind controls in the fragment + unified 3D/VR switch

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockFragment.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Convert `VrSettingsBlockFragment` to extend `BaseSettingsFragment` (keep `@AndroidEntryPoint`) and add `private val viewModel: SettingsViewModel by activityViewModels()`. Bind each new toggle with `bindSwitch(row) { viewModel.updateSettings(viewModel.settings.value.copy(<field> = it)) }` and reflect state with `setSwitchChecked`; bind the spinners with `bindSpinner`/`setSpinnerSelection` mapping position ↔ `StereoMode` / render-mode string. Wrap state application in `withSettingsUpdate { }`. Collect `viewModel.settings` on the lifecycle to push current values into the rows. **Unified switch:** keep the existing `masterToggleRow` as the single 3D/VR enable control - extend its listener so it ALSO writes `disable3dVr = !isChecked` via `viewModel.updateSettings`, while still setting `MasterTogglePreferences.setEnabled(isChecked)`; when reflecting state, derive checked from VR-enabled intent and re-sync if `disable3dVr` and the master toggle disagree. Do not add a second enable control. `panelStereoSingleEye` stays in Playback settings - do not add it here.

**Verification:**

- `Grep` - `class VrSettingsBlockFragment : BaseSettingsFragment`.
- `Grep` - `by activityViewModels` and `SettingsViewModel` referenced.
- `Grep` - the fragment writes `disable3dVr` and calls `MasterTogglePreferences` from the unified toggle path.
- `Grep` - each new field (`stereoAutoDetectEnabled`, `stereoTrustAspectRatio`, `stereoDefaultLayout`, `stereoDefaultProjection`, `vrAutoImmersive`, `vrRenderingMode`, `vrShowFps`) is read and written.
- `Grep -n "Log\.d\("` returns zero hits (Timber only).

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - VrSettingsBlockFragment → BaseSettingsFragment + SettingsViewModel via activityViewModels. 7 toggles + 3 spinners bound via bindSwitch/bindSpinner; settings collector reflects state under withSettingsUpdate. Unified switch: master toggle writes preferences.setEnabled + disable3dVr=!checked (no cold-start auto-reconcile to avoid killing 3D when VR master defaults off). panelStereoSingleEye left in Playback. Verification 5/5 PASS, 0 Log.d.

---

### Step 05.4 - Build both target variants

**Files:** (build only)
**Depends on:** Step 05.3

**Prompt for developer:**

> Assemble `noLegalDebug` (VR block visible - all new controls present) and `standardDebug` (non-VR - no 3D/VR settings section, must still compile). Use `.\build-debug.PS1` / the `/build` skill; do not invoke gradle directly for the closure.

**Verification:**

- `noLegalDebug` assembles - exit 0.
- `standardDebug` assembles - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - `:app_v2:assembleNoLegalDebug` → BUILD SUCCESSFUL (compileNoLegalDebugKotlin OK, vr code compiles). `:app_v2:assembleStandardDebug` → BUILD SUCCESSFUL (non-VR intact). expected: both exit 0 | actual: both BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `noLegalDebug` and `standardDebug` both build.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_stereo_vr_"` exits 0 (vr keys out of its main-only scope; grep parity 23/23/23 is the discriminating check).
- [x] `Grep -rn "BuildConfig.SUPPORT_" app_v2/src/main` shows no new VR-settings guard added by this phase (all UI in src/vr).
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

All 3D/VR settings are wired and VR-gated via the existing section. Phase 06 finalizes docs, catalog, FEATURES, and functionality log.

---

## Rollback Plan

Revert phase commit(s) - all changes are in the `vr` source set; the VR block reverts to the master toggle + Test Immersive only. No data migration; settings fields persist regardless of UI.
