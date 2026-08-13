# Tactical Plan: S1543 - audit-overbroad-and-stale-rules

**Strategic spec:** [`../S1543_audit-overbroad-and-stale-rules.md`](../S1543_audit-overbroad-and-stale-rules.md)
**Research inputs:** [`research/01__style-rule-scope.md`](research/01__style-rule-scope.md) · [`research/02__rules-and-gates-inventory.md`](research/02__rules-and-gates-inventory.md) · [`research/03__redundancy-measurement.md`](research/03__redundancy-measurement.md)
**Feature:** Narrow the house-style rule's enforcement to its written scope; record the rule inventory
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | approval-gate-scope | - | ✅ Done | 3/3 | [PHASE_01__approval-gate-scope.md](PHASE_01__approval-gate-scope.md) |
| 02 | fixer-default-scope | 01 | ✅ Done | 2/2 | [PHASE_02__fixer-default-scope.md](PHASE_02__fixer-default-scope.md) |
| 03 | stale-process-text | 01 | ✅ Done | 4/4 | [PHASE_03__stale-process-text.md](PHASE_03__stale-process-text.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 research items are `Resolved` with an artifact under `research/`.

Strategic §2 goal 5 (every examined rule carries a written verdict) has no phase: it is delivered by [`research/02__rules-and-gates-inventory.md`](research/02__rules-and-gates-inventory.md), per §9 ADR-3, which forbids turning the inventory into a standing document. The real-work filter forbids a phase step whose primary action edits `PLAN/**`, so this is out of scope for a phase by construction, not by omission.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` - skipped: no Kotlin source changes in this ticket.
- [ ] `/spec-check S1543` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1543`.

---

## Blockers Log

- none.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
