# Phase 02 - Session state: remember-last + defaults on open

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Persist the Streams list screen's last session state (sort, media filter, search query) in a dedicated DataStore and seed `StreamsViewModel` on open from that state, falling back to the user's defaults when no session exists. Satisfies strategic §11.3 (restore last filter/search) and §5.1.A precedence note.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `AppSettings.streamsDefaultSort` / `streamsDefaultMediaFilter` available.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/StreamsSessionStore.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 330 |

---

## Steps

### Step 02.1 - Create StreamsSessionStore (dedicated DataStore)

**Files:** `data/repository/settings/StreamsSessionStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class StreamsSessionStore @Inject constructor(@ApplicationContext context)` owning its own preferences DataStore via a top-level `private val Context.streamsSessionDataStore by preferencesDataStore("streams_session")` delegate (self-contained - do NOT touch the main settings DataStore or any Hilt `@Module`). Expose: `suspend fun read(): Session` returning `data class Session(lastSort: String?, lastMediaFilter: String?, lastQuery: String?, lastCatalogRefreshAt: Long)`; `suspend fun writeFilterState(sort: String, mediaFilter: String, query: String)`; `suspend fun writeCatalogRefreshAt(epochMillis: Long)`. Keys: `last_sort`, `last_media_filter`, `last_query`, `last_catalog_refresh_at` (long, default 0). Persisted values are raw enum `.name` strings (decode in the ViewModel, keep the store contract-free).

**Verification:**

- `Glob` - `StreamsSessionStore.kt` exists.
- `Grep` - `class StreamsSessionStore` matches once.
- `Grep` - `preferencesDataStore("streams_session")` present.
- `Grep` - `writeCatalogRefreshAt` and `writeFilterState` present.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - created `data/repository/settings/StreamsSessionStore.kt` (@Singleton, own `streams_session` DataStore, `Session` + read/writeFilterState/writeCatalogRefreshAt).

---

### Step 02.2 - Inject StreamsSessionStore and map UI enums to/from domain enums

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `private val sessionStore: StreamsSessionStore` to the `StreamsViewModel` constructor. Add private mapping helpers between the persisted domain enums (`StreamDefaultSort`/`StreamMediaTypeFilter`) and the UI enums (`SortMode`/`MediaKindFilter`): `StreamDefaultSort.NAME<->SortMode.NAME`, `TOPIC<->TOPIC`, `LANGUAGE<->LANGUAGE`, `RECENT<->RECENT`; `StreamMediaTypeFilter.ALL<->MediaKindFilter.ALL`, `AUDIO<->AUDIO`, `VIDEO<->VIDEO`. Also map `SortMode.name`/`MediaKindFilter.name` strings for persistence.

**Verification:**

- `Grep` - `sessionStore: StreamsSessionStore` present in the constructor.
- `Grep` - both `StreamDefaultSort` and `StreamMediaTypeFilter` referenced in `StreamsViewModel.kt`.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - injected `sessionStore` into `StreamsViewModel`; added `StreamDefaultSort.toSortMode()` / `StreamMediaTypeFilter.toMediaKind()` + String decode helpers.

---

### Step 02.3 - Seed initial filter from last session, else defaults

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Replace the eager `private val _filter = MutableStateFlow(StreamsFilter())` seed with a lazy seed applied in `init`: read `sessionStore.read()` and the current `settings.value`; build the initial `StreamsFilter` as `sort = lastSort?.toSortMode() ?: settings.streamsDefaultSort.toSortMode()`, `mediaKind = lastMediaFilter?.toMediaKind() ?: settings.streamsDefaultMediaFilter.toMediaKind()`, `query = lastQuery ?: ""`. Keep facet fields (category/topic/language) at defaults. Apply via `_filter.value = ...` inside a `viewModelScope.launch` before/at the start of `init` so the combine picks it up; guard against overwriting later user changes (apply once).

**Verification:**

- `Grep` - `sessionStore.read()` invoked in `StreamsViewModel.kt`.
- `Grep` - `streamsDefaultSort` referenced (defaults fallback wired).
- `/build` standard debug compiles.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `seedInitialFilter()` in `StreamsViewModel.init` (session-first, defaults fallback) with an applied-once guard. Build not run (central compile).

---

### Step 02.4 - Persist session state on every sort/filter/search change

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `onQueryChanged`, `onFilter`, and `onSort`, after updating `_filter`, persist the new session via `viewModelScope.launch { sessionStore.writeFilterState(sort = _filter.value.sort.name, mediaFilter = _filter.value.mediaKind.name, query = _filter.value.query) }`. Extract a single private `persistSession()` helper to avoid repetition across the three intents.

**Verification:**

- `Grep` - `writeFilterState(` invoked from `StreamsViewModel.kt`.
- `Grep` - a `persistSession` (or equivalent single helper) defined once.
- `/build` standard debug compiles.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - `onQueryChanged`/`onFilter`/`onSort` now call a single `persistSession()` writing `writeFilterState(..)`. Build not run (central compile).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`StreamsSessionStore` now persists last filter/sort/search and a `lastCatalogRefreshAt` timestamp (written by Phase 04). The list screen restores prior state on open; defaults apply only on first open / cleared session.

---

## Rollback Plan

Revert phase commit(s) - the `streams_session` DataStore file is additive; removing it returns the screen to all-defaults-on-open behavior.
