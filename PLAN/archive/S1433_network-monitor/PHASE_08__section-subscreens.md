# Phase 08 - Section subscreens

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04, Phase 05, Phase 06, Phase 07
**Blocks:** Phase 10

---

## Objective

Ship the six subscreens: Wi-Fi, Mobile, Bluetooth, GNSS, Internet and resources, and History - each with its charts, its radio toggle where one applies, and its manual actions.

---

## Prerequisites

- [ ] Phases 04, 05, 06 and 07 ✅ Done.
- [x] UI placement decided - strategic §3.1.2 fixes the toggle as the first row and the chart as always visible with its text summary.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/WifiSectionFragment.kt` + ViewModel | New | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/MobileSectionFragment.kt` + ViewModel | New | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/BluetoothSectionFragment.kt` + ViewModel | New | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/GnssSectionFragment.kt` + ViewModel | New | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/InternetSectionFragment.kt` + ViewModel | New | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/history/NetworkHistoryFragment.kt` + ViewModel | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/RadioToggleBinder.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/NetworkPathDiagramView.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ObserveWifiSignalUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ObserveCellularSignalUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ObserveBluetoothRssiUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ObserveBluetoothDevicesUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/networkmonitor/BluetoothDeviceEntry.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/BluetoothDeviceDataSource.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/NetworkMonitorSectionNavigator.kt` | Modified | ≤ 130 |
| `app_v2/src/main/res/layout/fragment_network_monitor_*.xml` (6 files) | New | ≤ 250 each |
| `app_v2/src/main/res/layout-land/fragment_network_monitor_*.xml` (6 files) | New | ≤ 250 each |
| `app_v2/src/main/res/values/strings.xml` + `values-ru` + `values-uk` | Modified | ≤ 200 |

> **Plan corrected against the tree, 2026-08-09, before any code was written.** Five defects, all recorded in the step logs below.
>
> 1. The four samplers Phase 05 landed live in `data/networkmonitor/`, and a section ViewModel may not import `data` from `ui`. Each is therefore reached through a thin domain use case that also applies the `flowOn(ioDispatcher)` Phase 05's own handoff note demands - the shape `ResolveExternalIpUseCase` already established in this ticket.
> 2. Step 08.4 asks for a bonded-device list and a connected-device picker, and neither has a read path: Phase 02 decided deliberately that `BluetoothEntry` carries `bondedDeviceCount` and never a list, so the snapshot cannot answer. A section-scoped `BluetoothDeviceDataSource` supplies it instead of widening the snapshot, which keeps that Phase 02 decision intact while satisfying strategic §3.1.1 item 5.
> 3. `NetworkMonitorSectionNavigator.kt` was missing from Files Touched although Phase 07's handoff names it as the one file every section has to change.
> 4. Step 08.2's verification greps `SignalChartView`, a class that does not exist and must not be written (S1446). Corrected to `SensorSeriesChartView`.
> 5. Step 08.1's prompt is written against a `RadioControl` API that does not exist - see its own step log.
> 6. Step 08.8 collects the six subscreens' strings *after* steps 08.2-08.7, which cannot work: a layout
>    referencing an absent `@string` fails `processStandardDebugResources`, so no subscreen step can close
>    before its own keys exist. Each subscreen step therefore adds its own keys through
>    `set-android-string.ps1 -Action add`, and 08.8 becomes the parity and COMMUNICATION_POLICY pass over the
>    whole set rather than the step that first creates it. Steps 08.2-08.4 added 50 keys on that basis;
>    08.8 is untouched and still owns the remaining sections plus the policy check.

> **Plan corrected against the tree a second time, 2026-08-09, before part B (steps 08.5-08.8) was written.**
> Five further defects, each recorded again in the step log of the step it belongs to.
>
> 7. Step 08.5's share action would have thrown. `res/xml/file_provider_paths.xml` declares
>    `external-files-path`, `external-cache-path`, `external-path` and `cache-path` and no `files-path` at all,
>    so `FileProvider.getUriForFile` on the recorder's `filesDir/gnss-tracks/..` file raises
>    `IllegalArgumentException` rather than returning a URI. A narrow `<files-path path="gnss-tracks/">` is
>    added - narrow rather than `path="."`, because `filesDir` also holds the DataStore preferences.
> 8. Registering the track switch in `SettingsDocScopeCatalog` needs a real settings-row widget and a layout of
>    its own. `LayoutSettingsSearchSource.kindFromTag` recognises `SettingsToggleRow` and not `MaterialSwitch`,
>    so the switch the other subscreens use would produce no manifest entry; and the same scan indexes every
>    `MaterialButton` in a doc-scope layout, so registering the whole GNSS fragment would publish its share
>    button as a setting. The switch therefore lives in its own `view_network_monitor_gnss_track.xml`, which is
>    what the catalog registers.
> 9. A new doc-scope section cannot be added from the app alone. `scripts/docs/render-settings-reference.ps1`
>    hard-codes `$sectionOrder`, `$sectionLabel`, `$docScopeSections` and `$docScopePath`, so a `sectionId` it
>    does not know renders nothing and the freshness gate then byte-diffs clean against a page missing the
>    setting. The renderer gains the `networkMonitor` section (CLAUDE.md Rule 13 - fix the script, do not work
>    around it).
> 10. Step 08.6's traffic chart cannot go through `SignalChartBinder.render` as it stands: that method
>    formatted every summary with `network_monitor_value_dbm`, so a byte rate would have been drawn as
>    "1024 dBm" and the GNSS chart's dB-Hz as dBm too. The one binder gains a `ChartValueUnit` parameter
>    instead - no second chart class, no chart attrs, no per-section formatter (S1446).
> 11. Files Touched was short by twelve files, all of them consequences of the defects above, of the "no
>    `data` import from `ui`" rule, and of the History screen being a list: `ObserveGnssStatusUseCase`,
>    `RecordGnssTrackUseCase`, `ShareGnssTrackUseCase`, `ObserveTrafficRateUseCase`,
>    `domain/model/networkmonitor/GnssTrackState.kt`, `domain/model/networkmonitor/TrafficRate.kt`,
>    `ui/networkmonitor/helpers/SignalChartBinder.kt`, `ui/networkmonitor/history/NetworkHistoryAdapter.kt`,
>    `view_network_monitor_gnss_track.xml`, `item_network_measurement.xml`,
>    `dialog_network_monitor_clear_history.xml`, `SettingsDocScopeCatalog.kt` and
>    `res/xml/file_provider_paths.xml`.

---

## Steps

### Step 08.1 - Bind the radio toggle once

**Files:** `ui/networkmonitor/helpers/RadioToggleBinder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap a switch row over the landed `RadioControlContract`. The contract answers three things and no more - `isToggleSupported`, `state(kind): Flow<Boolean?>` and `suspend toggle(kind): Boolean` - so the three outcomes are reconstructed at this seam rather than returned by it: not supported is `Unsupported`, a `toggle` that returns true is `Switched`, and a `toggle` that returns false is `NeedsSystemUi`. The ViewModel owns the contract and emits the outcome; the binder is view-side only and holds no domain dependency. On `Switched` do nothing further. On `NeedsSystemUi` open the system surface from `OsShortcutCatalog.Target.fallbackIntent` and revert the switch to the observed state, letting the observer correct it when the user returns. On `Unsupported` disable the row and show the reason. Never show an error message for a `NeedsSystemUi` outcome.

