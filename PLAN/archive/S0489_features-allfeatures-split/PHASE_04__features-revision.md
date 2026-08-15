# Phase 04 - FEATURES Revision (showcase)

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Reduce `docs/FEATURES*` to a curated public showcase: keep only unique/standout capabilities, drop technical-inventory noise now held in ALL_FEATURES, and add a pointer to the inventory. Preserve EN/RU/UK parity.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (full inventory exists to cross-check against).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

> Backup step required (each FEATURES file is content-critical though < 500 LOC): copy current trio to `temp/` before pruning.

---

## Steps

### Step 04.1 - Backup and select showcase set

**Files:** `docs/FEATURES.md` (read), `temp/` (backup)
**Depends on:** - start of phase

**Prompt for developer:**

> Timestamped-copy the current `docs/FEATURES.md` + `_RU` + `_UK` into `temp/`. Then mark, per current FEATURES entry, keep-as-showcase vs move-to-inventory-only. Keep criterion: unique/standout/headline capability worth publishing on the site; demote routine/technical entries (already captured as ALL_FEATURES records). The selection is reversible (originals in temp/ and full set in ALL_FEATURES).

**Verification:**

- `Glob` - three timestamped backups exist under `temp/`.
- Keep/demote decision list recorded in chat/Blockers Log, one line per current FEATURES entry.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Backups: temp/FEATURES{,_RU,_UK}.md.20260617_182630.bak. Demotion set (minor/technical, fully held in ALL_FEATURES): "Intelligent caching & sync", "Player-only keep screen on", "Follow OS auto-rotate". Rest kept as already-curated showcase; owner curates further via /skill-release diffs.

---

### Step 04.2 - Prune the three FEATURES files in lockstep

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Apply the keep-set to all three language files identically: remove demoted entries, keep showcase entries verbatim (do not reword surviving copy). Maintain section/entry parity across EN/RU/UK - same entries, same order. Author style applies to any new connective text (`..` not `...`, `ё`/`Ё` in RU). Surviving entries keep their `[Standard / VR]`-style availability labels.

**Verification:**

- `Grep` - identical count of `- **` bullets across the three files (parity).
- Run: `pwsh -NoProfile -File scripts/check_strings_localized.ps1` is N/A (docs, not strings) - instead confirm equal section headings across the trio via `Grep -c "^## "`.
- `Grep` - no demoted entry's title remains in any of the three files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. Lockstep prune applied to all three; bullet parity 51/51/51, section parity 18/18/18; demoted titles return zero hits. Surviving copy kept verbatim.

---

### Step 04.3 - Add inventory pointer

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one short intro line to each FEATURES file stating it is the curated public showcase, and that the complete developer inventory lives in `docs/ALL_FEATURES.jsonl`. Localize the line (EN/RU/UK). Keep it one sentence.

**Verification:**

- `Grep` - `ALL_FEATURES.jsonl` referenced in all three FEATURES files.
- `Grep` - the showcase-intro line present once per file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Intro paragraph in all three files rewritten to position FEATURES as curated showcase and point to docs/ALL_FEATURES.jsonl (localized EN/RU/UK).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] EN/RU/UK bullet-count parity holds.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the three FEATURES files.

---

## Handoff Notes to Next Phase

FEATURES is now showcase-only with a pointer to ALL_FEATURES. From here on, FEATURES is populated only by `/skill-release` (wired in Phase 05), never hand-edited per feature.

---

## Rollback Plan

Restore the three FEATURES files from the `temp/` backups. No data loss - full content remains in ALL_FEATURES.
