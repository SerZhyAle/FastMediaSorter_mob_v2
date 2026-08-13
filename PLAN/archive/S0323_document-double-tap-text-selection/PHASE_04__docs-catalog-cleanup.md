# Phase 04 - Docs + catalog cleanup

**Strategic spec:** [`../S0323_document-double-tap-text-selection.md`](../S0323_document-double-tap-text-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02 (03 deferred to S0324)
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-01
**Completed:** 2026-06-01

> **Step Log:** 2026-06-01 - Step 04.1 public FEATURES (EN/RU/UK) text-selection bullet added under §11. Step 04.2 (noLegal Office FEATURES) MOVED to S0324 - Office unchanged by S0323. Step 04.3 catalog: already regenerated in Phase 02 (PdfSelectionCoordinateMapper present); no new noLegal classes (Phase 03 deferred). Dev logs recorded.

---

## Objective

Document the new capability and regenerate the class catalog.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` / `_RU.md` / `_UK.md` | Modified | - |
| `docs/FEATURES_noLegal.md` / `_RU.md` / `_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated | - |

---

## Steps

### Step 04.1 - Public FEATURES (PDF/EPUB/TXT selection), trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise bullet to the PDF & EPUB Reader / Text Editor area: long-press selects text in PDF, EPUB and TXT/MD with draggable handles and a floating Copy action; PDF routes selection through the text mode (TXT). Mirror EN/RU/UK. Do NOT mention Office here (noLegal-only). Strings/UI copy follow `docs/COMMUNICATION_POLICY.md`.

**Verification:**

- `Grep` - the new selection bullet present in all three public FEATURES files.
- `Grep` - no "Office" token added in the public files by this step.

**Status:** `[ ]` not done

---

### Step 04.2 - noLegal FEATURES (Office selection), trilingual

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one bullet to the gitignored noLegal feature docs: the embedded Office viewer supports text selection with the unified Copy/Translate/Search menu. Mirror EN/RU/UK. These files are local/gitignored - never add Office to the public FEATURES files.

**Verification:**

- `Grep` - the Office selection bullet present in all three `FEATURES_noLegal*` files.

**Status:** `[ ]` not done

---

### Step 04.3 - Catalog regen + flavor hint + dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `.md`
**Depends on:** Steps 04.1-04.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. For the noLegal-only classes (`OfficeSelectionAugmenter`, `OfficeSelectionModule`) set the catalog flavor hint: `set.ps1 -NoFlavors "standard,lite,photos,legacy"`. Confirm dev log entries exist for all modified code/doc files from Phases 01-04.

**Verification:**

- `dev/CATALOG/app_v2.jsonl` contains `PdfSelectionCoordinateMapper` and `DocumentSelectionAugmenter`.
- noLegal augmenter classes carry the `-NoFlavors` hint.
- `Grep` - `dev/CHANGELOG.md` has entries for the modified files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] FEATURES public (EN/RU/UK) + noLegal (EN/RU/UK) updated.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Dev log complete for all touched files.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0323`.

---

## Rollback Plan

Docs-only + regenerated indexes - revert doc edits; catalog regenerates from source.
