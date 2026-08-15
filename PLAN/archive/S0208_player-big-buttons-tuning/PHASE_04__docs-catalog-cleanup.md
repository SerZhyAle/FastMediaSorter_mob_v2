# Phase 04 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0208_player-big-buttons-tuning.md`](../S0208_player-big-buttons-tuning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Refresh the class catalogue for `app_v2`, confirm the strategic spec did not introduce new public capabilities (so `docs/FEATURES*.md` stays untouched per strategic §8), and ensure the changelog already lists every modified file from Phases 01–03.

---

## Prerequisites

- [ ] Phases 01, 02, 03 Done.
- [ ] `/build` → `standardDebug` from Phase 03 step 03.5 passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Auto-regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Auto-regenerated | n/a |
| `dev/CHANGELOG.md` | Indirectly — verified, not edited | n/a |

No FEATURES change. No new spec ticket. No flavor source-set entries.

---

## Steps

### Step 04.1 — Catalogue scan + render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run, in order:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Both touched classes (`PlayerBigButtonsModeManager`, `CommandPanelController`) keep their existing `role` and `status` — no manual `set.ps1` edits required. The scan refreshes auto-fields (LOC, lastTouched); the render refreshes the human-readable `.md`. Note: `dev/CATALOG/app_v2.jsonl` and `app_v2.md` are gitignored — local-only state, not committed.

**Verification:**

- Bash — `scan.ps1 -Module app_v2` exits 0.
- Bash — `render.ps1 -Module app_v2` exits 0.
- Grep — `PlayerBigButtonsModeManager` matches in `dev/CATALOG/app_v2.jsonl` (verifies the catalogue still indexes the class).

**Status:** `[ ]` not done

---

### Step 04.2 — Confirm changelog completeness

**Files:** `dev/CHANGELOG.md` (read-only verify)
**Depends on:** Step 04.1

**Prompt for developer:**

> Inspect the most recent block of `dev/CHANGELOG.md` for entries under id `S0208` or referencing the three touched files. The entries should already exist because each prior phase appended them via `add_to_dev_log.ps1`. If any of the three touched files (`values/dimens.xml`, `CommandPanelController.kt`, `PlayerBigButtonsModeManager.kt`) is missing — append the missing entry now via `.\scripts\add_to_dev_log.ps1 "<path>" "S0208" "<short msg>"`. Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- Grep — `S0208` matches at least three lines in `dev/CHANGELOG.md`.
- Grep — `app_v2/src/main/res/values/dimens.xml` matches in the most recent S0208 block of `dev/CHANGELOG.md`.
- Grep — `PlayerBigButtonsModeManager.kt` matches in the most recent S0208 block.
- Grep — `CommandPanelController.kt` matches in the most recent S0208 block.

**Status:** `[ ]` not done

---

### Step 04.3 — Confirm FEATURES policy and skip update

**Files:** none — verification only.
**Depends on:** Step 04.2

**Prompt for developer:**

> Strategic §8 explicitly says "Без изменений в `docs/FEATURES.md`. Big Buttons Mode уже описан как существующая фича (родительский тикет S0158)." — so do NOT add a new FEATURES bullet. Confirm by re-reading §8 and checking that `docs/FEATURES.md` already mentions Big Buttons Mode (search for `big buttons`, case-insensitive). If the parent S0158 row is missing, that is a separate gap — do not patch it inside this spec; create a follow-up issue manually.

**Verification:**

- Grep (case-insensitive) — `big buttons` matches at least once in `docs/FEATURES.md`.
- Grep — `S0208` returns zero hits in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` (this spec deliberately does not add a row).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Strategic spec next transition (`Implemented` → `BlockNeedUserTest`) handled by `/spec-dev` / `/spec-all` orchestrator, not by this phase.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase the orchestrator inserts the `Timber.d("S0208: …")` device-verify tag at the entry of the changed flow (`updateCommandAvailability` big-buttons branch in `CommandPanelController`) and flips the spec to `BlockNeedUserTest` for on-device verification on 320dp / 411dp / 600dp / 1240dp screens (strategic §6.1, §6.4).

---

## Rollback Plan

Catalogue regen is reversible by running `scan.ps1` again. No persistent or user-visible state changes here.
