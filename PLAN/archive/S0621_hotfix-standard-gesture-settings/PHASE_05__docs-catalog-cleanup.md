# Phase 05 - Docs, catalog, cleanup

**Strategic spec:** [`../S0621_hotfix-standard-gesture-settings.md`](../S0621_hotfix-standard-gesture-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

**Step Log:**
- 05.1 ADJUSTED - per CLAUDE.md §11, docs/FEATURES*.md is release-only (populated by /skill-release from the ALL_FEATURES diff), so no per-spec FEATURES edit. Capability recorded in the dev inventory instead (05.2).
- 05.2 PASS - added ALL_FEATURES.jsonl record `screen-capture.edge-gesture-screenshot-standard` (flavors=standard, spec=S0621); validate.ps1 PASS (380 records). Also fixed a pre-existing malformed id on L378 (`s0612.` -> `player.`) that was failing validation (trivial inline fix, unrelated to S0621).
- 05.3 PASS (partial) - catalog regenerated via post-change catalog_sync (1972 records incl. the 2 new standard classes). post-change.ps1 PASS: dev-log, ticket-log (S0621 probes valid under BlockNeedUserTest), neuroslop, doc-pins, deprecated-pm, fgs-notification all green. Per-class flavor hint via set.ps1 SKIPPED: standard + noLegal same-name variants share a package-relative catalog path that set.ps1 -Path cannot disambiguate; flavor isolation is enforced by source-set placement regardless.

---

## Objective

Record the new standard capability in the public showcase + capability inventory, regenerate the class catalog with flavor hints for the new standard-only classes, and run the post-change closure.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done; standard + noLegal builds green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 10 |
| `docs/FEATURES_RU.md` | Modified | ≤ 10 |
| `docs/FEATURES_UK.md` | Modified | ≤ 10 |
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 05.1 - Add the capability to the public showcase (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one showcase sentence to the standard `docs/FEATURES.md` + `_RU` + `_UK` (strategic §8 mandates) describing the edge-gesture screenshot now in the Play build, e.g. EN "Snap any screen with a left-edge gesture and save it to a chosen resource or the screenshots folder." Run the message through `docs/COMMUNICATION_POLICY.md` §2 (formula) + §6 (tone checklist). Keep the parenthetical-free, plain-hyphen, `ё`-correct house style. This is a standard (published) capability, so it goes in the main FEATURES files, not `_noLegal`.

**Verification:**

- `Grep` - the new sentence present in all three FEATURES files (EN/RU/UK keyword match).
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[ ]` not done

---

### Step 05.2 - Record the capability in the inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add one EN-only record to `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` for the standard edge-gesture screenshot (MediaProjection consent path). Validate with `scripts/all_features/validate.ps1`. This is a standard (non-noLegal) capability, so it goes in the main inventory, not `ALL_FEATURES_noLegal.jsonl`.

**Verification:**

- `scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[ ]` not done

---

### Step 05.3 - Catalog regen with flavor hints + post-change closure

**Files:** `dev/CATALOG/app_v2.jsonl` (regenerated), dev changelog
**Depends on:** Step 05.2

**Prompt for developer:**

> Regenerate the class catalog (`scripts/catalog_sync.ps1 -Module app_v2`). For the two new standard-only classes (`ScreenGestureOverlayControllerImpl` + `ScreenCaptureModule` under `src/standard`) set catalog flavor hints via `set.ps1 -NoFlavors "lite,photos,legacy,noLegal,vr"` so their standard-exclusivity is searchable. Fill `role`/`status` for both. Run the bundled post-change closure (`scripts/post-change.ps1`) so dev-log, neuroslop, settings-doc-sync, and string gates pass. Confirm `src/standard` is among `dev/CATALOG/scripts/scan.ps1` source roots (it is a primary flavor); if the two new classes are missing from the scan, add the root.

**Verification:**

- `dev/CATALOG/app_v2.jsonl` contains `ScreenGestureOverlayControllerImpl` with a standard-only flavor hint.
- `dev/CHANGELOG.md` has an entry covering the S0621 change set.
- `post-change.ps1` reports PASS.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] FEATURES EN/RU/UK updated; ALL_FEATURES.jsonl record added + validated.
- [ ] Catalog regenerated; new standard classes carry flavor hints + role/status.
- [ ] `dev/CHANGELOG.md` entries present.
- [ ] `post-change.ps1` PASS.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After all phases, the spec enters `BlockNeedUserTest`: insert `Timber.d("S0621: ..")` probes at the changed-flow entry points (controller `setEnabled`, gesture capture, settings group setup) and hand off for on-device verification of the consent-dialog capture flow on standard.

---

## Rollback Plan

Revert doc + catalog edits. No data migration or runtime surface depends on this phase.
