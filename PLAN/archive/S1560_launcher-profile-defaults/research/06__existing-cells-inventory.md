# Research 06 - What of the requested starter set exists today

Resolves strategic §6.6. Read before planning any phase: every "add this to the table" item below is either
free (the cell exists) or carries a named construction cost (the cell does not).

Evidence gathered 2026-08-11 by reading `app_v2/src`. Every claim carries `file:line`.

---

## 1. The seeding machinery

- Table: `LauncherStarterSets.itemsFor(profile, resources, routeAvailableInBuild)` -
  `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt:92-107`. Pure, 250 LOC.
  The per-profile `when` sits at lines 138-165.
- Orchestrator: `SeedLauncherDesktopUseCase` -
  `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt:32-114`.
  Suspend, whole body in `runCatching`, early-exit when both orientations already seeded.
- Entry: `LauncherHomeActivity.seedDesktopIfNeeded` (`:975-981`, called from `onCreate:253` and from the
  empty-desktop-after-reset observer at `:440`) -> `LauncherHomeViewModel.seedDesktopIfNeeded` (`:635-644`).

Composition order inside `itemsFor`: app-functions section head, the four `act:` launcher actions,
the "everything else" section head, `clock`, the common virtual-resource shortcuts, **the per-profile branch**,
the common `fn:` features, the common tail (`fn:favorites`, `os:settings`, `app:__self__`).

Current per-profile differentiation, in full:

- `PHOTO_FRAME` - `folder_preview` gadget + `res:<last>:SLIDESHOW`, both only when a last-resource id exists.
- `AUDIO_PLAYER`, `CAR_HEAD_UNIT` - `playlist` gadget on the all-audio resource + `streams` gadget when the
  streams route is compiled in.
- `TV_MEDIA_BOX`, `MEDIA_PLAYER`, `VIDEO_PLAYER` - `streams` gadget + `folder_preview` gadget.
- `EBOOK_READER` - `res:<last>:PLAY`.
- `PERSONAL_SMARTPHONE`, `HOME_TABLET`, `VR_HEADSET`, `OTHER` - `emptyList()`.

So six of eleven profiles receive an identical desktop today. Confirmed by
`LauncherStarterSetsTest.kt:50-54`, which asserts `OTHER` gets exactly section head + `clock` + common tail.

---

## 2. Item-by-item verdict on the §0 request list

| Requested item | Exists as a placeable cell | Mechanism | Cost to seed |
|---|---|---|---|
| Clock | Yes | `clock` gadget, already common to every profile | none - already seeded |
| Date + weekday | Yes | same `ClockGadget`; its date line uses skeleton `EEEdMMM` (`ClockGadget.kt:93`), so weekday is already rendered | none - already seeded |
| Android settings button | Yes | `os:settings` in the common tail | none - already seeded |
| Local music | Yes | `res:<all-audio>:BROWSE` in the common resource block | none - already seeded |
| Voice recorder | Yes | `fn:quick_voice` in the common feature block | none - already seeded |
| Current-playback widget | Yes | `AudioNowPlayingGadget`, key `audio_now_playing` (`AudioNowPlayingGadget.kt:53`), registered via `HomeWidgetGadgetModule.kt:58`, present in the picker | table entry only |
| Current temperature | Yes | `WeatherGadget`, key `weather` (`LauncherGadgetRegistry.kt:79`), span 2x1 | table entry - but see §3, it needs a location before it shows a temperature |
| Current GPS speed | Yes | `SpeedGadget`, key `speed` (`LauncherGadgetRegistry.kt:86`), span 2x1, reads `ObserveMotionUseCase` | table entry only |
| Wi-Fi settings | Yes | `os:wifi` -> `Settings.ACTION_WIFI_SETTINGS` (`OsShortcutCatalog.kt:41,79`) | table entry only |
| Bluetooth settings | Yes | `os:bluetooth` -> `Settings.ACTION_BLUETOOTH_SETTINGS` (`OsShortcutCatalog.kt:42,88`) | table entry only |
| Radio (internal) | Yes | `streams` gadget, already in the car-head-unit branch | none - already seeded |
| Altitude above sea level | **No** - only `altitude_chart` (altitude-vs-distance chart, `LauncherGadgetRegistry.kt:88`) | needs a new single-value tile | new gadget class - but the datum already exists, see §4 |
| Satellite count | **No** - the reading lives only inside Network Monitor | needs a new tile over `GnssStatusDataSource` | new gadget class, see §5 |
| "All programs" | **No** as a cell - reachable only by the swipe-up gesture and the taskbar button | needs a 5th `act:` action | ~5 lines + string + icon, see §6 |
| Black screen | **No** as a launcher cell - exists only as a player-side overlay | needs a 6th `act:` action | overlay class is already generic, see §7 |
| YouTube / YouTube Music / Maps / external FM | Mechanism yes (`app:<package>` cell), conditional seeding **no** | needs an installed-package filter in the seed | see `02__third-party-package-visibility.md` |

