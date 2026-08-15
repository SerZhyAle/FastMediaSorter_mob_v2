# Phase 03 - Weather gadget

**Strategic spec:** [`../S0426_home-screen-weather-block.md`](../S0426_home-screen-weather-block.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Render the weather on the launcher desktop: a registered `weather` gadget that loads through the repository, refreshes on a timer while visible, degrades to cache or a message, and opens the device weather app on tap.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `WeatherRepository` injectable.
- [ ] Phase 02 ✅ Done - strings and icons resolve.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/weather/GetLauncherWeatherUseCase.kt` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/WeatherGadget.kt` | New | ≤ 260 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 70 |

> No `res/layout-land/` counterpart: gadget layouts are orientation-agnostic (the grid measures the cell), and no existing `gadget_launcher_*.xml` has a landscape variant.

---

## Steps

### Step 03.1 - Add the use case

**Files:** `domain/usecase/weather/GetLauncherWeatherUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `class GetLauncherWeatherUseCase @Inject constructor(private val repository: WeatherRepository)` with `suspend operator fun invoke(location: WeatherLocation): WeatherResult`. Thin by design - the UI must not touch the repository directly (layer discipline).

**Verification:**

- `Grep` - `class GetLauncherWeatherUseCase` matches exactly once.
- `Grep` - `suspend operator fun invoke` present in that file.

**Status:** `[x] done`

---

### Step 03.2 - Author the gadget layout

**Files:** `res/layout/gadget_launcher_weather.xml` (launcherEnabled source set)
**Depends on:** Step 03.1

**Prompt for developer:**

> `<merge tools:parentTag="android.widget.FrameLayout">` like `gadget_launcher_clock.xml`. A horizontal row: condition `ImageView` (tinted `?attr/colorOnSurface`) and a vertical block with an autosized temperature `TextView` (`autoSizeTextType="uniform"`, max 48sp, min 12sp, one line) plus a location `TextView` at `?attr/colorOnSurfaceVariant`. Under them a 9sp attribution `TextView` bound to `launcher_gadget_weather_attribution` and a message `TextView` (gone by default) for the no-location / unavailable states. No hardcoded `#hex` anywhere - theme attributes only.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml` exists.
- `Grep` - `="#` returns zero hits in that file.
- `Grep` - `launcher_gadget_weather_attribution` referenced in that file.

**Status:** `[x] done`

---

### Step 03.3 - Implement the gadget

**Files:** `ui/launcher/gadget/WeatherGadget.kt` (launcherEnabled source set)
**Depends on:** Step 03.2

**Prompt for developer:**

> `WeatherGadget @Inject constructor(private val getWeather: GetLauncherWeatherUseCase) : LauncherGadget` with `key = KEY_WEATHER`, `defaultSpanW = 2`, `defaultSpanH = 1`, `minSpanW = 1`, `minSpanH = 1`, `requiresResourceParam = false`, label `launcher_gadget_weather`, icon `ic_weather_partly_cloudy`. Its private `WeatherGadgetView` extends `LauncherGadgetView` and does all its work in `onActive()`: decode the param into a `WeatherLocation`, render the message state when it is null, otherwise loop `load -> delay(REFRESH_INTERVAL_MS)` (20 minutes, companion const) - the base class cancels the loop on detach or stop, which is the only lifecycle contract here. `Stale` renders the cached snapshot plus the staleness string; `Unavailable` renders the unavailable string without clearing a previously shown value. Tap opens the device weather app: resolve `Intent(Intent.ACTION_VIEW, Uri.parse("geo:<lat>,<lon>?q=weather"))` first, fall back to `Intent.ACTION_WEB_SEARCH` with a localized "weather <label>" query, and if neither resolves log at `Timber.i` and do nothing - never crash the home screen (same guard as `ClockGadget.openSystemClock`). Set `contentDescription` from `launcher_gadget_weather_actions`.

**Verification:**

- `Grep` - `class WeatherGadget` matches exactly once.
- `Grep` - `resolveActivityCompat` present in that file (no raw PackageManager int flags - Rule 21).
- `Grep` - `GlobalScope` returns zero hits in that file.

**Status:** `[x] done`

---

### Step 03.4 - Register the gadget

**Files:** `ui/launcher/gadget/LauncherGadgetRegistry.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add `KEY_WEATHER = "weather"` to the companion, inject `WeatherGadget` into the registry constructor and append it to the `gadgets` list after `clock` (picker order: the two ambient blocks together). The `target` codec is unchanged - the weather param is `lat,lon,label`, which contains no `:`.

**Verification:**

- `Grep` - `KEY_WEATHER` present in `LauncherGadgetRegistry.kt`.
- `Grep` - `weather: WeatherGadget` present in the constructor.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` exit 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the phase.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gadget renders and refreshes, but nothing can supply its location param yet - it always shows the no-location message until Phase 04 lands.

---

## Rollback Plan

Revert phase commit(s) - removing the registry entry hides the gadget; no persisted cell can exist before Phase 04.

---

## Step Log

- 2026-07-24 - Verification 4/4 PASS. Use case, layout, gadget with 20-minute refresh loop, registry entry. `..ps1 fc` exit 0.
