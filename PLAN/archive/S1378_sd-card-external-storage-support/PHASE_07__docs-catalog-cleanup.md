# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05, 06
**Blocks:** none
**Steps done:** 5 / 5
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Record the shipped capability, refresh the generated indexes, and update the user-facing documentation the document registry points at for this area.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.
- [ ] Working tree carries the full feature.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | 1 record added |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/HOW_TO*.md` | Modified | ≤ 30 added per locale |
| `dev/CHANGELOG.md` | Regenerated via script | - |

---

## Steps

### Step 07.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `scripts/all_features/add.ps1` describing the shipped capability: browsing and sorting media on SD cards and mounted external drives, including whole-folder move and copy in both directions. Set the flavor list from the actual build gate - all four flavors - and keep the text English.

**Why:**

Strategic §8 states this ticket introduces a capability the user would perceive as new, and the release showcase is generated from the `ALL_FEATURES` diff rather than edited per spec.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new record mentions removable storage.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Record `media-browsing.removable-storage-support` added, `validate.ps1` exit 0 over 642 records. Flavors corrected from the prompt's "all four" to all six: the capability carries no `BuildConfig` gate and lives entirely in `src/main`, so `docs/FLAVOR_MATRIX.md` has no row that could exclude a flavor. Dev log recorded.

---

### Step 07.2 - Regenerate the class catalog and set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 07.1

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` through `set.ps1` for every class this ticket introduced: the volume model, repository contract and implementation, the two use cases, the SAF strategy, the directory walker and the volume watch manager.

**Why:**

not stated in strategic spec - CLAUDE.md requires new classes to carry a filled role and status in the catalog, which is what keeps `query.ps1` usable as the first research step.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*StorageVolume*"` lists the new classes with a non-empty role.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 1\1 PASS. `catalog_sync` scanned 1997 files into 2444 records; roles set on all nine classes this ticket introduced - the volume model, the repository contract and its implementation, the platform source and its interface, the Hilt entry point, both use cases, the SAF walker, the SAF strategy and the volume watch manager. Classes the ticket only modified (`UriPathResolver`, `UnifiedFileOperationHandler`, `AddResourceScanManager`, `AddResourceViewModel`) keep their empty role - the step is scoped to what this ticket introduced. Dev log recorded.

---

### Step 07.3 - Update the user guide in three locales

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Add a short section explaining how to add a folder on an SD card or a connected drive, what the app asks for and why, and what happens when the medium is ejected. Keep the three locales in lockstep and follow the existing settings-path conventions of the file.

**Why:**

The document registry lists the user guides under the `browse` product area with a `user-feature` trigger, so a shipped user-visible capability in this area updates them in the same change.

**Verification:**

- `Grep` - the new section heading is present in all three files.
- `pwsh -NoProfile -File scripts/quality/assert-howto-settings-path.ps1` exits 0, if the added text names any settings path.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Heading present once in each of the three guides, each with its own numbered entry in that file's own table of contents. The gate is `assert-howto-settings-paths.ps1` (plural, the prompt names it singular): exit 0, "50 recipes across 5 guide groups, all paths resolve, HOW_TO locales in parity". The section carries no settings path - the flow starts from Add resource, not from Settings. It also states the Android 6 limit found in this phase's audit, so the empty section on such a device reads as a platform limit rather than a fault. Dev log recorded.

---

### Step 07.4 - Run the closure facade

**Files:** repository-wide
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `scripts/post-change.ps1` naming the whole changed set with `-Files` and `-ChangeType Mixed`, and read the verdict. Fix anything that fails before claiming the phase done - `PASS WITH ADVISORIES` is not `PASS`.
>
> Amended 2026-08-05 during execution: Step 07.5 runs entirely before this one - both the probe tags and the status flip. Two reasons, and they point the same way. As written, the tags would be the only code in the ticket never to pass a gate or a build, which is what CLAUDE.md's "insert the tags before the final phase's build" rule exists to prevent. And `assert-no-ticket-logs`, which this facade runs, permits an `Sxxxx` probe only while that ticket is `BlockNeedUserTest` - running the facade between the insertion and the flip fails it on the very tags the plan asked for.

**Why:**