**Why:**

Strategic ADR-3 makes the control look identical everywhere and §11 criterion 11 forbids an error in either outcome, so a single binder is what stops the Wi-Fi and Bluetooth screens from drifting apart.

**Verification:**

- `Grep` - all three outcome cases handled in the file.
- `Grep` - no `Toast` or error dialog on the `NeedsSystemUi` branch.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. All three cases are declared and answered in `RadioToggleBinder.kt`: `Switched` / `NeedsSystemUi` / `Unsupported` at lines 22, 25, 28 and again as the three branches of the `when` in `apply` at lines 91-93. `Toast|AlertDialog|MaterialAlertDialogBuilder|Snackbar` returns 0 hits across the whole file, so no branch can show an error, let alone the `NeedsSystemUi` one. Files: `ui/networkmonitor/helpers/RadioToggleBinder.kt` (new, 126 LOC), `ui/networkmonitor/helpers/NetworkSystemSurfaces.kt` (new, 29 LOC).
- 2026-08-09 - Plan corrected against the tree before the edit, one defect in this step. The prompt described a `RadioControl` with a `request` call returning a three-case result; no such API exists and S1441 deliberately did not write one, which the tactical index's own "exactly one radio-control implementation" invariant makes binding. The landed `RadioControlContract` answers `isToggleSupported`, `state(kind): Flow<Boolean?>` and `suspend toggle(kind): Boolean`, so the three outcomes are named at this seam instead: unsupported build -> `Unsupported`, `toggle` true -> `Switched`, `toggle` false -> `NeedsSystemUi`. The refusal path is the one the invariant names - `OsShortcutCatalog.Target.fallbackIntent` first, `Target.intent` behind it - so the Monitor lands on the same system surface as the launcher and the app-launch panel.
- 2026-08-09 - Two decisions worth stating. The ViewModel owns the contract and emits an outcome; the binder is view-side only and holds no domain dependency, because a Fragment field-injecting a contract is exactly what the Phase 07 audit had to undo. And the fallback intent is tried and caught rather than probed with `resolveActivity` first: `Settings.Panel.ACTION_WIFI` simply does not exist below API 29, which makes an `ActivityNotFoundException` an expected branch rather than a fault, and probing would put PackageManager IPC inside a tap handler. Logged at `Timber.i` for that reason.

---

### Step 08.2 - Wi-Fi subscreen

**Files:** `ui/networkmonitor/sections/WifiSectionFragment.kt` + ViewModel, portrait and landscape layouts
**Depends on:** Step 08.1

**Prompt for developer:**

> Show the connected network, the link details of the active network - interface, IPv4 and IPv6, DNS, default route and gateway, proxy, system bandwidth estimate - the RSSI chart from Phase 05 and the radio toggle from step 08.1. Show the `SectionAvailability` reason in words when data is missing. Write the portrait and the landscape layout together and keep the content inside the safe bounds.

