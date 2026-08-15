# Phase 01 - Filter model

**Strategic spec:** [`../S0580_streams-filter-category-language.md`](../S0580_streams-filter-category-language.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Make `StreamsViewModel` filter by category and language with a user-chosen AND/OR combine mode, split multi-language catalog values into individual facet options, and keep language-less rows visible under an active language filter. No UI, picker, or string changes in this phase.

---

## Prerequisites

- [ ] Strategic §6.1, §6.2, §6.3, §6.4 are Resolved (they are).
- [ ] Read [`research/01__catalog-language-format.md`](research/01__catalog-language-format.md) - language cells are lowercase English names, sometimes comma-separated.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt` | New | ≤ 220 |

---

## Steps

### Step 01.1 - Add combine-mode and split language facets

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `enum class FilterMatchMode { ALL, ANY }` to `StreamsViewModel` (ALL = category AND language, ANY = category OR language). Add `matchMode: FilterMatchMode = FilterMatchMode.ALL` to `StreamsFilter`. Change `facetsOf` so the `languages` facet splits each non-null `language` cell on `,`, trims, drops blanks, lowercases for de-dup, and presents each distinct language once (preserve a stable sorted order). Categories and topics facets stay as-is.

**Verification:**

- `Grep` - `enum class FilterMatchMode` matches once in `StreamsViewModel.kt`.
- `Grep` - `matchMode: FilterMatchMode` present in the `StreamsFilter` data class.
- `Grep` - `split` present inside `facetsOf` (language cell splitting).

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Added `FilterMatchMode { ALL, ANY }`, `matchMode` field on `StreamsFilter`, comma-split language facets in `facetsOf`. Files: StreamsViewModel.kt.

---

### Step 01.2 - Rewrite applyFilter category/language matching with combine mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `applyFilter`, replace the flat AND facet check for category+language with combine-mode logic. Define `categoryMatch = filter.category != null && source.category == filter.category` and `languageMatch = filter.language != null && source.language.tokens().any { it.equals(filter.language, ignoreCase = true) }`, where `tokens()` splits the language cell on `,` and trims. Per strategic §6.4, a row whose `language` is null/blank ALWAYS passes the language predicate (it is never hidden by an active language filter). Combine:
> - both `filter.category` and `filter.language` null → pass.
> - only one set → that single predicate (null-language rows still pass the language case).
> - both set → `categoryMatch && languageMatch` when `matchMode == ALL`, else `categoryMatch || languageMatch`; null-language rows still pass via the language branch.
> Keep the existing query substring check and the pinned-first + SortMode ordering unchanged. Keep `topic`/`mediaKind` equality checks as they are (still ANDed; not exposed in the new UI, query box covers topic).

**Verification:**

- `Grep` - `matchMode` referenced inside `applyFilter`.
- `Grep` - `ignoreCase = true` present in the language match in `applyFilter`.
- `Grep -n "Log\.d\("` - zero hits in `StreamsViewModel.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Rewrote `applyFilter` with ALL/ANY combine logic + null-language always passes language predicate; added top-level `tokens()` splitter. Files: StreamsViewModel.kt.

---

### Step 01.3 - Update onFilter to drive category, language and match mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace `onFilter(category, topic, language, mediaKind)` with `onFilter(category: String? = null, language: String? = null, matchMode: FilterMatchMode = FilterMatchMode.ALL)` updating those three fields on `_filter` (leave `topic`/`mediaKind` at their current values - do not reset them). The old four-arg signature has only one caller (`StreamsActivity.showFilterDialog`, rewritten in Phase 04), so no other call site breaks. Keep `onQueryChanged` and `onSort` unchanged.

**Verification:**

- `Grep` - `fun onFilter(` matches once and includes `matchMode: FilterMatchMode`.
- `Grep` - the old `topic: String? = null,` parameter no longer appears in the `onFilter` signature.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. `onFilter(category, language, matchMode)`; topic/mediaKind no longer reset. Compile bridge applied to the single caller `StreamsActivity.showFilterDialog` (routes topic via query) - full dialog rewrite in Phase 04 Step 04.2. Plan claim "no caller breaks" was inaccurate; recorded here. Files: StreamsViewModel.kt, StreamsActivity.kt.

---

### Step 01.4 - Unit-test the filter logic

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a JVM unit test exercising the filter logic via the ViewModel's public API (construct with fakes for the use cases, or extract `applyFilter`/`facetsOf` to an internal testable function if injection is heavy). Cover: (a) multi-language cell `"russian,ukrainian"` matches a `ukrainian` language filter; (b) a null-language row stays visible under an active language filter; (c) ALL mode requires both category and language; (d) ANY mode passes a row matching either; (e) only-one-filter-set behaves identically under ALL and ANY; (f) `facetsOf` splits `"russian,ukrainian"` into two distinct language facets. Use Timber-free pure assertions.

**Verification:**

- `Glob` - `StreamsFilterTest.kt` exists.
- `Grep` - `russian,ukrainian` present in the test (multi-language case).
- Build: `.\a.ps1 fu` (or `gradlew testStandardDebugUnitTest --tests *StreamsFilterTest*`) - the new test class passes (check its per-class XML report; the suite has unrelated pre-existing failures).

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. `check-standard-fast.ps1 -Mode Unit -Tests *StreamsFilterTest*` -> BUILD SUCCESSFUL; XML report tests=6 failures=0 errors=0. Extracted `applyFilter`/`facetsOf` to `internal companion` for testability. Files: StreamsFilterTest.kt (new), StreamsViewModel.kt.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Unit` compiled standard debug Kotlin/Java/Hilt + ran the new test (BUILD SUCCESSFUL).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added (post-change.ps1 dev-log PASS).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (catalog_sync via post-change.ps1).

---

## Handoff Notes to Next Phase

- `StreamsFacets.languages` now holds individual language NAMES (lowercase-deduped, display-cased). Phase 02's option mapper consumes these.
- `onFilter(category, language, matchMode)` is the single entry point Phase 04 calls from the filter UI.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or user-facing surface changed; the new test class is additive.
