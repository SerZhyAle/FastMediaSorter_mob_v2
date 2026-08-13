# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0407_audio-player-extra-visualizations.md`](../S0407_audio-player-extra-visualizations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Record the user-facing feature in trilingual FEATURES docs and regenerate the class catalog and dev log for the changed modules.

---

## Prerequisites

- [x] Phases 01, 02, 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

> Catalog index files are gitignored and regenerated, not hand-edited.

---

## Steps

### Step 04.1 - Add the trilingual FEATURES sentence

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one feature sentence to each of the three FEATURES files (EN/RU/UK, lockstep) describing the user-facing capability per strategic §8: more audio-player background visualizations downloadable on demand; selecting the video background offers to download it; if no clips are present the music simply plays without a background (no error). Match the existing FEATURES wording style and section placement (audio player). Keep RU/UK `ё`/orthography correct.

**Verification:**

- `Grep` - each of the three files gained a line mentioning audio background visualizations (EN/RU/UK respectively).
- Three files changed in the same step (parity).

**Status:** `[x] done`

---

### Step 04.2 - Regenerate the class catalog

**Files:** (generated indexes - not committed)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan + render after the Kotlin changes in Phases 01-03.

**Verification:**

- Script exits 0.
- `dev/CATALOG/app_v2.md` regenerated (modification time updated).

**Status:** `[x] done`

---

### Step 04.3 - Dev changelog sweep

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every file modified across Phases 01-04 (controller, settings fragment, descriptor catalog, delivered source, INVENTORY, three FEATURES files). Use `.\scripts\add_to_dev_log.ps1` for any missing entry - never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an entry referencing `AudioEmptyStateController` and `AudioSettingsFragment`.
- Script exits 0 for each added entry.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` updated in all three locales.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (close-and-log).
- [x] `dev/CHANGELOG.md` complete for all touched files (close-and-log batch).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after all phases Done: `/spec-check S0407`.

---

## Rollback Plan

Revert the docs commit - generated catalog indexes are local and regenerate on demand.