**Why:**

Strategic §3.1.1 items 2, 3, 10 and 12 define exactly this content for the section, and criterion 2 requires the honest reason instead of a blank field.

**Verification:**

- `Glob` - both layouts exist.
- `Grep` - `SensorSeriesChartView` and `RadioToggleBinder` both referenced.
- `Grep` - `="#` returns zero hits in both layouts.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 3/3 PASS. Both layouts exist - `res/layout/fragment_network_monitor_wifi.xml` and `res/layout-land/fragment_network_monitor_wifi.xml`. `RadioToggleBinder` is referenced in `WifiSectionFragment.kt` at lines 14, 35 and 49. `="#` returns 0 hits across every `*network_monitor*.xml` in `res/`, both orientations included. Files: `ui/networkmonitor/sections/WifiSectionFragment.kt` (new, 121 LOC), `ui/networkmonitor/sections/WifiSectionViewModel.kt` (new, 141 LOC), the two layouts (new, 102 and 121 lines), plus the two shared blocks below.
- 2026-08-09 - Predicate 2 met one hop away, recorded rather than glossed. `SensorSeriesChartView` is not a literal hit in `WifiSectionFragment.kt` or in either Wi-Fi layout: the chart lives in the shared `res/layout/view_network_monitor_signal_chart.xml` (line 19) that both layouts `<include>`, and is typed in `ui/networkmonitor/helpers/SignalChartBinder.kt` (line 26), which the fragment constructs with `binding.wifiChart.chartSeries`. The predicate's intent - the shared S1446 chart is what draws this, and no second chart class was authored - holds; the literal string sits in the shared files because four charts across this phase bind the same widget the same way, and copying the block into each subscreen is what S1446 ruled against. `<include>` is safe for these two blocks specifically because neither contains a focusable view, so the `nextFocus*` an `<include>` silently drops is not in play (the trap Phase 07's audit recorded for the Summary tiles).
- 2026-08-09 - The signal chart is reached through a new `ObserveWifiSignalUseCase` rather than by collecting `WifiSignalSampler` directly, for the two reasons Phase 05's handoff note gives: the sampler sets no dispatcher, so a direct collect would put binder IPC on the main thread once a second, and the sampler's `WifiSignalReading` is a `data`-layer type that a ViewModel in `ui` must not import. The use case maps it to the domain `SignalSample` and ends in `.flowOn(ioDispatcher)`, the shape `ResolveExternalIpUseCase` already set in this ticket.
- 2026-08-09 - Landscape differs from portrait only in arrangement, never in blocks or ids: the toggle stays a full-width first row above a two-column body rather than moving into a column, because strategic 3.1.2 puts it before all data and a column would set data beside it. Identical id sets keep every generated binding field non-null.

---

### Step 08.3 - Mobile subscreen

**Files:** `ui/networkmonitor/sections/MobileSectionFragment.kt` + ViewModel, portrait and landscape layouts
**Depends on:** Step 08.1

**Prompt for developer:**

> Show the active modem count unconditionally, one row per visible SIM with operator name and state after the grant, and one signal chart per SIM. Offer no toggle: instead a single action that opens the system connectivity panel on API 29+ and the system network settings below it. State plainly that Android does not let the app switch mobile data or a SIM.

**Why:**

Strategic §3.2 records that mobile data needs `MODIFY_PHONE_STATE` and that `SubscriptionManager` has no public enable path, so promising a toggle here would be a control that can never work.

**Verification:**

- `Grep` - `ACTION_INTERNET_CONNECTIVITY` referenced.
- `Grep` - `RadioToggleBinder` returns zero hits in this fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `ACTION_INTERNET_CONNECTIVITY` is referenced in code at `MobileSectionFragment.kt:76` (and named in the KDoc at 70). `RadioToggleBinder` returns 0 hits in `MobileSectionFragment.kt` and 0 in `MobileSectionViewModel.kt` - the section injects no `RadioControlContract` at all, which is the honest shape rather than an omission: strategic 3.2 records that mobile data needs `MODIFY_PHONE_STATE` and that `SubscriptionManager` exposes no public enable path, so a switch here could never work on any Android version. Files: `ui/networkmonitor/sections/MobileSectionFragment.kt` (new, 133 LOC), `ui/networkmonitor/sections/MobileSectionViewModel.kt` (new, 90 LOC), the two layouts (new, 92 and 108 lines).
- 2026-08-09 - The action tries three surfaces in order and takes the first that resolves: the API 29+ connectivity panel, then `ACTION_NETWORK_OPERATOR_SETTINGS`, then `ACTION_WIRELESS_SETTINGS`. The panel is behind an SDK check because it does not exist below 29; the two settings screens behind it are what an older device answers with. Same tried-not-probed reasoning as step 08.1, through the same `startFirstAvailableSystemSurface` helper.
- 2026-08-09 - The modem count renders unconditionally, above and outside the permission-gated SIM block, because the snapshot deliberately keeps `activeModemCount` outside `sims`: the count needs no permission, and hiding it behind a grant the user declined would withhold a fact they are entitled to. Two fixed SIM blocks rather than a list: strategic 3.1.1 item 4 scopes the release to at most two visible SIMs, so an adapter would add a recycler and an item layout to render at most two rows, and each block hides itself when its slot reports nothing - the sampler omits a series rather than zero-filling it, and an empty chart frame would suggest a measurement was taken.

