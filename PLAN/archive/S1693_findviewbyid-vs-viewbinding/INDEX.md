# Tactical Plan: S1693 - findviewbyid-vs-viewbinding

**Strategic spec:** [`../S1693_findviewbyid-vs-viewbinding.md`](../S1693_findviewbyid-vs-viewbinding.md)
**Research inputs:** [`research/01__call-site-classification.md`](research/01__call-site-classification.md)
**Feature:** Stop findViewById growth (ratchet gate) and clean the mixed-style class-B calls
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 40
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | findviewbyid-ratchet-rule | - | ✅ Done | 2/2 | [PHASE_01__findviewbyid-ratchet-rule.md](PHASE_01__findviewbyid-ratchet-rule.md) |
| 02 | class-b-conversion | 01 | ✅ Done | 3/3 | [PHASE_02__class-b-conversion.md](PHASE_02__class-b-conversion.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

No UI surface changes: conversions swap the access path to the same views; no placement decision
needed (S1338 gate not triggered - no layout, no new UI element).

---

## Pre-Implementation Blockers

None - both strategic §6 items are Resolved (2026-08-21 research).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*` untouched - strategic §8 is "Без изменений"; internal quality work, no
      ALL_FEATURES record (`-SkipFuncLog`).
- [ ] `dev/CHANGELOG.md` has entry for the change set.
- [ ] `/spec-check S1693` returns `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: run `/spec-check S1693`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech` (inline, /spec-all pipeline).
