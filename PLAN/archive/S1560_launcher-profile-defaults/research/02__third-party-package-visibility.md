# Research 02 - Seeding a third-party app cell only when the app is installed

Resolves the mechanism half of strategic §6.2 (external FM radio) and §5.1 pillar 3 (conditional seeding of
YouTube, YouTube Music, Maps, radio). Evidence gathered 2026-08-11.

---

## 1. Package visibility is already open for launchable apps

`app_v2/src/main/AndroidManifest.xml` declares a `<queries>` block (lines 90-215). It does **not** hold
`QUERY_ALL_PACKAGES`, but it does declare:

```xml
<intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent>
```

(lines 103-106, added by S0623 for the app-launch panel). On API 30+ that grants visibility of every app that
has a launcher entry, which covers YouTube, YouTube Music, Maps and any vendor FM application. No new
`<package>` entry is required for this ticket, and `PackageManager.getLaunchIntentForPackage(pkg)` resolves for
any such app.

Precedent in the same subsystem: `WeatherGadget.openWeatherApp` probes five stock weather packages with exactly
`packageManager.getLaunchIntentForPackage(it)` and treats `null` as "not installed"
(`WeatherGadget.kt:106-120`).

---

## 2. The Room-backed installed-apps cache is the wrong source for first-run seeding

`InstalledAppsRepository` (`domain/repository/InstalledAppsRepository.kt:21-45`, impl at
`data/repository/InstalledAppsRepositoryImpl.kt:29-100`) exposes `observeApps(): Flow<List<InstalledApp>>` and
mutators; **there is no single-package lookup**, so a caller would collect `observeApps().first()` and scan.

The cache is filled by `RefreshInstalledAppsUseCase.refreshIfStale()`
(`domain/usecase/apps/RefreshInstalledAppsUseCase.kt:45-51`), and it has exactly one caller:
`DeferredStartupWorker.kt:75`. That worker is deferred by construction, so on a genuine first launch the desktop
seed can run before the cache holds anything - a conditional cell keyed off the cache would then be dropped for
every app on the device.

Conclusion: the installed check must query `PackageManager` directly rather than read the cache. The strategic
spec's §5.1 wording ("the same cache of installed apps that already feeds the picker") describes intent, not a
binding mechanism; the cold-cache race makes the direct query the only correct reading of the requirement
"a third-party cell is placed only when the package is installed".

---

## 3. Where the check belongs

`SeedLauncherDesktopUseCase.invoke` is already suspend and already performs repository reads before calling the
pure table (`SeedLauncherDesktopUseCase.kt:42-70`). `LauncherStarterSets` is pure by contract and lives in
`src/main` with no Android dependency, so it cannot call `PackageManager` itself.

The shape that preserves both properties: the use case resolves the set of installed candidate packages on
`Dispatchers.IO` and passes it into `itemsFor` as a parameter, exactly as `routeAvailableInBuild` is passed
today. The table stays pure and unit-testable; the Android call stays in the use case.

Rule 21 applies to any `PackageManager` flag call: use the `*Compat` helpers in `util/PackageManagerCompat.kt`,
never a raw-int deprecated overload. `getLaunchIntentForPackage` takes no flags and is not affected.

---

## 4. Candidate packages already hardcoded in the repo

There is no central "known third-party packages" registry - every list is a `private companion object` constant
local to its consumer:

- `com.google.android.apps.maps` - `ExecuteLauncherCommandUseCase.kt:235` (`GOOGLE_MAPS_PACKAGE`), used to
  target `geo:` directions and navigation commands at lines 210 and 215.
- `com.google.android.apps.youtube.music` - `ui/player/standalone/AudioStandaloneActivity.kt:319`, an inline
  string in `searchInYouTubeMusic()`.
- `com.google.android.youtube` - **absent from the whole of `app_v2/src`**.
- Stock weather apps - `WeatherGadget.kt:135-141`, five packages.
- Google Keep - `GoogleKeepAvailabilityChecker.kt:19`, `ShareTargetModule.kt:32`, plus manifest `<package>` entries.
- Google Lens / Assistant - `GoogleLensShare.kt:45`, plus manifest `<package>` entries.
- Play Store installers - `InstallSourceProvider.kt:40`.
- Messaging clients for "Send to.." - manifest `<package>` block, lines 175-186.
- **No FM-radio package list exists anywhere.**

So the ticket needs its own candidate list. The existing house pattern is a `private companion object` constant
next to its consumer, which for this ticket means next to the starter-set table rather than a new shared file.

---

## 5. FM radio on car head units

Strategic §6.2 records the owner's decision: seed both the internal Streams gadget (already in the
`CAR_HEAD_UNIT` branch) and a shortcut to an external FM application when one is installed. There is no single
FM package - each head-unit vendor ships its own - so the check is "first installed package from a candidate
list wins", the same shape `WeatherGadget.WEATHER_PACKAGES` already uses for weather apps.

Candidates observable on AOSP-derived head units and mainstream phones, most common first:

- `com.android.fmradio` - the AOSP FM Radio application, shipped by several head-unit vendors verbatim.
- `com.caf.fmradio` - the Code Aurora / Qualcomm fork, common on Qualcomm-based units.
- `com.miui.fmradio` - Xiaomi.
- `com.sec.android.app.fm` - Samsung.
- `com.motorola.fmplayer` - Motorola.
- `com.lge.fmradio` - LG.

The list is a best-effort probe by construction: an unknown vendor package simply yields no cell, which is the
intended failure mode ("no dead icons") rather than a defect.

---

## 6. Consequence for the strategic risk table

Strategic §7 lists "a third-party cell is placed blind" with the mitigation "check installed state against the
cache of installed apps". This research narrows that mitigation to a direct `PackageManager` probe; the risk
itself and its severity are unchanged.