not stated in strategic spec - CLAUDE.md makes the facade the mechanical closure path, and it chains the dev log, the catalog sync and every quality gate in one run.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`; record `expected: PASS | actual: <verdict>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - `expected: PASS | actual: PASS` over the whole phase set (five Kotlin files, three guides, the inventory). Two advisories had to be cleared first, and the step's own "PASS WITH ADVISORIES is not PASS" is what forced it. detekt-preflight was flagging two 121-character lines in `UnifiedFileOperationHandler` that predate this ticket and sit in the baseline - wrapped rather than argued about, since Rule 7 owns lint in a file you touched. The document registry wanted two records acknowledged, `user-guides` and `feature-inventory`; both were read, and their siblings need no edit - `README.md` already promises SD-card music folders on a head unit, which this ticket makes truer than it was, and the remaining guides never mentioned removable media at all.
- 2026-08-05 - Cost recorded honestly: the facade ran three times over this set, so `dev/CHANGELOG.md` carries three rows for one closure. The dev log is append-only with no removal path, and the two earlier rows were the price of discovering which registry records the change had touched.

---

### Step 07.5 - Hand the ticket to device testing

**Files:** `PLAN/spec-catalog.jsonl` (through the CLI)
**Depends on:** Step 07.4

**Prompt for developer:**

> Insert one `Timber.d("S1378: ..")` probe at the entry of each changed flow - volume enumeration, document-tree directory operation, destination space pre-flight, picker section build - then move the ticket to `BlockNeedUserTest` through `update.ps1` with a status note naming what must be checked on a device with a physical SD card.

**Why:**

Strategic §3.3 sets the validation level at a device with a physical card because an emulator reproduces neither connection nor ejection, and CLAUDE.md binds the probe tags to exactly that status.

**Verification:**

- `Grep` - `Timber.d("S1378:` matches once per changed flow entry and nowhere else.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1378 -Format json` reports `BlockNeedUserTest` with a non-empty status note.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Four probes, one per changed flow and nowhere else: volume enumeration (`StorageVolumeRepositoryImpl`), document-tree directory copy (`SafOperationStrategy`), destination space pre-flight (`UnifiedFileOperationHandler`), removable-section build (`AddResourceScanManager`). Status `BlockNeedUserTest` with a note naming the five device checks and the probe lines to look for. Ran before Step 07.4 rather than after it, for the reason recorded in that step's amendment. Dev log recorded.

---

## Phase-boundary audit (2026-08-05)

The phase is mostly documentation, which the protocol lets an audit skip - but it also added Kotlin, so the added code was audited rather than the phase's page count.

- **P1, found and fixed in this phase.** `StorageManager.getStorageVolumes()` is API 24; the `legacy` flavor ships to minSdk 23 and its build file says so explicitly ("covers API 23-25 devices"). The enumeration had no version guard, and the `catch (e: Exception)` around it could not have helped: the platform raises `NoSuchMethodError`, a `LinkageError`, which is an `Error` and not an `Exception`. On an API 23 device the first folder-picker open would have taken the app down. Guarded to return no volumes below API 24, so every caller takes its own already-written "unknown" branch - the section stays hidden and the space check reports "cannot measure, proceed". The user guide states the limit in all three locales.
- **How it surfaced, worth noting.** Not from reading the code but from writing the inventory record: deriving the flavor list from `docs/FLAVOR_MATRIX.md` meant asking what `legacy` actually compiles, and `legacy` is the flavor whose minSdk differs. The step that looked like paperwork is what found the crash.
- **Probes are temporary by contract.** The four `Timber.d("S1378:` lines exist only while the ticket sits in `BlockNeedUserTest`; `/spec-check` deletes them on the `Verified` flip. None of them logs a path or a volume name at a persistent level.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] `post-change.ps1` verdict is `PASS` (bare, no advisories).
- [x] `dev/CHANGELOG.md` carries the entry for this ticket.
- [x] Ticket status is `BlockNeedUserTest` with the device-test note.
- [x] Added beyond the written criteria: `.\a.ps1 dq` exit 0 after the probes went in, so one build validates the implementation and its probes together, as the debug-tag rule requires.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Documentation-only steps revert cleanly; the generated catalog is regenerated, never hand-restored.
