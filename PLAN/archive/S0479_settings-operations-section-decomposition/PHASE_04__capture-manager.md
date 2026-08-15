# Phase 04 - Capture Manager

**Strategic spec:** [`../S0479_settings-operations-section-decomposition.md`](../S0479_settings-operations-section-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Extract the capture subgroup (camera photos, video recording, microphone recording rows and their destination selectors, incl. inverted master-flag semantics and RECORD_AUDIO consent) out of `OperationsSettingsFragment` into `OperationsCaptureManager`.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Backup `OperationsSettingsFragment.kt` (>500 LOC) to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 790 |

> No XML touched. Mic recording rows gate on `mediaCapabilities.supportsMicRecording`; preserve verbatim.

---

## Steps

### Step 04.1 - Create `OperationsCaptureManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `OperationsCaptureManager` taking `binding`, `viewModel: SettingsViewModel`, `fragment: Fragment`, `mediaCapabilities: MediaCapabilities`, `recordAudioPermissionLauncher: ActivityResultLauncher<String>` (stays registered in the fragment), `isUpdatingFromSettings: () -> Boolean`, and two shared callbacks `pickDestination: (Long?, (MediaResource?) -> Unit) -> Unit` and `refreshLabel: (String?, TextView, Int) -> Unit`. Move into it `setupCaptureSection()` (camera/video/mic listeners + destination-selector buttons) and a `fun render(settings: Settings)` containing the capture-related rows from `observeData`'s settings collect (camera-photos, video, mic rows and their destination labels and visibility). Preserve the inverted `disableCameraCapture` / `disableVideoCapture` persistence and the `supportsMicRecording` gate. Use the injected `isUpdatingFromSettings` guard exactly as the fragment did. No business logic beyond what is moved.

**Verification:**

- `Glob` - `OperationsCaptureManager.kt` exists.
- `Grep` - `class OperationsCaptureManager` matches exactly once.
- `Grep` - `fun setup` and `fun render` present in the new file.
- `Grep` - `supportsMicRecording` present in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Files: helpers/OperationsCaptureManager.kt (New, 188 LOC). setup() + render(settings); pickDestination/refreshLabel injected as lambdas; inverted master flags preserved.

---

### Step 04.2 - Delegate from the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `private val captureManager by lazy { OperationsCaptureManager(binding, viewModel, this, mediaCapabilities, recordAudioPermissionLauncher, { isUpdatingFromSettings }, ::showDestinationPicker, ::refreshDestinationLabel) }`. Replace the `setupCaptureSection()` call with `captureManager.setup()`, and call `captureManager.render(settings)` from inside the `withSettingsUpdate { }` block in `observeData`. Delete `setupCaptureSection()` from the fragment and the capture-row blocks now handled by `captureManager.render`. Keep `recordAudioPermissionLauncher` registered in the fragment; its granted-callback may call `captureManager.render(viewModel.settings.value)` or set the toggle directly - preserve existing behaviour.

**Verification:**

- `Grep` - `captureManager.setup()` present in the fragment.
- `Grep` - `captureManager.render(` present in the fragment.
- `Grep` - `private fun setupCaptureSection` returns zero hits in the fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 3/3 PASS. Fragment delegates captureManager.setup()/render(settings); removed setupCaptureSection + capture render block + unused Manifest/ContextCompat imports.

---

### Step 04.3 - Compile

**Files:** -
**Depends on:** Step 04.2

**Prompt for developer:**

> Compile the touched area. Confirm `showDestinationPicker` / `refreshDestinationLabel` method references resolve as callbacks.

**Verification:**

- `/build` (or `.\a.ps1 fk`) exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - `.\a.ps1 fk` BUILD SUCCESSFUL. Neuroslop gate PASS.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog regeneration deferred to Phase 06.

---

## Handoff Notes to Next Phase

`showDestinationPicker` / `refreshDestinationLabel` remain fragment-level shared helpers (also used by Gestures in Phase 05 and the link-autodownload row in `setupViews`).

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
