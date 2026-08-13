# Phase 06 - Entry points, swipe gesture, Start-menu cleanup

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 4 / 5
**Started:** 2026-08-07
**Completed:** -

---

## Objective

Make the screen reachable: an icon button beside Start, a swipe-up gesture on free desktop space that yields to scrolling, and the removal of the old expanding grid in the Start menu.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1401 phase 06"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml` | Modified | ≤ 150 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt` | Modified | ≤ 100 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAllAppsGestureManager.kt` | New | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 670 |
| `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 190 |
| `app_v2/src/launcherEnabled/res/values/dimens.xml` | Modified | - |

> **Landscape parity (CLAUDE.md Rule 11):** neither `launcher_taskbar.xml` nor `fragment_launcher_start_menu.xml` has a `res/layout-land/` counterpart - `app_v2/src/launcherEnabled/res/layout-land/` holds only `activity_launcher_home.xml`, and the taskbar file's own header records that it is deliberately shared by both orientations. `activity_launcher_home.xml` is not edited in this phase: the gesture is attached in code to views that already exist in both variants.
>
> **UI placement:** strategic §3.3 "UI placement contract" (owner ruling 2026-08-05) - icon-only button immediately right of Start and before the recents strip; swipe-up on free desktop space, active only while the desktop is at the top; Start-menu row and its grid removed.

---

## Steps

### Step 06.1 - Add the taskbar button

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an icon-only button `btnAllApps` immediately after `btnStart` and before the weighted strips block, reusing `@drawable/ic_view_grid` - the icon the removed Start-menu row already used - with `launcher_menu_all_apps` as its content description. Keep it out of the weighted block so it can never be squeezed by a long recents strip. Update the `nextFocus*` chain on both sides so D-pad traversal runs Start, All apps, recents. Add an `onAllAppsClick` parameter to `LauncherTaskbarManager` and wire it in `bind`.

**Why:**

Strategic §3.3 places the button immediately right of Start as an icon without a label, and §3.2 requires D-pad and keyboard traversal, which breaks the moment a new focusable is inserted into an explicit `nextFocus` chain without updating its neighbours. Reusing the existing grid icon avoids a new drawable for a control the user already associates with the app list.

**Verification:**

- `Grep` - `btnAllApps` present in `launcher_taskbar.xml`.
- `Grep` - `nextFocusRight="@id/btnAllApps"` present on `btnStart`.
- `Grep` - `contentDescription` present on `btnAllApps`.
- `Grep` - `onAllAppsClick` present in `LauncherTaskbarManager.kt`.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 06.2 - Add the swipe-up gesture manager

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAllAppsGestureManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Create `LauncherAllAppsGestureManager` taking the desktop container, the scroll viewport and an `onOpen` callback. Detect an upward fling on the container using `GestureDetector` with thresholds derived from `ViewConfiguration`, never hardcoded pixels. Fire `onOpen` only when the viewport's vertical scroll offset is zero; when it is not, consume nothing and let the scroll proceed. Attach as a touch listener on the container so a press landing on a cell or an interactive gadget is consumed by that child first, matching how the existing long-press entry to edit mode already behaves. Do not fire while edit mode is active.

**Why:**

Strategic ADR-4 and §7 record that the desktop already scrolls vertically and that a gesture stealing that scroll is the primary risk of this feature; gating on a zero scroll offset is the owner's chosen resolution from §3.3. Deriving thresholds from `ViewConfiguration` rather than fixed pixels is what keeps the gesture usable across the device densities the launcher runs on, including the owner's car head unit.

**Verification:**

- `Glob` - `LauncherAllAppsGestureManager.kt` exists under `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/`.
- `Grep` - `class LauncherAllAppsGestureManager` matches exactly once.
- `Grep` - `ViewConfiguration` present.
- `Grep` - `scrollY` present.

**Status:** `[x]` done

---

### Step 06.3 - Wire both entry points in the home surface

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add `showAllApps()` next to `showStartMenu()`, guarding against a second instance by fragment tag exactly as the Start menu does. Pass it as `onAllAppsClick` to `LauncherTaskbarManager` and construct `LauncherAllAppsGestureManager` over `binding.launcherDesktop` and `binding.launcherGridScroll` with the same callback. Remove the temporary hook added in Step 05.5.

**Why:**

Strategic §2 goal 1 requires both the button and the gesture to open the same screen, and §11 criterion 2 states the gesture's yield-to-scroll behaviour as an observable outcome. The tag guard exists because the desktop stays touchable while a sheet opens, which is the reason the Start menu already carries the same guard.

**Verification:**

- `Grep` - `showAllApps` present in `LauncherHomeActivity.kt`.
- `Grep` - `LauncherAllAppsGestureManager` present in `LauncherHomeActivity.kt`.
- `Grep` - `LauncherAllAppsFragment.TAG` present in the guard.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 06.4 - Remove the Start-menu row and its grid

**Files:** `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`, `app_v2/src/launcherEnabled/res/values/dimens.xml`
**Depends on:** Step 06.3

**Prompt for developer:**

> Delete `rowAllApps` and `rvAllApps` from the layout, then delete from the fragment: the `toggleAllApps` function, the `allAppsJob` field, the `appsAdapter` field, the `QueryLaunchableAppsUseCase` injection and every import that becomes unused. Repair the `nextFocus` chain across the rows that surrounded the deleted one. Remove `launcher_start_menu_apps_height` from the launcher dimens file if nothing else references it. Keep the `launcher_menu_all_apps` string - Step 06.1 now uses it as the taskbar button's content description.

**Why:**

Strategic ADR-2 and the owner's ruling in §3.3 remove the in-menu grid so there is one app list rather than two with different capabilities, and §11 criterion 9 states its absence as a checkable outcome. Deleting the now-dead field, job and dimension in the same change is required by CLAUDE.md Rule 20 rather than optional tidying.

**Verification:**

- `Grep -n "rowAllApps|rvAllApps"` returns zero hits across `app_v2/src`.
- `Grep -n "toggleAllApps|allAppsJob"` returns zero hits in `LauncherStartMenuFragment.kt`.
- `Grep -n "QueryLaunchableAppsUseCase"` returns zero hits in `LauncherStartMenuFragment.kt`.
- `Grep -n "launcher_start_menu_apps_height"` returns zero hits across `app_v2/src`.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 06.5 - Check both entry points and the gesture on a device

**Files:** none - verification step against the built app
**Depends on:** Step 06.4

**Prompt for developer:**

> Install the standard debug build, enter launcher mode and check: the button opens the screen; the swipe opens it while the desktop is at the top; with the desktop scrolled down the same swipe scrolls back up first and only the next one opens the screen; the Start menu no longer offers "All apps"; back closes the screen and returns to the desktop rather than leaving the launcher; a swipe starting on a cell or a gadget does not open the screen. Check the animator duration scale first - an emulator at scale 0 makes gesture-driven transitions look broken for reasons unrelated to this code. Record findings under `temp/S1401/`.

**Why:**

Strategic §11 criteria 1, 2 and 9 are stated as observable outcomes and none of them can be proven by a static check. The gesture's yield-to-scroll behaviour is the single decision the owner made in §3.3 to resolve the risk §7 ranks first, so it is the one that most needs a real device under a finger.

**Verification:**

- `Glob` - a findings note exists under `temp/S1401/`.
- Recorded value: expected = scrolled desktop scrolls first, does not open | actual = <fill in>.

**Status:** `[ ]` not done - deferred to the ticket's device gate (see Step Log 2026-08-07)

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles for `standard` and `noLegal` - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every file listed in "Files Touched".
- [ ] Dev log entry added for the phase.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings. The touch listener added to the desktop container is a listener-symmetry candidate: confirm it is removed with its host.
- [ ] `temp/CODE.LOCK` released.

---

## Step Log

- 2026-08-07 - PHASE-BOUNDARY AUDIT for phases 05 and 06 together, Layers 1, 2 and 3.
  - Layer 3, P1, FIXED IN PHASE: `LauncherAllAppsFragment` built its action-menu manager in a `by lazy` capturing `viewLifecycleOwner.lifecycleScope`. A rotation destroys that view lifecycle while the fragment instance survives, so the lazy would have held a cancelled scope for the rest of the fragment's life and the long-press menu would have silently stopped opening - a bug that only appears after the user rotates. Switched to the fragment's own `lifecycleScope`, matching the precedent the Start menu already set; the window is still dismissed in `onDestroyView`, which is what actually binds it to the view.
  - Layer 3, checked and clear: the gesture manager's touch listener is set on the Activity's own desktop view and captures only that Activity, so it dies with its host - this phase's Done Criteria call it out by name.
  - Layer 2, P3, accepted: every keystroke re-subscribes the query through `flatMapLatest`, so Room re-runs and the list re-sorts per character with no debounce. `flatMapLatest` cancels the previous one and the sort runs on `Dispatchers.Default`; at the size of an installed-app list this is not worth a debounce that would make the filter feel laggy.
  - Layer 1, checked and clear: the fragment holds no business logic, the ViewModel owns the query and the stored order, and every launcher-only file sits under `src/launcherEnabled/`.
  - Detekt, fixed not suppressed: `handleFling` had 4 returns (limit 2) and an unused `velocityX`. Split into `isDesktopAtTop()` (which also carries the probe log) and `isOpeningSwipe()`, and the unused parameter dropped from the private function - the override still takes it because the platform signature demands it.
- 2026-08-07 - Step 06.1 done. `btnAllApps` sits immediately right of Start and outside the weighted strip block, so a long recents strip can never squeeze it. The D-pad chain was repaired on both sides: Start now points right at it, the recents strip points left back at it. Icon-only, so `launcher_menu_all_apps` became its content description - the same string the removed row used, which is why 06.4 keeps it. Verification: 4/4 PASS.
- 2026-08-07 - Step 06.2 done. `LauncherAllAppsGestureManager` derives both thresholds from `ViewConfiguration` (paging touch slop, minimum fling velocity), never fixed pixels, so the gesture behaves the same on a phone and on a car head unit. It refuses on three counts: `viewport.scrollY != 0` (the swipe belongs to the scroll), edit mode active, and a swipe more horizontal than vertical. The touch listener returns false after feeding the detector, so the desktop's own handling is untouched. Verification: 4/4 PASS.
- 2026-08-07 - Step 06.3 done. `showAllApps()` carries the same fragment-tag guard as `showStartMenu()`, which matters more here than there: two entry points can now both fire while the desktop stays touchable behind the screen. Gesture manager is attached over `binding.launcherDesktop` / `binding.launcherGridScroll` with that same callback. Verification: 4/4 PASS.
- 2026-08-07 - Step 06.4 done. Row, grid, the `tools` namespace that only the grid's preview used, and `launcher_start_menu_apps_height` are gone; the fragment's own leftovers had already gone in Step 05.5. The rows carry no explicit `nextFocus` chain - they are a vertical `LinearLayout` and focus order is implicit - so there was nothing to repair. `launcher_menu_all_apps` deliberately survives as the taskbar button's content description, and `QueryLaunchableAppsUseCase` survives with one live caller (`AppPickerDialogFragment`). Verification: 5/5 PASS, `.\a.ps1 fc` exit 0.
- 2026-08-07 - Step 06.5 NOT done, deferred. No online device on this machine (`adb devices`). The button, the gesture's yield-to-scroll behaviour, Back returning to the desktop and the absence of the Start-menu row all need a finger on a real launcher desktop. Deferred to the ticket's `BlockNeedUserTest` device gate; screenshot deferred (no device).
- 2026-08-07 - UI PHASE GATE (S1338). Placement decision: recorded, strategic §3.3 "UI placement contract", owner ruling 2026-08-05 - button immediately right of Start and before the recents strip, swipe-up active only at the top of the desktop, Start-menu row removed. Screenshot: deferred, no device attached.

---

## Handoff Notes to Next Phase

The feature is complete and reachable. Phase 07 records it in the inventory, refreshes the generated indexes and closes the ticket's documentation obligations.

---

## Rollback Plan

Revert the phase commit. The Start-menu row returns with it, so the user is never left with no way to reach an app list.
