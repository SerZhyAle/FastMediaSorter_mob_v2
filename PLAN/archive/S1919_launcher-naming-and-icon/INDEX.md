# Tactical Plan: S1919 - launcher-naming-and-icon

**Strategic spec:** [`../S1919_launcher-naming-and-icon.md`](../S1919_launcher-naming-and-icon.md)
**Research inputs:** none - every §6 item was resolved from the codebase during authoring
**Feature:** The word "лаунчер" in RU/UK texts, and a recognisable launcher icon
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | glossary-term | - | ✅ Done | 1/1 | [PHASE_01__glossary-term.md](PHASE_01__glossary-term.md) |
| 02 | app-strings | 01 | ✅ Done | 2/2 | [PHASE_02__app-strings.md](PHASE_02__app-strings.md) |
| 03 | settings-row-icon | 02 | ✅ Done | 3/3 | [PHASE_03__settings-row-icon.md](PHASE_03__settings-row-icon.md) |
| 04 | published-docs-wording | 01 | ✅ Done | 2/2 | [PHASE_04__published-docs-wording.md](PHASE_04__published-docs-wording.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

**Ordering note.** Phase 03 must follow Phase 02: the settings manifest is regenerated from the live strings, so regenerating before the RU/UK values change would capture the old titles and the gate would go red again on the next touch.

---

## Pre-Implementation Blockers

None - all three strategic §6 research items are `Resolved`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, no Kotlin touched.
- [ ] `/spec-check S1919` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1919`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech`.
