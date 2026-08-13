# Tactical Plan: S0335 - settings-system-info-dialog

**Strategic spec:** [`../S0335_settings-system-info-dialog.md`](../S0335_settings-system-info-dialog.md)
**Feature:** Кнопка «System info» в настройках Common
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | system-info-collector | - | ✅ Done | 2/2 | [PHASE_01__system-info-collector.md](PHASE_01__system-info-collector.md) |
| 02 | settings-button-ui | 01 | ✅ Done | 3/3 | [PHASE_02__settings-button-ui.md](PHASE_02__settings-button-ui.md) |
| 03 | wire-and-show | 02 | ✅ Done | 2/2 | [PHASE_03__wire-and-show.md](PHASE_03__wire-and-show.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The three strategic §6 research items are tactical design choices, resolved here without external research:

- §6.1 (collection layer) → dedicated `GatherSystemInfoUseCase` in `domain/usecase` (constructor-injected, testable, mirrors `GetDeviceStorageUseCase`).
- §6.2 (field set) → strategic §11 minimum set only; "basic app settings" body inclusion deferred (v1 non-goal).
- §6.3 (label language) → body field labels are fixed English; only the button label and dialog title are localized EN/RU/UK.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a new-feature sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public use case class).
- [ ] `/spec-check S0335` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0335`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.
