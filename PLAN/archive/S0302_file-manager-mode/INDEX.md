# Tactical Plan: S0302 - file-manager-mode

**Strategic spec:** [`../S0302_file-manager-mode.md`](../S0302_file-manager-mode.md)
**Feature:** File Manager Mode
**Tier:** 4 - Strategic, ad-hoc
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ui-terminology | - | ✅ Done | 3/3 | [PHASE_01__ui-terminology.md](PHASE_01__ui-terminology.md) |
| 02 | manifest-integration | 01 | ✅ Done | 1/1 | [PHASE_02__manifest-integration.md](PHASE_02__manifest-integration.md) |
| 03 | browse-ux | 02 | ✅ Done | 2/2 | [PHASE_03__browse-ux.md](PHASE_03__browse-ux.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Terminology mapping and resource layout verification completed in Strategy Approval Gate.

---

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `docs/FAQ.md` + `README.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0302` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0302`.

---

## Blockers Log

*None.*

---

## Change Log

- 2026-05-30 - Initial tactical plan authored by `/spec-tech`.
