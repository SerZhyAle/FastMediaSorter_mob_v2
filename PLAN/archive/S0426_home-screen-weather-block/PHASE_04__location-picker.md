# Phase 04 - Location picker

**Strategic spec:** [`../S0426_home-screen-weather-block.md`](../S0426_home-screen-weather-block.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Let the user name the place: a keyless city search dialog that supplies the gadget's location param when the cell is added, and re-supplies it on long press.

---

## Prerequisites

- [ ] Phase 03 ✅ Done - the gadget renders and reads its param.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/dialog_launcher_weather_location.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherWeatherLocationDialogFragment.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/weather/SearchWeatherLocationsUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 400 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 600 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 1500 |

> `LauncherHomeActivity.kt` is over 500 LOC - take the timestamped backup into `temp/S0426/` before editing it (Rule 5).

---

## Steps

### Step 04.1 - Add the search use case

**Files:** `domain/usecase/weather/SearchWeatherLocationsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `class SearchWeatherLocationsUseCase @Inject constructor(private val repository: WeatherRepository)` exposing `suspend operator fun invoke(query: String): List<WeatherLocation>`. Blank or single-character queries return an empty list without a network call.

**Verification:**

- `Grep` - `class SearchWeatherLocationsUseCase` matches exactly once.

**Status:** `[x] done`

---

### Step 04.2 - Build the picker dialog

**Files:** `res/layout/dialog_launcher_weather_location.xml`, `ui/launcher/picker/LauncherWeatherLocationDialogFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> `@AndroidEntryPoint` `DialogFragment` with a text field, a search button and a results list (a plain `RecyclerView` or a `MaterialAlertDialog` single-choice list - whichever matches the neighbouring launcher dialogs). Search runs in `lifecycleScope` through `SearchWeatherLocationsUseCase`, shows the searching / empty / failed strings from Phase 02, and returns the picked place through `setFragmentResult` with the `WeatherLocation.encode()` string. The dialog is reused for both entry points, so it takes a request key and an optional cell id as arguments. Confirm/cancel buttons use the named styles per CLAUDE.md §11 (`Widget.FastMediaSorter.Button.DialogConfirm` / `.DialogCancel`). Every control is focusable and reachable by D-pad (Rule 16). Strings must satisfy `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `setFragmentResult` present in the fragment.
- `Grep` - `="#` returns zero hits in the layout.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 04.3 - Persist a changed target

**Files:** `domain/repository/LauncherDesktopRepository.kt`, `data/repository/LauncherDesktopRepositoryImpl.kt`, `ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `suspend fun updateCellTarget(id: Long, target: String): Boolean` to the repository, mirroring `moveCell`/`resizeCell`: read the entity by id via the DAO, return false when it is absent, otherwise `update()` the copy with the new target. Expose it on `LauncherHomeViewModel` as a `viewModelScope` action so the activity never calls the repository. Geometry is untouched, so no overlap check is needed - say so in one KDoc line.

**Verification:**

- `Grep` - `updateCellTarget` present in the interface, the impl and the ViewModel.
- `Grep` - `runBlocking` returns zero hits in the touched files.

**Status:** `[x] done`

---

### Step 04.4 - Wire both entry points

**Files:** `ui/launcher/LauncherHomeActivity.kt`, `ui/launcher/gadget/WeatherGadget.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> In `onGadgetChosen`, branch `KEY_WEATHER` to the picker before `placeGadget` (mirroring the existing `KEY_PLAYLIST` resource branch), then place the gadget with the returned encoded param. Register the fragment-result listener once in `onCreate`, next to the other picker listeners. For re-picking, give `WeatherGadgetView` a long-press handler that shows the same dialog with the cell id, and route its result through `viewModel.updateCellTarget`; the gadget view reaches the fragment manager through its context - resolve it as `FragmentActivity` and no-op when it is not one, rather than casting blindly. Long press outside edit mode is free (the edit scrim in `LauncherCellViewBinder.decorateForEdit` swallows gestures while editing, so drag is unaffected).

**Verification:**

- `Grep` - `KEY_WEATHER` present in `LauncherHomeActivity.kt`.
- `Grep` - `setOnLongClickListener` present in `LauncherHomeActivity.bindGadget` (corrected during implementation: the cell id lives at the host, so wiring the long press there avoids handing every gadget its database row).
- `.\a.ps1 fc` - exit code 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` exit 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the phase.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The feature is complete end to end: add a weather cell, name a place, see the weather, change the place, tap through to the weather app.

---

## Rollback Plan

Revert phase commit(s). Cells persisted with a weather target survive as gadget cells that show the no-location message - no schema change to undo.

---

## Step Log

- 2026-07-24 - Verification 4/4 PASS. Search use case, picker dialog, updateCellTarget through repository + ViewModel, both entry points wired. `..ps1 fc` exit 0.
