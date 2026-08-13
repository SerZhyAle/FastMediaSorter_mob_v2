# Research 03 - Map gadget: the S0426 skeleton to copy, and where it does not fit

**Serves strategic §6 item:** 3 (current-position gadget)
**Date:** 2026-08-09
**Mode:** read-only codebase survey

---

## Verdict summary

- The weather gadget gives a complete, layer-by-layer skeleton for a keyless provider behind a seam. Copy it.
- It does **not** cover three things the map gadget needs: a device-location source, a binary disk cache for tiles, and a custom User-Agent on the HTTP client.
- All three have precedent elsewhere in the codebase, so none is new territory.

---

## Files

| Path | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/weather/WeatherProvider.kt` | Domain seam - two `suspend` methods, neither throws |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/weather/WeatherSnapshot.kt` | Snapshot model + `WeatherLocation` codec (`"lat,lon,label"`) |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/WeatherRepository.kt` | Repository interface + sealed `WeatherResult` |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/weather/OpenMeteoWeatherProvider.kt` | Raw OkHttp impl, `withContext(Dispatchers.IO)`, catches `IOException`/`JSONException` and returns null |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/weather/WeatherRepositoryImpl.kt` | Memory map + SharedPreferences mirror, TTL, `Mutex`, stale fallback |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/WeatherModule.kt` | `@Binds` seam to impl, `SingletonComponent`, **`src/main`** |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/weather/GetLauncherWeatherUseCase.kt` | The gadget's only door to the repository |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadget.kt` | `LauncherGadget` / `LauncherGadgetHost` + `LauncherGadgetView` base class |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/WeatherGadget.kt` | Factory + private view, 144 LOC |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Key constants, gadget list, target codec |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml` | Gadget layout - **no `layout-land` counterpart** |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/AppModule.kt` | Shared `OkHttpClient` - 10s timeouts, debug-only Chucker |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt` | Glide 4.16.0 config - `image_cache` disk cache, default 2048 MB, clamped 512..16384 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/` | Existing custom `ModelLoader`/`Decoder` registrations |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLocationProvider.kt` | S0766 - start/stop `LocationManager`, exposes raw `Location` with lat/lon |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/MotionReadingSource.kt` | S1179 - `callbackFlow` over `LocationManager`, exposes speed/altitude only |

---

## 1. Layer-by-layer skeleton

| Layer | Weather (S0426) | Map equivalent to create |
|-------|-----------------|--------------------------|
| Seam | `domain/weather/WeatherProvider.kt` | `domain/map/MapTileProvider.kt` |
| Model | `domain/model/weather/WeatherSnapshot.kt` | `domain/model/map/` - point, zoom, snapshot |
| Repository contract | `domain/repository/WeatherRepository.kt` + sealed `WeatherResult` | `domain/repository/MapRepository.kt` + sealed result |
| Impl | `data/weather/OpenMeteoWeatherProvider.kt` | `data/map/<Osm>TileProvider.kt` |
| Cache | `data/weather/WeatherRepositoryImpl.kt` | `data/map/MapRepositoryImpl.kt` |
| DI | `core/di/WeatherModule.kt` (`src/main`) | `core/di/MapModule.kt` (`src/main`) |
| UseCase | `domain/usecase/weather/GetLauncherWeatherUseCase.kt` | `domain/usecase/map/GetLauncherMapUseCase.kt` |
| View | `src/launcherEnabled/.../gadget/WeatherGadget.kt` | `src/launcherEnabled/.../gadget/MapGadget.kt` |
| Layout | `src/launcherEnabled/res/layout/gadget_launcher_weather.xml` | `gadget_launcher_map.xml` |
| Registry | `LauncherGadgetRegistry` key constant + list entry | same, new key |

Note the split: **domain, data and DI live in `src/main` and are flavor-agnostic; only the view, registry and picker dialog are flavor-gated.** The weather stack proves flavor isolation does not require pushing the provider into the flavor source set.

## 2. State shape and degradation

`WeatherRepository.current()` is a plain `suspend fun` returning a sealed result, not a `Flow`. The view polls it. Degradation order in `WeatherRepositoryImpl.currentLocked()`:

1. cached snapshot younger than TTL -> `Fresh`
2. otherwise fetch; success -> `Fresh` + write-through
3. fetch fails, any cached snapshot exists -> `Stale(snapshot, ageMs)` - **the last snapshot stays on screen**
4. fetch fails, no cache -> `Unavailable`

This is exactly strategic §5.2 ("отказы источника не обнуляют показанное") and §11.4. Copy the three-state shape verbatim.

A `Mutex` serialises `current()` so two cells waking on the same tick do not double-fire the network call. `TTL_MS` is 20 minutes; the view's refresh loop uses the same 20-minute constant.

## 3. Gadget lifecycle - already solved

`LauncherGadgetView` is the shared base class and is itself the lifecycle-safe primitive:

- `onAttachedToWindow` -> `findViewTreeLifecycleOwner()` -> `lifecycleScope.launch { repeatOnLifecycle(STARTED) { onActive() } }`
- `onDetachedFromWindow` -> cancel the job

Strategic §3.2 "гаджет обновляет положение только пока действительно виден" therefore needs **no new mechanism** - implementing `onActive()` is sufficient. No bare `lifecycleScope.launch { collect {} }` is introduced, so CLAUDE.md Rule 19 is satisfied by construction.

## 4. What the weather gadget does NOT give

### 4.1 Device location

The weather gadget requests no permission and reads no device fix. Its "location" is a user-named place chosen through `LauncherWeatherLocationDialogFragment`, encoded as `"lat,lon,label"` into the cell's `target`.

Strategic §4 claims nothing in the app reads position. **That is wrong.** Two readers exist, both platform-only (`LocationManager`, no Google Play Services), both wrapping every platform call in `runCatching`, both implementing the four-method `LocationListener` explicitly rather than as a SAM because the default methods only exist from API 30:

- `data/sensors/MotionReadingSource.kt` - `callbackFlow`, GPS+NETWORK fallback, conflated. Exposes `speedKmh` / `altitudeMeters` / `distanceDeltaMeters` only - **no latitude or longitude**, so not reusable as a map centre.
- `ui/cameracapture/helpers/CameraLocationProvider.kt` - imperative `start()` / `stop()` / `lastKnownLocation(): Location?`, exposes the raw `android.location.Location`. Architecturally the right template, but it lives under `ui/cameracapture/helpers` and is not bound behind a domain seam.

Consequence for planning: the map gadget needs a shared, DI-bound location seam in `domain/`, modelled on `CameraLocationProvider`'s behaviour, not a third independent `LocationManager` implementation. Whether S0766's provider is moved behind that seam or left alone is a scoping choice for the plan.

### 4.2 Binary disk cache

`WeatherRepositoryImpl` caches scalar fields in `SharedPreferences` - not a fit for tile bitmaps. The codebase's binary cache is Glide's:

- Glide 4.16.0, `@GlideModule class GlideAppModule : AppGlideModule()`
- disk cache `InternalCacheDiskCacheFactory(context, "image_cache", size)`, default 2048 MB, clamped 512..16384 MB
- default `DiskCacheStrategy.RESOURCE`
- `data/network/glide/` already hosts custom `ModelLoader`/`Decoder` registrations (`NetworkFileModelLoaderFactory`, `NetworkVideoFrameDecoder`, `NetworkPdfThumbnailLoader`, `NetworkEpubCoverLoader`)

So a tile `ModelLoader` registered into the existing Glide instance satisfies §3.2 "тайлы кешируются на диск и не запрашиваются повторно при перерисовке" and "вне главного потока" without inventing a cache. No custom `DiskLruCache` and no `okhttp3.Cache()` exists anywhere in the project.

### 4.3 User-Agent

`OpenMeteoWeatherProvider` sets **no** `User-Agent`; the shared `OkHttpClient` sends OkHttp's default. Strategic §3.2 and the §7 risk row require a project-specific User-Agent for the tile source. This is a gap to close in the new provider, not a pattern to copy. It does not affect the shipped weather gadget's correctness, so it is not a separate defect.

## 5. Strings and layout conventions

- Two prefixes: `launcher_gadget_weather*` for the gadget surface, `launcher_weather_location*` for its picker dialog. Mirror as `launcher_gadget_map*`.
- All launcher strings live in the shared `app_v2/src/main/res/values/strings.xml`; there is no `launcherEnabled`-local `strings.xml`. Only code is source-set-scoped.
- `app_v2/src/launcherEnabled/res/layout-land/` exists but holds exactly one file, `activity_launcher_home.xml`. **Not one of the eleven `gadget_launcher_*.xml` layouts has a landscape variant** (verified by directory listing, 2026-08-09). Gadget cells are grid-measured by the desktop layout regardless of orientation, so CLAUDE.md Rule 11's landscape-parity requirement is satisfied for a gadget layout by the explicit note "landscape variant absent - gadget cells are grid-measured, precedent `gadget_launcher_weather.xml`".
- The weather layout uses `?attr/colorOnSurface` tinting and `autoSizeTextType="uniform"`, and carries a static attribution `TextView` for its CC-BY source. The OSM attribution requirement has the same shape and the same home.

## 6. Test coverage

No `*Weather*` test file exists under `app_v2/src/test`. The named reference pattern is itself untested, so a map stack that mirrors it inherits no test debt but also no test template. The codec-level tests that do exist (`LauncherCellCommandTest`) are the closer model for what a new command kind should carry.
