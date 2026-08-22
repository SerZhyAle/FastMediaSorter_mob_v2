# Tactical Plan: S1290 - blockneedusertest-probe-tag-gate

**Strategic spec:** [`../S1290_blockneedusertest-probe-tag-gate.md`](../S1290_blockneedusertest-probe-tag-gate.md)
**Research inputs:** none - strategic §6 items 1-4 all Resolved inline, and ADR-1 fixes the design.
**Feature:** Reverse direction of the debug-probe invariant, enforced in the gate that already reads both sides
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 2 / 2 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | reverse-probe-check | - | ✅ Done | 3/3 | [PHASE_01__reverse-probe-check.md](PHASE_01__reverse-probe-check.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 1/1 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All four strategic §6 items are `Resolved`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin in scope.
- [ ] `/spec-check S1290` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1290`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
