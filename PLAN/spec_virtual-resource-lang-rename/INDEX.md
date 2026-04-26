# Tactical Plan: virtual-resource-lang-rename

**Strategic spec:** [`../spec_virtual-resource-lang-rename.md`](../spec_virtual-resource-lang-rename.md)
**Feature:** Auto-rename virtual resources on language change
**Tier:** 2 — Easy
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-04-26

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_virtual-resource-lang-rename.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
| --- | --- | --- | --- | ---: | --- |
| 01 | domain-rename | — | ✅ Done | 2/2 | [PHASE_01__domain-rename.md](PHASE_01__domain-rename.md) |
| 02 | startup-wiring | 01 | ✅ Done | 3/3 | [PHASE_02__startup-wiring.md](PHASE_02__startup-wiring.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

No open research items from strategic spec.

---

## Completion Gate

The feature is Done when **every** item below is ticked:

- [ ] All phases show ✅ Done in the Phase Overview.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 bullet).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class added).
- [ ] `/spec-check virtual-resource-lang-rename` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. **Before starting a phase:** flip its row to `🚧 In Progress`. Update `Phases: X/3 done` at the top.
2. **During a phase:** flip each step's `Status:` to `[~] in progress` when started, `[x] done` when Verification passes.
3. **On phase completion:** confirm every step is `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. **If blocked:** flip row to `⛔ Blocked`, append to Blockers Log.
5. **On all phases done:** flip top `Status:` to `Done` and run `/spec-check virtual-resource-lang-rename`.

---

## Blockers Log

(empty)

---

## Change Log

- **2026-04-26** — Initial tactical plan authored by `/spec-tech`.
