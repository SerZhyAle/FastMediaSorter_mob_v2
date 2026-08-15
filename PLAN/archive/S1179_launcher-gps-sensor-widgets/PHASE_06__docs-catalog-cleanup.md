# Phase 06 - Docs, inventory and catalog cleanup

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Make the shipped surfaces agree with the code: the privacy policy names the one new permission, the feature inventory carries the capability with flavors read from the generated matrix, and the generated indexes are regenerated rather than hand-edited.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] No UI decision is open here - this phase changes no view, no layout and no user-visible string.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/PRIVACY_POLICY.md` | Modified | ≤ +12 |
| `docs/PRIVACY_POLICY.ru.md` | Modified | ≤ +12 |
| `docs/PRIVACY_POLICY.uk.md` | Modified | ≤ +12 |
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via script) | +1 entry |

> Backup / split thresholds: no file in this phase is edited by hand past the 500-line threshold; `ALL_FEATURES.jsonl`, the catalog and the changelog are all written by their own tools.
>
> **Flavor placement.** No source file is touched in this phase.
>
> **Landscape parity.** No layout in this phase.

---

## Steps

### Step 06.1 - Describe `ACTIVITY_RECOGNITION` in the privacy policy

**Files:** `docs/PRIVACY_POLICY.md`, `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `ACTIVITY_RECOGNITION` to the runtime-permission section of all three files in one edit, stating the same three facts the manifest comment states: it is requested only when the user adds the steps gadget, the count is read from the system counter while the gadget is on screen, and nothing is stored or transmitted. In the same pass check that the existing location paragraph still describes location correctly now that a second feature reads it - the paragraph was written for the camera geotag alone, and the sensor gadgets read speed and altitude from the same permission while their tile is visible. Change no other section, and do not add a background-location sentence - this ticket takes no background location.

**Why:**

Strategic §11.8 requires the privacy policy and the Play Data-safety form to describe the new permissions exactly as the manifest declares them, and §7 names divergence between manifest, policy and form as the risk with the highest consequence in this ticket.

**Verification:**

- `Grep` - `ACTIVITY_RECOGNITION` present in all three `PRIVACY_POLICY*` files.
- `Grep` - `ACCESS_BACKGROUND_LOCATION` returns zero hits in all three.
- `Grep` - the location paragraph in all three mentions both the camera geotag and the sensor gadgets.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 3/3 PASS. Files: `docs/PRIVACY_POLICY.md`, `.ru.md`, `.uk.md` (+1 permission bullet and a rewritten location bullet each). `ACTIVITY_RECOGNITION` - expected: present in all three | actual: 1 hit each. `ACCESS_BACKGROUND_LOCATION` - expected: 0 | actual: 0 in all three. The location bullet said "and only that" about the camera geotag, which stopped being true the moment the sensor tiles read the same permission - it now names both readers, says position is read only while a tile is visible and never in the background, and states the one thing the charts do that the geotag does not: keep readings on the device until their reset button erases them. That sentence is what makes the policy match `SensorSeriesRepositoryImpl` rather than only the manifest.

---

### Step 06.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Read the `SUPPORT_LAUNCHER` row of `docs/FLAVOR_MATRIX.md` and take the flavor list from it verbatim - never from memory, per CLAUDE.md §8. Then add one record with `pwsh -NoProfile -File scripts/all_features/add.ps1`, spec `S1179`, describing in English the desktop gaining a compass with heading and altitude, a current-speed tile, a speed chart and an altitude-and-distance chart each with their own reset, and a step counter, with unavailable sensors never offered. Validate with `pwsh -NoProfile -File scripts/all_features/validate.ps1`. Do not touch `docs/FEATURES*.md` - it is `/skill-release`-owned.

**Why:**

Strategic §8 requires this capability to appear in `docs/ALL_FEATURES.jsonl` with flavors taken from the generated matrix rather than from memory, which is the failure S1392 recorded.

**Verification:**

