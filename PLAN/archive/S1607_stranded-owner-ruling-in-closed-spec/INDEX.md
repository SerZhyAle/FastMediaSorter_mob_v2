# Tactical Plan: S1607 - stranded-owner-ruling-in-closed-spec

**Strategic spec:** [`../S1607_stranded-owner-ruling-in-closed-spec.md`](../S1607_stranded-owner-ruling-in-closed-spec.md)
**Research inputs:** none
**Feature:** Closing contract for strategic section 6 open items
**Tier:** not set
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-13

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | open-items-checker | - | ✅ Done | 2/2 | [PHASE_01__open-items-checker.md](PHASE_01__open-items-checker.md) |
| 02 | shared-close-gate | 01 | ✅ Done | 4/4 | [PHASE_02__shared-close-gate.md](PHASE_02__shared-close-gate.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No strategic section 6 item is `Open`. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic section 8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - skipped: no Kotlin touched, no public API changed.
- [ ] `/spec-check S1607` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1607`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-13 - Initial tactical plan authored by `/spec-tech`.
