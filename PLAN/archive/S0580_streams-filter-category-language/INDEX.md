# Tactical Plan: S0580 - streams-filter-category-language

**Strategic spec:** [`../S0580_streams-filter-category-language.md`](../S0580_streams-filter-category-language.md)
**Research inputs:** [`research/01__catalog-language-format.md`](research/01__catalog-language-format.md)
**Feature:** Фильтр трансляций по категории и/или языку
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | filter-model | - | ✅ Done | 4/4 | [PHASE_01__filter-model.md](PHASE_01__filter-model.md) |
| 02 | searchable-picker | - | ✅ Done | 4/4 | [PHASE_02__searchable-picker.md](PHASE_02__searchable-picker.md) |
| 03 | filter-strings | - | ✅ Done | 1/1 | [PHASE_03__filter-strings.md](PHASE_03__filter-strings.md) |
| 04 | filter-ui | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__filter-ui.md](PHASE_04__filter-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **UI clarification (strategic §6.5):** RESOLVED 2026-06-21 (`/ui-clarify`, owner) - single dialog from the filter button (category row + language row + AND/OR toggle + Clear); active state via a non-color marker on the filter button + Clear in dialog; no chip row; marker is a runtime icon swap so activity_streams layout (portrait/landscape) is not edited.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 contains a FEATURES sentence (strategic §8 DOES mandate one; populated by `/skill-release` from the `ALL_FEATURES` diff, not hand-edited per-spec).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0580` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0580`.

---

## Blockers Log

- 2026-06-21 - Phase 04 gated on strategic §6.5 (UI surface). Next: run `/ui-clarify S0580`, record the chosen layout in strategic §5/§6.5, then implement Phase 04.

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-tech`.
