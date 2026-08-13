# Tactical Plan: S1392 - bugfix-flavor-matrix-docs-contradict-gates

**Strategic spec:** [`../S1392_bugfix-flavor-matrix-docs-contradict-gates.md`](../S1392_bugfix-flavor-matrix-docs-contradict-gates.md)
**Research inputs:** [`research/01__flavor-matrix-surface-inventory.md`](research/01__flavor-matrix-surface-inventory.md)
**Feature:** Flavor capability matrix - one generated source of truth plus a docs conformance gate
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-04

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | matrix-snapshot-generator | - | ✅ Done | 4/4 | [PHASE_01__matrix-snapshot-generator.md](PHASE_01__matrix-snapshot-generator.md) |
| 02 | docs-conformance-gate | 01 | ✅ Done | 5/5 | [PHASE_02__docs-conformance-gate.md](PHASE_02__docs-conformance-gate.md) |
| 03 | structured-tables-fix | 02 | ✅ Done | 5/5 | [PHASE_03__structured-tables-fix.md](PHASE_03__structured-tables-fix.md) |
| 04 | prose-sweep-reference-docs | 03 | ✅ Done | 6/6 | [PHASE_04__prose-sweep-reference-docs.md](PHASE_04__prose-sweep-reference-docs.md) |
| 05 | prose-sweep-scenarios-showcase | 03 | ✅ Done | 5/5 | [PHASE_05__prose-sweep-scenarios-showcase.md](PHASE_05__prose-sweep-scenarios-showcase.md) |
| 06 | rules-persona-and-closure | 04, 05 | ✅ Done | 5/5 | [PHASE_06__rules-persona-and-closure.md](PHASE_06__rules-persona-and-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** `noLegal` column in the link-receiving matrix - required before Phase 04. See strategic §6.2. Not a Phase 01-03 blocker; resolved inside Phase 04 by comparing link-receiving gates between `noLegal` and `standard`.

Strategic §6.1 and §6.3 are Resolved and block nothing.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - flavor tags on existing entries corrected in Phase 05. No new showcase entry (strategic §8: no new capability).
- [x] `dev/CHANGELOG.md` has entry for every modified file, one entry per phase via `close-and-log.ps1 -DevLogs`.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, this ticket touches no `.kt` in `app_v2/src`.
- [x] `scripts/document_registry/generate.ps1 -Check` returns 0.
- [x] `/spec-check S1392` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1392`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-04 - Initial tactical plan authored by `/spec-all` Stage F2.



