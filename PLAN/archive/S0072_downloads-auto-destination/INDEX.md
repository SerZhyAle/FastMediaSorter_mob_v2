# Tactical Plan: S0072 — downloads-auto-destination

**Strategic spec:** [`../S0072_downloads-auto-destination.md`](../S0072_downloads-auto-destination.md)
**Feature:** Auto-add Downloads folder as first destination on fresh install
**Tier:** 1 — Quick Win
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | string-and-use-case | — | ✅ Done | 4/4 | [PHASE_01__string-and-use-case.md](PHASE_01__string-and-use-case.md) |
| 02 | viewmodel-wiring | 01 | ✅ Done | 2/2 | [PHASE_02__viewmodel-wiring.md](PHASE_02__viewmodel-wiring.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 has no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (§8 bullet added to section 4).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after new use case added.
- [ ] `/spec-check S0072` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0072`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