---

## 3. The weather tile does not show a temperature until it is given a place

`WeatherGadget` reads its place from the cell's stored `param`, decoded by `WeatherLocation.decode`
(`WeatherGadget.kt:55`). With `param == null` the view short-circuits in `onActive` and renders
`launcher_gadget_weather_no_location` without ever calling the network (`WeatherGadget.kt:62-67`).

Its click listener is `openWeatherApp` (`WeatherGadget.kt:59,106-120`) - it launches a stock third-party
weather app, or falls back to a web search. It does **not** open the place picker. So a weather tile seeded
without a place is a tile that permanently reads "no location" and whose tap leaves the app.

The place picker exists and is already wired for two entry points:
`LauncherWeatherLocationDialogFragment` (`ui/launcher/picker/`), invoked from `LauncherHomeActivity:637`
(reconfigure an existing cell, carries `cellId`) and `:758` (place a new weather cell, no `cellId`); the result
lands in `registerWeatherLocationListener` (`:858-870`).

First-run seeding cannot supply a place: there is no stored location, and deriving one would need runtime
location permission plus reverse geocoding, neither of which the gadget does today.

---

## 4. Altitude - the datum exists, the tile does not

`MotionReading` (`domain/model/sensors/SensorReading.kt:52-56`) already carries
`val altitudeMeters: Double?` next to `speedKmh`, and its own KDoc at line 46 states this single reading
"feeds the speed tile, the altitude tile and both charts" - the tile was anticipated and never built.

`SpeedGadget` (`ui/launcher/gadget/SpeedGadget.kt`, 96 LOC) is a thin `LauncherGadgetView` over
`Lazy<ObserveMotionUseCase>`, which is a one-line pass-through to `MotionReadingSource.readings()`
(`domain/usecase/sensors/ObserveMotionUseCase.kt`). It gates on `ACCESS_FINE_LOCATION` in
`hasLocationPermission()` (`:93-95`) and renders `launcher_gadget_sensor_no_permission` when refused
(`:60-61`), which is a readable state rather than a blank tile.

An altitude tile is therefore the same class with `reading.altitudeMeters` in place of `reading.speedKmh`,
the same permission gate and the same `SensorAvailabilityRepository.isAvailable(SensorCapability.LOCATION)`
availability check. Layout to mirror: `app_v2/src/launcherEnabled/res/layout/gadget_launcher_speed.xml`.

---

## 5. Satellite count - one singleton away, but permission-gated

`GnssStatusDataSource` (`data/networkmonitor/GnssStatusDataSource.kt:41-44`) is
`@Singleton class .. @Inject constructor(@param:ApplicationContext context: Context)` under `src/main` -
no flavor guard, no screen scope, injectable anywhere.

`GnssSnapshot` (`domain/model/networkmonitor/GnssSnapshot.kt:57-61`) exposes
`val visibleCount: Int get() = satellites.size` and `val usedInFixCount: Int get() = satellites.count { it.usedInFix }`.
The owner's "0 as the marker of no GPS" maps onto `usedInFixCount`.

Contract to respect: `observe()` is cold and foreground-only by design (class KDoc lines 32-37) - the GNSS
callback and location updates register only while a collector is active and unregister on `awaitClose`
(`:109-113`). A launcher gadget collecting only while its view is active fits that contract exactly.