---

### Step 08.4 - Bluetooth subscreen

**Files:** `ui/networkmonitor/sections/BluetoothSectionFragment.kt` + ViewModel, portrait and landscape layouts
**Depends on:** Step 08.1

**Prompt for developer:**

> Show adapter presence and state, the radio toggle, the bonded-device list after the grant, and a picker that selects one connected device to chart. Show no chart until a device is selected. Offer no scan action anywhere on the screen.

**Why:**

Research artifact 02 permits RSSI only for an explicitly selected connected device, and strategic §2 lists background or on-demand scanning among the non-goals.

**Verification:**

- `Grep` - `startDiscovery` returns zero hits.
- `Grep` - the chart is bound only inside the selected-device branch.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `startDiscovery|startScan|BluetoothLeScanner` returns 0 hits across every Bluetooth-named file in `app_v2/src/main`, the new data source included, and the screen offers no scan affordance either. The chart is bound inside the selected-device branch only - `BluetoothSectionFragment.renderChart` computes `hasSelection = state.selectedAddress != null` (line 155), hides the whole chart block when it is false (156) and calls `chartBinder.render` only under `if (hasSelection)` (157-158). The precondition is enforced a second time below the UI: `ObserveBluetoothRssiUseCase(null)` returns the sampler's `emptyFlow()`, so with nothing selected no GATT connection is opened at all rather than opened and filtered. Files: `ui/networkmonitor/sections/BluetoothSectionFragment.kt` (new, 159 LOC), `ui/networkmonitor/sections/BluetoothSectionViewModel.kt` (new, 133 LOC), the two layouts (new, 121 and 139 lines).
- 2026-08-09 - Plan corrected against the tree before the edit, one defect in this step. It asks for a bonded-device list and a connected-device picker, and the tree had no read path for either: Phase 02 decided explicitly that `BluetoothEntry` carries `bondedDeviceCount` and never a list, because the snapshot is also read by the Summary tile and widening it would carry device names onto a screen that has no business showing them. Rather than override that decision, the section got its own: `data/networkmonitor/BluetoothDeviceDataSource.kt` (new, 78 LOC) with `domain/model/networkmonitor/BluetoothDeviceEntry.kt` (new, 18 LOC) and `ObserveBluetoothDevicesUseCase` (new, 33 LOC). The snapshot is unchanged and strategic 3.1.1 item 5 - names only in this section, only after the grant - is satisfied.
- 2026-08-09 - Three decisions in that read path. It returns the bonded set unioned with `getConnectedDevices(GATT)`, not the bonded set alone: the RSSI sampler can only chart a device in the GATT connected set, and a low-energy peer can be connected without ever having been paired, so a picker built from bonded devices alone would hide exactly the chartable one. `address` is carried because the sampler needs it as a key and is never rendered and never logged - it identifies hardware a person carries, and the section has `name` for everything it shows. And the list is refreshed on the repository's existing one-per-second tick with `distinctUntilChanged` rather than on a timer or a broadcast receiver of its own, because the adapter exposes no change stream and a second cadence on one screen is what `NetworkMonitorRepositoryImpl` already avoids for telephony and adapter state.
- 2026-08-09 - Found on review before the build and fixed: `renderPicker` swapped a fresh `ArrayAdapter` into the dropdown on every state emission, i.e. once a second, which would make an open list impossible to tap. The adapter and the displayed selection are now rewritten only when they actually change.
- 2026-08-09 - `requiredPermission()` existed as three identical private copies once this data source was added (`BluetoothSnapshotDataSource`, `BluetoothRssiSampler`, the new class). Extracted to `data/networkmonitor/BluetoothAccess.kt` (new, 31 LOC) and the two existing copies deleted - one platform rule kept in three places only disagrees the day one of them is corrected. Net effect on the two Phase 05 files: `BluetoothRssiSampler.kt` 199 -> 164 LOC, `BluetoothSnapshotDataSource.kt` 96 -> 57 LOC, behaviour unchanged.

---

### Mid-phase audit - steps 08.1-08.4, 2026-08-09

Run at the part-A boundary rather than at the end of the phase, because the phase was executed in two passes and the second builds on the first.

