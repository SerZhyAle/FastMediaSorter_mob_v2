# Tactical Plan: S0108 — welcome-language-picker

**Strategic spec:** [`../S0108_welcome-language-picker.md`](../S0108_welcome-language-picker.md)
**Feature:** Language picker on the first Welcome page
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-05-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | layout-language-picker | — | ✅ Done | 2/2 | [PHASE_01__layout-language-picker.md](PHASE_01__layout-language-picker.md) |
| 02 | strings-resources | 01 | ✅ Done | 3/3 | [PHASE_02__strings-resources.md](PHASE_02__strings-resources.md) |
| 03 | model-and-adapter | 01, 02 | ✅ Done | 2/2 | [PHASE_03__model-and-adapter.md](PHASE_03__model-and-adapter.md) |
| 04 | activity-integration | 03 | ✅ Done | 2/2 | [PHASE_04__activity-integration.md](PHASE_04__activity-integration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Both strategic §6 research items are resolved by analysis — implementation may proceed.

- [x] **Research §6.1: `recreate()` locale correctness on API < 33** —
  `LocaleHelper.saveLanguage()` sets `@Volatile cachedLanguageCode` before `recreate()`.
  `attachBaseContext()` reads that cache, so the new locale is picked up correctly on all API levels. No extra cache clearing required.

- [x] **Research §6.2: Animation suppression on `recreate()`** —
  Call `overridePendingTransition(0, 0)` immediately after `recreate()` on the same Activity instance. This overrides the default fade-in for the recreated Activity. On API 33+ LocaleManager handles recreation silently; no extra suppression needed.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0108` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0108`.

---

## Blockers Log

_(empty)_

---

## Change Log

- 2026-05-07 — Initial tactical plan authored by `/spec-tech`.
