# Tactical Plan: S1365 - image-player-draw-edit-distinction

**Strategic spec:** [`../S1365_image-player-draw-edit-distinction.md`](../S1365_image-player-draw-edit-distinction.md)
**Research inputs:** none - strategic §4.1 and §5.1 carry the research findings inline
**Feature:** Image player draw and edit command distinction
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | label-strings | - | ✅ Done | 3/3 | [PHASE_01__label-strings.md](PHASE_01__label-strings.md) |
| 02 | label-wiring | 01 | ✅ Done | 5/5 | [PHASE_02__label-wiring.md](PHASE_02__label-wiring.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are `Resolved`.

---

## UI decision record (self-check 5.5)

Phase 02 touches `ui/**` classes and `res/layout*`, so the placement decision must be on record before it is written. It is:

- **Wording** - owner ruling, strategic §6 "Quiz decisions (2026-08-05)", quoted verbatim there: «Рисование» / «Коррекция» / «Текст файла», long variants rejected.
- **Placement** - unchanged by construction. Strategic §2 Non-goals assigns menu regrouping to S1364; no item moves, appears, or disappears in this ticket. Every layout edit is a `contentDescription` value swap on an existing button, never a position, size, or visibility change.
- **PDF wording** - agent decision, not the owner's, recorded as such in strategic §6 "Resolved without asking". It reuses the existing translated `pdf_edit_title` rather than inventing a phrase.

---

## Out of scope, recorded

- `PlayerCommand.shortTitleResId` is declared but read nowhere; ~25 `big_btn_short_*` string keys are dead weight. Ticketed separately as **S1451**.
- Menu regrouping - **S1364**. Edit-dialog layout - **S1366**.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1365` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1365`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