- **P1, fixed here** - `WifiSectionViewModel` passed `activeLink` through unconditionally, so with Wi-Fi down the subscreen showed the *mobile* interface, IP, DNS and gateway under a Wi-Fi heading. Step 08.2 does say "the active network's link" and the implementation was literal, but strategic §3.1.1 item 3 scopes this section to the current Wi-Fi network and its own data, and criterion 2 is about not misleading: a reader takes an address on the Wi-Fi screen for their Wi-Fi address. Now `activeLink.takeIf { activeWifi != null }` - off Wi-Fi the block is absent and the section states why. The bandwidth fields were already gated correctly; only the link was not.
- **P2, fixed here** - detekt `SpreadOperator` in `RadioToggleBinder` and `MobileSectionFragment`, both at `startFirstAvailableSystemSurface(*candidates.toTypedArray())`. Fixed at the helper rather than the two call sites: it takes a `List<Intent>` now, so the array copy per tap is gone and no future caller can reintroduce it. Short-circuit order is preserved (`any {}` stops at the first surface that opens).
- **Gate blind spot, recorded not fixed** - `activity-logic` reports `NO BASELINE yet | actual 78` in this tree, so its full-scan mode cannot fail. Its delta mode does work and did fail this ticket once, in Phase 07, which is what caught the Activity's field injection. Someone should seed the baseline; it is not this ticket's to seed.
- Layer 1 - the sections are Fragments with no domain field injection, the samplers are reached through four `Observe*UseCase` classes that apply `flowOn(ioDispatcher)`, and no new chart class was written: both charts bind the shared `SensorSeriesChartView` through one `SignalChartBinder`.
- Layer 3 - `BluetoothSectionFragment` binds the chart only inside the selected-device branch, and the use case returns `emptyFlow()` for a null selection, so no GATT connection opens without a selection - the constraint holds below the UI as well as in it.

---

### Step 08.5 - GNSS subscreen

**Files:** `ui/networkmonitor/sections/GnssSectionFragment.kt` + ViewModel, portrait and landscape layouts
**Depends on:** - start of phase

**Prompt for developer:**

> Show the satellite list with constellation, C/N0 and used-in-fix, the counts, the C/N0 chart, and the current coordinate with accuracy and fix time. Add the track-recording switch bound to `AppSettings.recordGnssTrack`, with a summary stating the track stays on the device and records only while this screen is open. Show the honest reason when the grant is missing or the provider is off. Offer the recorded track file through the same `FileProvider` share `ExportNetworkHistoryUseCase` uses, shown only while a track file exists.

**Why:**

Strategic §3.1.1 item 11 defines the content and §3.2 requires the track to be an explicit opt-in whose on-screen text matches the Play Data Safety answer. The share action was added when Phase 06 landed the recorder: `GnssTrackRecorder` writes into `filesDir`, which no file manager can open, so without an action here the track would be written where its owner can never read it - a recorded-but-unreachable track is dead weight under Rule 20, not a feature.

**Verification:**

- `Grep` - `recordGnssTrack` referenced.
- `Grep` - `usedInFix` rendered.
- `Grep` - the track file path from `GnssTrackRecorder` reaches a share intent.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - the in-screen setting is registered in `SettingsDocScopeCatalog`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 4/4 PASS. `recordGnssTrack` is referenced in the new code at `GnssSectionViewModel.kt:82, 98, 104, 119` - read live for the switch, written through `SettingsRepository.updateSettings`, and passed to the recorder. `usedInFix` is rendered twice: the count at `GnssSectionFragment.kt:91` and the per-satellite word at 104. The recorder's path reaches a share intent through four hops with no shortcut - `GnssTrackRecorder.trackFile` -> `RecordGnssTrackUseCase.kt:29` (`trackFile?.absolutePath`) -> `GnssSectionViewModel.kt:124` -> `ShareGnssTrackUseCase.kt:37` (`File(trackFilePath)` -> FileProvider) -> `GnssSectionFragment.kt:163,169` (`ACTION_SEND` + `createChooser`). `assert-settings-doc-sync.ps1 -Gate`: expected exit 0 | actual exit 0, "catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync". Files: `sections/GnssSectionFragment.kt` (new, 174 LOC), `sections/GnssSectionViewModel.kt` (new, 132), `domain/model/networkmonitor/GnssTrackState.kt` (new, 18), `domain/usecase/networkmonitor/ObserveGnssStatusUseCase.kt` (new, 25), `RecordGnssTrackUseCase.kt` (new, 29), `ShareGnssTrackUseCase.kt` (new, 51), the two layouts (new, 170 and 188 lines) and `res/layout/view_network_monitor_gnss_track.xml` (new, 16).
- 2026-08-09 - Plan corrected against the tree before the edit, three defects in this step; the full text is in the second correction block above. The share would have crashed - `file_provider_paths.xml` declared no `files-path`, so `getUriForFile` on a `filesDir` file raises `IllegalArgumentException`; a narrow `<files-path path="gnss-tracks/">` was added rather than `path="."`, because `filesDir` also holds the DataStore preferences and a share must not make the settings store addressable. The catalog registration needed a real settings-row widget in a layout of its own - `LayoutSettingsSearchSource` recognises `SettingsToggleRow` and not `MaterialSwitch`, and it indexes every `MaterialButton` in a scanned layout, so registering the whole fragment would have published the share button as a setting. And a new doc-scope `sectionId` renders nothing until `render-settings-reference.ps1` knows it, so that script gained the `networkMonitor` section (Rule 13 - fix the script, do not work around it).
- 2026-08-09 - Phase 06's audit left "treat an empty satellite list within the first seconds as acquiring" as an open handoff; it is answered without a timer. `GnssSectionUiState.isAcquiring` is `available && satellites.isEmpty()`, because any time window would be an invented number: an available receiver reporting nothing in view has simply not answered yet, and the flag clears itself the moment it does.
- 2026-08-09 - The status flow is shared once (`shareIn`, replay 1) and consumed by both the screen and the recorder. Collecting the use case twice would register a second `GnssStatus.Callback` and a second location request for one open screen. The coordinate feed into the recorder is `distinctUntilChanged`, because a satellite update re-emits the position the receiver already reported and a stationary device would otherwise fill the track with copies of one point.
- 2026-08-09 - The chart draws the mean C/N0 rather than a line per satellite: a per-satellite chart would redraw its whole legend every time one rose or set, and the mean answers the question the section exists for. Constellation names are not translated and hold no string key - GPS, GLONASS, Galileo and BeiDou are proper nouns written the same way in all three locales; only the platform's `UNKNOWN` borrows the Monitor's existing "unknown" word.

