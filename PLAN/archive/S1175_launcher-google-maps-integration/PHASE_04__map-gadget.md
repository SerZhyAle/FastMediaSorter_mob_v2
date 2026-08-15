# Phase 04 - Map Gadget

**Strategic spec:** [`../S1175_launcher-google-maps-integration.md`](../S1175_launcher-google-maps-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3

## Objective

Expose the current-location map as an accessible launcher gadget whose work runs only while visible.

## Steps

### Step 04.1 - Add map gadget presentation

**Files:** new `src/launcherEnabled/.../gadget/MapGadget.kt`, new `src/launcherEnabled/res/layout/gadget_launcher_map.xml`, launcher resources
**Depends on:** Phase 03

**Prompt for developer:**

> Build the map gadget from `LauncherGadgetView`, with OSM attribution, an address-or-coordinate text alternative, explicit loading, permission, offline, and stale states, and a generic map-app tap intent.

**Why:** Strategic criteria 3 and 4 require a meaningful accessible state without location permission or network, not an empty or colour-only error tile.

**Verification:**

- Landscape variant absent - gadget cells are grid-measured, matching `gadget_launcher_weather.xml`.
- `a.ps1 fc` passes.
- Done: `MapGadget` + `gadget_launcher_map.xml` with attribution, an address-or-coordinate caption fed into `contentDescription`, worded loading, permission, offline and stale states, and a generic `geo:` tap intent guarded by `resolveActivityCompat`. `check-standard-fast.ps1 -Mode CodeAndResources` exit 0.

**Status:** `[x]` done

### Step 04.2 - Register the gadget and request location at placement

**Files:** `LauncherGadgetRegistry.kt`, `LauncherSensorPermissionManager.kt`, gadget DI module
**Depends on:** Step 04.1

**Prompt for developer:**

> Register the map gadget in the existing launcher registry and extend the placement-time location permission map only; do not request location at startup.

**Why:** Strategic §3.2 requires asking only when the gadget is first placed and retaining a useful tile after refusal.

**Verification:**

- Map gadget key is registered and maps to fine location permission.
- Done: `KEY_MAP` added to `LauncherGadgetRegistry`, `MapGadget` joins the `@SensorGadgets` list (the registry constructor is at its threshold), and `LauncherSensorPermissionManager.PERMISSIONS` maps `KEY_MAP` to `ACCESS_FINE_LOCATION`, which is asked at placement only. `isAvailable()` reads hardware, never the grant, so a refusal leaves the cell in place.

**Status:** `[x]` done

### Step 04.3 - Audit visible-only gadget work

**Files:** Phase 04 source files
**Depends on:** Step 04.2

**Prompt for developer:**

> Audit view attachment, coroutine cancellation, bitmap ownership, and map-app fallback before final integration validation.

**Why:** Launcher gadgets are re-created on desktop rebind and must not outlive their cell.

**Verification:**

- Audit records no P0/P1 finding.
- Done: the gadget view carries no P0/P1 - `LauncherGadgetView` cancels the refresh loop on detach, the use case arrives as `dagger.Lazy`, and no bitmap crosses the domain boundary. Fixed from the same pass: Glide keyed a tile file by path alone, so a re-downloaded tile would never redraw, and the refresh interval equalled the cache TTL exactly, which only worked by accident of when the snapshot is stamped.

**Status:** `[x]` done

## Phase Done Criteria

- [x] All steps are `[x] done`.
- [x] `a.ps1 fc` passes.
