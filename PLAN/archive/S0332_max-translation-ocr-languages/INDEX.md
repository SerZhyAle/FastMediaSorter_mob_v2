# Tactical Plan: S0332 - max-translation-ocr-languages

**Strategic spec:** [`../S0332_max-translation-ocr-languages.md`](../S0332_max-translation-ocr-languages.md)
**Feature:** Max translation and OCR languages expansion
**Tier:** 4 - Strategic
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 2/2 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | ui-picker | 01 | ✅ Done | 2/2 | [PHASE_02__ui-picker.md](PHASE_02__ui-picker.md) |
| 03 | integration | 02 | ✅ Done | 3/3 | [PHASE_03__integration.md](PHASE_03__integration.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 1/1 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Final list of languages to support - resolved. Whitelist includes 59 ML Kit languages mapped to ISO country flags.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0332>`.

---

## Blockers Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.
