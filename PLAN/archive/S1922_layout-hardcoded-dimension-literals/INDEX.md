# Tactical Plan: S1922 - layout-hardcoded-dimension-literals

**Strategic spec:** [`../S1922_layout-hardcoded-dimension-literals.md`](../S1922_layout-hardcoded-dimension-literals.md)
**Research inputs:** none - both §6 items were answered from the rule registry and a re-count of the tree; findings are in §1, §4 and the ADRs.
**Feature:** layout dimension-literal ratchet
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dimen-ratchet-rule | - | ✅ Done | 4/4 | [PHASE_01__dimen-ratchet-rule.md](PHASE_01__dimen-ratchet-rule.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 items are Resolved before Phase 01 starts.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES."
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin touched.
- [x] `/spec-check S1922` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1922`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech`.
