# Phase 03 - Order, search and launch statistics

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Turn the cache into an ordered, filtered list: persist the chosen order, record per-command launch statistics, and add the strings the order picker and the empty state need.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1401 phase 03"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 545 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 940 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherJournalRepository.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherJournalRepositoryImpl.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/QueryAllAppsUseCase.kt` | New | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `docs/settings/device-profile-nonpresettable.json` | Modified | - |
| `app_v2/src/testStandard/java/com/sza/fastmediasorter/domain/usecase/apps/QueryAllAppsUseCaseTest.kt` | New | ≤ 220 |

> `AppSettings.kt` (528 LOC) and `SettingsRepositoryImpl.kt` (909 LOC) both exceed 500 LOC - back both up to `temp/S1401/` before editing (CLAUDE.md Rule 5), covered by Step 03.1.
>
> Strings live in `app_v2/src/main/res` for every flavor; `scripts/utils/set-android-string.ps1` only operates there.

---

## Steps

### Step 03.1 - Persist the chosen order

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up both files to `temp/S1401/` with a timestamp first. Add `allAppsSortOrder: String = InstalledAppSortOrder.LABEL.name` and `allAppsSortDescending: Boolean = false` to `AppSettings`, next to the existing `launcher*` block, and wire both through `SettingsRepositoryImpl` following the pattern of the neighbouring launcher keys. Store the order as its enum name, not an ordinal, so reordering the enum later cannot silently repoint a saved preference.

**Why:**

Strategic §2 goal 4 requires the chosen order to survive between visits, and §3.3 records it as a preference that outlives an app update. Reusing the existing settings store rather than a private preference file is what keeps it inside the backup and restore path the rest of the app already has.

**Verification:**

- `Grep` - `allAppsSortOrder` present in both files.
- `Grep` - `allAppsSortDescending` present in both files.
- `Glob` - timestamped copies of both files exist under `temp/S1401/`.

**Status:** `[x]` done

---

### Step 03.2 - Record launch statistics

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherJournalRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherJournalRepositoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend `LauncherJournalRepository.record` so that, alongside the existing journal insert and trim, it upserts the launch-statistics row for the same encoded target. The trim must not touch the statistics table. Add `observeLaunchStats(): Flow<Map<String, LaunchStats>>` to the interface and implement it over `LauncherLaunchStatsDao`.
>
> Host corrected during implementation: the read side already exists. Phase 01 Step 01.5 put `observeLaunchStats()` on `InstalledAppsRepository`, which is also where the app list itself is read from, so a second identical accessor on the journal repository would have no caller - Rule 20 dead weight. Only the write half is implemented here; `QueryAllAppsUseCase` reads both streams from `InstalledAppsRepository`.

**Why:**

`LauncherJournalRepositoryImpl` trims its journal to 50 rows, so a frequency order computed from the journal alone would reset itself as the user keeps launching things; the aggregate row is what makes strategic ADR-3's permission-free frequency signal actually durable. Recording in the same call keeps a single write path - every surface already reaches launches through `ExecuteLauncherCommandUseCase`, which calls `record`.

**Verification:**

- `Grep` - `observeLaunchStats` present on `InstalledAppsRepository` and its implementation (the read side, delivered by Phase 01).
- `Grep` - `LauncherLaunchStatsDao` present in `LauncherJournalRepositoryImpl.kt`.
- `Grep` - `recordLaunch` present in `LauncherJournalRepositoryImpl.kt`.
- `Grep` - `trim(` still present exactly once in the implementation - the trim applies to the journal only.

**Status:** `[x]` done

---

### Step 03.3 - Add the query use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/QueryAllAppsUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `QueryAllAppsUseCase` exposing `operator fun invoke(query: String, order: InstalledAppSortOrder, descending: Boolean): Flow<List<InstalledApp>>` built from the cache stream and the launch-statistics stream. Filter case-insensitively on the label, falling back to a package-name match so a user who knows the package still finds the app. Order: `LABEL` by the stored sort key; `INSTALL_DATE` and `UPDATE_DATE` newest first before the descending flag is applied; `LAUNCH_FREQUENCY` by count then by last-launch time, falling back to label order while the statistics table is empty; `CATEGORY` grouped by category with every uncategorised app in one group placed last, and label order inside each group. Ties always break on the label so the list never reshuffles between identical inputs.

**Why:**

Strategic §5.1 puts filtering and ordering in the application layer so the surface receives a finished list, and §7 records that a frequency order looks broken to a new user whose journal is still empty - the label fallback is that mitigation. The uncategorised group is placed last rather than dissolved because §6 item 4 accepts that the category may be unfilled for most apps and requires the outcome to stay readable.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/QueryAllAppsUseCase.kt` exists.
- `Grep` - `class QueryAllAppsUseCase` matches exactly once.
- `Grep` - each of `LABEL`, `INSTALL_DATE`, `UPDATE_DATE`, `LAUNCH_FREQUENCY`, `CATEGORY` present.

**Status:** `[x]` done

---

### Step 03.4 - Add the screen's strings in all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add these keys with one `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En -Ru -Uk` call each, so EN/RU/UK stay in lockstep: `launcher_all_apps_title`, `launcher_all_apps_search_hint`, `launcher_all_apps_empty`, `launcher_all_apps_sort`, `launcher_all_apps_sort_label`, `launcher_all_apps_sort_install_date`, `launcher_all_apps_sort_update_date`, `launcher_all_apps_sort_frequency`, `launcher_all_apps_sort_category`, `launcher_all_apps_sort_reverse`, `launcher_all_apps_category_other`. Check every message against `docs/COMMUNICATION_POLICY.md` §2 for its message type and §6 for tone before writing it. The empty state names what was searched for and offers the way out, it does not merely say that nothing was found.

**Why:**

Strategic §3.2 makes EN/RU/UK parity mandatory for every new string and binds user-visible text to the communication policy. Adding all three locales in one lockstep call is what stops the parity audit from failing later, which is the failure mode a per-locale hand edit produces.

**Verification:**

- `Grep` - each of the eleven keys present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_all_apps"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

---

### Step 03.5 - Unit-test the ordering

**Files:** `app_v2/src/testStandard/java/com/sza/fastmediasorter/domain/usecase/apps/QueryAllAppsUseCaseTest.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add unit tests covering: label order is case-insensitive; the descending flag reverses each order; frequency order falls back to label order when the statistics map is empty; the uncategorised group lands last; a query matches on label and on package name; ties break on label. Use fixed input lists, not the database.

