# Phase 05 - Flat list filter, sort, search

**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Implemented
**Depends on:** Phase 02, Phase 04
**Blocks:** Phase 06

## Objective

Make the (now large) list usable: show topic + language per row, and add a search field plus filter
(rubric/topic/language/media kind) and sort controls. Flat list, derived in the ViewModel (not the
Activity). Portrait + landscape parity.

> `/ui-clarify` note: strategic resolved presentation to a flat list with top search + filter + sort
> and topic/language chips in the row (Quiz 2026-06-21). Exact control placement is this phase's call
> within that decision. If a placement question is genuinely ambiguous at build time, mark it
> `[DEFERRED - /ui-clarify]` and ship the search + a single filter spinner first.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | <= +70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | <= +30 |
| `app_v2/src/main/res/layout/activity_streams.xml` | Modified | <= +40 |
| `app_v2/src/main/res/layout-land/activity_streams.xml` | Modified | <= +40 |
| `app_v2/src/main/res/layout/item_stream_source.xml` | Modified | <= +30 |
| `app_v2/src/main/res/values/strings.xml` (+ ru + uk) | Modified | <= +30 |

## Steps

### Step 05.1 - ViewModel filter/sort/search state

> Add a `StreamsFilter(query: String = "", category: String? = null, topic: String? = null, language: String? = null, mediaKind: String? = null, sort: SortMode = SortMode.NAME)` and a `MutableStateFlow`. Combine the source Flow with the filter to produce the displayed list: case-insensitive `query` match on title/topic/language; equality match on the chosen facets; `SortMode { NAME, TOPIC, LANGUAGE, RECENT }`. Expose available facet values (distinct categories/topics/languages present) for the filter UI. Add `onQueryChanged`, `onFilter`, `onSort`. Keep pinned-first ordering as the primary sort key before the chosen SortMode.

**Verification:** `Grep` - `StreamsFilter`, `SortMode`, `onQueryChanged`, `onSort` present; combine of source + filter present; `GlobalScope` zero hits.

### Step 05.2 - Row chips (topic + language)

> In `item_stream_source.xml` add small topic + language chips/labels (use `?attr/` colours, no hex). In `StreamSourceAdapter` bind `category`/`topic`/`language` (hide chip when null/blank). D-pad focus preserved.

**Verification:** `Grep` - no `="#` hex in `item_stream_source.xml`; adapter binds topic/language.

### Step 05.3 - Screen controls (portrait + landscape)

> In both `res/layout/activity_streams.xml` and `res/layout-land/activity_streams.xml` add a search input (`TextInputLayout`/`SearchView`) and filter + sort affordances (chips row or a compact menu) above the RecyclerView. `?attr/` colours only; set `nextFocus*` for D-pad/TV; keep content within `systemBars`+`displayCutout` insets. Wire to the ViewModel via `collectOnLifecycle`.

**Verification:** `Glob` - both layouts exist and changed. `Grep` - search view id present in BOTH portrait and land; no `="#"` hex; `.\a.ps1 fr` (resources) PASS.

### Step 05.4 - Strings

> Add via `set-android-string.ps1 -Action add` EN/RU/UK: `streams_search_hint`, `streams_filter`, `streams_sort`, `streams_sort_name`, `streams_sort_topic`, `streams_sort_language`, `streams_sort_recent`, `streams_filter_all`.

**Verification:** `check_strings_localized.ps1 -KeyPrefix "streams_sort"` and `-KeyPrefix "streams_filter"` exit 0.

**Status:**
- [x] Step 05.1 - ViewModel filter/sort/search state (`StreamsFilter`, `SortMode`, facets, `combine`)
- [x] Step 05.2 - row topic/language chips (adapter + item layout)
- [x] Step 05.3 - screen controls in portrait + landscape (search + filter + sort)
- [x] Step 05.4 - strings (EN/RU/UK lockstep, 8 keys)

## Phase Done Criteria

- [x] Steps 05.1-05.4 done.
- [ ] `.\a.ps1 fc` PASS; landscape layout has parity (search + controls in both). - central build deferred to orchestrator; parity verified by Grep (`etSearch` id present in both layouts).
- [x] `Grep` `="#` in touched layouts -> zero. No bare view-bound `lifecycleScope.launch { collect }` (uses `collectOnLifecycle`, which wraps `repeatOnLifecycle`).
- [x] Filtering/sorting derived in the ViewModel; Activity only forwards input.

## Step Log

- 05.1: `StreamsViewModel` adds `StreamsFilter(query,category,topic,language,mediaKind,sort)` + `enum SortMode { NAME, TOPIC, LANGUAGE, RECENT }` + a `_filter: MutableStateFlow`. `init` now `combine`s `observeStreamSources()` with `_filter` -> `applyFilter` (case-insensitive query on title/topic/language; equality on facets; pinned-first primary key, then SortMode). `StreamsUiState` gains `filter`, `facets` (distinct categories/topics/languages), `isImporting`. Added `onQueryChanged`, `onFilter`, `onSort`. The combine preserves the live `isImporting` flag across emissions. No View types; no GlobalScope.
- 05.2: `item_stream_source.xml` gains a `chipRow` (topic/language `TextView` chips on `@drawable/bg_stream_chip`, a new `?attr/colorSurfaceVariant` rounded shape). Adapter binds them via `bindChip` - hidden when null/blank; whole row hidden when both empty. Existing tap/pin/long-press + D-pad focus untouched.
- 05.3: both `res/layout/activity_streams.xml` and `res/layout-land/activity_streams.xml` gain a `streamControls` bar above the RecyclerView: `TextInputLayout` search (`etSearch`, weight 1) + `btnFilter` (`@drawable/ic_tune`) + `btnSort` (`@drawable/ic_sort`), all `?attr/` colours, `nextFocus*` wired (search<->filter<->sort<->list), inside the existing `fitsSystemWindows` insets. New generic `ic_tune`/`ic_sort` vectors added (existing `ic_filter_clear`/`ic_sort_random` carry wrong semantics). Activity wires search via `doAfterTextChanged -> onQueryChanged`; filter/sort buttons open single-choice dialogs (`topic` facet + "All"; SortMode list). Displayed list collected via `collectOnLifecycle`.
- 05.4: added `streams_search_hint`, `streams_filter`, `streams_sort`, `streams_sort_name`, `streams_sort_topic`, `streams_sort_language`, `streams_sort_recent`, `streams_filter_all` (EN/RU/UK lockstep).

## Deviations

- Filter UI ships the topic facet single-choice picker (+ "All" reset) per the strategic fallback note ("ship search + a single filter spinner first"). `onFilter` already accepts category/language/mediaKind for later controls; only topic has a UI surface in this phase.
- Chips rendered as lightweight `TextView` labels (not `com.google.android.material.chip.Chip`) to avoid Chip measurement overhead inside RecyclerView rows; `?attr/`-only styling preserved.
