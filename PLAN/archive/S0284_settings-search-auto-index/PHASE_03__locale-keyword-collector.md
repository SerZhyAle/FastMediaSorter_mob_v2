# Phase 03 - Locale Keyword Collector

**Strategic spec:** [`../S0284_settings-search-auto-index.md`](../S0284_settings-search-auto-index.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Implement `LocalizedKeywordCollector` — converts a `RawSettingsSearchEntry` into a fully-resolved `SettingsSearchIndex` whose `keywords` pool contains EN, RU, and UK strings for the entry's title, subtitle, and hint.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 Research items #1, #2, #3, #4 are `Resolved`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LocalizedKeywordCollector.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SupportedSearchLocales.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` | Modified | ≤ 60 |

---

## Steps

### Step 03.1 - Add `SupportedSearchLocales`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SupportedSearchLocales.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an object `SupportedSearchLocales` exposing `val tags: List<String> = listOf("en", "ru", "uk")`. The order MUST match the project's three locale `values*` directories (`values/`, `values-ru/`, `values-uk/`). Add a one-line KDoc explaining that this is the closed set; adding a new locale to the project means appending to this list.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SupportedSearchLocales.kt` exists.
- `Grep` - `object SupportedSearchLocales` matches exactly once.
- `Grep` - `listOf("en", "ru", "uk")` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SupportedSearchLocales.kt (+12 LOC).

---

### Step 03.2 - Implement `LocalizedKeywordCollector`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LocalizedKeywordCollector.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a class `LocalizedKeywordCollector @Inject constructor(@ApplicationContext private val context: Context)` implementing `SettingsSearchKeywordCollector`. Behavior:
>
> 1. Lazily build a `Map<String, Resources>` keyed by locale tag (`"en"`, `"ru"`, `"uk"`), each value obtained via `context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }).resources`. Compute once and cache as a member.
> 2. In `override fun enrich(raw: RawSettingsSearchEntry): SettingsSearchIndex?`:
>    - Resolve the title across all locales:
>      - If `raw.titleResId != null`, get its string in each of the 3 locales.
>      - Else if `raw.inlineTitle != null`, use the inline string for all 3 locales (no translation).
>      - Else if `raw.hintResId != null`, fall back to hint resId across 3 locales (covers TEXT_INPUT/SPINNER without explicit title — research item #2 default).
>      - Else if `raw.inlineHint != null`, use inline hint.
>      - Else return `null` (entry has nothing to display or search).
>    - Choose the entry's user-facing title: the EN string (first locale). If EN is blank, fall back to the first non-blank among RU/UK.
>    - Build the keyword pool:
>      - Start with the 3 locale strings of the title (de-duplicated, lowercased, trimmed).
>      - Add the 3 locale strings of the subtitle (`subtitleResId` / `inlineSubtitle`), de-duplicated.
>      - Do NOT add help-popup strings (research item #3 default = excluded).
>      - Filter out blank entries.
>    - Derive `key: String` from `viewId`: use Android resource entry name via `context.resources.getResourceEntryName(raw.viewId)`. If the call throws `Resources.NotFoundException` (defensive), use `"viewId_${raw.viewId}"` as fallback.
>    - Derive `sectionId` and `destination` from `SettingsSearchTabMapping.assignmentFor(raw.layoutResId)`. If `null`, return `null` (defensive — should not happen if `SettingsSearchLayoutCatalog` and `SettingsSearchTabMapping` stay in sync).
>    - Build and return `SettingsSearchIndex(key, title, keywords, sectionId, destination, viewId)`.
> 3. The `SettingsSearchIndex` data class itself stays in `ui/settings/SettingsSearchIndex.kt` — only its static `entries` list and `search()` function leave during Phase 04.
>
> Constraints:
> - Cache the per-locale `Resources` map - re-creating contexts on every call would dominate the cold-start cost.
> - Never call `context.resources.getString` directly without picking a locale - that would silently use the current device locale and merge keywords incorrectly.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LocalizedKeywordCollector.kt` exists.
- `Grep` - `class LocalizedKeywordCollector` matches exactly once.
- `Grep` - `: SettingsSearchKeywordCollector` present in class header.
- `Grep` - `override fun enrich` matches exactly once.
- `Grep` - `createConfigurationContext` matches at least once.
- `Grep` - `SupportedSearchLocales.tags` matches at least once.
- `Grep` - `getResourceEntryName` matches at least once.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 8/8 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LocalizedKeywordCollector.kt (+114 LOC).

---

### Step 03.3 - Trim static `SettingsSearchRegistry.entries` from `SettingsSearchIndex.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Modify `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`:
>
> 1. Keep the `SettingsSearchDestination` enum and the `SettingsSearchIndex` data class exactly as they are. These are still the contract with `SettingsSearchAdapter` and `SettingsActivity`.
> 2. Delete the `object SettingsSearchRegistry { val entries: List<...> = listOf(...) ; fun search(...) ; fun isEntryAvailable(...) }` block in its entirety.
> 3. The file's final size should be roughly the same as Step 01 expects: data class + enum only. No imports of `BuildConfig`, `R`, or `FastMediaSorterApp` should remain in this file.
>
> The replacement `SettingsSearchRegistry` (now a class, no longer an object) is introduced in Phase 04 — Step 03.3 is the deletion half of the transition. Between Phase 03 and Phase 04 the project will NOT compile because `SettingsActivity` still references `SettingsSearchRegistry.entries` and `SettingsSearchRegistry.search`. Both phases must land in the same commit, or Phase 04 must follow immediately on the same branch without intermediate build attempts.
>
> Note: this is the only step in S0284 that produces a temporarily broken build state. Phase 04 closes the gap.

**Verification:**

- `Grep` - `data class SettingsSearchIndex` matches exactly once in `SettingsSearchIndex.kt`.
- `Grep` - `enum class SettingsSearchDestination` matches exactly once in `SettingsSearchIndex.kt`.
- `Grep` - `object SettingsSearchRegistry` returns zero hits in `SettingsSearchIndex.kt`.
- `Grep` - `import com.sza.fastmediasorter.BuildConfig` returns zero hits in `SettingsSearchIndex.kt`.
- `Grep` - `import com.sza.fastmediasorter.R` returns zero hits in `SettingsSearchIndex.kt`.
- File line count check: `(Get-Content "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt" | Measure-Object -Line).Lines` ≤ 30.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 6/6 PASS (file is 24 LOC, contains only enum + data class, no BuildConfig/R imports). Project will not compile until Phase 04 — by design. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt (668 → 24 LOC; -644).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compilation will fail at this point — that is expected and documented in Step 03.3. Do NOT mark this phase Done by running `/build`; instead, verify via `Grep` predicates only.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

> **IMPORTANT:** Phase 04 must follow Phase 03 on the same branch with no build attempt in between. If you must pause work, leave Phase 03 unmerged.

---

## Handoff Notes to Next Phase

- The pipeline parts (`LayoutSettingsSearchSource` from Phase 02, `LocalizedKeywordCollector` from Phase 03) are ready to be wired into a new `SettingsSearchRegistry` class.
- The static registry is gone; the project does not compile until Phase 04 introduces the new class and updates `SettingsActivity`.

---

## Rollback Plan

Revert Phase 03 commit to restore the static registry. If Phase 03 and Phase 04 landed in one commit (preferred), revert both together. No data migration.
