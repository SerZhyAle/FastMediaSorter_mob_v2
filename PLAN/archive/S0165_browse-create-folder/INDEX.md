# Tactical Plan: S0165 — browse-create-folder

**Strategic spec:** [`../S0165_browse-create-folder.md`](../S0165_browse-create-folder.md)
**Feature:** Browse: Create Folder button in toolbar for subfolder-mode resources
**Tier:** 2 — Easy
**Priority:** 40
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-05-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resources | — | ✅ Done | 3/3 | [PHASE_01__resources.md](PHASE_01__resources.md) |
| 02 | kotlin-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__kotlin-wiring.md](PHASE_02__kotlin-wiring.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

_None — all open §7 questions resolved by research:_

- **Q1 (placement):** `btnCreateFolder` inserted in `layoutControls` between the spacer after `btnDeselectAll` and `btnResourceOps`. Same `gone`-by-default pattern as `btnMicRecord`. Portrait: icon-only. Landscape: icon + `action_create_folder` label.
- **Q2 (label):** Existing string `action_create_folder` reused for landscape label and `contentDescription`. No new string keys.
- **Q3 (current path):** Confirmed in `BrowseDirectoryOpsManager.createFolder()` — uses `stateFlow.value.currentPath`, creates in the currently-browsed subfolder. Correct.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0165` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/3 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0165`.

---

## Blockers Log

_None._

---

## Change Log

- 2026-05-13 — Initial tactical plan authored by `/spec-tech`.
