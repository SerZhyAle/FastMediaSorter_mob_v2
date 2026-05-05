# Tactical Plan: S0074 — copy-move-dialog-progress

**Strategic spec:** [`../S0074_copy-move-dialog-progress.md`](../S0074_copy-move-dialog-progress.md)
**Feature:** Copy/Move dialog: overall %, speed, ETA
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-total-bytes | — | ✅ Done | 3/3 | [PHASE_01__domain-total-bytes.md](PHASE_01__domain-total-bytes.md) |
| 02 | dialog-eta-percent | 01 | ✅ Done | 5/5 | [PHASE_02__dialog-eta-percent.md](PHASE_02__dialog-eta-percent.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items — all §6 items in the strategic spec are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0074` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0074`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
