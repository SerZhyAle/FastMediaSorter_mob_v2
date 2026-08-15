# Phase 04 - Statistics dashboard + navigation entry

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02 (reads snapshot + gate; does not need Phase 03 to compile)
**Blocks:** Phase 05 (adds bottom actions to this screen)
**Steps done:** 8 / 8
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Build the "Statistics" screen: a new `StatisticsActivity` + `@HiltViewModel` reading the all-time snapshot through a use case, rendering summary cards, a type-distribution bar, collapsible category sections (flavor-filtered, zero-hidden), a privacy note, and an empty state. Add the General-tab navigation row that opens it, visible only when the toggle is on. No "Send to author" / "Export" buttons yet (Phase 05). No "Reset" button ever (ADR-4).

---

## Prerequisites

- [ ] Phase 01 ✅ (`StatisticsRepository.getSnapshot`, `StatsCategoryAvailability`, models).
- [ ] Phase 02 ✅ (`enableStatistics` readable; toggle row present).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetStatisticsUseCase.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsViewModel.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsActivity.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsUiState.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsAdapter.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsRowFormatter.kt` | New | ≤ 220 |
| `app_v2/src/main/res/layout/activity_statistics.xml` | New | - |
| `app_v2/src/main/res/layout-land/activity_statistics.xml` | New | - |
| `app_v2/src/main/res/layout/item_stats_card.xml` | New | - |
| `app_v2/src/main/res/layout/item_stats_section_header.xml` | New | - |
| `app_v2/src/main/res/layout/item_stats_metric_row.xml` | New | - |
| `app_v2/src/main/res/layout/item_stats_distribution.xml` | New | - |
| `app_v2/src/main/res/layout/view_stats_empty.xml` | New | - |
| `app_v2/src/main/AndroidManifest.xml` | Modified | - |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 470 |
| `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`) | Modified | - |

> `activity_statistics.xml` has a landscape counterpart - BOTH must be authored (cards reflow per strategic §5.5). Colors via `?attr/`/`@color/` only - no hardcoded hex (Rule 19).

---

## Steps

### Step 04.1 - GetStatisticsUseCase

**Files:** `domain/usecase/GetStatisticsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class GetStatisticsUseCase @Inject constructor(private val repository: StatisticsRepository, private val availability: StatsCategoryAvailability)`. `suspend operator fun invoke(): StatsSnapshot` returns the snapshot; optionally also expose `availableCategories()` passthrough. Thin - no formatting, no Android imports.

**Verification:**

- `Glob` - `GetStatisticsUseCase.kt` exists.
- `Grep` - `class GetStatisticsUseCase` matches once; `getSnapshot` called.

**Step Log:** Thin use case: `invoke()` returns `repository.getSnapshot()`, `availableCategories()` passes through `StatsCategoryAvailability`. No Android imports.

**Status:** `[x]` done

---

### Step 04.2 - UI state + ViewModel

**Files:** `ui/statistics/StatisticsUiState.kt`, `ui/statistics/StatisticsViewModel.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Define `StatisticsUiState` (sealed or data class) with: loading flag, list of summary cards, type-distribution model, ordered list of visible category sections each with its metric rows, `isEmpty` flag for the empty state. Create `@HiltViewModel class StatisticsViewModel @Inject constructor(private val getStatistics: GetStatisticsUseCase, @IoDispatcher io)`. On init, load the snapshot in `viewModelScope` on `io`, map it to `StatisticsUiState`, and expose it as a `StateFlow`. Apply visibility rules: hide a category if `!StatsCategoryAvailability.isCategoryAvailable(it)`; within a visible category hide individual zero-value rows; always show the always-on baseline rows (first launch, launch count, install version) when present; set `isEmpty` when no detailed activity exists yet (only baseline). All-time totals only - no period selector state (ADR-7).

**Verification:**

- `Glob` - both files exist.
- `Grep` - `@HiltViewModel` on `StatisticsViewModel`; `StateFlow` exposed.
- `Grep` - `isCategoryAvailable` referenced (category gating).
- `Grep` - no period/`Reset` symbol in the ViewModel (ADR-4, ADR-7).

**Step Log:** `StatisticsUiState` + sealed `StatisticsListItem` (cards/distribution/header/row/empty/privacy) with raw values + `MetricFormat`. `@HiltViewModel` maps snapshot off `@IoDispatcher`, exposes `StateFlow`, applies category gating via `availableCategories()`, zero-row hiding (USAGE baseline kept), `isEmpty` on `hasNoDetailedActivity`, collapse via in-memory `collapsedCategories`. No period/reset state (the only "period/reset" hit is the doc line asserting their absence).

**Status:** `[x]` done

---

### Step 04.3 - Row formatter (counts, bytes, durations)

