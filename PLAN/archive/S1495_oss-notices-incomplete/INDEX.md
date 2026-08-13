# Tactical Plan: S1495 - oss-notices-incomplete

**Strategic spec:** [`../S1495_oss-notices-incomplete.md`](../S1495_oss-notices-incomplete.md)
**Research inputs:** [`research/current-state.md`](research/current-state.md)
**Feature:** OSS notices generated from the build files
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 70
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-10

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dependency-parser | - | ✅ Done | 3/3 | [PHASE_01__dependency-parser.md](PHASE_01__dependency-parser.md) |
| 02 | licence-manifest | 01 | ✅ Done | 3/3 | [PHASE_02__licence-manifest.md](PHASE_02__licence-manifest.md) |
| 03 | notices-generator | 01, 02 | ✅ Done | 4/4 | [PHASE_03__notices-generator.md](PHASE_03__notices-generator.md) |
| 04 | gate-and-legal-doc | 03 | ✅ Done | 3/3 | [PHASE_04__gate-and-legal-doc.md](PHASE_04__gate-and-legal-doc.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All four strategic §6 items are Resolved (owner ruling, 2026-08-10).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states the ticket adds no capability.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin touched.
- [ ] `/spec-check S1495` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1495`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-10 - Initial tactical plan authored by `/spec-tech`.
