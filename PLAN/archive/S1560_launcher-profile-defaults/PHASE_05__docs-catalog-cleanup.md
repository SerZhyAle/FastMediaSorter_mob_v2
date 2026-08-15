# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1560_launcher-profile-defaults.md`](../S1560_launcher-profile-defaults.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Record the delivered capability, refresh the generated indexes, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree carries every source change from Phases 01-04.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified via `scripts/all_features/add.ps1` | n/a |
| `docs/ARCHITECTURE.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated - gitignored, never committed | n/a |
| `dev/CHANGELOG.md` | Appended via `scripts/add_to_dev_log.ps1` | n/a |

---

## Steps

### Step 05.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the delivered capability in
> English: the launcher desktop now seeds a set matched to the detected device profile on first run, and places a
> third-party app cell only when that app is installed. Set the flavor field from the gate that actually governs
> the feature - `standard` and `noLegal`, the two flavors that mount the launcher - and read it from
> `docs/FLAVOR_MATRIX.md` rather than from memory. Reference `S1560` in the `spec` field. Then run
> `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Why:**

CLAUDE.md §11 makes `docs/ALL_FEATURES.jsonl` the inventory every release note is generated from, and strategic §8
names this ticket as a FEATURES candidate whose final wording `/skill-release` derives from that diff.

**Verification:**

- `Grep` - `S1560` appears in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 2\2 PASS. Files: docs/ALL_FEATURES.jsonl (record `launcher.profile-defaults`, flavors standard+noLegal read from docs/FLAVOR_MATRIX.md row `SUPPORT_LAUNCHER`). validate.ps1 exit 0, 688 records.

---

### Step 05.2 - Update the launcher section of the architecture document

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Launcher Mode" section, update the description of first-run seeding to say that the starter set is
> per-profile and that a third-party cell is conditional on the package being installed. Name
> `LauncherStarterSets` as the single place that answers "what does this profile get". Do not restate the grid -
> the table is the source of truth and a copy would go stale.

**Why:**

The document registry returns `docs/ARCHITECTURE.md` as the one maintained document owning the `launcher` product
area, and strategic §2 goal 2 requires the answer to "what does this profile see" to be findable in one place.

**Verification:**

- `Grep` - `LauncherStarterSets` appears in `docs/ARCHITECTURE.md`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 2\2 PASS. Files: docs/ARCHITECTURE.md ("First-run starter set" paragraph, section "Launcher Mode", line 300) - content was already applied in the working tree by an earlier run of this ticket and matches the prompt: it names `LauncherStarterSets` as the single profile table, states the third-party cell is conditional on the package being installed, and restates no grid. validate.ps1 exit 0, 28 records.

---

### Step 05.3 - Refresh the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket, then set `role`
> and `status` for the three new classes - `AltitudeGadget`, `SatellitesGadget`, `ResolveInstalledPackagesUseCase` -
> via `dev/CATALOG/scripts/set.ps1`. The two gadgets live in `src/launcherEnabled`, so declare
> `-NoFlavors "lite,photos,legacy,vr"` on both. Never commit the regenerated index files.

**Why:**

CLAUDE.md §"Catalog & Navigation" requires new classes to carry `role` and `status`, and a flavor-only class to
declare which flavors do not contain it, so the next catalog query finds them correctly.

**Verification:**

- `dev/CATALOG/scripts/query.ps1 -ClassMatches "*AltitudeGadget*"` returns one row with a non-empty `role`.
- Same for `*SatellitesGadget*` and `*ResolveInstalledPackagesUseCase*`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 3\3 PASS. `catalog_sync.ps1 -Module app_v2` exit 0 (2219 files, 2770 records). `AltitudeGadget` role "Launcher altitude sensor tile", `SatellitesGadget` role "Launcher satellites sensor tile", both `noFlavors: [lite, photos, legacy, vr]`; `ResolveInstalledPackagesUseCase` role "Installed package resolver for launcher seeding". All three `status: tested`. The generated index files stay gitignored and uncommitted.

---

### Step 05.4 - Close the ticket through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1, Step 05.2, Step 05.3

**Prompt for developer:**

> Run `scripts/post-change.ps1` with `-Files` naming the whole changed set from all five phases, `-ScopeToFile`,
> `-ChangeType Mixed` and `-Module app_v2`, and read its verdict - only a bare `post-change: PASS` counts, and a
> `PASS WITH ADVISORIES` line must be read item by item. Exit 1 means a gate failed, exit 2 means it could not
> look; treat them differently. One dev-log entry for the whole ticket, not one per file.

**Why:**

CLAUDE.md §12 requires mechanical closure through the facade rather than hand-rolled steps, and the always-dirty
tree makes `-ScopeToFile` the only way the scoped gates judge this ticket's files instead of other tickets' work
in flight.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.
- `Grep` - one new entry naming S1560 in `dev/CHANGELOG.md`.
- Strategic §11 criterion 4 - `Grep` for `BuildConfig.IS_` across every file this ticket touched returns zero
  hits, and every behavioural file added by Phases 01-02 sits under `src/launcherEnabled`, which `lite`, `photos`,
  `legacy` and `vr` do not mount. Read `docs/FLAVOR_MATRIX.md` to confirm which flavors mount the launcher rather
  than asserting it from memory.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `post-change.ps1` printed `post-change: PASS`.
- [x] `docs/FEATURES*.md` untouched - `/skill-release` owns them.
- [x] Phase-boundary audit run for the ticket as a whole.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

## Step Log

- 2026-08-11 - 05.4 done. `post-change.ps1 -ScopeToFile` returned `post-change: PASS`.
- 2026-08-11 - 05.4 verification 3\3 PASS. Facade exit 0 over the 21-file set from all five phases; one dev-log row written. Strategic §11 criterion 4 confirmed two ways: the scoped `flavor-flag-gate` reported 0 new occurrences across the whole set, and a direct grep for `BuildConfig.IS_`/`SUPPORT_`/`ENABLE_` over the four `src/main` files this ticket touched returned zero hits. `docs/FLAVOR_MATRIX.md` row `SUPPORT_LAUNCHER` reads `[+]` for `standard` and `noLegal` and `[-]*` for `lite`, `photos`, `legacy`, `vr`, so the four flavors that do not mount `src/launcherEnabled` see no behavioural change.
- 2026-08-11 - Phase-boundary audit for the ticket as a whole (`docs/CODE_AUDIT_PROTOCOL.md` Layers 1-4). Layer 1: gadget views hold no business logic, reading `ObserveMotionUseCase` and `GnssStatusDataSource` through the domain layer; `ResolveInstalledPackagesUseCase` follows `VerbNounUseCase`. Layer 2: both new views drive their flow from `LauncherGadgetView.onActive`, the framework hook already bound to `repeatOnLifecycle(STARTED)`, so neither introduces a scope or a collector of its own. Layer 3: no manual listener registration - the GNSS source registers and unregisters around its own collector, documented in `SatellitesGadget`'s KDoc. Layer 4: no Room entity, DAO or migration touched; launcher cells reuse the existing table. No P0/P1/P2 findings. One P3 observation recorded and deliberately not acted on: `SatellitesGadget` overrides no `isAvailable()` the way `AltitudeGadget` does, so the tile is offerable on a device without GNSS - it then renders the defined "no fix" state rather than a blank, which is the behaviour the layout comment describes.

---

## Rollback Plan

Revert the phase commit. Documentation and inventory only; the generated catalog files are gitignored and are
rebuilt by the next `catalog_sync.ps1` run.
