# Phase 01 - Wear view foundations

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05, Phase 06, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Introduce the watch view-mode value, the two new preference keys and the column-fit rule that decides how many columns a grid actually gets. No screen reads them yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearViewMode.kt` | New | ≤ 40 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/util/GridColumnFit.kt` | New | ≤ 80 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt` | Modified | ≤ 120 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences/WearPreferencesRepositoryImpl.kt` | Modified | ≤ 300 |
| `wear/src/main/res/values/strings.xml` | Modified | ≤ 40 |
| `wear/src/test/java/com/sza/fastmediasorter/wear/util/GridColumnFitTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Add the WearViewMode value

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearViewMode.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an enum `WearViewMode` with entries `LIST`, `GRID_2`, `GRID_3` and a `requestedColumns` property returning 1, 2 and 3. Add a `fromNameOrDefault(name: String?)` companion function returning `LIST` for an unknown or null name.

**Why:**

Strategic ADR-1 makes the view one stored value with three states shared by the main screen and the Resources page, so the value needs a single home rather than a boolean per screen; `fromNameOrDefault` exists because a stored preference read before first write must land on the current list behaviour rather than crash.

**Verification:**

- `Glob` - `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearViewMode.kt` exists.
- `Grep` - `enum class WearViewMode` matches exactly once.
- `Grep` - `GRID_3` present.
- `Grep` - `fun fromNameOrDefault` present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - WearViewMode enum (LIST/GRID_2/GRID_3 + fromNameOrDefault) and GridColumnFit.columnsFor created; predicates PASS, arithmetic reproduces the strategic measurement (170dp->54dp keeps 3 cols, 127dp->39dp steps down to 2)

---

### Step 01.2 - Add the column-fit rule

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/util/GridColumnFit.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create an object `GridColumnFit` with `fun columnsFor(mode: WearViewMode, availableWidthDp: Int, gapDp: Int = 4, minTargetDp: Int = 48): Int`. Return 1 for `LIST`. Otherwise start at `mode.requestedColumns` and step down while the resulting cell width - available width minus the gaps between columns, divided by the column count - is below `minTargetDp`; never return less than 1. Declare `gapDp` and `minTargetDp` as named constants with defaults rather than inline numbers so the detekt MagicNumber rule stays satisfied.

**Why:**

Strategic ADR-2 requires the column count to be derived from the available width rather than the mode name, because the 2026-08-18 emulator measurement showed three columns yield about 54 dp on a 240 dp watch but about 40 dp on a 180 dp one, below the 48 dp accessibility target recorded in strategic §3.2.

**Verification:**

- `Glob` - `wear/src/main/java/com/sza/fastmediasorter/wear/util/GridColumnFit.kt` exists.
- `Grep` - `object GridColumnFit` matches exactly once.
- `Grep` - `fun columnsFor` present.
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - WearViewMode enum (LIST/GRID_2/GRID_3 + fromNameOrDefault) and GridColumnFit.columnsFor created; predicates PASS, arithmetic reproduces the strategic measurement (170dp->54dp keeps 3 cols, 127dp->39dp steps down to 2)

---

### Step 01.3 - Cover the column-fit rule with unit tests

**Files:** `wear/src/test/java/com/sza/fastmediasorter/wear/util/GridColumnFitTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add unit tests asserting: `LIST` returns 1 at any width; `GRID_3` returns 3 at 170 dp available width; `GRID_3` returns 2 at 127 dp; `GRID_2` returns 2 at 127 dp and 1 at 60 dp; the result is never below 1 at a width of 0.

**Why:**

The fallback is the one behaviour the owner will see as "Grid3 is unavailable on small watches" rather than as silently merged cells, per strategic §6 item 4, and the two widths in the test are the inscribed-square figures that measurement produced for the 240 dp and 180 dp watches.

**Verification:**

- `Glob` - `wear/src/test/java/com/sza/fastmediasorter/wear/util/GridColumnFitTest.kt` exists.
- `Grep` - `GRID_3` present in the test file.
- `.\a.ps1 fu` - the new test class passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - GridColumnFitTest added; targeted run :wear:testDebugUnitTest --tests GridColumnFitTest exit 0, result XML reports tests=6 failures=0 errors=0 skipped=0

---

### Step 01.4 - Persist the two new preferences

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences/WearPreferencesRepositoryImpl.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `viewMode: Flow<WearViewMode>` and `keepScreenAwakeOutsidePlayers: Flow<Boolean>` to `WearPreferencesRepository` with matching setters, and implement them in `WearPreferencesRepositoryImpl` over the existing `wear_settings` DataStore using new keys. Default `viewMode` to `LIST` and `keepScreenAwakeOutsidePlayers` to `false`. Add the display strings for the three view modes and for the keep-awake setting through `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` so EN, RU and UK stay in lockstep.

**Why:**

Strategic §4 records that the watch settings store today holds only media-type and slideshow toggles, so both new settings need keys before any screen can read or mirror them; defaulting keep-awake to off keeps the current battery behaviour for anyone who never opens the setting.

**Verification:**

- `Grep` - `viewMode` present in `WearPreferencesRepository.kt`.
- `Grep` - `keepScreenAwakeOutsidePlayers` present in `WearPreferencesRepository.kt` and in `WearPreferencesRepositoryImpl.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_view_"` - exit 0.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - viewMode and keepScreenAwakeOutsidePlayers added to WearPreferencesRepository and its DataStore impl; four strings added EN/RU/UK via set-android-string; parity audit exit 0; compiled the WEAR module (:wear:compileDebugKotlin exit 0) instead of the plan's a.ps1 fk, which builds app_v2 and would not have covered these files
- 2026-08-18 - Phase-boundary audit: Layer 1 clean - dependency direction data->domain correct, no new UseCase/Repository/ViewModel/Manager needing the naming rule, largest touched file 146 LOC. Layer 2 clean - both new flows are cold dataStore.data.map, identical to the eight pre-existing preference flows, no scope held and no collection introduced. Noted but not filed: the preferencesDataStore delegate is declared inside the impl class rather than top-level; the Hilt provider is @Singleton so exactly one DataStore exists, pre-existing and untouched here.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/wear.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module wear`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`WearViewMode`, `GridColumnFit.columnsFor` and the two preference flows exist and are unit-tested. No screen reads them yet - Phase 03 is the first consumer. S1730 reuses `GridColumnFit` unchanged and stores its own separate mode value.

---

## Rollback Plan

Revert phase commit(s) - new files only plus two additive preference keys; no migration and no user-facing surface changed.
