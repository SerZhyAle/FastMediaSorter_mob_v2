# Research 02 - Maps launch paths, tile policy, and the location stack

**Serves strategic §6 items:** 1 (route to a place), 2 (driver mode), 3 (tiles and position)
**Date:** 2026-08-09
**Mode:** codebase survey plus external documentation

---

## Verdict summary

- Goal 1 (route from the current position to a place) is launchable by a documented intent. No obstacle.
- **Goal 2 (driver mode) has no publicly documented launch mechanism.** Only turn-by-turn navigation to a named destination is documented. §11.2 as written promises a surface Google no longer documents.
- Goal 3 is feasible, but the OSM tile policy carries a service-blocking requirement the project's shared HTTP client does not satisfy today.
- The share payload from Google Maps is not officially documented and cannot be captured on an emulator. The §7 three-tier fallback is the correct design precisely because the payload cannot be pinned down in advance.

---

## 1. Launching Google Maps

**Route from the current position (goal 1).** Documented in `developer.android.com/guide/components/google-maps-intents` and `developers.google.com/maps/documentation/urls`. The directions intent starts from the device's current position when no origin is supplied, and accepts either a coordinate pair or a free-text destination query. Both forms are covered by the §7 fallback ladder, so a shared place resolves to a route whichever of the two the parser manages to extract.

**Driver mode (goal 2) - NOT DOCUMENTED.** The only documented "driving" entry point is turn-by-turn navigation, which **requires a destination**. Google's separate driving-mode dashboard surface is absent from current official documentation. A destination-less "turn on driver mode" shortcut, which is what §0, §5.1 item 2 and §11.2 describe, has no supported intent.

Three ways out, none of them derivable from the strategic spec:

1. Redefine goal 2 as turn-by-turn navigation to a saved place - launchable today, but it is a different feature from what §0 asked for.
2. Launch Google Maps' own driving-mode activity directly - undocumented and unexported in practice, so it breaks on any Maps update. Against §3.2's stability expectations.
3. Drop goal 2 and adjust §11.2.

This is an owner decision because it edits a §11 acceptance criterion.

**Existence check before launching.** `util/PackageManagerCompat.kt` provides `resolveActivityCompat` / `queryIntentActivitiesCompat` and is used at 36+ sites, including `ExecuteLauncherCommandUseCase` and `WeatherGadget.openWeatherApp()`. The §7 risk "Google Maps not installed" is therefore closed by the existing project pattern, and CLAUDE.md Rule 21 requires that helper anyway.

## 2. The shared-place payload

Google does not document what its Share action puts into `EXTRA_TEXT`. Observed shapes in the wild differ - a short link, a full place URL carrying `@lat,lng`, or a plain name and address. Only the full URL form yields coordinates without a network round trip; a short link requires following a redirect, which the app must not do on the main thread and may not be able to do at all offline.

This could not be verified in a read-only pass, and **cannot be verified on an emulator**: capturing a real payload needs Google Maps and Play services, so it needs the owner's phone. See "Manual verification owed" below.

The design consequence is already correct in the spec: §7's ladder (coordinates from the text, else place name, else open the received link as-is) is exactly what an undocumented payload demands. No parser design decision is blocked on the capture - only the confidence in which rung fires most often.

**Adjacent existing code:** `ui/share/UrlInTextDetector.kt` (`httpUrls()`) already extracts http(s) links from shared free text for `ReceiveShareActivity`. Reusable for the third rung.

## 3. OSM tile usage policy

Source: `operations.osmfoundation.org/policies/tiles/`.

Hard requirements, and where the project stands:

| Requirement | Project status |
|---|---|
| A distinct, identifying User-Agent per app | **NOT MET.** The shared `OkHttpClient` from `core/di/AppModule.kt` sends OkHttp's default. The policy states traffic using defaults will be blocked. |
| Disk caching, minimum seven days | Not present for tiles. Two candidate mechanisms exist - see below. |
| No bulk download or offline prefetch | Satisfied by design: one cell renders one small area. §3.2 already forbids prefetching. |
| Attribution displayed | Not present. The weather gadget already carries a static attribution `TextView` for its CC-BY source - same shape, same home. |

