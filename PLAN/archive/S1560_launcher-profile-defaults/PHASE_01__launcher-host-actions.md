# Phase 01 - Launcher host actions

**Strategic spec:** [`../S1560_launcher-profile-defaults.md`](../S1560_launcher-profile-defaults.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Turn "all programs" and "black screen" into placeable launcher actions, and make an unconfigured weather cell
open its place picker on a tap - the three host-side cells Phase 04 needs before it can seed them.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.6, resolved by `research/06__existing-cells-inventory.md`.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "//spec-dev S1560 phase 01"` before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/LauncherActionCatalog.kt` | Modified | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 1060 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> `LauncherHomeActivity.kt` is 1004 LOC - over the 500-LOC threshold, so Step 01.1 takes a timestamped backup
> into `temp/S1560/` before it is edited (CLAUDE.md Rule 5). It stays under the 1500-LOC ceiling: this phase adds
> roughly 30 lines.
>
> **Flavor placement.** `LauncherActionCatalog` stays in `src/main` because it is a pure key/label table with no
> launcher dependency and `LauncherStarterSets` (also `src/main`) reads its keys. Every behavioural change lives in
> `src/launcherEnabled`, which only `standard` and `noLegal` mount - no `BuildConfig.IS_*` guard is introduced.

---

## Steps

### Step 01.1 - Back up the launcher home activity

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `LauncherHomeActivity.kt` to `temp/S1560/LauncherHomeActivity.<yyyyMMdd-HHmmss>.kt.bak` before any edit in
> this phase. Create `temp/S1560/` if absent.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup before editing any file over 500 LOC, and this file is 1004 LOC.

**Verification:**

- `Glob` - at least one file matches `temp/S1560/LauncherHomeActivity.*.kt.bak`.

**Status:** `[x]` done

---

### Step 01.2 - Register the two new launcher actions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/LauncherActionCatalog.kt`
**Depends on:** Step 01.3 - the catalog rows reference the label resources, so the strings must exist first or the intermediate state does not compile

**Prompt for developer:**

> Add `const val KEY_ALL_APPS = "all_apps"` and `const val KEY_BLACK_SCREEN = "black_screen"` next to the four
> existing key constants, and append two `Action(..)` rows to `all` in that order, after `KEY_EDIT_DESKTOP` and
> before `KEY_EXIT_LAUNCHER_MODE`. Use `R.drawable.ic_apps` and `R.drawable.ic_black_screen`, both of which
> already exist, and the two string keys added in Step 01.3. Do not change the four existing entries, their keys
> or their order relative to one another.

**Why:**

Strategic §2 goal 3 requires every requested element to become a placeable cell rather than be silently dropped,
and `research/06__existing-cells-inventory.md` §6 established that "all programs" and "black screen" exist as
behaviour but not as cells; the `act:` prefix already generalises over any key, so a catalog row is the whole
cost of making them placeable.

**Verification:**

- `Grep` - `KEY_ALL_APPS = "all_apps"` matches exactly once in `LauncherActionCatalog.kt`.
- `Grep` - `KEY_BLACK_SCREEN = "black_screen"` matches exactly once in `LauncherActionCatalog.kt`.
- `Grep` - `Action(` matches exactly 7 times in `LauncherActionCatalog.kt` - six list rows plus the `data class Action(` declaration.
- `Grep` - `ic_apps` and `ic_black_screen` each match once in `LauncherActionCatalog.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Add the trilingual labels for both actions

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `launcher_action_all_apps` and `launcher_action_black_screen` across EN, RU and UK in one lockstep call
> each:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_action_all_apps -En "All apps" -Ru "Все программы" -Uk "Усі програми"`
> and the same shape for `launcher_action_black_screen` ("Black screen" / "Чёрный экран" / "Чорний екран").
> Check both labels against `docs/COMMUNICATION_POLICY.md` §2 (message formula for a control label) and §6 (tone
> checklist) before writing them. Then run
> `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_action_"`.

**Why:**

Strategic §3.2 requires EN/RU/UK labels for every new cell, and a launcher action without a label cannot be
rendered in the picker or on the desktop.

**Verification:**

- `Grep` - `launcher_action_all_apps` present in all three `strings.xml` files.
- `Grep` - `launcher_action_black_screen` present in all three `strings.xml` files.
- `scripts/check_strings_localized.ps1 -KeyPrefix "launcher_action_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.4 - Dispatch both actions in the launcher host

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `performLauncherAction` add a branch for `LauncherActionCatalog.KEY_ALL_APPS` calling the existing
> `showAllApps()`, and a branch for `LauncherActionCatalog.KEY_BLACK_SCREEN` calling a new private
> `showBlackScreen()`. Implement `showBlackScreen()` by lazily constructing one `SystemBarsManager(this)` and one
> `BlackScreenOverlayManager(WeakReference(this), systemBarsManager)` held in activity fields, then calling
> `show()`; construct the pair the same way `BrowseManagerInitializer` already does for `BrowseActivity`. Reuse
> the single instance across taps rather than building a new overlay each time. Do not touch the three existing
> branches.

**Why:**

Strategic §6.4 assigns the black-screen cell to the always-on profiles and the all-apps cell to every profile, so
both keys need an execution path in the host - `ExecuteLauncherCommandUseCase` refuses `LauncherAction` by design
because the host intercepts it first.

**Verification:**

- `Grep` - `KEY_ALL_APPS ->` matches once in `LauncherHomeActivity.kt`.
- `Grep` - `KEY_BLACK_SCREEN ->` matches once in `LauncherHomeActivity.kt`.
- `Grep` - `private fun showBlackScreen` matches once in `LauncherHomeActivity.kt`.
- `Grep` - `BlackScreenOverlayManager(` matches once in `LauncherHomeActivity.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `LauncherHomeActivity.kt`.

**Status:** `[x]` done

---

### Step 01.5 - Open the place picker on a tap of an unconfigured weather cell

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `bindGadget`, inside the existing `decoded.first == LauncherGadgetRegistry.KEY_WEATHER` branch, also set an
> `OnClickListener` opening `LauncherWeatherLocationDialogFragment.newInstance(REQ_WEATHER_LOCATION, cellUi.cell.id)`
> - but only when the cell carries no usable place, i.e. `WeatherLocation.decode(decoded.second) == null`. Leave
> the long-press listener and the configured-cell behaviour untouched: a weather cell that already has a place
> keeps opening a weather app on tap.

**Why:**

Strategic §3.4 records that a seeded weather cell has no place on first run and would otherwise stay permanently
on the "no location" message with its only repair path hidden behind a long press, which contradicts §2 goal 4's
ban on cells that cannot do their job.

**Verification:**

- `Grep` - `WeatherLocation.decode(` matches once in `LauncherHomeActivity.kt`.
- `Grep` - `setOnClickListener` inside the `KEY_WEATHER` branch of `bindGadget` (read the branch and confirm both
  a click and a long-click listener are set).
- `Grep` - `setOnLongClickListener` still matches once in `LauncherHomeActivity.kt`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-11 - 01.1 done. Backup `temp/S1560/LauncherHomeActivity.20260811-021243.kt.bak` (51998 bytes).
- 2026-08-11 - 01.3 done, executed before 01.2. Depends-on corrected: the catalog rows reference the label
  resources, so writing the catalog first would not compile. `set-android-string.ps1 -Action add` x2,
  `check_strings_localized.ps1 -KeyPrefix "launcher_action_"` exit 0.
- 2026-08-11 - 01.2 done. Verification predicate corrected: `Action(` matches 7, not 6 - the `data class Action(`
  declaration matches the same pattern as the six list rows.
- 2026-08-11 - 01.4 done. Overlay held as a lazy Activity field, not rebuilt per tap. LOC 1022.
- 2026-08-11 - 01.5 done. Click redirect guarded on `WeatherLocation.decode(param) == null`; import re-ordered
  into the `domain.model` block for ktlint import-ordering. LOC 1035.
- 2026-08-11 - Phase close. `.\a.ps1 fk` BUILD SUCCESSFUL (exit 0). `post-change.ps1 -ScopeToFile -ChangeType Mixed`
  printed `post-change: PASS` (exit 0) over all five files; detekt scoped PASS, neuroslop all dimensions at
  baseline, strings parity OK, catalog re-scanned.
- 2026-08-11 - UI gate (S1338): placement decision recorded in strategic §3.4; screenshot deferred (no device) -
  `adb.ps1 devices` exit 2, no online device. Phase Done Criteria do not demand the shot.
- 2026-08-11 - Phase-boundary audit, Layers 1-3. No P0/P1. Checked: the overlay manager holds the Activity through
  a `WeakReference` and lives as an Activity field, so it cannot outlive its host; the weather click listener is
  set on a view the desktop binder rebuilds per emission, so listeners do not accumulate (listener-symmetry gate
  reported new imbalance 0); `showBlackScreen()` is a one-line delegation and the activity-logic gate reported no
  new occurrence.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).
- [ ] `CODE.LOCK` released via `scripts/utils/exit-code-lock.ps1`.

---

## Handoff Notes to Next Phase

`LauncherActionCatalog.KEY_ALL_APPS` and `KEY_BLACK_SCREEN` are stable cell targets from here on - Phase 04 seeds
them as `act:` shortcuts and must not re-spell the literals. The weather cell is now safe to seed without a place.

---

## Rollback Plan

Revert the phase commit. No data migration and no persisted cell format changed - an already-placed `act:` cell
whose key disappears renders through the existing unavailable-cell path rather than crashing.
