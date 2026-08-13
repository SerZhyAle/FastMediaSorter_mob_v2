# Tactical Plan: S1426 - welcome-permissions-status-and-density

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Research inputs:** [`research/01__row-anatomy-and-not-applicable.md`](research/01__row-anatomy-and-not-applicable.md)
**Feature:** Honest permission status and compact rows on the permissions row shared by onboarding and settings
**Tier:** ui-facing, localization-touched
**Priority:** 60
**Status:** Done - awaiting device test
**Phases:** 6 / 6 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | request-marker-store | - | ✅ Done | 4/4 | [PHASE_01__request-marker-store.md](PHASE_01__request-marker-store.md) |
| 02 | not-requested-status | 01 | ✅ Done | 4/4 | [PHASE_02__not-requested-status.md](PHASE_02__not-requested-status.md) |
| 03 | single-action-rule | 02 | ✅ Done | 5/5 | [PHASE_03__single-action-rule.md](PHASE_03__single-action-rule.md) |
| 04 | compact-row-indicator | 02, 03 | ✅ Done | 5/5 | [PHASE_04__compact-row-indicator.md](PHASE_04__compact-row-indicator.md) |
| 05 | shortened-descriptions | 04 | ✅ Done | 1/1 | [PHASE_05__shortened-descriptions.md](PHASE_05__shortened-descriptions.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 item 1 is `Resolved` - see the research artifact above.

---

## Ordering rationale

- 01 before 02: the status value cannot be computed before a marker exists to compute it from.
- 02 before 03 and 04: both the action rule and the indicator branch on the new state, so the enum and its
  producer must exist first.
- 03 before 04: the button's label follows from the action the button will perform, so the rule is settled
  before the row renders it.
- 04 before 05: the description budget is one line in the rebuilt row, and the rewritten texts are sized
  against that budget rather than against today's three-line block.
- 06 last: it re-renders generated inventories, which can only be correct once every asset and string exists.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1426` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1426`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