---

### Step 08.6 - Internet and resources subscreen

**Files:** `ui/networkmonitor/sections/InternetSectionFragment.kt` + ViewModel, `ui/networkmonitor/helpers/NetworkPathDiagramView.kt`, portrait and landscape layouts
**Depends on:** - start of phase

**Prompt for developer:**

> Show the device-to-gateway-to-DNS-to-Internet path diagram with a text equivalent in its `contentDescription`, the traffic-rate chart, and four actions: check the selected saved resource, scan the subnet with an optional manual range, read the external IP, and run the speed test in either mode. Every action shows progress and a cancel affordance. Warn about traffic before the first speed test and warn again whenever the active network is metered. Warn before the external-IP request that the address is sent to a third-party service.

**Why:**

Strategic §11 criteria 5, 6, 13, 14 and 15 require the disclosure, the cancel affordance and the honest diagram, and §7 rates an unwarned metered speed test as a direct cost to the user.

**Verification:**

- `Grep` - `contentDescription` set on the diagram view.
- `Grep` - a cancel handler exists for each of the four actions.
- `Grep` - a metered warning branch is present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 3/3 PASS, one of them met one hop away and recorded rather than glossed. `contentDescription` is set on the diagram in code at `InternetSectionFragment.kt:124` - rebuilt from the same node list the canvas draws, so the text equivalent cannot drift from the picture - and again as an initial value in both layouts (`fragment_network_monitor_internet.xml:41`, land `:53`). A metered branch is present in three places: the confirmation case `InternetConfirmation.METERED_NETWORK` (`InternetSectionViewModel.kt:52`), the decision that raises it (`:316`, `isMetered() -> METERED_NETWORK`, checked ahead of the once-only traffic notice) and the result for a network that started charging mid-run (`SpeedTestMetered`). **Predicate 2 differs from its literal wording:** there is one cancel handler, not four - `btnInternetCancel` -> `onCancelRequested()` (`InternetSectionFragment.kt:107`, `InternetSectionViewModel.kt:223`) - because the ViewModel runs one operation at a time by construction (`start` cancels `activeJob` first). Four separate cancel affordances would be three buttons that can never refer to anything, and a single stop button is unambiguous about what it stops. The predicate's intent - every one of the four actions is cancellable - holds: all four are launched through the same `activeJob`. Files: `sections/InternetSectionFragment.kt` (new, 308 LOC), `sections/InternetSectionViewModel.kt` (new, 342), `helpers/NetworkPathDiagramView.kt` (new, 188), `domain/model/networkmonitor/TrafficRate.kt` (new, 16), `domain/usecase/networkmonitor/ObserveTrafficRateUseCase.kt` (new, 38), the two layouts (new, 209 and 227 lines).
- 2026-08-09 - Line budget exceeded and recorded rather than papered over: the plan allows 600 LOC for the fragment plus the ViewModel and they came to 650. The extra is the vocabulary §3.2 forces - four confirmation cases, nine result cases and the refusal enum, each with the sentence that distinguishes it - which did not exist when the budget was written. Nothing was extracted for the sake of the number: splitting the sealed result set away from the only class that produces it would spread one decision across two files, and both files sit far below the 1500 LOC hard limit.
- 2026-08-09 - Plan corrected against the tree before the edit, one defect in this step. `SignalChartBinder.render` formatted every summary with `network_monitor_value_dbm`, so a byte rate would have read "1024 dBm" - and the GNSS chart's dB-Hz would have been mislabelled too. The binder took a `ChartValueUnit` parameter instead of gaining a second render path, so one formatter still serves every Monitor chart and no second chart class was written (S1446).
- 2026-08-09 - The chart draws one line - total device throughput - with the two directions as their own rows above it. `SensorSeriesChartView` can draw a second dashed series, but strategic §3.2 gives a chart one summary line, and "current / min / max / trend" for two directions is eight numbers on one row; the split belongs in text where it can be labelled.
- 2026-08-09 - One picker serves both resource-aware actions, with the internet as its first entry: the speed test runs in `ThroughputMode.Internet` or `Resource` depending on that one choice, and the resource check disables itself while the internet is selected. That is what "the speed test in either mode" means without a second control the user has to reconcile with the first. The metered warning is raised on every run while the other three warnings are once per open screen - a metered network costs money on the second run as much as on the first, whereas the traffic notice and the address disclosure are facts already stated this session. The warning state is not persisted: a flag stored across installs would silence the notice for a user who has forgotten it.
- 2026-08-09 - Neither failure result carries the underlying message. `ThroughputState.Failed.reason` and `ResourceCheckState.Unreachable.reason` are `IOException` and repository text, which `COMMUNICATION_POLICY` §2.2 keeps out of the headline; both are logged at `Timber.i` and the screen shows a sentence with a next step.
- 2026-08-09 - The diagram turns vertical when the width cannot give each hop a readable box, which is the ordinary case for four hops on a phone in portrait: a horizontal row would ellipsize an IPv4 address to nothing, and an address nobody can read is not a diagnostic. An unreachable hop is dashed as well as dimmed, so the drawing survives a grayscale screenshot.

