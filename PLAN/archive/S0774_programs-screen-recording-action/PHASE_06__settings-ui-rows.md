# Phase 06 - Settings UI rows

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-29
**Completed:** 2026-06-29

> **Phase Step Log (2026-06-29):** screen-recording toggle + destination selector added to both portrait + landscape layouts (4 ids each); `OperationsCaptureManager` setup/render + `isScreenRecordingAvailable` gate; fragment injects `Set<ScreenVideoRecordingController>` and passes `.isNotEmpty()`. Manifest regenerated (export test), 2 annotations added, reference re-rendered. `.\a.ps1 fc` SUCCESSFUL; `assert-settings-doc-sync.ps1` OK.

---

## Objective

Add the "Screen video recording" toggle and its subordinate "Screen recordings destination" picker to Settings → Management → Additional programs and scenarios, mirroring the microphone-recording block. Rows are hidden when the capability is absent (empty `Set<ScreenVideoRecordingController>`).

---

## Prerequisites

- [ ] Phase 02 done (settings fields).
- [ ] Phase 03 done (strings).
- [ ] Phase 04 done (`ScreenVideoRecordingController` for gating).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | +1 block |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | +1 block |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 660 |

> **Landscape parity (MANDATORY):** the landscape variant exists - both layout files are edited in this phase. `fragment_settings_destinations.xml` is > 500 LOC in both: back up each into `temp/` before editing.

---

## Steps

### Step 06.1 - Add the UI block to both layouts

**Files:** `layout/fragment_settings_destinations.xml`, `layout-land/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Photo, Video, Voice recorder" section, after the microphone-recording destination selector, add a block mirroring the mic block:
> - `SettingsToggleRow` `@+id/rowScreenRecordingEnabled` with `app:str_title="@string/settings_screen_recording_enable_title"` and `app:str_subtitle="@string/settings_screen_recording_enable_desc"`.
> - `LinearLayout` `@+id/layoutScreenRecordingDestSelector` (nested padding `@dimen/settings_nested_margin_start`) containing a `TextView` titled `@string/setting_screen_recording_destination_title`, a horizontal row with `TextView @+id/tvScreenRecordingDest` (`layout_weight=1`) + `MaterialButton @+id/btnSelectScreenRecordingDest` (style `@style/Widget.FastMediaSorter.SettingsButton.Outlined`, text `@string/setting_select_destination`).
> No hardcoded `#hex` colors (use `?attr/`/`@color/`). Apply the same block in both portrait and landscape, matching the surrounding orientation layout idiom.

**Verification:**

- `Grep` - `rowScreenRecordingEnabled`, `layoutScreenRecordingDestSelector`, `tvScreenRecordingDest`, `btnSelectScreenRecordingDest` present in BOTH layout files.
- `.\a.ps1 fr` (resources/manifest) green.

**Status:** `[x] done`

---

### Step 06.2 - Wire setup() in OperationsCaptureManager

**Files:** `OperationsCaptureManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Mirror the mic block in `setup()`: a `setOnCheckedChangeListener` on `rowScreenRecordingEnabled` that (guarding the from-settings update flag) calls `viewModel.updateSettings(.. .copy(screenRecordingEnabled = isChecked))` and toggles `layoutScreenRecordingDestSelector` visibility; a click listener on `btnSelectScreenRecordingDest` that calls the existing `pickDestination(currentId) { resource -> updateSettings(.. .copy(screenRecordingDestinationResourceId = resource?.id?.toString())) }`. Add a constructor param `isScreenRecordingAvailable: Boolean`; when false, hide `rowScreenRecordingEnabled` + `layoutScreenRecordingDestSelector` (mirror the `supportsMicRecording` gate).

**Verification:**

- `Grep` - `rowScreenRecordingEnabled.setOnCheckedChangeListener` and `btnSelectScreenRecordingDest.setOnClickListener` present.
- `Grep` - `isScreenRecordingAvailable` referenced for visibility gating.

**Status:** `[x] done`

---

### Step 06.3 - Wire render() + fragment gating

**Files:** `OperationsCaptureManager.kt`, `OperationsSettingsFragment.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> In `render()`: set `rowScreenRecordingEnabled` checked-silently from `settings.screenRecordingEnabled`, set `layoutScreenRecordingDestSelector` visibility from it, and refresh `tvScreenRecordingDest` via the existing destination-label helper using `settings.screenRecordingDestinationResourceId` with fallback string `R.string.setting_screen_recording_destination_default_downloads`. In `OperationsSettingsFragment`, `@Inject lateinit var screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>` and pass `isScreenRecordingAvailable = screenVideoRecordingControllers.isNotEmpty()` when constructing `OperationsCaptureManager`.

**Verification:**

- `Grep` - `screenRecordingEnabled` referenced in `render()`; `tvScreenRecordingDest` label set with the default-downloads fallback string.
- `Grep` - `screenVideoRecordingControllers` injected and `.isNotEmpty()` passed into the manager.
- `.\a.ps1 fk` compiles.

**Status:** `[x] done`

---

### Step 06.4 - Regenerate settings docs (Rule 22)

**Files:** `docs/settings/settings-manifest.json`, `docs/settings/settings-annotations.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Regenerate the settings manifest + reference and add annotations for the two new controls (`rowScreenRecordingEnabled`, `btnSelectScreenRecordingDest`), section `destinations`, destination `OPERATIONS`, layout `fragment_settings_destinations`. Use the project's manifest-regen tool (quote `-D` args in pwsh). This must pass the `assert-settings-doc-sync` gate.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` → exit 0.
- `Grep` - `rowScreenRecordingEnabled` present in `settings-manifest.json` and `settings-annotations.json`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] All four steps `[x]`.
- [ ] `.\a.ps1 fc` (code + resources) green.
- [ ] `assert-settings-doc-sync.ps1` exits 0.
- [ ] Both portrait + landscape layouts carry the new block.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

The toggle persists `screenRecordingEnabled`; Phase 07 reads it to gate the programs-block scenario visibility. The destination picker persists `screenRecordingDestinationResourceId`, already consumed by the Phase 05 service.

---

## Rollback Plan

Revert the phase commit - additive settings rows and docs; no data migration.
