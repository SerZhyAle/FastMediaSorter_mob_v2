# Phase 09 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Document the on-demand behavior and the extensions-manager screen trilingually, update noLegal-specific docs, regenerate the class catalog, and confirm changelog coverage (strategic Pillar F, §8).

---

## Prerequisites

- [ ] Phases 01-08 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `docs/FEATURES_noLegal*.md` | Modified (gitignored) | - |

---

## Steps

### Step 09.1 - Trilingual FEATURES: behavior note + extensions screen

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two items per strategic §8: (1) a behavior clarification that OCR/translation are off by default and install on first enable with a size estimate, staying unavailable on refusal without crashing - framed as a clarification of existing capability, not a new feature, no duplication; (2) a new entry for the "Downloadable Extensions" settings screen (manage downloadable modules and languages: status, size, download/delete). Keep EN/RU/UK in parity; `ё/Ё` in Russian.

**Verification:**

- `Grep` - the behavior sentence and the extensions-screen entry both present in all three FEATURES files.

**Status:** `[x]` done

---

### Step 09.2 - noLegal self-download specifics

**Files:** `docs/FEATURES_noLegal*.md`
**Depends on:** Step 09.1

**Prompt for developer:**

> Document the self-download specifics for sideload/Quest (`noLegal`): sets come from our GitHub mirror with vendor/Play fallback where available, translation uses the self-load technique, Set B includes the Paddle engine, and DTS (Set D) is on-demand. This file is gitignored - update locally.

**Verification:**

- `Grep` - mirror/self-download wording present in the noLegal FEATURES file.

**Status:** `[x]` done

---

### Step 09.3 - Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 09.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the catalog after the new delivery/contract/UI classes. Fill `role` + `status` for new classes via `set.ps1` where the scan leaves them blank.

**Verification:**

- `Bash` - `scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `DeliverableInventory` and `DeliverableCapabilityRepository` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 09.4 - Changelog + functionality log coverage

**Files:** `dev/CHANGELOG.md` (via script), `dev/FUNCTIONALITY.log` (via script)
**Depends on:** Step 09.3

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every modified file across phases 01-09. Add a `CHANGE` functionality-log entry via `scripts/add_to_functionality_log.ps1` recording that OCR/translation/audio-viz/DTS moved to on-demand delivery (default OFF) and that a Downloadable Extensions screen was added.

**Verification:**

- `Grep` - an S0386 / on-demand-delivery entry present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 09.*` above is `[x] done`.
- [ ] `docs/FEATURES*.md` trilingual parity holds.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Run `/spec-check S0386` to drive status toward `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - docs/catalog only, no runtime impact.
