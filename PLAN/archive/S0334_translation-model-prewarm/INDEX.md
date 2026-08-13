# Tactical Plan: S0334 - translation-model-prewarm

**Strategic spec:** [`../S0334_translation-model-prewarm.md`](../S0334_translation-model-prewarm.md)
**Feature:** Прогрев модели перевода при выборе языка
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | prewarm-core | - | ✅ Done | 3/3 | [PHASE_01__prewarm-core.md](PHASE_01__prewarm-core.md) |
| 02 | settings-trigger | 01 | ✅ Done | 2/2 | [PHASE_02__settings-trigger.md](PHASE_02__settings-trigger.md) |
| 03 | settings-ui-status | 02 | ✅ Done | 3/3 | [PHASE_03__settings-ui-status.md](PHASE_03__settings-ui-status.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved. No blockers.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed - new use case).
- [ ] `/spec-check S0334` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0334`.

---

## Blockers Log

- 2026-06-03 - Device verification not completed in Codex session: `scripts/devtest/device-ready.ps1` returned `FAIL (2) - no online device`, and mobile-mcp is not available in the current toolset. Strategic status remains `BlockNeedUserTest`.

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-03 - Post-audit hardening completed: domain prewarm no longer depends on UI `TranslationManager`; flavor availability is provided through `@Multibinds` plus `src/translationEnabled/java`; `lite` and `photos` build with the empty no-op marker set.
