# Phase 01 - Foundation: stores, models, repository, capability map

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-06-17
**Completed:** 2026-06-17

**Step Log:**

- 2026-06-17 - All 6 steps verified. New: StatsModels, StatsBaselineDataStore, StatsAggregateDataStore, StatsCategoryAvailability, StatisticsRepository(+Impl); RepositoryModule binding added. `.\a.ps1 fk` BUILD SUCCESSFUL (Hilt graph resolves). Negative greps PASS (no android import / no Log.d / no BuildConfig.IS_).

---

## Objective

Introduce the local persistence layer for statistics: an always-on baseline DataStore, a detailed-aggregate DataStore (with detail wipe), the read-side domain models + repository, and the flavor capability map. No UI, no sink, no settings flag yet - this phase only creates the storage + read contract that later phases consume.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] DataStore singleton `DataStore<Preferences>` is provided by `AppModule` (already present - see `core/di/AppModule.kt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/StatsBaselineDataStore.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/StatsAggregateDataStore.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/stats/StatsModels.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/stats/StatsCategoryAvailability.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StatisticsRepository.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StatisticsRepositoryImpl.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | ≤ 145 |

> No file exceeds 500 LOC after edit - no backup step required. All new files in `src/main/` (feature is shared across all flavors).

---

## Steps

### Step 01.1 - Define statistics domain models

**Files:** `domain/stats/StatsModels.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the read-side immutable models. Define: `enum class StatsCategory { OPERATIONS, CAPTURE, VIEWING, EDITING, SOURCES, USAGE }`. Define `enum class StatsMediaType { IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }` for the action×type matrix. Define `data class StatsSnapshot` aggregating all read values: baseline (`firstLaunchEpochMs: Long`, `firstInstallVersion: String`, `firstInstallFlavor: String`, `launchCount: Long`), operation counters with optional byte sums and durations, capture counters, viewing counters + watch/listen millis, editing counters, source-connection counters, session count + active millis, and a `Map<StatsMediaType, MediaActionCounts>` for group G. Define `data class MediaActionCounts(val copied: Long, val moved: Long, val deleted: Long, val totalBytes: Long)`. Keep all fields `Long`/`String`/`Map`; no Android imports. These map directly to strategic §5.4 groups A-H (all-time totals only, ADR-7 - no period buckets).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/stats/StatsModels.kt` exists.
- `Grep` - `data class StatsSnapshot` matches once.
- `Grep` - `enum class StatsCategory` and `enum class StatsMediaType` both present.
- `Grep` - no `import android` line in the file.

**Status:** `[x] done`

---

### Step 01.2 - Always-on baseline DataStore

**Files:** `data/local/preferences/StatsBaselineDataStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class StatsBaselineDataStore @Inject constructor(private val dataStore: DataStore<Preferences>)`, modeled on `data/local/preferences/ReviewEligibilityDataStore.kt`. Namespace keys with `stats_baseline_`: `stats_baseline_first_launch_epoch` (Long), `stats_baseline_first_install_version` (String), `stats_baseline_first_install_flavor` (String), `stats_baseline_launch_count` (Long). Provide: `suspend fun recordLaunch(installVersion: String, installFlavor: String)` - increments launch count by 1, and sets first-launch epoch + first-install version + flavor ONLY if not already set (first write wins; use `System.currentTimeMillis()` for the epoch). Provide `suspend fun snapshot(): StatsBaselineSnapshot` returning a small data class with the four values (epoch 0 / empty string / count 0 defaults). This store is written unconditionally (outside the opt-in gate) per strategic ADR-2 / §5.1.

**Verification:**

- `Glob` - `StatsBaselineDataStore.kt` exists.
- `Grep` - `class StatsBaselineDataStore` matches once and is annotated `@Singleton`.
- `Grep` - `stats_baseline_launch_count` literal present.
- `Grep` - `fun recordLaunch` present.

**Status:** `[x] done`

---

### Step 01.3 - Detailed aggregate DataStore with detail wipe

**Files:** `data/local/preferences/StatsAggregateDataStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class StatsAggregateDataStore @Inject constructor(private val dataStore: DataStore<Preferences>)`. Namespace keys with `stats_agg_`. Persist the detailed counters/bytes/durations and the action×type matrix that back `StatsSnapshot` (one key per scalar; for the matrix use per-`StatsMediaType` keys like `stats_agg_type_image_copied`). Provide `suspend fun apply(delta: StatsAggregateDelta)` that atomically adds a batch of increments in a single `dataStore.edit {}` (so one flush = one disk write, strategic §3.2 / ADR-6). Define `StatsAggregateDelta` as a plain additive value object (maps of key→delta) in this file or `StatsModels.kt`. Provide `suspend fun read(): StatsAggregateValues` returning all detailed values. Provide `suspend fun wipeDetailed()` that removes every `stats_agg_` key (leaving `stats_baseline_` untouched) - called when the toggle is switched off (strategic §3.2 "Поведение при выключении", ADR-2). Do not add a public reset for baseline.

**Verification:**

- `Glob` - `StatsAggregateDataStore.kt` exists.
- `Grep` - `class StatsAggregateDataStore` matches once and is `@Singleton`.
- `Grep` - `fun wipeDetailed` present.
- `Grep` - `fun apply` present and takes a delta parameter.
- `Grep -n "Log\.d\("` on the file returns zero hits (Timber only).

**Status:** `[x] done`

---

### Step 01.4 - Flavor capability map

**Files:** `domain/stats/StatsCategoryAvailability.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class StatsCategoryAvailability @Inject constructor()` (or a stateless object exposed via a `@Provides`). Expose `fun availableCategories(): Set<StatsCategory>` and `fun isCategoryAvailable(category: StatsCategory): Boolean`, deciding from existing `BuildConfig` capability flags only (NOT `IS_*` flavor guards - these are sanctioned `SUPPORT_*` capability reads, identical to the existing `remoteSourceAvailabilityGate` usage): `SOURCES` requires `BuildConfig.SUPPORT_CLOUD || BuildConfig.SUPPORT_LOCAL_NETWORK`; `VIEWING`/`CAPTURE`/`EDITING` always available where `SUPPORT_IMAGES` (images exist in every flavor); video/audio sub-rows are filtered at render time by `SUPPORT_VIDEO`/`SUPPORT_AUDIO` (the dashboard reads those flags directly in Phase 04). `OPERATIONS` and `USAGE` always available. This class is the single source of truth for category-level visibility; per-row zero-hiding is the dashboard's job (Phase 04).

**Verification:**

- `Glob` - `StatsCategoryAvailability.kt` exists.
- `Grep` - `fun availableCategories` present.
- `Grep` - `BuildConfig.SUPPORT_CLOUD` and `BuildConfig.SUPPORT_LOCAL_NETWORK` referenced.
- `Grep` - no `BuildConfig.IS_` substring in the file (no flavor guards).

**Status:** `[x] done`

---

### Step 01.5 - Statistics repository interface + impl

**Files:** `domain/repository/StatisticsRepository.kt`, `data/repository/StatisticsRepositoryImpl.kt`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Create `interface StatisticsRepository` in `domain/repository/` with: `suspend fun getSnapshot(): StatsSnapshot`, `suspend fun wipeDetailed()`, `suspend fun recordLaunch(installVersion: String, installFlavor: String)`. Create `@Singleton class StatisticsRepositoryImpl @Inject constructor(private val baseline: StatsBaselineDataStore, private val aggregate: StatsAggregateDataStore, @IoDispatcher private val io: CoroutineDispatcher)` in `data/repository/`. `getSnapshot()` reads baseline + aggregate values on `io` and assembles a `StatsSnapshot`. `wipeDetailed()` delegates to `aggregate.wipeDetailed()`. `recordLaunch(..)` delegates to `baseline.recordLaunch(..)`. Follow the constructor-injection + `@IoDispatcher` qualifier convention used by existing repository impls in `data/repository/`.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `interface StatisticsRepository` matches once.
- `Grep` - `class StatisticsRepositoryImpl` matches once and is `@Singleton`.
- `Grep` - `fun getSnapshot` and `fun wipeDetailed` present in both files.

**Status:** `[x] done`

---

### Step 01.6 - Hilt binding for the repository

**Files:** `core/di/RepositoryModule.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> In `core/di/RepositoryModule.kt` add an abstract `@Binds` method binding `StatisticsRepositoryImpl` to `StatisticsRepository`, matching the existing `@Binds` style in that module. Do not add a new module; reuse `RepositoryModule`.

**Verification:**

- `Grep` - `StatisticsRepository` and `StatisticsRepositoryImpl` both referenced in `RepositoryModule.kt`.
- `Grep` - the new `@Binds abstract fun` line is present.
- Build: `.\a.ps1 fk` compiles (Hilt graph resolves).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `.\a.ps1 fk` (compile-only; no UI yet).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new public classes added).

---

## Handoff Notes to Next Phase

- `StatsBaselineDataStore.recordLaunch(..)` exists and is the always-on hook Phase 02 calls from app startup.
- `StatsAggregateDataStore.apply(delta)` is the single batched write the Phase 03 sink flushes into.
- `StatsAggregateDataStore.wipeDetailed()` is what Phase 02's disable-handler calls.
- `StatisticsRepository.getSnapshot()` is the read contract Phase 04's use case consumes.
- `StatsCategoryAvailability` is the category-visibility source for Phase 04.

---

## Rollback Plan

Revert phase commit(s) - no schema migration, no user-facing surface, no DataStore keys read by any other feature. New keys are inert until later phases write them.
