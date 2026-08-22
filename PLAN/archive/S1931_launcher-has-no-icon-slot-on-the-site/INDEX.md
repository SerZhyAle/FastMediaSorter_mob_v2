# Tactical Plan: S1931 - launcher-has-no-icon-slot-on-the-site

**Strategic spec:** [`../S1931_launcher-has-no-icon-slot-on-the-site.md`](../S1931_launcher-has-no-icon-slot-on-the-site.md)
**Research inputs:** none - strategic §4 carries the mechanics reading (2026-08-21) and its `/spec-quiz` refinement (2026-08-22).
**Feature:** Launcher mode gets its own slot on the site - a landing card and its own guide page
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-22

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | guide-pages | - | ✅ Done | 3/3 | [PHASE_01__guide-pages.md](PHASE_01__guide-pages.md) |
| 02 | landing-card | 01 | ✅ Done | 4/4 | [PHASE_02__landing-card.md](PHASE_02__landing-card.md) |
| 03 | guide-index-rows | 01, 02 | ✅ Done | 3/3 | [PHASE_03__guide-index-rows.md](PHASE_03__guide-index-rows.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6.1 is `Resolved` - the owner ruled on 2026-08-22 that the launcher gets both slots and that the guide is its own page.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений", the capability already exists and only its visibility changes.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required - no Kotlin touched.
- [ ] `/spec-check S1931` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1931`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-22 - Phases 03 and 04 executed: guide-index rows in three locales, howto icon map entry, registry regeneration, icon gate PASS.
