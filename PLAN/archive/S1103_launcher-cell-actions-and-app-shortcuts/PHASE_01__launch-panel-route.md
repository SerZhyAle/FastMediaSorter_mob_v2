# Phase 01 - Launch-Panel Route

**Strategic spec:** [`../S1103_launcher-cell-actions-and-app-shortcuts.md`](../S1103_launcher-cell-actions-and-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none (independent of the scheduled-op phases)
**Steps done:** 3 / 3
**Started:** 2026-07-22
**Completed:** 2026-07-22

**Step Log:**

- 2026-07-22 - 01.1-01.3 grep-verified (route label string; appLaunchPanel intent + KEY_APP_LAUNCH_PANEL route; availability branch). Compiled with the rest of S1103. Self-referential panel tile accepted (documented).

---

## Objective

Add a "Quick-access panel" internal route so a desktop cell (and, harmlessly, a panel tile) can open the app-launch panel overlay, reusing the existing feature-route machinery.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` (+ values-ru, values-uk) | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` | Modified | ≤ 130 |

---

## Steps

### Step 01.1 - Route label string

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `app_launch_panel_route_launch_panel` EN/RU/UK: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key app_launch_panel_route_launch_panel -En "Quick-access panel" -Ru "Панель быстрого доступа" -Uk "Панель швидкого доступу"` (COMMUNICATION_POLICY §6).

**Verification:**

- `Grep` - `app_launch_panel_route_launch_panel` in all three `values*/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_route_launch_panel"` - exit 0.

**Status:** `[x]` done

---

### Step 01.2 - Panel intent + catalog route

**Files:** `AppLaunchPanelRouteIntents.kt`, `InternalRouteCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `AppLaunchPanelRouteIntents`, add `fun appLaunchPanel(context: Context): Intent = Intent(context, AppLaunchPanelActivity::class.java).withPanelFlags()` (import `com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity`). In `InternalRouteCatalog`, add `const val KEY_APP_LAUNCH_PANEL = "app_launch_panel"` and a `Route` entry for it in the `routes` list: `labelRes = R.string.app_launch_panel_route_launch_panel`, `iconRes = R.drawable.ic_view_grid`, `intent = AppLaunchPanelRouteIntents::appLaunchPanel`.

**Verification:**

- `Grep` - `fun appLaunchPanel(` in `AppLaunchPanelRouteIntents.kt`.
- `Grep` - `KEY_APP_LAUNCH_PANEL` and `AppLaunchPanelRouteIntents::appLaunchPanel` in `InternalRouteCatalog.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Route availability

**Files:** `ResolvePanelRouteAvailabilityUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `resolve`, add a branch `InternalRouteCatalog.KEY_APP_LAUNCH_PANEL -> Availability(availableInBuild = true, enabledAtRuntime = true)` - the panel exists in every launcher build and has no runtime toggle. Placing it BEFORE the `else` closes the silent-unavailable trap this `when` is built to prevent.

**Verification:**

- `Grep` - `InternalRouteCatalog.KEY_APP_LAUNCH_PANEL ->` present in `ResolvePanelRouteAvailabilityUseCase.kt`.
- `.\a.ps1 fc` - compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The panel is now a cell-able feature route. The scheduled-op phases are independent.

---

## Rollback Plan

Revert the phase commit(s) - one route entry, one intent, one availability branch, one string.
