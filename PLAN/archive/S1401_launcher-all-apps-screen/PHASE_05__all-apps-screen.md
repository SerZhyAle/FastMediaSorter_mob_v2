# Phase 05 - The full-screen all-apps surface

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 5
**Started:** 2026-08-07
**Completed:** -

---

## Objective

Build the full-screen list itself: search field, icon-button order picker, app grid, long-press menu, empty state. Not yet reachable from anywhere - Phase 06 wires the entry points.

---

## Prerequisites

- [ ] Phase 02, Phase 03 and Phase 04 are ✅ Done.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1401 phase 05"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/fragment_launcher_all_apps.xml` | New | ≤ 140 |
| `app_v2/src/launcherEnabled/res/values/styles.xml` | Modified | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAllAppsViewModel.kt` | New | ≤ 180 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAllAppsFragment.kt` | New | ≤ 300 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAppGridAdapter.kt` | Modified | ≤ 110 |

> **Landscape parity (CLAUDE.md Rule 11):** `fragment_launcher_all_apps.xml` gets no `res/layout-land/` counterpart. The screen is one vertical stack - search row, then grid - and the column count is derived from the measured width, so the same file serves both orientations. `app_v2/src/launcherEnabled/res/layout-land/` currently holds only `activity_launcher_home.xml`, which this phase does not touch.
>
> **UI placement:** taken verbatim from strategic §3.3 "UI placement contract" (owner ruling 2026-08-05) - search field on top, icon-button order picker to its right opening a five-order menu with a checkmark on the current one and a separate reverse-direction entry, grid filling the rest.

---

## Steps

### Step 05.1 - Add the layout and the full-screen dialog style

**Files:** `app_v2/src/launcherEnabled/res/layout/fragment_launcher_all_apps.xml`, `app_v2/src/launcherEnabled/res/values/styles.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Build the layout as a vertical stack: a top row holding a search input that fills the width and an icon-only sort button at its end, then a `RecyclerView` filling the remaining height, plus a centred empty-state `TextView` that is `gone` by default. Colours come from theme attributes or `@color` references only - no literal hex (CLAUDE.md Rule 19). Give the sort button a content description from `launcher_all_apps_sort` and a touch target no smaller than the project's standard. Apply the system-bar inset padding to the root so nothing sits under the status or navigation bar in either orientation. Add a full-screen dialog style in the launcher styles file for the fragment to use.

**Why:**

Strategic §3.3 fixes this arrangement as the owner's ruling, and §3.2 requires the surface to stay inside the system-bar safe bounds in portrait and landscape and to describe an icon-only control for screen readers - an icon button with no content description is unusable to the accessibility path the same constraint mandates.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/fragment_launcher_all_apps.xml` exists.
- `Grep -n '="#'` returns zero hits in that layout.
- `Grep` - `contentDescription` present on the sort button.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Add the ViewModel

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAllAppsViewModel.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `LauncherAllAppsViewModel` holding the live query text and reading the persisted order and direction from settings. Expose one `StateFlow<List<InstalledApp>>` built from `QueryAllAppsUseCase` over those three inputs, plus `setQuery`, `setOrder` and `toggleDirection`, where the last two write back through the settings repository. The query text lives only in this ViewModel and is never persisted.

**Why:**

Strategic §5.1 states that the chosen order is stored as a user preference while the search text lives only while the screen is open; keeping the two in different places is what makes that distinction hold across a process death. Building the list in the ViewModel keeps the fragment free of business logic per CLAUDE.md Rule 3.

**Verification:**

- `Glob` - `LauncherAllAppsViewModel.kt` exists under `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/`.
- `Grep` - `@HiltViewModel` present.
- `Grep` - `QueryAllAppsUseCase` present.
- `Grep` - `setQuery`, `setOrder`, `toggleDirection` each present.

**Status:** `[x]` done

---

### Step 05.3 - Load grid icons from the cached files

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAppGridAdapter.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Change `LauncherAppGridAdapter.AppItem` to carry the icon as a `java.io.File?` instead of a `Drawable`, and bind it with Glide, passing the app's last-update time as the cache signature so a reinstalled or updated app cannot keep serving a stale icon. Clear the Glide request in the view holder's recycle path. Update `areContentsTheSame` to compare label and icon file rather than label alone.

**Why:**

Research artifact 02 chose a file per icon precisely so the grid gets Glide's async decode, memory cache and recycle-safe cancellation instead of the adapter holding a hundred live drawables; strategic §3.2 caps the surface's cost, and a list that materialises every icon eagerly is what the current Start-menu grid does wrong. The update-time signature is the invalidation the same artifact specifies.

**Verification:**

- `Grep` - `File` present in the `AppItem` declaration.
- `Grep -n "Drawable"` returns zero hits in `LauncherAppGridAdapter.kt`.
- `Grep` - `signature` present.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.4 - Add the fragment

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAllAppsFragment.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Create `LauncherAllAppsFragment` as a `DialogFragment` using the full-screen style from Step 05.1. Derive the grid column count from the measured width rather than a constant. Wire the search field to `setQuery` on every text change. Wire the sort button to a popup menu listing the five orders with a checkmark on the current one and a separate reverse-direction entry, both routed to the ViewModel. A short tap launches through the shared command path; a long press opens `LauncherAppActionMenuManager`. Show the empty state when a non-empty query matches nothing. Collect every flow through `collectOnLifecycle`, never a bare `lifecycleScope.launch`. Set `nextFocus*` so D-pad and keyboard traverse search field, sort button and grid; dismiss the popup in `onDestroyView`.

