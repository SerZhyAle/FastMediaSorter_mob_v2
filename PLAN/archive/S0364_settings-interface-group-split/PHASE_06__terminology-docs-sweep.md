# Phase 06 - Terminology docs sweep

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Align user-facing documentation wording to "браузер файлов" for the Browse window across the trilingual doc mirrors per the Phase 04 inventory.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done; inventory lists `CHANGE` doc occurrences.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| other `docs/**` user help files (per inventory) | Modified | n/a |

> Run through the `/doc-update` skill to keep EN/RU/UK mirrors in sync. noLegal-only docs (`docs/FEATURES_noLegal*.md`) are out of this spec's scope unless the inventory flags a Browse-window mention there.

---

## Steps

### Step 06.1 - Rewrite Browse-window wording in docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, other `docs/**` per inventory
**Depends on:** - start of phase

**Prompt for developer:**

> Via `/doc-update`, replace inconsistent Browse-window terms with "браузер файлов" / "file browser" / "браузер файлів" (or qualified variants) in every `CHANGE` doc occurrence from `temp/S0364_terminology_inventory.md`. Keep the three locale mirrors in lockstep. Do not alter web-browser mentions.

**Verification:**

- `Grep` - `браузер файлов` present in `docs/FEATURES_RU.md`.
- `Grep` - `file browser` present in `docs/FEATURES.md`.
- `Grep` - `браузер файлів` present in `docs/FEATURES_UK.md`.
- `Grep` - `проводник` returns zero hits across `docs/**` (excluding any quoted web-browser context flagged EXCLUDE).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. FEATURES.md `file browser` ×3 (Telegram, blank canvas, quick notes). FEATURES_RU.md / _UK.md canonical term present in declension-correct forms («браузера/браузере файлов», «браузера/браузері файлів») on lines 52/57/107. `проводник`/`провідник`/"explorer" reduced to zero in user-facing docs - the only remaining `проводник` is the COMMUNICATION_POLICY glossary forbidden-term declaration (intended, documented EXCLUDE). During this step a previously-missed FAQ.md/_RU/_UK line 48 ("File Manager Mode" described with forbidden "explorer"/«проводник»/«провідник») was caught and fixed (inventory rows D10-D12). WHATS_NEW history + developer docs left untouched per inventory.

---

### Step 06.2 - Mirror-parity check

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Confirm the three FEATURES mirrors describe the same items with the aligned term and no mirror was edited alone (same bullet count for any touched section).

**Verification:**

- `Grep` - count of `браузер файлов` in `docs/FEATURES_RU.md` ≥ 1 and matched by `file browser` count ≥ 1 in `docs/FEATURES.md` for the same sections.
- Manual: touched sections present in all three mirrors.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. FEATURES sections "Send to Telegram" (52), "Blank canvas creation"/«Создание пустых холстов»/«Створення порожніх полотен» (57), "Quick notes"/«Создание заметок»/«Створення заміток» (107) present in all three mirrors with the aligned term. FAQ "File Manager Mode" (48) aligned in all three. No mirror edited alone - same bullet count per touched section (expected EN=RU=UK | actual match).

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every modified doc via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Strings (Phase 05) and docs (Phase 06) both use the canonical term. Phase 07 finalizes catalog/changelog/functionality-log and runs the completion checks.

---

## Rollback Plan

Revert the doc edits via `/git`. No code or data surface changed.
