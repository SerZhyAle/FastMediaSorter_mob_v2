# Tactical Plan: S0234 — google-account-card-error-ui

**Strategic spec:** [`../S0234_google-account-card-error-ui.md`](../S0234_google-account-card-error-ui.md)
**Feature:** Surface Google Drive sign-in errors on the Settings card
**Tier:** UI / UX bug-adjacent improvement
**Priority:** 70
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-05-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | — | ✅ Done | 2/2 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | viewmodel-events | 01 | ✅ Done | 3/3 | [PHASE_02__viewmodel-events.md](PHASE_02__viewmodel-events.md) |
| 03 | helper-render-and-dialog | 02 | ✅ Done | 4/4 | [PHASE_03__helper-render-and-dialog.md](PHASE_03__helper-render-and-dialog.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all §6 questions resolved in strategic spec (Decisions D1, D2, D3).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — SKIP (strategic §8 is bug-adjacent UX improvement, not a new feature).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin file changes.
- [ ] `/spec-check S0234` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0234`.

---

## Blockers Log

- 2026-05-17 — none.

---

## Change Log

- 2026-05-17 — Initial tactical plan authored by `/spec-tech`.
