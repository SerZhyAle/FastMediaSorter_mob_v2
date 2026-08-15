# Tactical Plan: S1434 - launcher-static-striped-wallpaper

**Strategic spec:** [`../S1434_launcher-static-striped-wallpaper.md`](../S1434_launcher-static-striped-wallpaper.md)
**Research inputs:** none
**Feature:** Launcher wallpaper - frozen branded frame ("Striped wallpaper")
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 5 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | frozen-frame-entry-point | - | ⬜ Not started | 0/4 | [PHASE_01__frozen-frame-entry-point.md](PHASE_01__frozen-frame-entry-point.md) |
| 02 | wallpaper-mode-and-render | 01 | ⬜ Not started | 0/4 | [PHASE_02__wallpaper-mode-and-render.md](PHASE_02__wallpaper-mode-and-render.md) |
| 03 | settings-row-option | 02 | ⬜ Not started | 0/2 | [PHASE_03__settings-row-option.md](PHASE_03__settings-row-option.md) |
| 04 | mapper-unit-test | 02 | ⬜ Not started | 0/1 | [PHASE_04__mapper-unit-test.md](PHASE_04__mapper-unit-test.md) |
| 05 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 records no open research items; the three candidate questions are resolved in §3.3.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 mandates only the `docs/ALL_FEATURES.jsonl` record, and the showcase is `/skill-release`-owned.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1434` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1434`.

---

## Blockers Log

- 2026-08-07 - Plan authored by a session that then stood down: a concurrent session (pid 43180) was already implementing this ticket, holding a place in the CODE.LOCK queue for the shared `AppSettings` token and having advanced the catalog status to `In Progress` at 10:24. Reconcile this plan against whatever that session landed before starting any phase.

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
