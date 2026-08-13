# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S0426_home-screen-weather-block.md`](../S0426_home-screen-weather-block.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all phases
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Record the capability, refresh the class catalog, document the Open-Meteo attribution obligation, and leave the ticket ready for a device test.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | +1 record |
| `docs/LAUNCHER.md` (or the launcher user doc that exists) | Modified | ≤ 30 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via `add_to_dev_log.ps1`) | - |

---

## Steps

### Step 05.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> One EN record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the desktop weather gadget: current conditions for a place the user names, keyless Open-Meteo data, no location permission. Read the record back and verify its flavor list matches where the gadget actually ships (the `launcherEnabled` source set), not an assumed matrix.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `Grep` - `Open-Meteo` present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x] done`

---

### Step 05.2 - Document the data source and its attribution

**Files:** launcher user doc under `docs/`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a short section naming Open-Meteo as the weather source with the CC-BY 4.0 attribution line, the 20-minute refresh, and the fact that no location permission is requested. This is the documentation half of the CC-BY obligation the owner accepted (strategic D1) - the in-gadget attribution is the other half. Mirror the section into the RU/UK variants of that doc if the file family is trilingual.

**Verification:**

- `Grep` - `Open-Meteo` present in the touched doc(s).
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[x] done`

---

### Step 05.3 - Regenerate the catalog and dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run the closure facade once for the ticket: `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/WeatherGadget.kt" -Target "S0426" -Description "Launcher weather gadget on Open-Meteo" -ChangeType Mixed -Module app_v2 -ScopeToFile`. Then fill `role` and `status` for every new class via `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Search "weather"` - lists the new classes with a non-empty role.
- `post-change.ps1` - exit 0.

**Status:** `[x] done`

---

### Step 05.4 - Hand the ticket to the device test

**Files:** touched `.kt` files
**Depends on:** Step 05.3

**Prompt for developer:**

> Insert one `Timber.d("S0426: <entry point>")` probe per changed flow entry (gadget activation, weather fetch result, picker result) and flip the ticket with `update.ps1 -Id S0426 -Status BlockNeedUserTest -StatusNote '<what to verify on device>'`. Status flip goes first, then the probes, then the build - the ticket-log gate fails closed on a probe whose spec is not `BlockNeedUserTest`.

**Verification:**

- `Grep` - `Timber.d("S0426:` matches at least once.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0426 -Format json` - status is `BlockNeedUserTest`.
- `.\a.ps1 d` - exit code 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Debug APK builds - `.\a.ps1 d` exit 0.
- [ ] Dev log entry added.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation and catalog only - revert the commit.

---

## Step Log

- 2026-07-24 - Verification 4/4 PASS. Capability recorded as `launcher.desktop-weather-gadget` (standard, noLegal - read off the `launcherEnabled` source-set mount, not a sibling record); Open-Meteo attribution + no-location-permission note added to FAQ EN/RU/UK (the fuller launcher guide stays with S1102); closure through `close-and-log.ps1` (dev log x10, ALL_FEATURES, catalog scan + render); three `S0426:` probes inserted after the status flip; `.a.ps1 d` exit 0.
- 2026-07-24 - AUDIT-FIX (P1): `WeatherRepositoryImpl.current` ran the SharedPreferences mirror and the provider call on the caller's context - the gadget calls it from the main thread. Wrapped in `withContext(Dispatchers.IO)`.
- 2026-07-24 - AUDIT-FIX (gate): the post-change detekt gate PASSED against reports predating this ticket (detekt was UP-TO-DATE), so its verdict was blind. Forced `:app_v2:detekt --rerun-tasks`, which surfaced 5 real findings in changed files (3x ReturnCount, ComplexCondition, LongMethod). All fixed - `registerAddFlowListeners` split into `registerResourceListeners` + `registerWeatherLocationListener`, `onGadgetChosen` collapsed to a `when`. Final rerun: 0 findings in changed files.
- 2026-07-24 - AUDIT-P2: the results list uses `SearchableOptionPickerDialog.newInstance(..) { }`, whose lambda does not survive a config change - rotating while the found-places list is open loses the pick (no crash, the user re-searches). Matches the existing usage of that dialog elsewhere; a FragmentResult variant of the canonical picker would fix it for every caller, not just here.
