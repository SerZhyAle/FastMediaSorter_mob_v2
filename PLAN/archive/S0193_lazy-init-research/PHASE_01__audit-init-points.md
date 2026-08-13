# Phase 01 — audit-init-points

**Strategic spec:** [`../S0193_lazy-init-research.md`](../S0193_lazy-init-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Produce a classified inventory of every object constructed before the first user interaction in the `standard` and `noLegal` builds: what is eagerly created in `Application.onCreate`, what runs inside `AppStartupInitializer`, which `@Singleton` Hilt providers are implicitly pulled into the graph, and when native libraries are loaded in the `noLegal` flavor.

---

## Findings — at a glance

The eager-init culprits in `standard` are concentrated in four `@Inject lateinit var` fields of `FastMediaSorterApp` that register lifecycle observers / callbacks synchronously in `onCreate`. Each of these pulls a transitive chain into the heap:

- `smbBackgroundLifecycleManager` → `SmbConnectionManager` → `NetworkStateMonitor` + `SmbPlaybackConnectionTracker` + `NetworkReachabilityGate`
- `networkLifecycleObserver` → `ConnectionGateRegistry` → **all four** protocol gates (`SmbConnectionGate`, `SftpConnectionGate`, `FtpConnectionGate`, `CloudConnectionGate`) — unconditional
- `networkStateMonitor` → lightweight constructor, but `.start()` immediately registers a `ConnectivityManager.NetworkCallback`
- `smbConnectionManager` (already pulled via 1) → `init { networkStateMonitor.registerCallback(...) }`

Six additional fields are passed to `AppStartupInitializer` constructor and used inside coroutines launched from `initialize()` — they are safe to lazify but the initializer needs them to exist at the moment of `initialize()` call (i.e., the `Lazy<T>` is unwrapped immediately).

Two fields (`unifiedCache`, `cachedFileListRepository`) are injected but never read in `onCreate` — pure `DEFERRED_CANDIDATE`. They get constructed only because Hilt resolves all `@Inject` fields on injection.

`noLegal` native code is **already correctly lazy**: `Python.start(AndroidPlatform)` runs inside `ChaquopyRuntimeHolder.ensureInitialized()` called from `YtDlpExtractionStrategy` on first URL extraction; `openxr_loader` + `openxr_native` are loaded inside `VrPlayerActivity.onCreate` and `OpenXrNative.ensureLoaderLoaded()`. Neither runs from Application.

---

## Step 01.1 — Classification of Application-level `@Inject` fields ✅

Source: `FastMediaSorterApp.kt`. Total: **17 fields**.

| # | Field | Usage in `onCreate` | Classification | Safe to lazify? |
|---|-------|---------------------|----------------|:---------------:|
| 1 | `workManagerScheduler` | inside coroutine, `delay(2000)` then `.scheduleResourcesSync()` | EAGER_ASYNC | **YES** |
| 2 | `workerFactory` | only via `workManagerConfiguration` getter (queried by WorkManager when needed) | DEFERRED_CANDIDATE | **YES** |
| 3 | `settingsRepository` | passed to `AppStartupInitializer` ctor + `Bootstrapper.apply()` async + WorkManager coroutine | EAGER_SYNC | YES (Initializer can take `Lazy<T>`) |
| 4 | `playbackPositionRepository` | passed to `AppStartupInitializer` ctor; used inside `cleanupPlaybackPositions()` coroutine | EAGER_SYNC | YES (same approach) |
| 5 | `thumbnailCacheRepository` | passed to `AppStartupInitializer` ctor; used in `migrateThumbnailCache()` + `cleanupOldThumbnails()` coroutines | EAGER_SYNC | YES |
| 6 | `resourceRepository` | passed to `AppStartupInitializer` ctor; used in `fixCloudResourcesWritableFlag()` etc | EAGER_SYNC | YES |
| 7 | `unifiedCache` | **never read in onCreate** | DEFERRED_CANDIDATE | **YES** |
| 8 | `cachedFileListRepository` | **never read in onCreate** | DEFERRED_CANDIDATE | **YES** |
| 9 | `networkStateMonitor` | `.start()` called sync — registers `ConnectivityManager.NetworkCallback` | EAGER_SYNC | **NO** (sync registration) — requires "first network use" trigger refactor |
| 10 | `smbConnectionManager` | `.setResetCallback(...)` called sync via `setupSmbAutoReset()` | EAGER_SYNC | **NO** (sync callback) — requires refactor |
| 11 | `smbBackgroundLifecycleManager` | `ProcessLifecycleOwner.addObserver(this)` — sync registration | EAGER_SYNC | **NO** (sync observer registration) — requires refactor |
| 12 | `networkLifecycleObserver` | `.attach()` called sync — calls `ProcessLifecycleOwner.addObserver(this)` and starts diagnostics collector | EAGER_SYNC | **NO** (sync observer + flow collector) — requires refactor |
| 13 | `tempFileManager` | inside coroutine — `cleanupOldTempFiles(...)` | EAGER_ASYNC | **YES** |
| 14 | `renameVirtualResourcesUseCase` | passed to `AppStartupInitializer`; used inside `renameVirtualResourceNames()` coroutine | EAGER_SYNC | YES |
| 15 | `backfillSmbCredentialShareNameUseCase` | inside coroutine | EAGER_ASYNC | **YES** |
| 16 | `inputBindingRepository` | passed to `AppStartupInitializer`; used inside Chrome OS-only coroutine | EAGER_SYNC | YES |
| 17 | `defaultsMapLoader` | passed to `AppStartupInitializer`; used inside Chrome OS-only coroutine | EAGER_SYNC | YES |

**Counts:** EAGER_SYNC = 11, EAGER_ASYNC = 4, DEFERRED_CANDIDATE = 2. Safe to lazify directly = 13. Require refactor = 4.

---

## Step 01.2 — `AppStartupInitializer.initialize()` task classification ✅

Source: `core/init/AppStartupInitializer.kt`. The method body is purely synchronous (all 10 tasks return immediately), but every task delegates to a coroutine launched on `applicationScope`. So `initialize()` returns quickly; the actual work runs off the main thread.

| # | Task | Coroutine? | Touches | Classification |
|---|------|:----------:|---------|----------------|
| 1 | `syncCacheSizeToSharedPreferences()` | yes | DataStore (read) + SharedPreferences (write) | ASYNC_DB |
| 2 | `logPermissionsStatus()` (DEBUG only) | no | local state (PackageManager) | SYNC_LOCAL |
| 3 | `fixCloudResourcesWritableFlag()` | yes | Room (read + update via `resourceRepository`) | ASYNC_DB |
| 4 | `fixLocalResourcesWritableFlag()` | yes | Room | ASYNC_DB |
| 5 | `fixVirtualAggregateWritableFlag()` | yes | Room | ASYNC_DB |
| 6 | `renameVirtualResourceNames()` | yes | Room (rename use case) | ASYNC_DB |
| 7 | `cleanupPlaybackPositions()` | yes | Room | ASYNC_DB |
| 8 | `migrateThumbnailCache()` | yes | local file I/O (cacheDir → filesDir) | ASYNC_LOCAL |
| 9 | `cleanupOldThumbnails()` | yes | Room + local files + DataStore (settings) | ASYNC_DB |
| 10 | `initializeConnectionThrottleManager()` | yes | DataStore (continuous flow collection) | ASYNC_DB (long-running) |
| 11 | `applyDefaultsChromeOsOnStart()` (Chrome OS only) | yes | Room | ASYNC_DB |

**Key observations:**
- **No `ASYNC_NETWORK` tasks** — initializer touches DB and local files only. The network stack does NOT get exercised by `AppStartupInitializer`.
- Tasks #1, #9 require `settingsRepository`; #3–7 require `resourceRepository`; #7 requires `playbackPositionRepository`; #8, #9 require `thumbnailCacheRepository`; #10 also reads settings continuously.
- Task #10 is **long-running** — registers a permanent flow collector that observes settings changes and forwards them to `ConnectionThrottleManager` (a static singleton). This means `settingsRepository` is held for the entire process lifetime.

---

## Step 01.3 — `@Singleton` provider audit (DI modules) ✅

Audit scope: 19 DI module files (`core/di/` + `di/` + `vr/di/`). Selected groupings below — full list of `@Singleton`-bound classes in `data/` exceeds 50 files (see grep for `@Singleton` in `app_v2\src\main\java\com\sza\fastmediasorter\data`).

### ALWAYS_NEEDED (cannot lazify — required from frame 1)

- `DataStore<Preferences>` (`AppModule`) — settings backend; read on every startup task
- `OkHttpClient` (`AppModule`) — used by Retrofit + cloud clients + Glide network loader (Glide pulls it on first thumbnail load, so technically lazy, but heavyweight ALWAYS)
- `Retrofit` (`AppModule`) — used by iTunes API service; created at first audio-cover lookup, but the singleton itself is created when iTunesApiService is requested
- `MediaFilesCacheManager` (`AppModule`) — object singleton, no construction cost
- `UnifiedFileCache` (`AppModule`) — actually unused in `onCreate` per Step 01.1, candidate for lazify
- Repository implementations bound in `RepositoryModule` (12 `@Binds @Singleton`) — pulled in by ViewModels and use cases as needed; only `SettingsRepository`, `ResourceRepository`, `PlaybackPositionRepository`, `ThumbnailCacheRepository` are eagerly created via Application injection

### NETWORK_ONLY (created at startup, used only with network resources)

These are pulled in by `NetworkLifecycleModule.provideRegistry(...)` which `networkLifecycleObserver` depends on:

- `SmbConnectionGate`, `SmbRecreateTracker`
- `SftpConnectionGate`, `SftpRecreateTracker`
- `FtpConnectionGate`, `FtpRecreateTracker`
- `CloudConnectionGate`, `CloudRecreateTracker`
- `ConnectionDiagnostics`
- `ConnectionGateRegistry` (the registry itself)
- `SmbConnectionManager` (pulled via `smbBackgroundLifecycleManager` injection)
- `SmbPlaybackConnectionTracker`
- `NetworkReachabilityGate`
- `NetworkStateMonitor` (constructor lightweight, but `.start()` registers OS callback)

→ **All ~14 NETWORK_ONLY singletons are constructed at process start in `standard`, even when no network resource will ever be opened in this session.**

Additionally NETWORK_ONLY but only pulled when their type is first requested by a ViewModel/UseCase:

- `SftpClient`, `FtpClient` (constructor-injected `@Singleton`)
- `GoogleDriveRestClient`, `DropboxClient`, `OneDriveRestClient` (cloud SDK clients, constructor-injected `@Singleton`)
- `GoogleDriveCredentialsManager`, `GoogleDriveHttpClient`

→ These are **already lazy in practice** — they get created only when a SMB/FTP/SFTP/Cloud resource is actually accessed, because nothing in `Application` pulls them in directly.

### PLAYER_ONLY (already lazy via ViewModel scope)

- ExoPlayer instances — confirmed NOT in `Application` injection chain. Created in `PlayerViewModel` / `AudioPlayerService`.
- `VrLayerFactory`, `VrFullscreenCommandOverride`, `VrSaveFrameCommandOverride`, `VrSystemUiCommandOverride`, `VrBrowsePassthroughCaptureManager`, `VrRecentDestinationsPrefs` (`VrModule`) — `@Singleton` bound but only pulled when `VrPlayerActivity` / VR-specific code paths run. **Already lazy.**
- `PlayerCommandOverride*` bindings (`PlayerCommandOverrideModule`, `PlayerContractsModule`) — pulled by player UI only. **Already lazy.**

### VR_NOLEGAL_ONLY (already correctly lazy)

- `ChaquopyRuntimeHolder` (`@Singleton`) — singleton object exists, but `Python.start()` runs only inside `ensureInitialized()` called from `YtDlpExtractionStrategy.extract(...)`. Python runtime is **never started at process init**.
- `YtDlpExtractionStrategy`, `NewPipeSiteExtractionStrategy`, `ArtStationExtractionStrategy`, `DeviantArtExtractionStrategy`, `VimeoExtractionStrategy`, `DailymotionExtractionStrategy` — bound via `NoLegalLinkDownloadModule` into a multibinding `Set<UrlExtractionStrategy>`. Pulled in when `LinkExtractionRegistry` is requested by URL-download flow.

`LinkExtractionRegistry` is `@Singleton` — when first injected, it pulls ALL strategies (including Chaquopy holder construction, BUT not Python runtime start). Need to verify nothing in main path pulls `LinkExtractionRegistry` at startup. Quick check: grep for `linkExtractionRegistry` in `Application` / `AppStartupInitializer` — **no occurrences**. ✅ `LinkExtractionRegistry` is pulled only by the link-download flow, which is user-initiated.

### LIGHTWEIGHT (constructors do nothing meaningful)

- Dispatcher providers (`IoDispatcher`, `MainDispatcher`, `DefaultDispatcher`, `ApplicationScope`) — Kotlin singletons / trivial wrappers
- Gson, MediaFilesCacheManager — trivial

---

## Step 01.4 — `noLegal` native library load points ✅

Grep targets in `app_v2/`:
- `System.loadLibrary` → 4 matches: `VrPlayerActivity.kt:677-678`, `OpenXrNative.kt:27-28`. **Zero in `Application`.**
- `Python.start` → in `ChaquopyRuntimeHolder.kt:41`, inside `ensureInitialized()`.
- `Python.getInstance()` → in `YtDlpExtractionStrategy.kt` only (after `ensureInitialized()` returns true).

**Verdict:** noLegal native code is **already correctly lazy**:

1. **OpenXR libraries** (`libopenxr_loader.so`, `libopenxr_native.so`): loaded inside `OpenXrNative.ensureLoaderLoaded()` and `VrPlayerActivity.onCreate`. Never loaded in `FastMediaSorterApp`. First load happens when user opens VR player. ✅
2. **Chaquopy Python runtime** (`libpython3.so` + Python stdlib bytecode unpack): started inside `ChaquopyRuntimeHolder.ensureInitialized()`. First start happens when user attempts URL extraction via yt-dlp. ✅
3. **No `System.loadLibrary` calls in `Application` or `AppStartupInitializer`.** ✅

The only "leak" is that `ChaquopyRuntimeHolder` `@Singleton` object itself gets constructed when `LinkExtractionRegistry` is first requested — but its construction does nothing heavy (constructor is empty body; state machine starts as `UNINITIALIZED`). Negligible footprint.

---

## Step 01.5 — Summary and §6 updates ✅

### Consolidated counts

| Group | Count | Lazy-init status |
|-------|------:|------------------|
| Application-level `@Inject` fields | 17 | 13 safe to lazify directly, 4 require refactor (lifecycle observers / sync callbacks) |
| `AppStartupInitializer` async tasks | 11 | All run off-main; touch DB/local only, never network |
| NETWORK_ONLY singletons pulled at startup via `NetworkLifecycleObserver` | ~14 | All eagerly created in `standard` — primary lazy-init target |
| PLAYER_ONLY singletons | several | Already lazy via ViewModel / Activity scope |
| VR_NOLEGAL_ONLY native code | 2 subsystems | Already correctly lazy ✅ |

### Conclusion of Phase 01

The architectural problem identified in the strategic spec is confirmed: when a `standard`-flavor user opens the app to view a local photo, the heap contains the entire `NETWORK_ONLY` cluster — SMB connection manager + 4 protocol gates + diagnostics + 2 lifecycle observers + network state monitor + their flow collectors — none of which will be used in this session. The chain originates from **four** `@Inject` fields in `Application` (#9–12 in Step 01.1) that register OS callbacks / lifecycle observers synchronously in `onCreate`.

The good news: **13 of 17 Application-level fields are safe to lazify directly** via `dagger.Lazy<T>` with no architectural changes. The remaining 4 need a "trigger on first use" refactor — the registration of lifecycle observers must move from `Application.onCreate` to a Repository/Manager that is itself triggered by user action (opening a network resource, starting playback).

VR and noLegal subsystems are not in scope for further work — they are already lazy by design.

### Strategic spec §6 updates

- §6.2 (`Lazy<T>` applicability) → **Resolved**: 13 of 17 Application-level fields directly safe for `dagger.Lazy<T>`; 4 require a lifecycle-observer registration refactor. Counts in Step 01.1 table.
- §6.5 (noLegal library timing) → **Resolved**: All noLegal native libraries (OpenXR + Python runtime) are already loaded on first feature use, not at process init. No work required in this area.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Classification tables for Application fields, Startup tasks, and DI singletons are recorded in the Steps above.
- [x] Strategic §6.2 and §6.5 are `Resolved`.
- [ ] Dev log entry added (will be added after this file is committed).

---

## Handoff Notes to Next Phase

- Phase 02 (measurements) — Phase 01 already classifies the network cluster as ~14 singletons created unconditionally. Phase 02 numbers will quantify the heap footprint but cannot change the architectural conclusion. Phase 02 may proceed in parallel with Phase 03 or be skipped if owner accepts the principle-first path.
- Phase 03 (mechanism evaluation) — directly receives the 17-field classification table. Per-field `Lazy<T>` evaluation reduces to confirming the verdicts in Step 01.1 column 5.
- The 4 fields requiring refactor (`networkStateMonitor`, `smbConnectionManager`, `smbBackgroundLifecycleManager`, `networkLifecycleObserver`) will need a dedicated child spec — the work is non-trivial because moving observer registration changes the semantics of the protocol-neutral lifecycle gates (S0067 / S0061 Phase 04).

---

## Rollback Plan

Research phase — no code changed. Nothing to roll back.