Severity note: a User-Agent violation is blocked **by source IP and app**, so it would affect the whole application, not just the map cell. This is the single highest-risk item in the ticket.

**Precedent for the fix.** `di/LinkDownloadModule.kt` already builds a purpose-specific named `OkHttpClient` (`@Named("linkDownload")`) with its own interceptor chain including `DefaultUserAgentInterceptor`. The tile provider must follow that pattern rather than reuse the shared client, as `OpenMeteoWeatherProvider` does.

## 4. Tile disk cache - two candidates

1. **A Glide `ModelLoader`** registered into the existing `GlideAppModule` instance, reusing the configured `image_cache` disk cache (default 2048 MB). `data/network/glide/` already hosts four such custom registrations. Gives caching and off-main-thread decoding for free.
2. **`data/launcher/InstalledAppIconStore.kt`'s shape** - one file per cache key under a `cacheDir` subdirectory, `Dispatchers.IO`-confined, tolerant of write failure. Simpler, fully owned, easier to enforce a seven-day retention on explicitly.

Either satisfies §3.2. The choice is a design decision for the plan, not a research fact. Option 2 makes the policy's seven-day minimum explicit and auditable; option 1 inherits an LRU whose eviction is not tied to age.

## 5. Location stack

- **No Google Play Services location dependency.** The platform `LocationManager` is the only keyless option, and the project already committed to it.
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are **already declared** in `app_v2/src/main/AndroidManifest.xml` since S0766. No new manifest entry.
- Two existing readers, both platform-only, both wrapping platform calls in `runCatching`, both implementing the four-method `LocationListener` explicitly because the default methods only exist from API 30:
  - `data/sensors/MotionReadingSource.kt` (S1179) - `callbackFlow`, GPS+NETWORK fallback, `awaitClose` removes updates. Exposes speed and altitude only, no coordinates.
  - `ui/cameracapture/helpers/CameraLocationProvider.kt` (S0766) - `start()`/`stop()`/`lastKnownLocation(): Location?`, exposes raw coordinates.
- `LauncherSensorPermissionManager.placeAfterAsking()` registers `ActivityResultContracts.RequestPermission()` in the Activity field initializer (must happen before `STARTED`), keys the permission per gadget key, and **places the cell regardless of the answer** - the answer only decides what the cell shows. This is precisely §3.2's "запрашивать только при первом размещении гаджета" and §11.4's "остаётся осмысленной без разрешения".

Consequence: the permission and degradation requirements need one new row in an existing map plus a new location seam in `domain/`. Neither is new territory.

## 6. Test debt inherited

Zero unit tests exist for the weather provider and repository (the named pattern to copy), for `MotionReadingSource`, for `CameraLocationProvider`, or for `ExecuteLauncherCommandUseCase` (the class a new command kind extends). The codec is the one well-tested seam (`LauncherCellCommandTest`, `LauncherContactCommandCodecTest`), which makes the codec the natural place to concentrate this ticket's own tests.

---

## Manual verification owed

- Capture the real `EXTRA_TEXT` that Google Maps' Share action produces, on a device with Maps and Play services. Not reproducible on an emulator. Needed to know which rung of the §7 ladder fires in practice, not to design the ladder.
- Confirm a Maps-published pinned shortcut (a shared-location person) lands and launches, closing the last open item of S1205's own audit and delivering strategic goal 4.

## Decisions owed before planning can finish

1. **Goal 2 redefinition** - see §1. Turn-by-turn to a saved place, undocumented activity launch, or drop.
2. **Tile cache mechanism** - Glide `ModelLoader` versus an own key-per-file store with explicit seven-day retention.
