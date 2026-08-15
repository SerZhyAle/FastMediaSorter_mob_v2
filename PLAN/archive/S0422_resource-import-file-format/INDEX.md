# Tactical Plan: S0422 - resource-import-file-format

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Research inputs:** [`research/01__file-association.md`](research/01__file-association.md)
**Feature:** Resource import/export file format
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 5 / 6 done
**Last updated:** 2026-06-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | format-and-export | - | ✅ Done | 3/3 | [PHASE_01__format-and-export.md](PHASE_01__format-and-export.md) |
| 02 | import-from-file | 01 | ✅ Done | 3/3 | [PHASE_02__import-from-file.md](PHASE_02__import-from-file.md) |
| 03 | settings-ui | 01, 02 | ✅ Done | 4/4 | [PHASE_03__settings-ui.md](PHASE_03__settings-ui.md) |
| 04 | per-resource-export | 01 | ✅ Done | 3/3 | [PHASE_04__per-resource-export.md](PHASE_04__per-resource-export.md) |
| 05 | file-association | 01, 02 | ✅ Done | 2/2 | [PHASE_05__file-association.md](PHASE_05__file-association.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. The single §6 research item is Resolved (see `research/01__file-association.md`).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).
- [ ] `/spec-check S0422` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0422`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-15 - Initial tactical plan authored by `/spec-tech`.