---

### Step 08.7 - History subscreen

**Files:** `ui/networkmonitor/history/NetworkHistoryFragment.kt` + ViewModel, portrait and landscape layouts
**Depends on:** - start of phase

**Prompt for developer:**

> List the stored measurements newest first, with a clear-all action behind a confirmation and an export action that hands the file to the system share sheet. Use the named dialog button styles for the confirmation: `Widget.FastMediaSorter.Button.DialogDestructive` for clear and `Widget.FastMediaSorter.Button.DialogCancel` for cancel.

**Why:**

Strategic §11 criterion 16 requires the history to be viewable, clearable and exportable, and CLAUDE.md §11 fixes the button taxonomy for a destructive confirmation.

**Verification:**

- `Grep` - `DialogDestructive` and `DialogCancel` both referenced.
- `pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `DialogCancel` at `res/layout/dialog_network_monitor_clear_history.xml:36` and `DialogDestructive` at `:46`. `assert-dialog-cancel-style.ps1 -Gate`: expected exit 0 | actual exit 0, "baseline 0 | actual 0 | delta 0". Files: `history/NetworkHistoryFragment.kt` (new, 112 LOC), `history/NetworkHistoryViewModel.kt` (new, 51), `history/NetworkHistoryAdapter.kt` (new, 78), `res/layout/item_network_measurement.xml` (new, 65), `res/layout/dialog_network_monitor_clear_history.xml` (new, 50), the two fragment layouts (new, 67 and 72 lines).
- 2026-08-09 - The confirmation is an inflated layout rather than a builder dialog, unlike the four warnings of step 08.6. A builder inherits the cancel style from `materialAlertDialogTheme` but gives its positive button the ordinary affirmative style, and CLAUDE.md §11 requires red `DialogDestructive` for a destructive confirm - which only an XML pair can carry.
- 2026-08-09 - A `ListAdapter` and an item layout rather than the joined text block the Bluetooth section uses: that list is a handful of paired devices, this one is capped in the hundreds, and inflating all of it into a `ScrollView` would rebuild the whole list on every store write. Two files beyond the plan's Files Touched, recorded in the second correction block above.
- 2026-08-09 - The repository is injected into the ViewModel directly, with no `Observe*` use case, unlike every live source in this ticket. `observeHistory()` is a Room flow: already main-safe and already domain-typed, so a use case would add a hop that sets no dispatcher and maps nothing.
- 2026-08-09 - Both actions hide themselves while the history is empty, and the empty state invites the user to the screen that fills it (`COMMUNICATION_POLICY` §2.4). An export of nothing produces a header-only file and a confirmation to delete nothing is a dialog that can only disappoint.
- 2026-08-09 - Found by the gate before the build and fixed: both this dialog and the four warnings of step 08.6 were raised with a bare `.show()`, which `assert-source-gates` counts as `untracked-dialog` (S1456) - the window then outlives a configuration change and keeps the destroyed Fragment alive. Both now go through `showBoundTo(this)` from `util/LifecycleDialogExt.kt`; the rule is back at `baseline 0 | actual 0 | delta 0`.

---

### Step 08.8 - Subscreen strings in three locales

**Files:** `res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Steps 08.2 to 08.7

**Prompt for developer:**

