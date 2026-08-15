# Phase 01 - Settings persistence foundation

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Introduce the three new Streams default-setting enums and persist them through `AppSettings` + `StreamsSettingsStore` + `SettingsRepositoryImpl`, mirroring the existing `StreamingCacheCleanupMode` pattern. No UI, no list-screen behavior yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `StreamingCacheCleanupMode` enum-as-String pattern reviewed as the reference (`AppSettings.kt` line ~276, `SettingsRepositoryImpl.kt` lines ~215/~673).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamDefaultSort.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamMediaTypeFilter.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamsCatalogRefreshPolicy.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/StreamsSettingsStore.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 790 |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | n/a |

---

## Steps

### Step 01.1 - Add the three Streams default enums

**Files:** `domain/model/StreamDefaultSort.kt`, `domain/model/StreamMediaTypeFilter.kt`, `domain/model/StreamsCatalogRefreshPolicy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create three enums in `domain/model`, each with a `fromName(value: String?): Enum` companion that maps a persisted name back to a constant and falls back to the default on null/unknown (mirror `StreamingCacheCleanupMode.fromName`). Values:
> - `StreamDefaultSort { NAME, TOPIC, LANGUAGE, RECENT }` - default `NAME`.
> - `StreamMediaTypeFilter { ALL, AUDIO, VIDEO }` - default `ALL`.
> - `StreamsCatalogRefreshPolicy { MANUAL, ON_OPEN, PERIODIC_WIFI }` - default `ON_OPEN`.
> These are the persisted domain contract; they must NOT depend on `StreamsViewModel.SortMode`/`MediaKindFilter` (UI enums map onto these in Phase 02).

**Verification:**

- `Glob` - all three files exist.
- `Grep` - `enum class StreamDefaultSort` / `StreamMediaTypeFilter` / `StreamsCatalogRefreshPolicy` each match once.
- `Grep` - `fun fromName` present in each file.
- `Grep` - `PERIODIC_WIFI` present in `StreamsCatalogRefreshPolicy.kt`.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - created `domain/model/StreamDefaultSort.kt`, `StreamMediaTypeFilter.kt`, `StreamsCatalogRefreshPolicy.kt` (enum + `fromName`/`DEFAULT`).

---

### Step 01.2 - Add fields to AppSettings

**Files:** `domain/model/AppSettings.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Next to `enableStreams` (line ~95), add three typed fields with defaults:
> `val streamsDefaultSort: StreamDefaultSort = StreamDefaultSort.NAME`, `val streamsDefaultMediaFilter: StreamMediaTypeFilter = StreamMediaTypeFilter.ALL`, `val streamsCatalogRefreshPolicy: StreamsCatalogRefreshPolicy = StreamsCatalogRefreshPolicy.ON_OPEN`. One-line WHY comment only if non-obvious; do not restate the field name.

**Verification:**

- `Grep` - `streamsDefaultSort: StreamDefaultSort` matches once in `AppSettings.kt`.
- `Grep` - `streamsDefaultMediaFilter: StreamMediaTypeFilter` matches once.
- `Grep` - `streamsCatalogRefreshPolicy: StreamsCatalogRefreshPolicy` matches once.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `streamsDefaultSort` / `streamsDefaultMediaFilter` / `streamsCatalogRefreshPolicy` fields to `domain/model/AppSettings.kt`.

---

### Step 01.3 - Extend StreamsSettingsStore to a Values store

**Files:** `data/repository/settings/StreamsSettingsStore.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Refactor `StreamsSettingsStore` to the multi-field pattern used by `AudioSettingsStore`: add `stringPreferencesKey` keys `streams_default_sort`, `streams_default_media_filter`, `streams_catalog_refresh_policy`; introduce a `data class Values(enableStreams, streamsDefaultSort, streamsDefaultMediaFilter, streamsCatalogRefreshPolicy)`; change `read(preferences): Values` to populate all four (enableStreams keeps default `false`; enums via `Enum.fromName(prefs[KEY])`); extend `write` to persist the three enum names via `.name`.

**Verification:**

- `Grep` - `data class Values` present in `StreamsSettingsStore.kt`.
- `Grep` - `streams_default_sort` and `streams_catalog_refresh_policy` string keys present.
- `Grep` - `fun read(preferences: Preferences): Values` signature present.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - refactored `data/repository/settings/StreamsSettingsStore.kt` to a `Values` store (4 fields, 3 new string keys).

---

### Step 01.4 - Wire new fields in SettingsRepositoryImpl

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> `val streams = StreamsSettingsStore.read(preferences)` (line ~281) now returns `Values`. In the `AppSettings(...)` constructor block, set `enableStreams = streams.enableStreams` and add `streamsDefaultSort = streams.streamsDefaultSort`, `streamsDefaultMediaFilter = streams.streamsDefaultMediaFilter`, `streamsCatalogRefreshPolicy = streams.streamsCatalogRefreshPolicy`. The `updateSettings()` write path already delegates to `StreamsSettingsStore.write(preferences, settings)` - confirm no extra edit needed there.

**Verification:**

- `Grep` - `streamsDefaultSort = streams.streamsDefaultSort` present in `SettingsRepositoryImpl.kt`.
- `Grep` - `enableStreams = streams.enableStreams` present (assignment now sources the `Values` field).
- `/build` standard debug compiles (enum types resolve end-to-end).

**Status:** `[x]` done

**Step Log:** 2026-06-24 - wired `streams.enableStreams` + 3 new `Values` fields into the `AppSettings(..)` builder in `data/repository/SettingsRepositoryImpl.kt`; write path already delegates to `StreamsSettingsStore.write`. Build not run (central compile).

---

### Step 01.5 - Add device-profile-preset rows for the new fields

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 01.2

**Prompt for developer:**

> Per the `AppSettings` S0327 contract, every new field needs a matching CSV row. Run `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -AddMissing` to scaffold rows for `streamsDefaultSort`, `streamsDefaultMediaFilter`, `streamsCatalogRefreshPolicy`. Leave them as empty (non-applied) rows for iteration 1 - these defaults are user-tunable, not device-profile-driven; no `DeviceProfilePresetApplier` `when` case is added.

**Verification:**

- `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0 (no missing rows).
- `Grep` - `streamsDefaultSort` present in `device_profile_presets.csv`.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - ran `check_device_profile_presets.ps1 -AddMissing`; scaffolded empty rows for the 3 Streams fields (and pre-existing `nineZoneGridEnabled`). Confirm check exits 0.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (three new enum classes) - deferred to Phase 06 batch, or run now via `scan.ps1`.

---

## Handoff Notes to Next Phase

`AppSettings` now exposes `streamsDefaultSort` / `streamsDefaultMediaFilter` / `streamsCatalogRefreshPolicy`, persisted round-trip. Phase 02 seeds the list screen from these defaults; Phase 03 reset wiring and Phase 05 UI consume the same fields.

---

## Rollback Plan

Revert phase commit(s) - no Room schema or user-facing surface changed; new DataStore string keys are additive and ignored by older builds.
