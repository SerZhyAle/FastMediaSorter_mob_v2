# Phase 02 - Altitude and satellite gadgets

**Strategic spec:** [`../S1560_launcher-profile-defaults.md`](../S1560_launcher-profile-defaults.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Add the two single-value tiles the request needs and the desktop does not have - altitude above sea level and
satellite count - as ordinary registered gadgets, placeable by hand and seedable by Phase 04.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.6, resolved by `research/06__existing-cells-inventory.md`.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "//spec-dev S1560 phase 02"` before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AltitudeGadget.kt` | New | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SatellitesGadget.kt` | New | ≤ 130 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt` | Modified | ≤ 100 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_altitude.xml` | New | n/a |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_satellites.xml` | New | n/a |
| `app_v2/src/main/res/drawable/ic_altitude.xml` | New | n/a |
| `app_v2/src/main/res/drawable/ic_satellites.xml` | New | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> **Flavor placement.** Both gadgets and both layouts live under `src/launcherEnabled`, mounted only by `standard`
> and `noLegal`; their data sources (`ObserveMotionUseCase`, `GnssStatusDataSource`) stay in `src/main` where they
> already are. No `BuildConfig.IS_*` guard is introduced.
>
> Landscape parity: neither layout has a `res/layout-land` counterpart, and none is needed - a gadget never sizes
> itself, the grid measures its cell exactly (`LauncherGadget` KDoc, ADR-9). The existing sensor tiles
> (`gadget_launcher_speed.xml`, `gadget_launcher_steps.xml`) have no landscape variant either.

---

## Steps

### Step 02.1 - Add the trilingual strings for both tiles

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these keys across EN, RU and UK, one `set-android-string.ps1 -Action add` call each:
> `launcher_gadget_altitude` ("Altitude" / "Высота" / "Висота"),
> `launcher_gadget_altitude_value` ("%1$d m" / "%1$d м" / "%1$d м"),
> `launcher_gadget_altitude_description` ("Altitude above sea level: %1$s" / "Высота над уровнем моря: %1$s" / "Висота над рівнем моря: %1$s"),
> `launcher_gadget_altitude_unknown` ("No altitude yet" / "Высота пока неизвестна" / "Висота поки невідома"),
> `launcher_gadget_satellites` ("Satellites" / "Спутники" / "Супутники"),
> `launcher_gadget_satellites_value` ("%1$d / %2$d" for used-in-fix over visible, same in RU and UK),
> `launcher_gadget_satellites_description` ("Satellites: %1$d in fix out of %2$d visible" / "Спутники: %1$d в решении из %2$d видимых" / "Супутники: %1$d у розв'язку з %2$d видимих").
> Reuse the existing `launcher_gadget_sensor_no_permission` for the refused-permission state - do not add a second
> one. Check every new string against `docs/COMMUNICATION_POLICY.md` §2 and §6. Then run
> `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_altitude"` and again with
> `-KeyPrefix "launcher_gadget_satellites"`.

**Why:**

Strategic §3.2 requires EN/RU/UK labels for every new cell, and both gadgets need their label before they can be
registered - `LauncherGadget.labelRes` is not nullable.

**Verification:**

- `Grep` - each of the seven keys present in all three `strings.xml` files.
- `scripts/check_strings_localized.ps1` exits 0 for both prefixes.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.2 - Add the two vector icons

**Files:** `app_v2/src/main/res/drawable/ic_altitude.xml`, `app_v2/src/main/res/drawable/ic_satellites.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two 24dp vector drawables in the shape of the existing sensor icons (`ic_speed.xml`, `ic_steps.xml`,
> `ic_compass.xml`): `android:tint="?attr/colorOnSurface"` or the same attribute those files use, no hardcoded
> hex colour anywhere in the file.

**Why:**

`LauncherGadget.iconRes` is not nullable and the repository has no altitude or satellite icon
(`research/06__existing-cells-inventory.md` §2), so the gadgets cannot be registered without them.

**Verification:**

- `Glob` - both drawable files exist.
- `Grep` - `="#` returns zero hits in both files (CLAUDE.md Rule 19: no hardcoded hex in resources).

**Status:** `[x]` done

---

### Step 02.3 - Add the two tile layouts

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_altitude.xml`, `app_v2/src/launcherEnabled/res/layout/gadget_launcher_satellites.xml`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Copy the structure of `app_v2/src/launcherEnabled/res/layout/gadget_launcher_speed.xml` for both files - the
> same icon plus value plus message arrangement, the same ids renamed to `gadget_altitude_*` and
> `gadget_satellites_*`. Do not set an explicit height on the root: the grid measures the cell exactly and a
> gadget that sizes itself fights it (`LauncherGadget` KDoc, ADR-9). No hardcoded hex colours - use `?attr/`
> or `@color/` exactly as the speed layout does.

**Why:**

Strategic §3.4 records that the new tiles mirror the existing single-value speed tile rather than introduce a
shape of their own, and §3.2 requires the new cells to be focusable on the same terms as existing ones, which the
speed layout already satisfies.

**Verification:**

- `Glob` - both layout files exist.
- `Grep` - `="#` returns zero hits in both files.
- `Grep` - `layout_height="match_parent"` or the same root height the speed layout uses is present in both roots.

**Status:** `[x]` done

---

### Step 02.4 - Write `AltitudeGadget`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AltitudeGadget.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Mirror `SpeedGadget.kt`: an `@Inject constructor(private val observeMotion: Lazy<ObserveMotionUseCase>)`
> implementing `LauncherGadget` with `key = LauncherGadgetRegistry.KEY_ALTITUDE`, `defaultSpanW = 2`,
> `defaultSpanH = 1`, `minSpanW = 1`, `minSpanH = 1`, `requiresResourceParam = false`, and a private
> `LauncherGadgetView` subclass collecting the same flow. Read `reading.altitudeMeters` instead of
> `reading.speedKmh`; render `launcher_gadget_altitude_unknown` while it is null, since a fix without a barometric
> or GNSS altitude is normal rather than an error. Keep the `hasLocationPermission()` gate and the
> `launcher_gadget_sensor_no_permission` message verbatim from `SpeedGadget`, and keep `isAvailable()` delegating
> to the same `SensorAvailabilityRepository.isAvailable(SensorCapability.LOCATION)` check that `SpeedGadget`
> performs. Set `contentDescription` from `launcher_gadget_altitude_description`.

**Why:**

Strategic §5.1 pillar 2 requires the missing readings to become ordinary placeable cells rather than seed-only
special cases, and `research/06__existing-cells-inventory.md` §4 established that `MotionReading` already carries
`altitudeMeters` for exactly this tile - only the view was never built.

**Verification:**

- `Glob` - `AltitudeGadget.kt` exists.
- `Grep` - `class AltitudeGadget` matches twice - the gadget and its private `AltitudeGadgetView`, the same shape `SpeedGadget` has.
- `Grep` - `altitudeMeters` matches at least once in that file.
- `Grep` - `KEY_ALTITUDE` matches once in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 02.5 - Write `SatellitesGadget`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SatellitesGadget.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Follow the same `LauncherGadget` + private `LauncherGadgetView` shape as `SpeedGadget`, with
> `key = LauncherGadgetRegistry.KEY_SATELLITES`, span 2x1, min 1x1, `requiresResourceParam = false`. Inject
> `Lazy<GnssStatusDataSource>` and collect its `observe()` flow inside `onActive()`, so the GNSS callback
> registers only while the tile is attached and started - the data source is cold and foreground-only by contract
> and unregisters on `awaitClose`. Render `usedInFixCount` over `visibleCount` through
> `launcher_gadget_satellites_value`, and show `0` rather than an empty state when a fix is absent. When the flow
> reports `SectionAvailability.NoPermission`, render `launcher_gadget_sensor_no_permission` exactly as
> `SpeedGadget` does. Set `contentDescription` from `launcher_gadget_satellites_description`.

**Why:**

The owner asked for the satellite count with "0 as the marker of a missing GPS fix" (§0), so an absent fix must
read as a zero rather than as a blank tile, and §5.1 pillar 2 requires the reading to become a real placeable cell.

**Verification:**

- `Glob` - `SatellitesGadget.kt` exists.
- `Grep` - `class SatellitesGadget` matches twice - the gadget and its private `SatellitesGadgetView`.
- `Grep` - `usedInFixCount` matches at least once in that file.
- `Grep` - `GnssStatusDataSource` matches once in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 02.6 - Register both gadgets

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt`
**Depends on:** Step 02.4, Step 02.5

**Prompt for developer:**

> Add `const val KEY_ALTITUDE = "altitude"` and `const val KEY_SATELLITES = "satellites"` to the registry's
> companion beside the existing sensor keys, and append both gadgets to the `@SensorGadgets` provider list in
> `SensorGadgetModule`. Do not add them to the registry constructor - the module list is what keeps the
> constructor under detekt's parameter threshold.

**Why:**

A gadget absent from the registry is invisible to both the picker and the desktop binder, so the cells written in
Steps 02.4-02.5 would be unreachable, and Phase 04 could not seed a key the registry does not know.

**Verification:**

- `Grep` - `KEY_ALTITUDE = "altitude"` matches once in `LauncherGadgetRegistry.kt`.
- `Grep` - `KEY_SATELLITES = "satellites"` matches once in `LauncherGadgetRegistry.kt`.
- `Grep` - `AltitudeGadget` and `SatellitesGadget` each match once in `SensorGadgetModule.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` - two new classes.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`), with Layer "coroutine/Flow/listener change" applied to both new collectors.
- [ ] `CODE.LOCK` released via `scripts/utils/exit-code-lock.ps1`.

