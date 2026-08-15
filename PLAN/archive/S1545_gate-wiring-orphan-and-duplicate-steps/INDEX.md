# Tactical Plan: S1545 - gate-wiring-orphan-and-duplicate-steps

**Strategic spec:** [`../S1545_gate-wiring-orphan-and-duplicate-steps.md`](../S1545_gate-wiring-orphan-and-duplicate-steps.md)
**Research inputs:** [`research/01__closure-gate-coverage.md`](research/01__closure-gate-coverage.md), [`research/02__rule-diagnostics.md`](research/02__rule-diagnostics.md), [`research/03__doc-icon-gate-routing.md`](research/03__doc-icon-gate-routing.md)
**Feature:** Closure gate routing without duplicate lexical checks
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | lexical-dispatch | - | ✅ Done | 3/3 | [PHASE_01__lexical-dispatch.md](PHASE_01__lexical-dispatch.md) |
| 02 | doc-icon-routing | 01 | ✅ Done | 3/3 | [PHASE_02__doc-icon-routing.md](PHASE_02__doc-icon-routing.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** closure-gate coverage resolved in `research/01__closure-gate-coverage.md`.
- [x] **Research:** rule diagnostics resolved in `research/02__rule-diagnostics.md`.
- [x] **Research:** document-icon routing resolved in `research/03__doc-icon-gate-routing.md`.

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - no update; strategic §8 excludes user-facing changes.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` - no Kotlin public API changed.
- [x] `/spec-check S1545` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1545`.

## Blockers Log

- None.

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
