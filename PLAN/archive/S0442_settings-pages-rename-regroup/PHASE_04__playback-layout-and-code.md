# Phase 04 - playback-layout-and-code

**Strategic spec:** [`../S0442_settings-pages-rename-regroup.md`](../S0442_settings-pages-rename-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Remove the four moved group cards and the Controls & Keybindings row from both Playback layout variants; strip the corresponding initialization code, permission launchers, and section-state keys from `PlaybackSettingsFragment`. After this phase the Player tab shows only its own four groups.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (groups and code are present in Operations before being removed here).
- [ ] Working tree is clean or on a feature branch.
- [ ] Manual smoke-test on device or emulator: open Settings → Management tab and confirm all four moved groups render correctly (catches a binding or layout issue before the Playback side is stripped).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | shrinks by ~4 group cards |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | mirror of portrait |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 932 (backup first) |

> **Backup required** — `PlaybackSettingsFragment.kt` is 932 LOC. Create timestamped copy in `temp/` before editing.
>
> **Landscape parity** — `layout-land/fragment_settings_playback.xml` exists; Steps 4.3 must mirror every removal from Step 4.2.

---

## Steps

### Step 4.1 - Backup PlaybackSettingsFragment.kt

**Files:** _(backup only, no source change)_
**Depends on:** start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` to `temp/PlaybackSettingsFragment_S0442_<timestamp>.kt`.

**Verification:**

- `Glob` - `temp/PlaybackSettingsFragment_S0442_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Backup created at temp/PlaybackSettingsFragment_S0442_20260615_164909.kt.

---

### Step 4.2 - Remove four group cards and keybindings row from portrait layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** Step 4.1

**Prompt for developer:**

> Delete the following XML blocks from `app_v2/src/main/res/layout/fragment_settings_playback.xml`:
>
> - The `MaterialCardView` (id `cardBehaviour`) containing `headerBehaviour` / `containerBehaviour`.
> - The `MaterialCardView` containing `headerOtherFeatures` / `containerOtherFeatures`.
> - The outer group (`groupSystemApps`) containing `headerSystemApps` / `containerSystemApps`.
> - The block containing `headerScreenGestures` / `containerScreenGestures`.
> - The `rowControlsKeybindings` view block (and its parent wrapper, if any).
>
> Delete only the complete outer wrapper element of each block (including its children). Verify that the `btnResetPlaybackSection` button remains in the layout and still references `@string/reset_playback_section`.

**Verification:**

- `Grep` - `headerBehaviour` in `layout/fragment_settings_playback.xml` → zero hits.
- `Grep` - `headerOtherFeatures` in `layout/fragment_settings_playback.xml` → zero hits.
- `Grep` - `headerSystemApps` in `layout/fragment_settings_playback.xml` → zero hits.
- `Grep` - `headerScreenGestures` in `layout/fragment_settings_playback.xml` → zero hits.
- `Grep` - `rowControlsKeybindings` in `layout/fragment_settings_playback.xml` → zero hits.
- `Grep` - `btnResetPlaybackSection` in `layout/fragment_settings_playback.xml` → exactly one hit.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - First run (s0442_remove_groups.ps1) had a bug: walk-back from "GROUP: Behaviour" comment found Touch Zones card instead of Behaviour card (comment precedes MCV opening tag). Layouts restored from git. Fixed script: s0442_remove_groups_v2.ps1 uses android:id="@+id/cardBehaviour" as anchor. Portrait: group-block 286..736, keybindings 741..791 removed; written 293 lines. Landscape: 304..787 + 792..842; written 311 lines. headerTouchZones + btnResetPlaybackSection retained. Verification 8/8 PASS.

---

### Step 4.3 - Mirror removals to landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 4.2

**Prompt for developer:**

> Apply the identical deletions from Step 4.2 to `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`. Remove `cardBehaviour`, the OtherFeatures card, `groupSystemApps`, the ScreenGestures block, and `rowControlsKeybindings`. Confirm `btnResetPlaybackSection` remains.

**Verification:**

- `Grep` - `headerBehaviour` in `layout-land/fragment_settings_playback.xml` → zero hits.
- `Grep` - `headerOtherFeatures` in `layout-land/fragment_settings_playback.xml` → zero hits.
- `Grep` - `headerSystemApps` in `layout-land/fragment_settings_playback.xml` → zero hits.
- `Grep` - `headerScreenGestures` in `layout-land/fragment_settings_playback.xml` → zero hits.
- `Grep` - `rowControlsKeybindings` in `layout-land/fragment_settings_playback.xml` → zero hits.
- `Grep` - `btnResetPlaybackSection` in `layout-land/fragment_settings_playback.xml` → exactly one hit.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Handled by s0442_remove_groups_v2.ps1 along with Step 4.2. All landscape-specific verifications PASS (same 8/8 check). headerTouchZones + btnResetPlaybackSection retained.

---

### Step 4.4 - Remove KEY_ constants and expandable-section entries for moved groups

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 4.3

**Prompt for developer:**

> In `PlaybackSettingsFragment`'s `companion object`, delete the four constants:
> `KEY_BEHAVIOUR_EXPANDED`, `KEY_OTHER_FEATURES_EXPANDED`, `KEY_SYSTEM_APPS_EXPANDED`, `KEY_SCREEN_GESTURES_EXPANDED`.
>
> In `setupExpandableSections()`, remove the four `ExpandableSection(...)` entries that reference `binding.headerBehaviour`, `binding.headerOtherFeatures`, `binding.headerSystemApps`, `binding.headerScreenGestures`. Remove any corresponding `getSavedSectionStates()` map entries for those keys.

**Verification:**

- `Grep` - `KEY_BEHAVIOUR_EXPANDED` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `KEY_OTHER_FEATURES_EXPANDED` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `KEY_SYSTEM_APPS_EXPANDED` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `KEY_SCREEN_GESTURES_EXPANDED` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `headerBehaviour` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `headerSystemApps` in `PlaybackSettingsFragment.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Removed 4 KEY_ constants from companion object, 4 ExpandableSection entries from setupExpandableSections(), 4 map entries from getSavedSectionStates(). Also removed scrollToHighlightedSettingIfRequested() call (onViewCreated) and method body — it referenced headerOtherFeatures + KEY_OTHER_FEATURES_EXPANDED, both now gone. Removed unused import android.graphics.Rect. Verification 6/6 PASS.

---

### Step 4.5 - Remove group init methods, permission launchers, and Inject fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 4.4

**Prompt for developer:**

> Remove the following from `PlaybackSettingsFragment`:
>
> - The `recordAudioPermissionLauncher` field and its registration block.
> - The `overlayPermissionLauncher` field and its registration block.
> - The `@Inject lateinit var screenGestureControllers` field.
> - The `@Inject lateinit var capabilityAvailability` field (only if it was used exclusively for the moved groups — verify by searching for other usages; if it is also used for remaining Player-tab features, keep it).
> - The call to `setupCaptureSection()` in `setupViews()` and the `setupCaptureSection()` private method body itself.
> - The call to `setupSystemAppsSection()` in `setupViews()` and the `setupSystemAppsSection()` private method body itself.
> - The `rowControlsKeybindings.setOnClickListener` handler in `setupViews()`.
> - The `DefaultPlayerSettingsManager` bind call (`defaultPlayerSettingsManager.bind(...)`) **only if** `DefaultPlayerSettingsManager` manages views exclusively inside `containerSystemApps`. If it also manages views in a section staying in Playback, keep the bind call.
> - The `private val defaultPlayerSettingsManager = DefaultPlayerSettingsManager()` field **only if** removed in the previous bullet.
>
> Also remove the `applyFlavorRestrictions()` call from `setupViews()` **only if** its body only gated OtherFeatures-group rows. If it also applies to remaining Player-tab rows (e.g., `rowCameraOcrTranslationEnabled` in PlayerUI), keep it.

**Verification:**

- `Grep` - `recordAudioPermissionLauncher` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `overlayPermissionLauncher` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `screenGestureControllers` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `setupCaptureSection` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `setupSystemAppsSection` in `PlaybackSettingsFragment.kt` → zero hits.
- `Grep` - `openKeybindingRemap` in `PlaybackSettingsFragment.kt` → zero hits (the handler is gone).
- `Grep` - `Log\.d\(` in `PlaybackSettingsFragment.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Removed recordAudioPermissionLauncher, overlayPermissionLauncher, screenGestureControllers, capabilityAvailability, mediaCapabilities, defaultPlayerSettingsManager, destinationTargets fields; removed applyFlavorRestrictions(), setupCaptureSection(), setupSystemAppsSection(), showGesturePermissionDialog(), showDestinationPicker(), refreshDestinationLabel(); cleaned setupViews() and observeData() of all moved-group wiring. File reduced to 377 lines. Verification 7/7 PASS.

---

### Step 4.6 - Verify Player reset button and full compile

**Files:** _(validation only)_
**Depends on:** Step 4.5

**Prompt for developer:**

> Confirm `btnResetPlaybackSection.setOnClickListener` in `PlaybackSettingsFragment` calls `viewModel.resetPlaybackSection()` and references `R.string.reset_playback_section_title` / `_message` / `_success` (the narrowed strings from Phase 01). No code change is needed if the wiring was untouched; this step verifies correctness.
>
> Then run `.\a.ps1 fc` for a full compile. The build must pass with no unresolved binding references for the removed view IDs.

**Verification:**

- `Grep` - `btnResetPlaybackSection` in `PlaybackSettingsFragment.kt` → at least one hit (click listener still wired).
- `Grep` - `resetPlaybackSection` in `PlaybackSettingsFragment.kt` → at least one hit (ViewModel call present).
- Run `.\a.ps1 fc` → exit 0.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Confirmed btnResetPlaybackSection.setOnClickListener wired to viewModel.resetPlaybackSection(). Discovered DefaultPlayerSettingsManager.kt had dead first bind() overload for FragmentSettingsPlaybackBinding (view IDs removed in Step 4.2/4.3). Removed the dead overload and unused imports. a.ps1 fc → exit 0. Verification 3/3 PASS.

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] `.\a.ps1 fc` → exit 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The Player tab contains only Sorting/Slideshow, File-ops-in-player, Player UI, Touch Zones, and the narrowed reset button. The Management tab hosts everything else. Proceed to Phase 05 for catalog and dev-log finalization.

---

## Rollback Plan

Restore `temp/PlaybackSettingsFragment_S0442_*.kt`; revert both playback layout files. Since Phase 03 has already added the groups to Operations, rollback of this phase only leaves the groups present in BOTH tabs simultaneously — not desirable in production. Roll back Phase 03 as well to fully restore the original state.