**Files:** `ui/statistics/StatisticsRowFormatter.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create a formatter mapping raw `Long` values to display strings: counts with locale grouping, bytes via the project's existing human-readable size formatter (reuse it - query catalog for a `*FileSize*`/`*formatBytes*` util rather than writing a new one), durations as `h m`/`m s`. The type-distribution percentages computed from `MediaActionCounts`. Keep it pure (no Android `Context` beyond a `Locale`/resources passthrough if needed). Non-color differentiation for the distribution legend (values shown as text, strategic §3.2 accessibility).

**Verification:**

- `Glob` - `StatisticsRowFormatter.kt` exists.
- `Grep` - reuse of an existing byte/size formatter (no duplicated extension/size table) - the existing util's symbol referenced.

**Step Log:** `StatisticsRowFormatter` (object): `formatCount` (locale grouping), `formatBytes` -> reuses `core/util/FileSize.kt#formatFileSize` (imported, not duplicated), `formatDuration` (`Hh Mm` / `Mm Ss` via short unit strings), `formatDate` (medium), `buildDistribution` -> `DistributionSlice` list with value-bearing percentages. `Locale.getDefault()` keeps it API-23-safe on legacy.

**Status:** `[x]` done

---

### Step 04.4 - List item layouts (cards, headers, rows, distribution, empty)

**Files:** `res/layout/item_stats_card.xml`, `item_stats_section_header.xml`, `item_stats_metric_row.xml`, `item_stats_distribution.xml`, `view_stats_empty.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Author the item layouts per strategic §5.5: a summary card (big value + label + icon), a collapsible section header (title + chevron, focusable), a metric row (icon + label + primary value + optional secondary value), a distribution bar with a values-bearing legend, and a friendly empty-state view. Use `?attr/` theme colors and existing dimens/styles; ensure focusable/clickable + `nextFocus*` for D-pad (Rule 16); keep content within `systemBars`+`displayCutout` safe area (Rule 17). No hardcoded hex.

**Verification:**

- `Glob` - all five item layout files exist.
- `Grep` - no `="#` hardcoded hex in any of the five files.
- `Grep` - `?attr/` referenced in `item_stats_card.xml`.

**Step Log:** Authored the five required item layouts plus two helpers (`item_stats_distribution_legend.xml` for legend rows, `item_stats_privacy_note.xml` for the footer note). Section header is a focusable/clickable row with a rotatable `ic_arrow_drop_down` chevron. Distribution segments tint `bg_stats_distribution_segment.xml` with new `stats_type_*` colors (declared in colors.xml; legend always shows value text so colour is never the only differentiator). All `?attr/` theme colors, no hex.

**Status:** `[x]` done

---

### Step 04.5 - RecyclerView adapter

**Files:** `ui/statistics/StatisticsAdapter.kt`
**Depends on:** Step 04.2, Step 04.4

**Prompt for developer:**

> Create a multi-view-type `ListAdapter` (DiffUtil) rendering cards, distribution, section headers (collapse/expand), and metric rows from `StatisticsUiState`. Collapsing a section toggles visibility of its rows. Use `collectOnLifecycle`/`repeatOnLifecycle` in the Activity to feed it - never a bare `lifecycleScope.launch { flow.collect {} }` on the view-bound state (Rule 19). No business logic in the adapter.

**Verification:**

- `Glob` - `StatisticsAdapter.kt` exists.
- `Grep` - `ListAdapter` / `DiffUtil` used.
- `Grep` - adapter contains no `getSnapshot`/repository reference (no business logic).

**Step Log:** `StatisticsAdapter` (`ListAdapter` + `DiffUtil`, stable item ids) with 6 view types via ViewBinding. Header tap -> `onToggleSection` callback (no state held in adapter). Distribution view holder builds weighted bar segments + legend rows programmatically. `spanSizeFor(position)` lets the Activity grid keep cards at 1 cell, everything else full width. No repository/snapshot access.

**Status:** `[x]` done

---

### Step 04.6 - Activity + screen layout (portrait + landscape) + manifest

**Files:** `ui/statistics/StatisticsActivity.kt`, `res/layout/activity_statistics.xml`, `res/layout-land/activity_statistics.xml`, `AndroidManifest.xml`
**Depends on:** Step 04.5

**Prompt for developer:**

> Create `@AndroidEntryPoint class StatisticsActivity : AppCompatActivity` hosting the RecyclerView + privacy-note footer (strategic §5.5). Collect `StatisticsViewModel.uiState` via `repeatOnLifecycle`. Author `activity_statistics.xml` for portrait and a landscape variant where summary cards reflow (2-3 across). Include a Toolbar with the window title and up-navigation. Register the Activity in `AndroidManifest.xml` (parent = SettingsActivity; respect existing theme). Add the privacy-note `TextView` referencing the Phase 04 string (data stored on device; user sends the email themselves). NO send/export/reset buttons in this phase.

**Verification:**

- `Glob` - Activity + both layouts exist.
- `Grep` - `@AndroidEntryPoint` on `StatisticsActivity`.
- `Grep` - `repeatOnLifecycle` or `collectOnLifecycle` used in the Activity.
- `Grep` - `StatisticsActivity` registered in `AndroidManifest.xml`.
- `Grep` - no `="#` hardcoded hex in either `activity_statistics.xml`.