Without `ACCESS_FINE_LOCATION`, `observe()` emits a single absent section carrying
`SectionAvailability.NoPermission` (`:58-63,74,180-182`) instead of failing - an explicit "why" state,
the same shape `SpeedGadget` already renders.

`GnssStatusDataSource` does **not** expose altitude: `GnssCoordinate` (`GnssSnapshot.kt:36-41`) has no
altitude field and `toCoordinate()` (`:250-255`) never reads `Location.altitude`. Altitude and satellite
count therefore come from two different sources and cannot share one tile implementation.

---

## 6. "All programs" - the screen exists, only the cell is missing

`LauncherHomeActivity.showAllApps()` (`:671-678`) shows `LauncherAllAppsFragment` behind a
`findFragmentByTag` dedup guard. Exactly two call sites reach it: the taskbar button
(`onAllAppsClick = { showAllApps() }`, `:283`) and the swipe-up gesture (`onOpen = { showAllApps() }`, `:316-322`).

Adding a fifth `act:` action costs: one `const val` plus one `Action(..)` row in `LauncherActionCatalog.kt`
(38 LOC today), one `when` branch in `LauncherHomeActivity.performLauncherAction` (`:492-503`) calling the
same `showAllApps()`, one label string, one icon. No `LauncherCellCommand` schema change - the `act:` prefix
and the `LauncherAction` data class already generalise over any key, and
`ExecuteLauncherCommandUseCase.launch` deliberately refuses `LauncherAction` because the host intercepts it
first (`ExecuteLauncherCommandUseCase.kt:60-62`).

---

## 7. Black screen - the overlay is already host-agnostic

`BlackScreenOverlayManager` (`ui/player/helpers/BlackScreenOverlayManager.kt`, 63 LOC) takes only
`WeakReference<Activity>` and a `SystemBarsManager`; `show()`/`hide()` add and remove a plain black `View` on
`activity.window.decorView` and a tap on the overlay hides it. Nothing in it depends on a player.
`SystemBarsManager` (`ui/player/helpers/SystemBarsManager.kt:33-35`) takes only `Activity`.

It is already reused outside the player: `BrowseManagerInitializer.kt:508` constructs it for `BrowseActivity`,
next to `PlayerManagerInitializer.kt:273` and `PhotoVideoStandaloneActivity.kt:288`. A launcher-host action can
construct the same pair against `LauncherHomeActivity`.

---

## 8. Test coverage that a table change must keep green

- `LauncherStarterSetsTest` - `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`,
  241 LOC, 11 tests over `itemsFor` and `place`. It asserts section framing, that no gadget lands inside the
  app-functions section, the `PHOTO_FRAME` and `AUDIO_PLAYER` branches, and `place()`'s no-overlap invariant at
  column counts 3, 4, 6 and 12.
- `LauncherStarterSetsParityTest` - `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt`,
  63 LOC. It ties the gadget-key string literals in `LauncherStarterSets.kt:32-35` to `LauncherGadgetRegistry.KEY_*`,
  which is necessary because `LauncherStarterSets` lives in `src/main` and cannot import the registry
  (`src/launcherEnabled`, Rule 14). **It covers only four keys** - `clock`, `folder_preview`, `playlist`, `streams`.
  Any new key the table starts referencing has no compile-time or test tie to the registry until this test is extended.
- No test exists for `SeedLauncherDesktopUseCase`, `ResolvePanelRouteAvailabilityUseCase`,
  `RefreshInstalledAppsUseCase` or `InstalledAppsRepositoryImpl`.

---

## 9. Availability filters already in the seed path

1. Route build-availability - `ResolvePanelRouteAvailabilityUseCase.all()` mapped to `availableInBuild` only
   (`SeedLauncherDesktopUseCase.kt:67-68`); a compiled-but-runtime-disabled feature still seeds, deliberately
   (`LauncherStarterSets.kt:120-121`).
2. Resource-id existence - `lastResourceId` is filtered against the live resource set
   (`SeedLauncherDesktopUseCase.kt:51-56`), so a deleted resource never seeds a dead cell.

There is no third filter for "is this third-party app installed" - that is the gap
`02__third-party-package-visibility.md` covers.