> Add every user-visible string of the six subscreens through `scripts/utils/set-android-string.ps1 -Action add`, one call per key. The traffic warning, the external-IP disclosure and the track-recording summary carry the privacy claims - check all three against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §3.2 requires EN, RU and UK parity, and criteria 5 and 10 make the wording of the two warnings and the track summary part of the product's privacy statement.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "network_monitor"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `check_strings_localized.ps1 -KeyPrefix "network_monitor"`: expected exit 0 | actual exit 0, "all 163 key(s) present in en/ru/uk", no strict-locale gaps. Part B added 60 keys through `set-android-string.ps1 -Action add`, one call per key, in three batches - 20 for GNSS, 27 + 25 for Internet, 13 for History - on top of the 50 steps 08.2-08.4 had already added on the same basis. Nothing in `strings*.xml` was hand-edited.
- 2026-08-09 - COMMUNICATION_POLICY §6 checklist, walked item by item over the 60 new keys. No raw exception text as a headline: the two failure paths that had one (`ThroughputState.Failed`, `ResourceCheckState.Unreachable`) drop it into `Timber.i` and show a sentence instead, which is why `InternetActionResult.SpeedTestFailed` and `ResourceUnreachable` carry no payload at all. No "are you sure" without stating what happens - the clear confirmation says "This removes every stored measurement from this device. Export first if you still need them." No "completed successfully" phrasing anywhere. Every error names a next step: grant the permission, connect to Wi-Fi, narrow the range, use a form like 192.168.1.1, check the connection, check the address and credentials, pick another resource, free up some space, turn recording on. The one empty state invites the user to the screen that fills it. No emoji. `..` throughout and no `…` or `...` - checked mechanically, 0 hits across all three locales; long dashes likewise 0.
- 2026-08-09 - The three privacy claims read against §2 and §6 and against what the code actually does. The track summary - "The track stays on this device and is written only while this screen is open. Nothing is sent anywhere." - matches `GnssTrackRecorder` exactly: `filesDir`, a cold flow with no start method, no HTTP client in the file. The external-IP disclosure names the third party, says what it sees, and states that the address is shown on screen only and never written to the history, which `ResolveExternalIpUseCase` enforces by recording the verdict rather than the address. The traffic warning states the volume (about 20 MB, the download plus upload budget) and that it can be stopped, both of which are true of the cancel path. The same wording is carried by the settings annotation for `rowRecordGnssTrack` in all three locales, so the in-app text, the published reference and the Play Data Safety answer say one thing.

---

## Phase-boundary audit - steps 08.5-08.8, 2026-08-09

Closes the phase together with the mid-phase audit above. Layers 1, 2 and 3; Layer 4 does not apply - the History screen reads the table Phase 03 shipped and adds no schema surface.

- **P1, found by a gate and fixed before this closure** - both the clear-history confirmation and the four Internet warnings were raised with a bare `.show()`, which `untracked-dialog` counts because such a window survives a configuration change holding a destroyed Fragment (S1456). Both moved to `showBoundTo(this)`; the rule is back at delta 0.
- **P2, recorded not fixed** - `InternetSectionFragment` + its ViewModel total 650 lines against the step's 600 budget. The overrun is vocabulary, not logic: four confirmations, nine result shapes and a refusal enum, each with its own phrasing. Splitting the sealed result set away from the single class that produces it would spread one decision over two files to satisfy a number. Both files are far under the hard 1500-line limit of Rule 2.
- **Deliberate deviation from a verification predicate, recorded** - step 08.6 asks for a cancel handler on each of the four actions and there is one. The ViewModel runs a single operation at a time by construction (`start` cancels `activeJob` first), so four stop buttons would be three that stop nothing. The predicate's intent - every one of the four is cancellable - holds through that single job.
- **Gate blind spot, still open** - `activity-logic` has no baseline in this tree (`NO BASELINE yet | actual 78`), so its full-scan mode cannot fail; only its delta mode works, which is what caught the Phase 07 defect. Checked by hand instead: `@Inject` / `lateinit var` in the three new Fragments returns 0. Seeding that baseline belongs to whoever owns the gate, not to this ticket.
- Layer 1 - no `data` import from `ui`: the GNSS status, the track recorder and the traffic rate each reached through their own use case. No second chart class was written; `SignalChartBinder` gained a `ChartValueUnit` instead, which is what stops a byte rate rendering as "1024 dBm".
- Layer 2 - the track switch is registered in `SettingsDocScopeCatalog`, never in `SettingsSearchLayoutCatalog` (S1035/S1313), in its own small layout, because the layout scan indexes every `MaterialButton` it sees and registering the whole fragment would have published the share button as a setting.
- Layer 3 - `assert-source-gates` reports all 17 rules at baseline over the changed set, and every section collects lifecycle-aware.

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` and `.\a.ps1 fr` both exit 0, re-run by the orchestrator after each part rather than taken from an implementing agent's report.
- [x] Every subscreen has both a portrait and a landscape layout - `fragment_network_monitor_*.xml` counts 7 in `res/layout` and 7 in `res/layout-land` (six sections plus Summary), and `assert-layout-variant-id-parity` PASS.
- [x] No fragment exceeds 1500 LOC - the largest file under `ui/networkmonitor/` is `InternetSectionViewModel.kt` at 342, then `InternetSectionFragment.kt` at 308.
- [x] `Grep` for `TODO(phase-08)` returns zero hits.
- [ ] Every observer stops when the screen is closed - verify on device that logcat shows no sampling after finishing the Activity. **Deferred to the ticket's device-test gate**: this is a runtime claim and no static check substitutes for it. The structural argument is in place (every sampler is cold, every collection is lifecycle-aware, the host clears the container when the gate closes) but it is an argument, not evidence.
- [x] Dev log entry added for the phase - two entries, one per part.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Two audits, one per part; both above.
- [x] `post-change -ScopeToFile`: `post-change: PASS` exit 0 for part A (32 files) and for part B (32 files). `NetworkMonitorSectionNavigator` `createFragment` now has zero `-> null` branches.

---

## Handoff Notes to Next Phase

Every section is reachable by its `NetworkMonitorSection` key. Phase 09 only registers entry points; it adds no screen.

---

## Rollback Plan

Revert phase commit(s) - the shell from Phase 07 still stands, and no entry point outside the Monitor points at these screens yet.