**Step Log:** `StatisticsActivity` extends `BaseActivity<ActivityStatisticsBinding>` (deviation from the literal `AppCompatActivity` brief - matches sibling `AuthSessionsActivity` and inherits edge-to-edge/locale/TV/mouse plumbing). Toolbar + up-nav (`setSupportActionBar` + `setDisplayHomeAsUpEnabled` + `finish()`), `GridLayoutManager` span from `@integer/statistics_card_span` (2 portrait / 3 landscape), `collectOnLifecycle(uiState)`. Portrait + landscape layouts authored (CoordinatorLayout, `fitsSystemWindows`); reflow driven by the orientation-qualified integer so both XMLs are identical. Registered in manifest with `parentActivityName=.ui.settings.SettingsActivity`. Privacy note rendered as the final list item (cleaner than a fixed footer over a scrolling list). No hex.

**Status:** `[x]` done

---

### Step 04.7 - Dashboard strings (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add all dashboard strings in lockstep via `scripts/utils/set-android-string.ps1 -Action add` (`-En -Ru -Uk`): window title (`statistics_title`), navigation row label (`settings_statistics_open`), summary-card labels (sorted / freed space / player time), category names (operations/capture/viewing/editing/sources/usage), metric-row labels for the wired metrics, distribution label, privacy note (`statistics_privacy_note`), empty-state text (`statistics_empty`). RU/UK with ё/є. Strings pass `docs/COMMUNICATION_POLICY.md` §2 + §6.

**Verification:**

- `Grep` - `statistics_title`, `statistics_privacy_note`, `statistics_empty`, `settings_statistics_open` present in all three locale files.
- Script: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_"` exits 0.
- Script: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_statistics_open"` exits 0.
- Predicate: strings pass COMMUNICATION_POLICY §6 checklist.

**Step Log:** Added 47 `statistics_`-prefixed keys (+ `settings_statistics_open`) EN/RU/UK in lockstep via `set-android-string.ps1 -Action add`: title, nav label, 3 card labels, 6 category names, 5 media-type labels, 25 metric-row labels, distribution title + value format, 3 short time units, empty state, privacy note. Empty state explains the why + invites to act (§2.4); privacy note is a direct human line (Voice). RU ё / UK є applied. Both `check_strings_localized.ps1` runs exit 0.

**Status:** `[x]` done

---

### Step 04.8 - General-tab navigation row (visible only when toggle on)

**Files:** `res/layout/fragment_settings_general.xml`, `res/layout-land/fragment_settings_general.xml`, `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 04.6, Step 04.7

**Prompt for developer:**

> Add a clickable "Statistics" row beneath the Phase 02 toggle in BOTH `layout/` and `layout-land/fragment_settings_general.xml`, mirroring an existing settings row that opens an Activity. In `GeneralSettingsViewSetupHelper`, bind its visibility to the current `enableStatistics` value (`row.isVisible = settings.enableStatistics`) and update it reactively when the toggle changes; on click `startActivity(Intent(context, StatisticsActivity::class.java))` (the established settings→Activity pattern). Row must be D-pad focusable. When the toggle is off the row is `GONE` (strategic ADR-3, §11.2).

**Verification:**

- `Grep` - the navigation row id present in BOTH `layout/` and `layout-land/fragment_settings_general.xml`.
- `Grep` - `StatisticsActivity::class.java` referenced in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - the row visibility is bound to `enableStatistics` (conditional `isVisible`/`visibility`).
- `Grep` - `settings_statistics_open` referenced from both layout files.
- Build: `.\a.ps1 fc` passes.

**Step Log:** Added a clickable `MaterialCardView` (`rowOpenStatistics`, focusable, default `gone`) just after `rowEnableStatistics` in BOTH portrait and landscape `fragment_settings_general.xml`, mirroring the `row_saved_authorizations` nav-row pattern. Wired in `GeneralSettingsViewSetupHelper.setupStatisticsRow()`: click -> `startActivity(Intent(.., StatisticsActivity::class.java))`; visibility bound reactively via `collectOnLifecycle(viewModel.settings) { isVisible = it.enableStatistics }`, so toggling collection hides/shows the row without a reload. Build check (`fc`) deferred to central build per task instructions.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles + resources link - run `.\a.ps1 fc`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` passes (no hardcoded hex, lifecycle-safe collection).
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- `StatisticsActivity` + `StatisticsViewModel` exist and render the snapshot; Phase 05 adds the bottom action bar (Send to author + Export) to `activity_statistics.xml` (both orientations) and the VM methods behind them.
- The privacy note is already on screen; Phase 05's send flow must stay consistent with it.
- Navigation row is gated by the toggle - no change needed in Phase 05.

---

## Rollback Plan

Revert phase commit(s). New Activity + layouts are self-contained; the only edits to existing files are the manifest registration and the General-tab navigation row - both additive. Restore prior `fragment_settings_general.xml` if needed.
