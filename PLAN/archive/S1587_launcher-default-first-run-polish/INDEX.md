# Tactical Plan: S1587 - launcher-default-first-run-polish

**Strategic spec:** [`../S1587_launcher-default-first-run-polish.md`](../S1587_launcher-default-first-run-polish.md)
**Research inputs:** [`research/01__group-packing-width.md`](research/01__group-packing-width.md), [`research/02__first-screen-order.md`](research/02__first-screen-order.md)
**Feature:** Launcher default desktop, first run on a phone
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | section-aware-seed | - | ✅ Done | 4/4 | [PHASE_01__section-aware-seed.md](PHASE_01__section-aware-seed.md) |
| 02 | cell-label-legibility | 01 | ✅ Done | 3/3 | [PHASE_02__cell-label-legibility.md](PHASE_02__cell-label-legibility.md) |
| 03 | gadget-first-run-cards | 01 | ✅ Done | 4/4 | [PHASE_03__gadget-first-run-cards.md](PHASE_03__gadget-first-run-cards.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - both strategic §6 items are Resolved, and the placement decisions are recorded as an owner ruling in strategic §3.3 (2026-08-12).

Coverage notes - strategic §1 observations deliberately left unplanned:

- Defect 9 (own-app icon drawn as a cropped red shape) - not a defect: the screenshots are a debug build, which carries its own icon under `app_v2/src/debug/res/mipmap-*`.
- Defect 11 (taskbar carries only Start and All apps) - out of scope: the recents and pinned strips are empty because nothing has been launched or pinned yet, which is the correct first-run state.
- Defect 12 (the desktop is two screens tall) - out of scope: strategic §2.3 targets what the first screen shows, not the total height; the desktop is a one-screen-plus-scroll surface by design (S1209).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 routes the capability record to `docs/ALL_FEATURES.jsonl` as CHANGE.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1587` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1587`.

---

## Blockers Log

- none.

---

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-tech`.
