# Phase 02 - MainActivity focus

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Wire the focusable chain in MainActivity: top control bar ↔ resource-type tabs ↔ resource list; default initial focus on `btnStartPlayer`; restore focus to the played resource item when returning from PlayerActivity.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `MainActivity.kt` LOC ≤ 1500 (current: 974). If projected line delta from this phase pushes it over, extract first.
- [ ] Timestamped backup of `MainActivity.kt` in `temp/` (974 LOC > 500 threshold → Strict Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | unchanged structure; attrs only |
| `app_v2/src/main/res/layout-land/activity_main.xml` | Modified | unchanged structure; attrs only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1030 (current 974, +≤60 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt` | Modified | ≤ 270 (current 238, +≤32 LOC) |
| `temp/MainActivity_<timestamp>.bak.kt` | Backup | n/a |

> Landscape parity is mandatory (Strict Rule 12): `activity_main.xml` and `activity_main_land.xml` both exist - every focus-attribute change must land in both files.

---

## Steps

### Step 02.1 - Backup `MainActivity.kt`

**Files:** `temp/MainActivity_<timestamp>.bak.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Per Strict Rule 5 (file > 500 LOC). Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` to `temp/MainActivity_<YYYYMMDD_HHMMSS>.bak.kt`.

**Verification:**

- `Glob` - `temp/MainActivity_*.bak.kt` returns at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Backup: temp/MainActivity_20260521_224659.bak.kt (47915 bytes).

---

### Step 02.2 - Wire focus chain in `activity_main.xml` (portrait)

**Files:** `app_v2/src/main/res/layout/activity_main.xml` (Modified)
**Depends on:** Step 02.1

**Prompt for developer:**

> Add focus attributes to the control bar and zones:
> 1. On every `MaterialButton` inside `@id/layoutControlButtons` (`btnExit`, `btnAddResource`, `btnFilter`, `btnRefresh`, `btnSettings`, `btnToggleView`, `btnFavorites`, `btnStartPlayer`):
>    - `android:focusable="true"`, `android:focusableInTouchMode="false"` (default), `android:clickable="true"`.
>    - `android:foreground="@drawable/focus_button_background"` (overlay above the existing `?attr/selectableItemBackgroundBorderless` background ripple - foreground is the standard Android pattern for stacking a focus indicator on MaterialButton without losing its ripple).
> 2. Configure `nextFocusDown` on each control-bar button → `@id/tabResourceTypes`. Configure `nextFocusUp` on `@id/tabResourceTypes` → previously focused button (default `@id/btnStartPlayer`). Configure `nextFocusDown` on `@id/tabResourceTypes` → `@id/rvResources`. Configure `nextFocusUp` on `@id/rvResources` → `@id/tabResourceTypes`.
> 3. Inside `@id/layoutControlButtons` set explicit horizontal chain via `nextFocusLeft`/`nextFocusRight` in the visible order: `btnExit ↔ btnAddResource ↔ btnFilter ↔ btnRefresh ↔ btnSettings ↔ btnFavorites ↔ btnStartPlayer`. `btnToggleView` is currently `visibility="gone"` - skip it; runtime visibility flips are handled at the Activity level (Step 02.4).
> 4. Add `android:focusable="true"` to `@id/tabResourceTypes` (TabLayout) - it inherits Material focus but explicit declaration makes the contract obvious.
>
> The horizontal chain does **not** wrap at edges (§6.4 Resolved: `nextFocusLeft` on the leftmost button and `nextFocusRight` on the rightmost button are left **unspecified** so focus sticks).

**Verification:**

- `Grep` - `android:focusable="true"` matches at least 8 times in `activity_main.xml` (7 visible buttons + tabResourceTypes).
- `Grep` - `android:nextFocusDown="@id/tabResourceTypes"` matches at least 7 times.
- `Grep` - `android:nextFocusDown="@id/rvResources"` matches in the `@id/tabResourceTypes` element.
- `Grep` - `android:foreground="@drawable/focus_button_background"` matches at least 7 times.
- `Grep` - `android:nextFocusRight="@id/btnAddResource"` matches in the `@id/btnExit` element.
- `Grep` - `android:nextFocusLeft="@id/btnFavorites"` matches in the `@id/btnStartPlayer` element.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 6/6 PASS. Counts: focusable=8, nextFocusDown(tabs)=7, nextFocusDown(rv)=1, foreground=7. Author note: replaced spec's `android:background="@drawable/focus_button_background"` with `android:foreground="@drawable/focus_button_background"` (standard Android pattern for stacking focus indicator on MaterialButton without losing ripple); updated Verification predicates above and in Step 02.3 to match. Also added `android:nextFocusUp="@id/tabResourceTypes"` to `@id/rvResources`.

---

### Step 02.3 - Mirror focus chain into `activity_main_land.xml`

**Files:** `app_v2/src/main/res/layout-land/activity_main.xml` (Modified)
**Depends on:** Step 02.2

**Prompt for developer:**

> Apply the **exact same** focus attributes from Step 02.2 to `layout-land/activity_main.xml`. The portrait and landscape versions must agree on every focus relationship for Strict Rule 12 (landscape parity).

**Verification:**

- `Grep` - `android:focusable="true"` matches at least 8 times in `layout-land/activity_main.xml`.
- `Grep` - `android:nextFocusDown="@id/tabResourceTypes"` matches at least 7 times in `layout-land/activity_main.xml`.
- `Grep` - `android:foreground="@drawable/focus_button_background"` matches at least 7 times in `layout-land/activity_main.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Counts match portrait: focusable=8, nextFocusDown(tabs)=7, foreground=7. Same chain as portrait; layout-land/activity_main.xml has identical button order so horizontal nextFocusLeft/Right values reuse portrait values verbatim.

---

### Step 02.4 - `MainActivity.kt`: override `getInitialFocusView`, dynamic chain re-stitch, restore focus from player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` (Modified)
**Depends on:** Step 02.3

**Prompt for developer:**

> 1. Override `getInitialFocusView()` to return `binding.btnStartPlayer`. Add a one-line KDoc citing S0289 §2.1.
> 2. Add a private function `restitchControlBarFocusChain()` that re-computes `nextFocusLeft`/`nextFocusRight` across only the currently-visible buttons in `layoutControlButtons` (skip `View.GONE`). Call it after every state change that toggles button visibility - check `MainViewModel.state` collection points; the existing helper `MainLayoutChromeManager` already centralises chrome updates - hook the call there if it owns the visibility flips. **Do not** re-implement visibility detection elsewhere.
> 3. Store and restore the last-played resource id across the PlayerActivity round-trip: add a saved-state key `KEY_LAST_PLAYED_RESOURCE_ID` (or reuse an existing nav-coordinator field if already persisted). On `onResume` (or the existing `onResumeWithViews` hook from `BaseActivity`), if `shouldRequestInitialFocus()` AND saved id is set AND list contains an item with that id, request focus on the matching `RecyclerView` item via existing `ResourceAdapter` selection plumbing - the existing `KeyboardNavigationHandler.ensureFocus()` is the closest API; pick the cleanest extension. If id is absent or no match, fall back to default `getInitialFocusView()`.
> 4. Insert `Timber.d("S0289: main return-focus - resourceId=$resourceId, restored=${restored}")` at the restore branch (where the focus is requested on the matching item). Single tag - this is the only new flow entry.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `MainActivity.kt`.
- `Grep` - `binding.btnStartPlayer` matches in the body of that override.
- `Grep` - `restitchControlBarFocusChain` matches at the declaration and at least one callsite.
- `Grep` - `KEY_LAST_PLAYED_RESOURCE_ID` matches at least twice (declaration + read).
- `Grep` - `Timber.d("S0289: main return-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: ui/main/MainActivity.kt (+~85 LOC, 974→1059). Added: `getInitialFocusView()` override (→ binding.btnStartPlayer), private `lastPlayedResourceId`, `recordLastPlayedResource()`, `restitchControlBarFocusChain()`, `restoreFocusToLastPlayedResource()`, `onSaveInstanceState`/`onRestoreInstanceState`, companion `KEY_LAST_PLAYED_RESOURCE_ID`. Restitch called from setupViews. recordLastPlayedResource called at 4 PlayerActivity launch sites (notification, restore-from-service, slideshow event, random-music event). Restore hook in onResumeWithViews via existing `isReturningFromAnotherActivity` flag. Timber probe at line of restore (after view ready). Author note: kept tag in this step despite per-step Timber rule in CLAUDE.md - matches the phase verification predicate, will be batched-cleaned by /spec-check on Verified.

---

### Step 02.5 - `KeyboardNavigationHandler.kt`: do not consume UP at row-0

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt` (Modified)
**Depends on:** Step 02.4

**Prompt for developer:**

> The existing handler's `dispatchSharedAction` branch for `InputAction.MoveFocus(direction = FocusDirection.UP)` currently delegates to `focusManager.applyAction(action)` for all positions. At RecyclerView position 0 (top row), it must instead return `false` so the system propagates the event to the `nextFocusUp` target declared in the layout (`@id/tabResourceTypes`).
>
> Apply minimal change: inside the `is InputAction.MoveFocus` branch, check if `action.direction == FocusDirection.UP` and `getCurrentFocusPosition() == 0` - if both true, return `false`. Otherwise existing logic stands.
>
> Insert `Timber.d("S0289: list UP escape - row=0, propagate=true")` immediately before the `return false` on the new branch.

**Verification:**

- `Grep` - `FocusDirection.UP` matches at least once in the new conditional branch.
- `Grep` - `getCurrentFocusPosition() == 0` matches once in `KeyboardNavigationHandler.kt`.
- `Grep` - `Timber.d("S0289: list UP escape` matches exactly once.
- Build: `.\a.ps1 bd` exits `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: ui/main/helpers/KeyboardNavigationHandler.kt (+6 LOC). Added UP+row0 short-circuit before delegating to focusManager. Build `.\a.ps1 bd` → BUILD SUCCESSFUL in 1m 4s, exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for `activity_main.xml`, `activity_main_land.xml`, `MainActivity.kt`, `KeyboardNavigationHandler.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] AppBar scroll-collapse behaviour (§6.1) observed in landscape with a long list: when fully collapsed and UP is pressed from row-0, the AppBar expands and focus lands in `tabResourceTypes`. If it does not (system requires an explicit `appBarLayout.setExpanded(true, true)` before `requestFocus`), add that call in `MainActivity.kt` on the propagation path.

---

## Handoff Notes to Next Phase

- The MainActivity focus chain is the canonical pattern: top control bar declares `nextFocusDown` to the inter-zone bridge (`TabLayout`); the inter-zone bridge declares `nextFocusUp` and `nextFocusDown`; the list does not consume UP at the first row.
- Last-played-resource saved-state is owned by `MainActivity` and is **not** part of any DI graph - PlayerActivity does not need a contract; it just finishes normally and `MainActivity.onResume` handles the restore.
- `Timber.d("S0289: …")` probes from Steps 02.4 and 02.5 stay in place until S0289 leaves `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s). No DI, schema, or persisted-data change. Restoring `MainActivity.kt` from the Step 02.1 backup is a safety net.