---

## Step Log

- 2026-08-11 - 02.1 done. Seven keys added through `set-android-string.ps1 -Action add`; parity checked with both
  prefixes, `check_strings_localized.ps1` exit 0 for each. The altitude prefix reports six keys because the
  pre-existing `launcher_gadget_altitude_chart` pair shares it.
- 2026-08-11 - 02.2 done. `ic_altitude.xml`, `ic_satellites.xml`, both 24dp vectors with `@color/white` fill like
  every other gadget icon; zero hardcoded hex.
- 2026-08-11 - 02.3 done. Both layouts copied from the speed tile's `<merge>` shape; no fixed root height, so the
  grid keeps sole control of the cell size (ADR-9).
- 2026-08-11 - 02.4 done. Predicate corrected: `class AltitudeGadget` matches twice, the gadget plus its private
  view, exactly as `SpeedGadget` is shaped.
- 2026-08-11 - 02.5 done. Same predicate correction for `SatellitesGadget`. A refused grant and a missing receiver
  render as different messages because `MonitorSection` carries the reason.
- 2026-08-11 - 02.6 done. Both gadgets joined the `@SensorGadgets` provider list, not the registry constructor -
  the constructor is already at detekt's parameter threshold.
- 2026-08-11 - Phase close. `.\a.ps1 fk` BUILD SUCCESSFUL, then `.\a.ps1 dq` run because `fk` does not validate the
  Hilt graph and this phase added two constructor-injected gadgets to a `@Provides` list; `hiltJavaCompile`
  completed with no MissingBinding.
- 2026-08-11 - UI gate (S1338): placement decision recorded in strategic §3.4 (tiles mirror the speed tile);
  screenshot deferred (no device) - `adb.ps1 devices` exit 2.
- 2026-08-11 - Finding handed to Phase 04: `LauncherStarterSets.launcherActions()` maps the whole of
  `LauncherActionCatalog.all`, so Phase 01's two new actions are already seeded to every profile. `act:all_apps`
  everywhere is correct; `act:black_screen` everywhere contradicts strategic §6.4. Captured as new Step 04.0.

---

## Handoff Notes to Next Phase

`LauncherGadgetRegistry.KEY_ALTITUDE` and `KEY_SATELLITES` are stable persisted cell targets from here on. Phase 04
seeds both and must extend `LauncherStarterSetsParityTest` to tie its own literals to these constants, because
`LauncherStarterSets` lives in `src/main` and cannot import the registry.

---

## Rollback Plan

Revert the phase commit. No data migration; an orphaned `altitude`/`satellites` cell left on a desktop renders
through the existing unavailable-gadget path.
