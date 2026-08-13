# Research 01 - Streams settings architecture (S0659)

**Date:** 2026-06-24
**Method:** catalog-first navigation + targeted reads (android-solution-researcher).
**Purpose:** ground the tactical plan for expanding the «Трансляции» settings group. Locked iteration-1 scope: Default sort mode, Default media type filter, Catalog refresh policy, Clear play statuses action; built-in remember-last filter/search; OK/FAIL always visible.

---

## A. Settings section

- Fragment: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StreamsSettingsFragment.kt` - programmatic rows, NOT PreferenceFragment.
- Inflates `FragmentSettingsStreamsBinding` -> layouts (both kept in sync, Rule 11):
  - `app_v2/src/main/res/layout/fragment_settings_streams.xml`
  - `app_v2/src/main/res/layout-land/fragment_settings_streams.xml`
- Current rows: `SettingsToggleRow` id `rowEnableStreams` + `MaterialButton` id `btnStreams` (opens `StreamsActivity`). `btnStreams` visibility gated on `settings.enableStreams`.
- Host: `MediaSettingsFragment.buildSections()` instantiates `StreamsSettingsFragment` only when `capabilityAvailability.isStreamsAvailable()`; section key `media__streams`, default-collapsed.
- Base class `BaseSettingsFragment` has helpers for toggle/spinner/edittext, but NO `SettingsDropdownRow` helper.

## B. Preferences store (the pattern to mirror)

- Jetpack DataStore (`androidx.datastore.preferences`). Single `DataStore<Preferences>` owned by `SettingsRepositoryImpl` (`data/repository/SettingsRepositoryImpl.kt`, ~781 LOC, `@Singleton`).
- Domain model: `domain/model/AppSettings.kt` (data class, all fields defaulted).
- Logical group stores in `data/repository/settings/` - relevant: `StreamsSettingsStore` (currently one key `enable_streams`).
- Enum-as-String reference pattern = `StreamingCacheCleanupMode`:
  - key: `stringPreferencesKey("streaming_cache_cleanup_mode")` in `SettingsRepositoryImpl.companion`
  - field: `AppSettings.streamingCacheCleanupMode: StreamingCacheCleanupMode = ASK`
  - read: `getSettings()` map block `Enum.fromName(prefs[KEY])`
  - write: `updateSettings()` edit block `prefs[KEY] = settings.field.name`
- UI binding: `SettingsViewModel.settings: StateFlow<AppSettings>` collected via `collectOnLifecycle`; fragment persists with `viewModel.updateSettings(viewModel.settings.value.copy(field = newValue))` (optimistic override + async persist).

## C. Streams list screen (remember-last gap)

- `ui/streams/StreamsActivity.kt` + `ui/streams/StreamsViewModel.kt`.
- `StreamsViewModel.SortMode { NAME, TOPIC, LANGUAGE, RECENT }`, `MediaKindFilter { ALL, AUDIO, VIDEO }`, search query - all held in `_filter: MutableStateFlow<StreamsFilter>`.
- `StreamsFilter()` defaults: sort=NAME, mediaKind=ALL, query="". Re-initialised on EVERY Activity open. NO persistence of sort/filter/search anywhere today - this is the gap S0659 closes.

## D. Play-status (OK/FAIL)

- Room table `stream_sources`, entity `data/local/db/StreamSourceEntity.kt`: `lastPlayOutcome: String?` (null/"OK"/"FAIL"), `lastPlayOutcomeAt: Long?`.
- Constants `OUTCOME_OK`/`OUTCOME_FAIL` in `RecordStreamPlayOutcomeUseCase`.
- Write path: `StreamSourceDao.markPlayOutcome(id, outcome, atMillis)` <- `StreamSourceRepository.recordPlayOutcome` <- `RecordStreamPlayOutcomeUseCase`.
- Render: `StreamSourceAdapter.bindPlayStatus(outcome)` - icon always visible (ok/failed/unknown), no hide path.
- Clear-statuses needs NEW: DAO `UPDATE stream_sources SET lastPlayOutcome=NULL, lastPlayOutcomeAt=NULL` + repo method + `ClearStreamPlayOutcomesUseCase`. Columns already exist -> NO Room schema/version change.

## E. Catalog import/refresh

- `domain/usecase/streams/ImportStreamCatalogUseCase.kt` - downloads `stream-catalog.zip` from fixed GitHub Releases URL, parses `streams.csv`, `StreamSourceRepository.mergeCatalog()`.
- Call site: `StreamsViewModel.onImportCatalog()` - user-initiated only (toolbar import chooser).
- NO on-open / periodic refresh hook exists. `StreamingCacheStartupGcWorker` is unrelated (cache GC, not catalog).
- Other import paths: `ImportStreamPlaylistUseCase` (.m3u), `AddStreamSourceUseCase` (single manual URL).

## F. Flavor / capability gate

- `BuildConfig.SUPPORT_STREAMS`: standard=true, legacy=true, noLegal=true, lite=false, photos=false.
- Read via `core/capability/CapabilityAvailability.isStreamsAvailable() = BuildConfig.SUPPORT_STREAMS` (`@Singleton`, Hilt). Treated as a capability flag (rationale comment at `StreamsActivity.kt`), pre-established deviation from Rule 14 - not introduced by S0659.
- New rows live inside `StreamsSettingsFragment`, which is only created when `isStreamsAvailable()` -> new rows inherit the gate automatically; no extra gate needed. lite/photos never instantiate the fragment.

## G. Strings

- `app_v2/src/main/res/values/strings.xml`. Settings-surface prefix `settings_streams_*`; operational screen prefix `streams_*`.
- Reusable labels already present: `streams_sort_name|topic|language|recent`, `streams_filter_media_audio|video`, `streams_filter_all`.
- New settings-row strings use `settings_streams_*` prefix; trilingual EN/RU/UK; settings-doc-sync (Rule 22) must regen manifest/reference/annotations.

## H. Risks / gaps

- Filter/sort/search not persisted -> remember-last needs a storage layer. Mixing ephemeral session state into the user-settings DataStore (AppSettings) is semantically wrong; prefer a dedicated session store.
- No `clearPlayOutcomes` DAO/repo/usecase yet; no action-button pattern in settings fragments today.
- `SettingsRepositoryImpl` ~781 LOC (under 1500 limit; keep new keys in `StreamsSettingsStore`).
- `SettingsViewModel.resetMediaSection()` omits `enableStreams` today; new Streams defaults must be added there (and fix the pre-existing `enableStreams` omission inline).
- No on-open catalog refresh hook; closest analog (`MainViewModel` sync) unrelated.
- `BaseSettingsFragment` lacks a `SettingsDropdownRow` helper (`SettingsDropdownRow.kt` widget itself is ready with setSelection / setOnItemSelectedListener).

---

## Design decisions for the tactical plan (owner-autonomy: decide with explicit assumptions)

1. **Storage split (settings vs session).**
   - DEFAULTS are user settings -> `AppSettings` + `StreamsSettingsStore`: `streamsDefaultSort`, `streamsDefaultMediaFilter`, `streamsCatalogRefreshPolicy`.
   - LAST-SESSION state is not a user setting -> dedicated `StreamsSessionStore` (own DataStore file, e.g. `streams_session`), injected into `StreamsViewModel`. Keys: last sort, last media filter, last query, last-catalog-refresh epoch.
   - Keeps the domain settings model clean (research H concern).

2. **Domain enums (no UI dependency in settings layer).** Introduce `domain/model` enums `StreamDefaultSort`, `StreamMediaTypeFilter`, `StreamsCatalogRefreshPolicy` with `fromName()` companions. Map to/from `StreamsViewModel.SortMode` / `MediaKindFilter` in the VM. The settings layer must not depend on a ViewModel-nested enum.

3. **Open-state precedence (matches §5.1.A note "defaults = base profile; remember-last overrides empty start").** On `StreamsActivity` open, initial `StreamsFilter` = persisted last-session state if present, else seeded from settings (`streamsDefaultSort`, `streamsDefaultMediaFilter`, query=""). Persist last-session on any sort/filter/search change. Thus the Default settings define the first-run / post-clear profile; remembered state wins on subsequent opens. Sort is persisted symmetrically with filter to avoid "filter remembered, sort reset" jank (explicit assumption).

4. **Catalog refresh policy semantics (light, no heavy background per §3.2).**
   - `MANUAL` - nothing automatic (today's behavior).
   - `ON_OPEN` (default) - on open, show a non-intrusive, dismissible suggestion (snackbar with refresh action), throttled via last-refresh timestamp; "предлагать", not silent auto-download.
   - `PERIODIC_WIFI` - auto-refresh on open, WiFi-only, throttled to ~daily; implemented as throttled opportunistic-on-open (NOT a WorkManager periodic job) to honor the no-heavy-background constraint in iteration 1 (explicit assumption).

5. **Clear play statuses wiring (layer-correct).** `StreamSourceDao.clearAllPlayOutcomes()` (UPDATE ... NULL) -> `StreamSourceRepository.clearPlayOutcomes()` -> `ClearStreamPlayOutcomesUseCase` -> a method on `SettingsViewModel` (`clearStreamPlayStatuses()`), invoked from a confirmation dialog behind a new `MaterialButton` in `StreamsSettingsFragment`. Fragment never calls the usecase directly.

6. **Dropdown rows.** Add three `SettingsDropdownRow` + one action `MaterialButton` to `fragment_settings_streams.xml` (+ layout-land). Add a reusable `bindDropdown` / `setDropdownSelection` helper to `BaseSettingsFragment` (covers research /spec-draft candidate #3 in-scope). Full keyboard/D-pad/mouse focus (Rule 16) + systemBars safety (Rule 17).

7. **Runtime visibility.** New rows (dropdowns + clear action) visible only when `settings.enableStreams == true`, mirroring `btnStreams`. Keeps the section compact when the feature is off.

8. **No Room schema change.** Clear-statuses is a plain UPDATE on existing columns -> no version bump, no migration (avoids the schema hard-stop).

## /spec-draft candidates surfaced (handled in-scope, NOT parked)

- `resetMediaSection()` omits `enableStreams` -> fix inline while adding new Streams defaults (trivial, same method).
- `BaseSettingsFragment` lacks dropdown helper -> add the helper in this spec's UI phase (needed anyway).
- `CapabilityAvailability` reads `SUPPORT_STREAMS` in `src/main` (Rule 14 deviation) -> pre-established deliberate exception with a rationale comment; not a defect introduced here, not parked.
