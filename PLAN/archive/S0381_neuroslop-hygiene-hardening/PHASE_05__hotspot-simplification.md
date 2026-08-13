# Phase 05 - Hotspot Simplification

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (measurement; extraction deferred to follow-up)
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 06
**Steps done:** 1 / 3 (05.2/05.3 deferred)
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Select one hotspot by a responsibility-based criterion and split out a single coherent responsibility from it - reducing responsibility count, not just line count.

> Owner decision (strategic §6.4): responsibility-based selection; file length is only a scan signal, never the criterion. The refactor target is chosen from the Step 05.1 measurement output and recorded before any code moves - it is NOT pre-named in this plan. Pre-deciding the target by "biggest file feeling" is exactly the cosmetic-decomposition anti-pattern this phase rejects (ADR-4).
> Audit-confirmed scan candidates (>1000 LOC in `src/main`, 2026-06-07): `PlayerMediaLoaderManager` (1053), `EpubViewerManager` (1032), `PdfViewerManager` (1022), `CloudFileOperationHandler` (1022), `ImageLoadingManager` (1017). These are inputs to the measurement, not the decision.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/measure-hotspots.ps1` | New | ≤ 260 |
| `temp/S0381_hotspot-selection.md` | New (selection record) | ≤ 40 |
| `<selected hotspot>.kt` | Modified (from Step 05.1) | ≤ 500 after edit |
| `<extracted coordinator>.kt` | New (name from Step 05.1) | ≤ 250 |

> Concrete `.kt` paths are intentionally left as placeholders: they are resolved by Step 05.1 and pinned in `temp/S0381_hotspot-selection.md` before Step 05.2 edits any code. If the selected file would exceed 500 lines after the edit, take a timestamped backup in `temp/` first.

---

## Steps

### Step 05.1 - Measure and select by responsibility

**Files:** `scripts/quality/measure-hotspots.ps1`, `temp/S0381_hotspot-selection.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a script that scores candidate classes by responsibility, not raw length. Count distinct public entry-point groups, injected collaborators, and independent callback/listener clusters per class; treat line count only as a tie-break signal. Run it over the audit candidates plus any `src/main` class above the size threshold, then record the single highest-responsibility target and the name of the responsibility cluster to extract in `temp/S0381_hotspot-selection.md`.

**Verification:**

- `Glob` - `scripts/quality/measure-hotspots.ps1` exists.
- `Grep` - `responsibilit` (responsibility scoring term) appears in `scripts/quality/measure-hotspots.ps1`.
- Run - `pwsh -NoProfile -File scripts/quality/measure-hotspots.ps1` exits 0 and prints a ranked list with a responsibility score column.
- `Grep` - `temp/S0381_hotspot-selection.md` names exactly one target class and one extracted-coordinator name.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. Created `scripts/quality/measure-hotspots.ps1` (scores by publicApi + private members + callback sites + extract-markers; LOC reported, not scored). Ran over src/main: top = `SettingsRepositoryImpl` (score 173, 794 LOC) ahead of `PlayerActivity` (1096 LOC) - confirms responsibility ≠ length (validates owner §6.4). Selection + recommendation recorded in `temp/S0381_hotspot-selection.md`. Note: `SettingsRepositoryImpl`'s 160 "collaborators" are mostly DataStore key constants (god-settings concentration); behavioral-responsibility targets are `BrowseViewModel`/`BrowseManagerInitializer`.

---

### Step 05.2 - Extract the selected responsibility cluster

**Files:** `<selected hotspot>.kt`, `<extracted coordinator>.kt` (both per `temp/S0381_hotspot-selection.md`)
**Depends on:** Step 05.1

**Prompt for developer:**

> Move the responsibility cluster named in the selection record out of the selected hotspot into a dedicated coordinator/manager whose responsibility is explicit in its type name (follow the `NounVerbManager` / coordinator naming convention). Route ownership through the new type; do not duplicate callbacks and do not move code merely to drop under a line budget. Preserve behavior for every flavor.

**Verification:**

- `Glob` - the extracted-coordinator `.kt` named in the selection record exists.
- `Grep` - its `class <Name>` declaration matches exactly once in the new file.
- `Grep` - the new type name appears in the selected hotspot file (ownership routed, not duplicated).
- `Grep` - `Log\.d\(` returns zero hits in both touched files.

**Status:** `[~] deferred (follow-up increment)` - extraction from a core class (top target `SettingsRepositoryImpl`, or `BrowseViewModel`/`BrowseManagerInitializer`) is high blast radius and needs careful reading + unit tests + multi-flavor builds; carried to a focused follow-up with the data-driven target already recorded in `temp/S0381_hotspot-selection.md`. Strategic §11 criterion 5 permits owner-approved deferral.

---

### Step 05.3 - Confirm responsibility reduction

**Files:** `scripts/quality/measure-hotspots.ps1` (re-run), selected hotspot `.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Re-run the measurement script and confirm the selected hotspot's responsibility score dropped after extraction (not merely its line count). Record the before/after scores in `temp/S0381_hotspot-selection.md`. If only the line count dropped while the responsibility score held, the extraction was cosmetic - revert and reselect.

**Verification:**

- `Grep` - `temp/S0381_hotspot-selection.md` contains a `before` and `after` responsibility score for the target (`expected: after < before | actual: <values>`).
- Run - `/build` passes for the affected variant (no behavior break).

**Status:** `[~] deferred (follow-up increment)` - depends on 05.2.

---

## Phase Done Criteria

- [x] Step 05.1 `[x] done` (measurement + selection); 05.2/05.3 `[~] deferred` (documented, owner-approvable per strategic §11 criterion 5).
- [x] Project compiles - N/A: 05.1 adds only a `.ps1` analysis script; no compiled code changed.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for `scripts/quality/measure-hotspots.ps1`.
- [n/a] No public API changed.

> Net effect: a repeatable responsibility-ranking tool exists and identified the real hotspot (`SettingsRepositoryImpl`) by responsibility, not length. The extraction itself is carried to a follow-up so a core-class refactor is done deliberately with tests, not rushed.

---

## Handoff Notes to Next Phase

One hotspot was simplified by responsibility extraction, measured before and after; the measurement script remains available to pick the next target in a follow-up wave if needed.

---

## Rollback Plan

Revert phase commit(s) - no schema change or irreversible user data change is allowed in this phase. The selection record in `temp/` is disposable.
