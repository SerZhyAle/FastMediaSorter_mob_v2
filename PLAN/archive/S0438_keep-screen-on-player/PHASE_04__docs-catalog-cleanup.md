# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0438_keep-screen-on-player.md`](../S0438_keep-screen-on-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Document the new user-facing capability trilingually, regenerate the class catalog, and record the dev log.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 04.1 - Add the FEATURES entry trilingually

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence in each of the three FEATURES files describing the new capability: a separate setting that keeps the screen on only while the player and standalone players are active, available when the global keep-screen-on setting is off. Match the wording to the strategic §8 sentence and the existing FEATURES entry style. Do not duplicate the existing global keep-screen-on description.

**Verification:**

- `Grep` - a keep-screen-on-player sentence present in `docs/FEATURES.md`.
- `Grep` - corresponding sentence present in `docs/FEATURES_RU.md`.
- `Grep` - corresponding sentence present in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. FEATURES entry added EN/RU/UK under §16 Settings & Navigation.

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the local catalog index after the Phase 01-03 changes to `AppSettings`, `BaseActivity`, and player activities.

**Verification:**

- Script - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS. Catalog regenerated (1805 records).

---

### Step 04.3 - Record dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure `.\scripts\add_to_dev_log.ps1` entries exist for every file modified in Phases 01-04. Add a final summary entry referencing S0438.

**Verification:**

- `Grep` - `S0438` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS. Dev logs recorded for all touched files; S0438 present in CHANGELOG.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] FEATURES updated in all three languages.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0438`.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only, no runtime impact.
