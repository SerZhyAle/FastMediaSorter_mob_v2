# Phase 07 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Finalize user-facing documentation, regenerate the class catalog with roles for the new classes, and record the feature in FEATURES (strategic §8 mandates a sentence).

---

## Prerequisites

- [ ] Phases 01-06 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` (regenerated) | Modified | n/a |

---

## Steps

### Step 07.1 - Record the feature in FEATURES (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the strategic §8 sentence to all three FEATURES files: the user can enable/disable individual network and cloud sources in settings to remove unused ones from selection and stop their background activity. Keep wording aligned with `docs/COMMUNICATION_POLICY.md`.

**Verification:**

- `Grep` - the new sentence (or its key phrase) present in all three FEATURES files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Added a "Toggle remote sources" bullet to §14 Network & Cloud Integration in FEATURES.md / _RU / _UK (RU/UK authored via Edit tool, ё correct). Wording: enable/disable SMB/(S)FTP/cloud in settings or welcome; disabled source hidden + inert, resources kept.

---

### Step 07.2 - Assign catalog roles for new classes

**Files:** `dev/CATALOG/app_v2.jsonl` (via tooling)
**Depends on:** Step 07.1

**Prompt for developer:**

> Regenerate the catalog and set `role` + `status` for the new classes: `RemoteSourceId`, `RemoteSourceAvailabilityGate`, `RemoteSourceSettingsStore`, `RemoteSourceDisableCoordinator`, `WelcomeRemoteSourcesController`. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then `set.ps1` for each new class.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "RemoteSourceAvailabilityGate"` returns one record with a non-empty role.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `set.ps1` (module-relative path) assigned role + status=new to the 5 new classes: RemoteSourceId, RemoteSourceAvailabilityGate, RemoteSourceSettingsStore, RemoteSourceDisableCoordinator, WelcomeRemoteSourcesController. Roles confirmed to survive the close-and-log rescan (scan merges set roles).

---

### Step 07.3 - Dev log sweep

**Files:** `dev/CHANGELOG.md` (via tooling)
**Depends on:** Step 07.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every file modified across phases 01-06 (each phase's Done Criteria already requires this); add any missing entry via `scripts/add_to_dev_log.ps1`. Record the functionality lifecycle via `scripts/add_to_functionality_log.ps1` (ADD: per-source remote toggles).

**Verification:**

- `Grep` - `RemoteSourceAvailabilityGate.kt` appears in `dev/CHANGELOG.md`.
- `Grep` - a S0391 ADD entry in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Every modified file across phases 01-07 dev-logged at each phase boundary. `close-and-log.ps1` recorded the final BlockNeedUserTest flip, the 5 debug-tag edits + 3 FEATURES files dev logs, and the functionality-log ADD (per-source remote toggles) in one pass.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated with roles for the five new classes.
- [x] Project still compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (with the 5 debug tags).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0391` to advance the strategic spec to Verified.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only, no runtime impact.
