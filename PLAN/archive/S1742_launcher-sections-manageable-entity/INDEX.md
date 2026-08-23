# Tactical Plan: S1742 - launcher-sections-manageable-entity

**Strategic spec:** [`../S1742_launcher-sections-manageable-entity.md`](../S1742_launcher-sections-manageable-entity.md)
**Research inputs:** [`research/01__sections-as-entity.md`](research/01__sections-as-entity.md)
**Feature:** Desktop sections a user can create, rename, move and delete
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-08-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | user-section-identity-and-label | - | ✅ Done | 3/3 | [PHASE_01__user-section-identity-and-label.md](PHASE_01__user-section-identity-and-label.md) |
| 02 | create-section-from-picker | 01 | ✅ Done | 2/2 | [PHASE_02__create-section-from-picker.md](PHASE_02__create-section-from-picker.md) |
| 03 | section-actions-sheet-and-gestures | 01 | ✅ Done | 3/3 | [PHASE_03__section-actions-sheet-and-gestures.md](PHASE_03__section-actions-sheet-and-gestures.md) |
| 04 | block-move-and-delete-cleanup | 03 | ✅ Done | 3/3 | [PHASE_04__block-move-and-delete-cleanup.md](PHASE_04__block-move-and-delete-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] Every research item in strategic §6 is Resolved (research/01, 2026-08-18).
- [x] Every owner UI decision in strategic §6 items 5-9 is Resolved.

---

## Ordering rationale

Phase 01 comes first because it is the one the research turned up as a hard prerequisite: a section key outside the code catalogue resolves to no visual at all, and the label override is applied only on top of a resolved one. Until that is fixed, a created section renders "unavailable" whatever it is named - so creation (02) and renaming (03) would both look broken if built first.

Phases 02 and 03 are independent of each other and both depend only on 01. Phase 04 needs the actions sheet from 03 to have somewhere to put move and delete.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1742` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.
5. All done: run `/spec-check S1742`.

---

## Blockers Log

- 2026-08-18 - Phase 03 carries a layout-evidence criterion (UI phase gate) that needs a screenshot of
  the header and the actions sheet. Two devices are attached and neither is selectable without an
  explicit device id, so `device-ready` reports `multiple-devices`. The phase's code can be written
  before that; the phase cannot flip to Done until the shot exists.

---

## Change Log

- 2026-08-18 - Initial tactical plan authored from research/01.