**Why:**

Ordering is the only part of this ticket that is pure logic with no device dependency, and strategic §11 criterion 5 states the five orders as an observable outcome the owner will check by hand - a test here is what stops a hand check from being the first place a wrong comparator shows up.

**Verification:**

- `Glob` - the test file exists.
- `.\a.ps1 fu` reports this test class passing - verify the class by name, the full suite has known pre-existing failures.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every file listed in "Files Touched".
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_all_apps"` exits 0.
- [ ] Dev log entry added for the phase.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.
- [ ] `temp/CODE.LOCK` released.

---

## Step Log

- 2026-08-07 - PHASE-BOUNDARY AUDIT. Layers 1, 2 and 4 over the five changed Kotlin files.
  - Layer 2, P2, FIXED IN PHASE: `QueryAllAppsUseCase` returned a bare `combine`, so the filter and the sort ran in the collector's context - the main thread for the ViewModel that will subscribe in Phase 05 - and every recorded launch re-emits the statistics flow, re-sorting the whole installed-app list there. Added `.flowOn(Dispatchers.Default)`.
  - Layer 2, P3, accepted: `record()` writes the journal row and the statistics row in one `withContext(IO)` but not in one transaction, so a crash between them leaves a launch counted once in the journal and not in the counter. The counter is derived history with no reader that can tell, and wrapping two DAOs from a repository would need a database-level transaction seam this phase does not have.
  - Layer 1, checked and clear: the use case holds no Android type (`CATEGORY_UNDEFINED` spelled as `-1`), lives in `domain/usecase/apps` beside its sibling, and is named `VerbNounUseCase`; the settings field pair follows the neighbouring launcher keys exactly.
  - Layer 4, checked and clear: no schema change; `recordLaunch` is the phase-01 `@Transaction` DAO method and is called from an IO context.
  - Layer 3: not applicable - this phase adds no listener, lifecycle owner or long-lived UI reference.
- 2026-08-07 - Step 03.5 done. Eight tests in `QueryAllAppsUseCaseTest`, placed in `src/testStandard` as planned (the source set exists and was empty). Verification: `check-standard-fast.ps1 -Mode Unit -Tests "*QueryAllAppsUseCaseTest"` exit 0; `TEST-...QueryAllAppsUseCaseTest.xml` written 10:36 reports `tests="8" failures="0" errors="0"`. The same run compiled `compileStandardDebugKotlin` and `bundleStandardDebugClassesToCompileJar`, so it is also this phase's compile evidence - no separate `dq` was run for it (CLAUDE.md section 6, no redundant compile).
- 2026-08-07 - Step 03.4 done. Eleven keys added through one `-Action add -En -Ru -Uk` call each, so no locale can drift. The empty state names the query and says how to get back to the full list rather than only reporting a miss (`docs/COMMUNICATION_POLICY.md` §6). Verification: 11 keys in all three files; `check_strings_localized.ps1 -KeyPrefix "launcher_all_apps"` exit 0.
- 2026-08-07 - Step 03.3 done. `QueryAllAppsUseCase` reads both streams from `InstalledAppsRepository`. Launch statistics are keyed by the encoded `LauncherCellCommand.App`, the same key `record` writes, so no second convention appears. `CATEGORY_UNDEFINED` is spelled as `-1` rather than imported from `ApplicationInfo`, keeping the domain layer free of Android types. Verification: 3/3 PASS.
- 2026-08-07 - Step 03.2 done, write half only. `record()` now inserts the journal row, trims it and then calls `statsDao.recordLaunch` on the same encoded target and the same timestamp, so the counter and the journal can never disagree about when a launch happened. The planned `observeLaunchStats` on the journal repository was NOT added: Phase 01 already put it on `InstalledAppsRepository`, next to the app list the same consumer reads, so a second accessor would have had zero callers. `clearJournal` deliberately still leaves the counters alone - `InstalledAppsRepository.clearLaunchStats` owns that. Verification: 4/4 PASS (predicates amended to match the corrected split).
- 2026-08-07 - Step 03.1 done. Both files backed up to `temp/S1401/*.20260807-102900.bak`. `allAppsSortOrder` (enum name, read back through `InstalledAppSortOrder.fromNameOrDefault` so an unknown token degrades to `LABEL`) and `allAppsSortDescending` added beside the launcher block. Files Touched extended by `docs/settings/device-profile-nonpresettable.json`: `check_device_profile_presets.ps1` refuses any new `AppSettings` field that is neither a CSV preset row nor declared non-presettable, and these two are view state the screen writes back itself, not a device characteristic. Verification: 3/3 PASS; preset matrix check re-run, `OK`.

---

## Handoff Notes to Next Phase

The application layer can now answer "give me the apps matching this text in this order", the preference survives restarts, and every string the screen needs exists in three locales. Nothing draws any of it yet.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed. Two settings keys become orphaned in an existing store; they are ignored on read.
