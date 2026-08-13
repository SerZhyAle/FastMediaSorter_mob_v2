# Phase 01 - Weather data layer

**Strategic spec:** [`../S0426_home-screen-weather-block.md`](../S0426_home-screen-weather-block.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Introduce the weather domain model, the provider seam, the Open-Meteo implementation (current weather + city search) and a TTL-cached repository with Hilt wiring. No UI.

---

## Prerequisites

- [ ] Strategic §4 D1-D5 recorded (done 2026-07-24).
- [ ] `android.permission.INTERNET` already declared in `app_v2/src/main/AndroidManifest.xml` line 25 - no manifest change.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/weather/WeatherSnapshot.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/weather/WeatherProvider.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/WeatherRepository.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/weather/OpenMeteoWeatherProvider.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/weather/WeatherRepositoryImpl.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/WeatherModule.kt` | New | ≤ 60 |

---

## Steps

### Step 01.1 - Add the weather domain model

**Files:** `domain/model/weather/WeatherSnapshot.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `WeatherLocation(latitude: Double, longitude: Double, label: String)`, `WeatherCondition` (enum: CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, THUNDERSTORM, UNKNOWN) with a `fromWmoCode(code: Int)` companion mapper over the WMO weather-code table Open-Meteo returns, and `WeatherSnapshot(location, temperature: Double, unit: WeatherUnit, condition, isDay: Boolean, observedAtMs: Long)`. `WeatherUnit` is an enum CELSIUS/FAHRENHEIT. Encode/decode helpers for the gadget cell param (`"lat,lon,label"`) live here as `WeatherLocation.encode()` / `WeatherLocation.decode(raw: String?)` so both the picker and the gadget share one codec.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/weather/WeatherSnapshot.kt` exists.
- `Grep` - `enum class WeatherCondition` matches exactly once.
- `Grep` - `fun decode(` and `fun encode(` both present.

**Status:** `[x] done`

---

### Step 01.2 - Declare the provider seam

**Files:** `domain/weather/WeatherProvider.kt`, `domain/repository/WeatherRepository.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> `WeatherProvider` declares `suspend fun currentWeather(location: WeatherLocation, unit: WeatherUnit): WeatherSnapshot?` and `suspend fun searchLocations(query: String, languageTag: String): List<WeatherLocation>`; both return null/empty on any network or parse failure - the caller never sees an exception. `WeatherRepository` declares `suspend fun current(location: WeatherLocation): WeatherResult` and `suspend fun search(query: String): List<WeatherLocation>`, where `WeatherResult` is a sealed interface with `Fresh(snapshot)`, `Stale(snapshot, ageMs)` and `Unavailable`. The seam exists so the provider can be swapped for api.met.no without touching UI (strategic D2) - state that in one KDoc line, not more.

**Verification:**

- `Grep` - `interface WeatherProvider` matches exactly once.
- `Grep` - `sealed interface WeatherResult` present in `WeatherRepository.kt`.

**Status:** `[x] done`

---

### Step 01.3 - Implement the Open-Meteo provider

**Files:** `data/weather/OpenMeteoWeatherProvider.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement `WeatherProvider` over the injected shared `OkHttpClient` (provided by `core/di/AppModule.kt`) and `org.json`, mirroring `data/delivery/DeliveryManifestDataSource.kt` in style: `withContext(Dispatchers.IO)`, `runCatching`-free explicit `try`/`catch (IOException | JSONException)` with a `Timber.i` fallback line, no broad `catch (Exception)`. Endpoints: `https://api.open-meteo.com/v1/forecast?latitude=&longitude=&current=temperature_2m,weather_code,is_day&temperature_unit=&timezone=auto` and `https://geocoding-api.open-meteo.com/v1/search?name=&count=8&language=&format=json`. Search results map `name`, `admin1` and `country_code` into one display label. Keep every log line ≤ 120 chars and put the base URLs plus the result cap in a private companion.

**Verification:**

- `Grep` - `api.open-meteo.com` and `geocoding-api.open-meteo.com` each match once.
- `Grep` - `Dispatchers.IO` present.
- `Grep` - `catch (e: Exception)` returns zero hits in this file.

**Status:** `[x] done`

---

### Step 01.4 - Implement the cached repository

**Files:** `data/weather/WeatherRepositoryImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> `@Singleton` implementation holding an in-memory snapshot map keyed by rounded coordinates plus a `SharedPreferences` mirror (`weather_cache`) so a cold start after a screen-off still renders something. Return `Fresh` inside the TTL (20 minutes, a companion const), `Stale(snapshot, ageMs)` when the network call fails but a cached snapshot exists, `Unavailable` when there is neither. Guard concurrent refreshes for the same location with a `Mutex` - a desktop with two weather cells must not fire two identical requests. Unit selection: FAHRENHEIT when `Locale.getDefault().country` is one of US/LR/MM, CELSIUS otherwise.

**Verification:**

- `Grep` - `class WeatherRepositoryImpl` matches exactly once.
- `Grep` - `Mutex` present.
- `Grep` - `WeatherResult.Stale` present.

**Status:** `[x] done`

---

### Step 01.5 - Wire Hilt bindings

**Files:** `core/di/WeatherModule.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> New `@Module @InstallIn(SingletonComponent::class)` abstract class binding `OpenMeteoWeatherProvider` to `WeatherProvider` and `WeatherRepositoryImpl` to `WeatherRepository`. No `@Provides` for `OkHttpClient` - `AppModule` already provides the shared client.

**Verification:**

- `Grep` - `abstract fun bindWeatherRepository` present.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exit 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the phase.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Domain types, the repository contract and its Hilt bindings exist; nothing consumes them yet.

---

## Rollback Plan

Revert phase commit(s) - new files only, no migration, no user-facing surface.

---

## Step Log

- 2026-07-24 - Verification 5/5 PASS. Weather model, provider seam, Open-Meteo impl, TTL-cached repository, Hilt bindings. `..ps1 fk` exit 0.
