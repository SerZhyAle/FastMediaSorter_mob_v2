# Tactical Plan: S0321 - text-editor-calculator-integration

**Strategic spec:** [`../S0321_text-editor-calculator-integration.md`](../S0321_text-editor-calculator-integration.md)
**Feature:** Text editor calculator integration
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-31

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | calculator-result-contract | - | ✅ Done | 3/3 | [PHASE_01__calculator-result-contract.md](PHASE_01__calculator-result-contract.md) |
| 02 | editor-overflow-entry | 01 | ✅ Done | 3/3 | [PHASE_02__editor-overflow-entry.md](PHASE_02__editor-overflow-entry.md) |
| 03 | player-round-trip | 02 | ✅ Done | 3/3 | [PHASE_03__player-round-trip.md](PHASE_03__player-round-trip.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers. Strategic §6 owner-input questions are resolved.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified non-temp file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] `/spec-check S0321` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0321`.

---

## Blockers Log

- 2026-05-31 - No blockers.

---

## Change Log

- 2026-05-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-31 - Implementation completed by `/spec-dev`; standard debug build and targeted calculator unit test passed.
