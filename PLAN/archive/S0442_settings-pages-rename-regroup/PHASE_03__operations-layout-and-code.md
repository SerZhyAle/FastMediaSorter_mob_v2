# Phase 03 - operations-layout-and-code

**Strategic spec:** [`../S0442_settings-pages-rename-regroup.md`](../S0442_settings-pages-rename-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 8 / 8
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Add the four moved group cards, the Controls & Keybindings row, and the "Reset Management settings" button to the Operations layout (portrait + landscape); extend `OperationsSettingsFragment` with the corresponding initialization code, permission launchers, and section state keys.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (all new/updated strings exist in resources).
- [ ] Phase 02 ✅ Done (`resetOperationsSection()` exists in `SettingsViewModel`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ existing + moved blocks |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | mirror of portrait |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 636 (backup first) |

> **Backup required** — `OperationsSettingsFragment.kt` is 636 LOC. Create timestamped copy in `temp/` before editing.
>
> **Landscape parity** — `layout-land/fragment_settings_destinations.xml` exists; Step 3.3 + Step 3.4 must mirror every layout change from Steps 3.2 and 3.4 respectively.

---

## Steps

### Step 3.1 - Backup OperationsSettingsFragment.kt

**Files:** _(backup only, no source change)_
**Depends on:** start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` to `temp/OperationsSettingsFragment_S0442_<timestamp>.kt`.

**Verification:**

- `Glob` - `temp/OperationsSettingsFragment_S0442_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Backup created at temp/OperationsSettingsFragment_S0442_20260615_161625.kt.

---

### Step 3.2 - Add four group cards to fragment_settings_destinations.xml (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`
**Depends on:** Step 3.1

**Prompt for developer:**

> Open `app_v2/src/main/res/layout/fragment_settings_playback.xml` and locate the four `MaterialCardView` blocks:
> - `cardBehaviour` / `headerBehaviour` / `containerBehaviour`
> - The card containing `headerOtherFeatures` / `containerOtherFeatures`
> - `groupSystemApps` containing `headerSystemApps` / `containerSystemApps`
> - The block containing `headerScreenGestures` / `containerScreenGestures`
>
> Copy each block's full XML (including the outer `MaterialCardView` or container wrapper) and paste all four, in order, into `app_v2/src/main/res/layout/fragment_settings_destinations.xml`. Insert them after the card containing `headerCopyMove` / `containerFileOperations` (the "Copy, move and overwrite behavior" section), following the owner-specified order from strategic §3.1:
>
> Safety → CopyMove → Destinations → Scheduled → **Behaviour → OtherFeatures → SystemApps → ScreenGestures**
>
> Do not modify the pasted XML content (view IDs, text refs, visibility settings all stay identical). Do not use hardcoded hex colors in any attribute — use `?attr/` or `@color/` only.

**Verification:**

- `Grep` - `headerBehaviour` in `layout/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `headerOtherFeatures` in `layout/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `headerSystemApps` in `layout/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `headerScreenGestures` in `layout/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - no hardcoded hex color `="#[0-9A-Fa-f]` in the added blocks.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 5/5 PASS. 4 group headers present in portrait layout. No hex colors.

---

### Step 3.3 - Mirror four group cards to layout-land/fragment_settings_destinations.xml

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 3.2

**Prompt for developer:**

> Apply the identical XML additions from Step 3.2 to `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`. The landscape layout must have the same four group cards in the same relative order after the CopyMove section. Source the XML blocks from `layout-land/fragment_settings_playback.xml` (the landscape variant) to preserve any landscape-specific dimension overrides.

**Verification:**

- `Grep` - `headerBehaviour` in `layout-land/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `headerOtherFeatures` in `layout-land/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `headerSystemApps` in `layout-land/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `headerScreenGestures` in `layout-land/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - no hardcoded hex color `="#[0-9A-Fa-f]` in the added blocks.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 5/5 PASS. 4 group headers present in landscape layout. No hex colors.

---

### Step 3.4 - Add keybindings row and btnResetOperationsSection to both layouts

**Files:**
- `app_v2/src/main/res/layout/fragment_settings_destinations.xml`
- `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`

**Depends on:** Step 3.3

**Prompt for developer:**

> In `layout/fragment_settings_destinations.xml`, at the very end of the ScrollView content (after all group cards), add two elements:
>
> 1. Copy the `rowControlsKeybindings` view block from `fragment_settings_playback.xml` (the row that uses `settings_controls_keybindings_title` / `settings_controls_keybindings_desc`). Paste it as a standalone row after the `headerScreenGestures` / `containerScreenGestures` block.
> 2. Add a `MaterialButton` with `id="btnResetOperationsSection"`, `text="@string/reset_operations_section"`, `style="@style/Widget.FastMediaSorter.SettingsButton.Outlined"`, `layout_gravity="end"`, matching the placement and styling of `btnResetPlaybackSection` in `fragment_settings_playback.xml`.
>
> Apply the identical additions to `layout-land/fragment_settings_destinations.xml`, sourcing the row XML from `layout-land/fragment_settings_playback.xml`.

**Verification:**

- `Grep` - `rowControlsKeybindings` in `layout/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `btnResetOperationsSection` in `layout/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `@string/reset_operations_section` in `layout/fragment_settings_destinations.xml` → at least one hit.
- `Grep` - `rowControlsKeybindings` in `layout-land/fragment_settings_destinations.xml` → exactly one hit.
- `Grep` - `btnResetOperationsSection` in `layout-land/fragment_settings_destinations.xml` → exactly one hit.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 5/5 PASS. rowControlsKeybindings + btnResetOperationsSection present in both portrait and landscape.

---

### Step 3.5 - Add @Inject fields and permission launchers to OperationsSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 3.4

**Prompt for developer:**

> Add the following fields to `OperationsSettingsFragment` (all initialized at field-declaration site, before `onCreateView`, matching the pattern in `PlaybackSettingsFragment`):
>
> 1. `@Inject lateinit var capabilityAvailability: CapabilityAvailability`
> 2. `@Inject lateinit var mediaCapabilities: MediaCapabilities`
> 3. `@Inject lateinit var screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>`
> 4. `private val recordAudioPermissionLauncher` — registered via `registerForActivityResult(ActivityResultContracts.RequestPermission())` with the same callback logic as in `PlaybackSettingsFragment`: if granted, call `viewModel.updateSettings(current.copy(micRecordingEnabled = true))`; if denied, silence the toggle and show the `mic_recording_permission_denied` snackbar.
> 5. `private val overlayPermissionLauncher` — registered via `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` with the same callback as in `PlaybackSettingsFragment`: check `controller.isOverlayPermissionGranted()`, update `gestureOverlayEnabled` accordingly.
>
> Ensure `@AndroidEntryPoint` is already present on the class (it should be via `BaseSettingsFragment`); if not, add it.

**Verification:**

- `Grep` - `capabilityAvailability` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `screenGestureControllers` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `recordAudioPermissionLauncher` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `overlayPermissionLauncher` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `Log\.d\(` in `OperationsSettingsFragment.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 5/5 PASS. capabilityAvailability(3), screenGestureControllers(4), recordAudioPermissionLauncher(2), overlayPermissionLauncher(3) present; Log.d zero hits. @AndroidEntryPoint added (BaseSettingsFragment does not carry it). DefaultPlayerSettingsManager.bind() extracted to bindViews() with FragmentSettingsDestinationsBinding overload added (binding type conflict resolved inline, no blocker).

---

### Step 3.6 - Add KEY_ constants and extend setupExpandableSections()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 3.5

**Prompt for developer:**

> In the `companion object` of `OperationsSettingsFragment`, add four new SharedPreferences keys for the moved groups. Use unique key strings that do not collide with any key in `PlaybackSettingsFragment`'s `PREFS_NAME` (`"playback_sections_state"`):
>
> ```kotlin
> private const val KEY_BEHAVIOUR_EXPANDED = "mgmt_behaviour_expanded"
> private const val KEY_OTHER_FEATURES_EXPANDED = "mgmt_other_features_expanded"
> private const val KEY_SYSTEM_APPS_EXPANDED = "mgmt_system_apps_expanded"
> private const val KEY_SCREEN_GESTURES_EXPANDED = "mgmt_screen_gestures_expanded"
> ```
>
> In `setupExpandableSections()`, append four new `ExpandableSection` entries at the end of the `sections` list (after Scheduled), in order: Behaviour, OtherFeatures, SystemApps, ScreenGestures. Use the same prefs-file (`PREFS_NAME` = `"settings_section_states"`) and `defaultExpanded = false` for each. The exact binding field names are: `binding.headerBehaviour` / `binding.containerBehaviour`, `binding.headerOtherFeatures` / `binding.containerOtherFeatures`, `binding.headerSystemApps` / `binding.containerSystemApps`, `binding.headerScreenGestures` / `binding.containerScreenGestures`.

**Verification:**

- `Grep` - `KEY_BEHAVIOUR_EXPANDED` in `OperationsSettingsFragment.kt` → at least two hits (declaration + usage).
- `Grep` - `KEY_SYSTEM_APPS_EXPANDED` in `OperationsSettingsFragment.kt` → at least two hits.
- `Grep` - `headerBehaviour` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `headerScreenGestures` in `OperationsSettingsFragment.kt` → at least one hit.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 4/4 PASS. KEY_BEHAVIOUR_EXPANDED(2), KEY_SYSTEM_APPS_EXPANDED(2), headerBehaviour(1), headerScreenGestures(1). 4 new sections added to setupExpandableSections(); "mgmt_*" prefix avoids collision with PlaybackSettingsFragment's "section_*" keys.

---

### Step 3.7 - Add initialization code for moved groups

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 3.6

**Prompt for developer:**

> Copy the initialization logic for the four moved groups from `PlaybackSettingsFragment` into `OperationsSettingsFragment`. Add calls to the new methods at the end of `setupViews()` (or inside `onViewCreated()` after the existing setup calls):
>
> - **Capture/microphone group** (`containerOtherFeatures`): copy `setupCaptureSection()` from `PlaybackSettingsFragment` verbatim, including the `applyFlavorRestrictions()` call for OCR/translation visibility; adapt binding references to `FragmentSettingsDestinationsBinding` (the fragment's own `binding`). Use `recordAudioPermissionLauncher` (declared in Step 3.5).
> - **System apps group + screen gestures** (`containerSystemApps`, `containerScreenGestures`): copy `setupSystemAppsSection()` from `PlaybackSettingsFragment` verbatim; adapt binding references; use `overlayPermissionLauncher` and `screenGestureControllers` (declared in Step 3.5).
> - **Behaviour group** (`containerBehaviour`): wire every `SettingsToggleRow`/spinner in the container to its corresponding `viewModel.updateSettings(…)` call, copied from `PlaybackSettingsFragment.setupViews()`. Cross-check the layout's `containerBehaviour` XML to enumerate all controls.
> - **Controls & Keybindings row** (`rowControlsKeybindings`): add `binding.rowControlsKeybindings.setOnClickListener { SettingsActivity.openKeybindingRemap(requireContext()) }`.
>
> Also migrate the `DefaultPlayerSettingsManager` binding if it manages views inside `containerSystemApps` — inspect `DefaultPlayerSettingsManager.bind()` signature; if it accepts a `FragmentSettingsPlaybackBinding`-specific type, a binding-adapter extraction may be required (flag as a blocker if so).
>
> Preserve the noLegal-only visibility gate for the screen gestures group: `binding.groupSystemApps.isVisible = screenGestureControllers.isNotEmpty()` (or equivalent check used in `setupSystemAppsSection()`).

**Verification:**

- `Grep` - `setupCaptureSection` (or equivalent local call) in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `setupSystemAppsSection` (or equivalent local call) in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `openKeybindingRemap` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `screenGestureControllers` presence-check (`isNotEmpty` or `firstOrNull`) in `OperationsSettingsFragment.kt` → at least one hit (noLegal gate preserved).
- `Grep` - `Log\.d\(` in `OperationsSettingsFragment.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 5/5 PASS. setupCaptureSection(2), setupSystemAppsSection(2), openKeybindingRemap(1), screenGestureControllers.firstOrNull/isNotEmpty(7); Log.d zero hits. applyFlavorRestrictions/showGesturePermissionDialog/showDestinationPicker/refreshDestinationLabel added verbatim from PlaybackSettingsFragment (stale S0435 Timber.d omitted from setupSystemAppsSection). Full observeData() sync for all 4 moved groups added.

---

### Step 3.8 - Wire btnResetOperationsSection click handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 3.7

**Prompt for developer:**

> In `setupViews()` (or wherever `btnResetPlaybackSection` is wired in `PlaybackSettingsFragment`), add:
>
> ```kotlin
> binding.btnResetOperationsSection.setOnClickListener {
>     MaterialAlertDialogBuilder(requireContext())
>         .setTitle(R.string.reset_operations_section_title)
>         .setMessage(R.string.reset_operations_section_message)
>         .setPositiveButton(R.string.reset) { _, _ ->
>             viewModel.resetOperationsSection()
>             Snackbar.make(binding.root, R.string.reset_operations_section_success, Snackbar.LENGTH_SHORT).show()
>         }
>         .setNegativeButton(R.string.cancel, null)
>         .show()
> }
> ```
>
> Use the same `R.string.reset` and `R.string.cancel` keys used by other dialogs in the file. Verify `R.string.reset_operations_section_title` / `_message` / `_success` already exist (Phase 01 Step 1.3 created them).

**Verification:**

- `Grep` - `btnResetOperationsSection` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `reset_operations_section_title` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `resetOperationsSection` in `OperationsSettingsFragment.kt` → at least one hit.
- `Grep` - `Log\.d\(` in `OperationsSettingsFragment.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 4/4 PASS. btnResetOperationsSection(1), reset_operations_section_title(1), resetOperationsSection(1); Log.d zero hits. MaterialAlertDialogBuilder + Snackbar used (matching spec); R.string.reset/cancel confirmed to exist.

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] `.\a.ps1 fc` → exit 0 (full compile including resources and Kotlin).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The Operations/Management tab now hosts all four moved groups, the keybindings row, and the reset button. Phase 04 may now safely remove these groups from the Playback layout and fragment.

---

## Rollback Plan

Restore `temp/OperationsSettingsFragment_S0442_*.kt` to original path; revert the two destination layout files. Groups remain in Playback until Phase 04 runs, so a rollback of Phase 03 leaves the app in its pre-S0442 state.