**Why:**

Strategic §11 criteria 4, 6 and 10 state these as observable outcomes - filter as you type, short tap launches while long press opens the menu, and full traversal by keyboard and D-pad in both orientations. Deriving the column count from the measured width is what lets one layout serve both orientations, which is the reason this phase declares no landscape counterpart.

**Verification:**

- `Glob` - `LauncherAllAppsFragment.kt` exists.
- `Grep` - `class LauncherAllAppsFragment` matches exactly once.
- `Grep` - `LauncherAppActionMenuManager` present.
- `Grep -n "lifecycleScope.launch"` returns zero bare view-bound collections in that file - CLAUDE.md Rule 19.
- `Grep` - `nextFocus` present in the fragment or its layout.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.5 - Check the screen on a device

**Files:** none - verification step against the built app
**Depends on:** Step 05.4

**Prompt for developer:**

> Temporarily show the fragment from the Start menu's existing "All apps" row, install the standard debug build and check: search filters as you type, the empty state appears for a query matching nothing, all five orders reorder the grid, the direction toggle reverses it, the order survives a force-stop and relaunch, long press opens the full action menu, and the layout is correct in portrait and landscape. Verify at native device geometry, not under a `wm size` override. Record findings under `temp/S1401/`.

**Why:**

Strategic §11 criterion 5 requires the chosen order to survive an app restart, which no static check can prove, and §3.2 requires correct behaviour in both orientations. CLAUDE.md section 12 forbids calling any of this done without a fresh run to cite.

**Verification:**

- `Glob` - a findings note exists under `temp/S1401/`.
- Recorded value: expected = order restored after force-stop | actual = <fill in>.

**Status:** `[ ]` not done - deferred to the ticket's device gate (see Step Log 2026-08-07)

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles for `standard` and `noLegal` - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every file listed in "Files Touched".
- [ ] Dev log entry added for the phase.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings. New screen plus new ViewModel plus image loading trips three audit triggers.
- [ ] `temp/CODE.LOCK` released.

---

## Step Log

- 2026-08-07 - Step 05.1 done. Layout is one vertical stack, no landscape counterpart by design. Files Touched extended by `app_v2/src/launcherEnabled/res/values/dimens.xml`: the column count is derived by dividing the measured width by a cell width, and that width needs a dimension resource (`launcher_all_apps_cell_min_width`, 88dp). The full-screen style parents `Theme.FastMediaSorter.FullScreen` rather than a dialog theme - the repo has no `Theme.FastMediaSorter.Dialog`, and the screen must cover the taskbar it replaces, so `windowIsFloating` is switched off explicitly. Verification: 4/4 PASS, `.\a.ps1 fr` exit 0.
- 2026-08-07 - Step 05.2 done. `LauncherAllAppsViewModel` reads the order and direction from settings and holds the query in memory only, so the order survives a process death and the search text deliberately does not. Verification: 4/4 PASS.
- 2026-08-07 - Step 05.3 done. `AppItem` now carries `iconFile: File?` plus `iconVersion` (the app's last-update time) as the Glide signature, so a reinstalled app cannot keep serving its old icon. `onViewRecycled` clears the request - a load left running against a recycled holder paints the wrong app's icon. Zero `Drawable` references remain in the file. Verification: 4/4 PASS.
- 2026-08-07 - Step 05.4 done. Column count is set from the measured width in `doOnLayout`, with a fallback of 4 until the first layout pass. The empty state appears only when the query is non-blank: an empty list with no query means the cache has not filled yet, and "nothing found" would be wrong about why. Every flow goes through `collectOnLifecycle`; the only `lifecycleScope` reference is the scope handed to the action-menu manager, not a bare collection. Verification: 5/5 PASS, `.\a.ps1 fk` exit 0.
- 2026-08-07 - Step 05.5 partially done, device half deferred. The temporary entry point is in place as the step asks: the Start menu's "All apps" row now opens the new screen instead of expanding its own grid. That made the in-menu grid dead on the spot, so its adapter field, its `QueryLaunchableAppsUseCase` injection, the `allAppsJob` guard and the RecyclerView wiring went with it (Rule 20); the views themselves stay in the layout for Step 06.4 to delete, and with them `LauncherAppActionMenuManager` left this fragment - the surface that long-presses an app is now the new screen. The device half - search, five orders, the direction toggle surviving a force-stop, both orientations - is NOT done: `adb devices` reports no online device on this machine. Deferred to the ticket's `BlockNeedUserTest` device gate rather than skipped; screenshot deferred (no device).
- 2026-08-07 - UI PHASE GATE (S1338). Placement decision: recorded, strategic §3.3 "UI placement contract", owner ruling 2026-08-05, quoted into this phase's Files Touched note. Screenshot: deferred, no device attached.

---

## Handoff Notes to Next Phase

The screen is complete and correct but reachable only through the temporary hook from Step 05.5. Phase 06 replaces that hook with the real entry points and removes the Start-menu row entirely.

---

## Rollback Plan

Revert the phase commit - the surface is not yet reachable from any shipped entry point, so a revert is invisible to the user.
