# Tactical Plan: S0483 - crash-report-email-button

**Strategic spec:** [`../S0483_crash-report-email-button.md`](../S0483_crash-report-email-button.md)
**Research inputs:** [`research/01__crash-vs-info-gate.md`](research/01__crash-vs-info-gate.md) · [`research/02__email-attachment-delivery.md`](research/02__email-attachment-delivery.md)
**Feature:** Кнопка «Отправить краш-репорт автору» в диалоге об ошибке
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 1/1 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | email-attachment-capability | - | ✅ Done | 3/3 | [PHASE_02__email-attachment-capability.md](PHASE_02__email-attachment-capability.md) |
| 03 | dialog-report-button | 01, 02 | ✅ Done | 4/4 | [PHASE_03__dialog-report-button.md](PHASE_03__dialog-report-button.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items 1 and 2 are Resolved (see `research/`). §6 item 3 (report path for uncaught release crashes) is explicitly out-of-scope future work, not a prerequisite for any phase here.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `ScrollableTextDialog`, `SupportIntentFactory`, `LogExportHelper` changed).
- [ ] `/spec-check S0483` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to the matching `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0483`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