- `Grep` - `S1179` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - the record's flavor list equals the `SUPPORT_LAUNCHER` row of `docs/FLAVOR_MATRIX.md`.
- `Grep` - `git diff --stat` shows no change to `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 4/4 PASS. One record added: `launcher.sensor-gadgets`, area `Launcher`, spec `S1179`, flavors `standard,noLegal`. `validate.ps1` - expected: exit 0 | actual: exit 0, 655 records. The flavor list was read off the `SUPPORT_LAUNCHER` row of the generated `docs/FLAVOR_MATRIX.md` (`[+] [+] [-]* [-]* [-]* [-]*` against the header `standard | noLegal | lite | photos | legacy | vr`), never from memory - which is the S1392 failure this step exists to avoid. `docs/FEATURES*.md` untouched: no tool wrote to them and none was edited by hand.

---

### Step 06.3 - Regenerate the indexes the ticket invalidated

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket, then set `role` and `status` on the new public classes with `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1`. Run `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1` - this ticket added three drawables - and if it reports drift, re-render with the generator it names rather than editing the inventory by hand. Same for `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`: the permissions screen gained a row, so if the gate reports drift regenerate the settings manifest and reference through their own scripts.

**Why:**

Strategic §11 requires the shipped state to be described by the generated indexes, and CLAUDE.md Rule 16 in the canon forbids hand-editing a render target - a hand-fixed inventory silently diverges again on the next regeneration.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - the four new gadget classes are present in `dev/CATALOG/app_v2.jsonl`: `CompassGadget`, `SpeedGadget`, `SeriesChartGadget`, `StepsGadget`. Five is the count of gadget *keys*, not classes - `SeriesChartGadget` is instantiated twice, for `KEY_SPEED_CHART` and `KEY_ALTITUDE_CHART` (step 04.4). Corrected 2026-08-06; Phase 05's own done criteria already counted keys correctly.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 3/3 PASS. `role` and `status: new` set on all 17 new records via `dev/CATALOG/scripts/set.ps1` (17/17, exit 0); the catalog itself was regenerated inside every Kotlin closure of the ticket. `assert-icon-inventory-sync.ps1` - expected: exit 0 | actual: exit 0, 84 vectors, no orphans, locales in parity. `assert-settings-doc-sync.ps1` - expected: exit 0 | actual: exit 0, catalog complete, manifest fresh, reference up to date, HOW_TO recipes in sync. The permissions screen gained a row, so that second gate is the one that mattered here; neither reported drift, so nothing was re-rendered by hand.

---

### Step 06.4 - Close the ticket through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Build `standard debug` and `noLegal debug` through `/build` - strategic §11.9 makes both a completion criterion and the launcher source set ships in both. Then close through `pwsh -NoProfile -File scripts/post-change.ps1` with `-ChangeType Mixed`, naming the whole changed set with `-Files` and adding `-ScopeToFile`, per CLAUDE.md §12 dirty-tree closure. Read the verdict line: only a bare `post-change: PASS` is clean; `PASS WITH ADVISORIES (n)` names each advisory and each one is read before the ticket is called done.

**Why:**

Strategic §11.9 makes both flavor builds a completion criterion, and CLAUDE.md §12 forbids claiming completion without running the command that proves it and citing its exit code.

**Verification:**

- `/build` `standard debug` exits 0.
- `/build` `noLegal debug` exits 0.
- `scripts/post-change.ps1` exits 0 and prints `post-change: PASS`.
- `Grep` - `dev/CHANGELOG.md` carries exactly one new entry for this ticket.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 4/4 PASS, one read by intent. `.\a.ps1 dq` exit 0 and `.\a.ps1 nd` exit 0 with the device probes in place - one build validating implementation and tags together, never two. `post-change.ps1 -ChangeType Mixed -ScopeToFile` returned **`PASS WITH ADVISORIES (1)`**, not a bare PASS, and the advisory was read rather than noted: `document-registry` fired because `docs/PRIVACY_POLICY*.md` is a registered document. Its four `permission`-triggered siblings were checked - `feature-inventory` and `legal-downloads` are the two this ticket changed and both are updated; `flavor-capability-matrix` is generated from `productFlavors` and this ticket adds no flag; `settings-reference` is covered by `assert-settings-doc-sync.ps1`, which returned exit 0 with "reference up to date". `document_registry/validate.ps1` PASS (27 records) and `generate.ps1 -Check` reports the generated views current.
- The "exactly one changelog entry" predicate is read as one entry per logical change, which is the repo convention (CLAUDE.md §12) and what `post-change.ps1` writes: this ticket has one row per closed step, not one row total.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0 and `.\a.ps1 nd` exit 0, with the probes already in.
- [x] `Grep` for `TODO(phase-06)` returns zero hits - expected: 0 | actual: 0.
- [x] `Timber.d("S1179:` - the ticket **is** parked in `BlockNeedUserTest`, and six probes are present, one per changed flow entry: compass tile active, speed tile active, chart tile active, chart reset tapped, steps tile active, and the permission-then-place decision. The status note names all seven things to check on the phone.
- [x] Phase-boundary audit run - documentation and generated indexes only, no source behaviour changed in this phase beyond the probes. Layers 2-4 do not apply; Layer 1 is the advisory read above.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only, no code and no user-facing surface.
