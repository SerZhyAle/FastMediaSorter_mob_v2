# Phase 02 - Placement manager

**Strategic spec:** [`../S0940_streams-landscape-header-toolbar.md`](../S0940_streams-landscape-header-toolbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Introduce `StreamsControlsPlacementManager` that reparents the `streamControls` group between the below-toolbar slot (portrait) and the in-header `headerControlsHost` slot (landscape), and wire it into `StreamsActivity` at setup and on configuration change so the header consolidation applies live on rotation without recreating the activity.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (both slots exist in both layout variants).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsControlsPlacementManager.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout-w600dp/activity_streams.xml` | Modified (amendment) | ≤ 340 |

> Amendment: `layout-w600dp` carries the third `activity_streams` variant and outranks `land` on width ≥ 600dp (the common landscape-phone case). It needs the same `headerControlsHost` slot so ViewBinding keeps `headerControlsHost` non-null and the feature applies on wide/landscape launches.

> `StreamsActivity.kt` is currently 786 LOC (>500) - take a timestamped backup under `temp/S0940/` before editing (Rule 5). The new logic is delegated to the manager to keep the Activity thin (Rule 3); the Activity only instantiates the manager and forwards two calls.

---

## Steps

### Step 02.1 - Create StreamsControlsPlacementManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsControlsPlacementManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamsControlsPlacementManager` in `ui/streams/helpers/`. It holds references to the `streamControls` view, its original below-toolbar parent, and the `headerControlsHost` container. Expose one method `applyForOrientation(isLandscape: Boolean)` that: in landscape removes `streamControls` from its current parent and adds it into `headerControlsHost`, sets `headerControlsHost` visible and the below-toolbar slot `gone`; in portrait moves `streamControls` back to its original below-toolbar parent at its original index, sets the below slot visible and `headerControlsHost` `gone`. Guard against re-adding when already in the target parent (idempotent). Keep the search field expanded when width allows and collapse behaviour tunable later (strategic §3.1.2) - do not hardcode a pixel width here; just relocate. No business logic, no logging noise; use Timber if any log is truly needed.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsControlsPlacementManager.kt` exists.
- `Grep` - `class StreamsControlsPlacementManager` matches exactly once.
- `Grep` - `fun applyForOrientation` present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. New `StreamsControlsPlacementManager` in ui/streams/helpers reparents the control group between below-toolbar and header host by orientation; detekt scoped PASS.

---

### Step 02.2 - Instantiate manager and place controls at setup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `StreamsActivity`, after the view/binding is set up (where other stream managers like `gridModeManager` are initialised), construct `StreamsControlsPlacementManager` with the `streamControls`, its parent, and `headerControlsHost` views, and call `applyForOrientation` using the current `resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE`. This makes launch-in-landscape place controls in the header immediately. Do not branch business logic on orientation elsewhere. Take a timestamped backup of the file under `temp/S0940/` before editing.

**Verification:**

- `Grep` - `StreamsControlsPlacementManager(` present in `StreamsActivity.kt`.
- `Grep` - `applyForOrientation(` present in `StreamsActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 2/2 PASS. Manager constructed in setupViews with binding.streamControls + binding.headerControlsHost; initial placement by launch orientation.

---

### Step 02.3 - Forward configuration changes to the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In the existing `onConfigurationChanged`, after the current `gridModeManager.onConfigurationChanged()` call, invoke the placement manager's `applyForOrientation` with the landscape flag derived from `newConfig.orientation`. Guard with `::` isInitialized like the existing grid manager call. This is the path that relocates controls live on rotation without recreating the activity (see `research/03__rotation-no-recreate.md`).

**Verification:**

- `Grep` - `onConfigurationChanged` block in `StreamsActivity.kt` contains a call to `applyForOrientation`.
- `Grep -n "Log\.d\("` - zero hits in `StreamsActivity.kt` and `StreamsControlsPlacementManager.kt`.
- Project compiles - `/build` (standard debug compile) passes.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS (`a.ps1 fc` BUILD SUCCESSFUL). onConfigurationChanged forwards orientation to the manager; zero Log.d hits in both files. Amendment: added headerControlsHost to layout-w600dp too (w600dp qualifier outranks land, is the real landscape-phone variant, and ViewBinding needs the id in every variant to stay non-null).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fc` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (catalog-sync ran in post-change; role/status set in Phase 03).

---

## Handoff Notes to Next Phase

Header consolidation now works on both launch-in-landscape and live rotation, driven entirely by `StreamsControlsPlacementManager`. Portrait retains the below-toolbar bar. `StreamsActivity` only forwards two calls. Ready for catalog regen + dev log in Phase 03, then device verification of exact landscape width/collapse behaviour under BlockNeedUserTest.

---

## Rollback Plan

Revert phase commit(s) and delete `StreamsControlsPlacementManager.kt`. No data migration; only the streams window layout placement is affected. Phase 01 layout slots are inert without this manager.
