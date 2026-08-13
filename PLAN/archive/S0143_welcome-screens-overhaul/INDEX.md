# Tactical Plan: S0143 — welcome-screens-overhaul

**Strategic spec:** [`../S0143_welcome-screens-overhaul.md`](../S0143_welcome-screens-overhaul.md)
**Feature:** Пересмотр экранов Welcome (онбординг)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | nav-consolidation | — | ✅ Done | 3/3 | [PHASE_01__nav-consolidation.md](PHASE_01__nav-consolidation.md) |
| 02 | page-template | 01 | ✅ Done | 5/5 | [PHASE_02__page-template.md](PHASE_02__page-template.md) |
| 03 | touch-zones-grid | 02 | ✅ Done | 3/3 | [PHASE_03__touch-zones-grid.md](PHASE_03__touch-zones-grid.md) |
| 04 | extras-grid | 02 | ✅ Done | 5/5 | [PHASE_04__extras-grid.md](PHASE_04__extras-grid.md) |
| 05 | docs-catalog-cleanup | 01,02,03,04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (onboarding revamp bullet).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` regenerated.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_"` exits 0.
- [ ] `/spec-check S0143` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0143`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
