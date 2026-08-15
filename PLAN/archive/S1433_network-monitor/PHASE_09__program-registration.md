# Phase 09 - Program registration

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 07
**Blocks:** Phase 10

---

## Objective

Register the Monitor as a screen-program everywhere the calculator is registered, plus the three section deep-links, so it appears when its setting is on and disappears when it is off.

---

## Prerequisites

- [x] Phase 07 ✅ Done - the Activity and the section keys exist.
- [x] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/panel/AppLaunchPanelRouteTarget.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainProgramsMenuCoordinator.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 60 |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_network_monitor.xml` | New | ≤ 40 |
| `docs/icons/icon-inventory.json` | Regenerated | - |

---

## Steps

### Step 09.1 - Add the icon

**Files:** `res/drawable/ic_network_monitor.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a vector drawable for the program, distinct at a glance from `ic_wifi` and from the app-settings gear, using `?attr` tinting rather than a baked-in colour.

**Why:**

Strategic §2 goal 5 gives the program its own icon like the calculator, and the existing S1405 note in `InternalRouteCatalog` records that two programs sharing a glyph become readable only by their caption.

**Verification:**

- `Glob` - the drawable exists.
- `Grep` - `="#` returns zero hits in the drawable.

**Status:** `[x]` done

---

### Step 09.2 - Register the route and its intent

**Files:** `core/panel/InternalRouteCatalog.kt`, `core/panel/AppLaunchPanelRouteIntents.kt`
**Depends on:** Step 09.1

**Prompt for developer:**

> Add `KEY_NETWORK_MONITOR` to the route catalog with the new label, the new icon and an intent that starts `NetworkMonitorActivity`, plus a settings intent pointing at the Operations settings section. Add the matching intent builder that accepts an optional `NetworkMonitorSection` and writes it into the launch intent.

**Why:**

Strategic §11 criterion 1 requires the program to appear in the approved launch points exactly like the calculator, and criterion 12 requires those points to be able to name a section.

**Verification:**

- `Grep` - `KEY_NETWORK_MONITOR` present in both files.
- `Grep` - the intent builder accepts a section argument.

**Status:** `[x]` done

---

### Step 09.3 - Add the section-addressed panel target

**Files:** `domain/model/panel/AppLaunchPanelRouteTarget.kt`, `domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt`, `domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt`
**Depends on:** Step 09.2

**Prompt for developer:**

> Add a sealed target variant carrying a section key, following the existing `Resource(resourceId)` shape, and decode it where the panel launches a tile. Resolve availability for `KEY_NETWORK_MONITOR` as available in build only when the injected `NetworkMonitorContract.isAvailableInBuild` is true, and enabled at runtime only when `AppSettings.enableNetworkMonitor` is true. Keep the seed order unchanged unless the owner asked for the tile by default.

**Why:**

The research pass found the calculator reports itself always-enabled at the panel layer while gating only the Programs menu on its setting, which strategic §11 criterion 1 forbids here: the Monitor must disappear from its launch points when the setting is off.

**Verification:**

- `Grep` - `isNetworkMonitorAvailable` referenced in `ResolvePanelRouteAvailabilityUseCase.kt`.
- `Grep` - `enableNetworkMonitor` referenced in the same resolution branch.

**Status:** `[x]` done

---

### Step 09.4 - Add the launcher starter cell

**Files:** `core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 09.3

**Prompt for developer:**

> Add `KEY_NETWORK_MONITOR` to the common padding keys, gated on the same route availability the other feature keys use. Add no status cell and no gadget here.

**Why:**

Strategic §6.10 keeps the ordinary program shortcut in this ticket and moves the status gadget and the widget to S1440, so adding either here would ship the surface the owner deferred.

**Verification:**

- `Grep` - `KEY_NETWORK_MONITOR` present in `LauncherStarterSets.kt`.
- `.\a.ps1 fu` - `LauncherStarterSetsTest` passes.

**Status:** `[x]` done

---

### Step 09.5 - Add the Programs menu entry

**Files:** `ui/main/helpers/MainProgramsMenuCoordinator.kt`, `ui/main/MainActivity.kt`
**Depends on:** Step 09.3

**Prompt for developer:**

> Extend the programs gate with the Monitor, add the popup item, its click handler and its remove handler, following the calculator's pattern where remove turns the setting off rather than deleting anything. Keep `MainActivity` at its current responsibility level - the new state is one field folded into the existing gate holder, nothing more.

**Why:**

Strategic §11 criterion 1 requires the program to leave every launch point when the setting is off, and the calculator's remove-turns-off behaviour is the existing contract for that.

**Verification:**

- `Grep` - the Monitor appears in the gate holder, the popup builder, the click branch and the remove branch.
- `MainActivity.kt` line count does not exceed its current value by more than 30.

**Status:** `[x]` done

---

### Step 09.6 - Add the settings rows

**Files:** `ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 09.5

**Prompt for developer:**

> Add the enable row for the Monitor next to the calculator row, using the canonical settings row component. Do not add the track-recording row here - it lives inside the GNSS section from Phase 08.

**Why:**

Strategic §2 goal 5 puts the enable path where the calculator's is, and §3.2 keeps the track switch next to the data it controls so its wording is read in context.

**Verification:**

- `Grep` - `enableNetworkMonitor` referenced in `OperationsSettingsFragment.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - exit 0 after the manifest regeneration in Phase 10.

**Status:** `[x]` done

---

### Step 09.7 - Regenerate the icon inventory

**Files:** `docs/icons/icon-inventory.json`
**Depends on:** Step 09.2

**Prompt for developer:**

> Regenerate the committed icon inventory so the new route icon is recorded, then run the inventory test.

**Why:**

`IconInventoryExportTest` is a freshness gate that fails the build on a stale inventory, so a new route icon without a regeneration breaks every later build.

**Verification:**

- `.\a.ps1 fu` - `IconInventoryExportTest` passes.
- `Grep` - `ic_network_monitor` present in `docs/icons/icon-inventory.json`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 09.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fc` passed.
- [manual - deferred] Turning the setting off removes the Monitor from the Programs menu, the launch panel and the launcher cell - requires an online device.
- [x] A `lite` build compiles and shows no Monitor anywhere - `check-standard-fast.ps1 -Flavor Lite` passed.
- [x] `Grep` for `TODO(phase-09)` returns zero hits.
- [x] Dev log entry added for the phase.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The settings source is `fragment_settings_destinations.xml`, not a dedicated Operations layout. The
portrait and landscape rows are therefore both part of this phase, despite the original file list omitting
them. S1440 will add the status gadget and the home widget on top of the section keys registered here.

---

## Rollback Plan

Revert phase commit(s) - the screens survive but become unreachable, which is the pre-phase state.
