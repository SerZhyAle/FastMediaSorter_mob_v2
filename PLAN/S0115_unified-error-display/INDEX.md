# Tactical Plan: S0115 — unified-error-display

**Strategic spec:** [`../S0115_unified-error-display.md`](../S0115_unified-error-display.md)
**Feature:** Unified error display — colored severity toasts + enhanced detail dialog
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 5 done
**Last updated:** 2026-05-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | error-severity-model | — | ⬜ Not started | 0/4 | [PHASE_01__error-severity-model.md](PHASE_01__error-severity-model.md) |
| 02 | enhanced-error-dialog | 01 | ⬜ Not started | 0/5 | [PHASE_02__enhanced-error-dialog.md](PHASE_02__enhanced-error-dialog.md) |
| 03 | browse-player-wiring | 01, 02 | ⬜ Not started | 0/3 | [PHASE_03__browse-player-wiring.md](PHASE_03__browse-player-wiring.md) |
| 04 | addresource-main-wiring | 01, 02 | ⬜ Not started | 0/3 | [PHASE_04__addresource-main-wiring.md](PHASE_04__addresource-main-wiring.md) |
| 05 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Both §6 research items from the strategic spec are resolved by ADR before phase execution. No blockers remain.

- [x] **Research §6.1:** Toast on API 33+ → resolved by ADR-1: Snackbar on all API levels using `Activity.window.decorView`.
- [x] **Research §6.2:** Save-to-file directory → resolved: MediaStore Downloads on API 29+; `Environment.DIRECTORY_DOWNLOADS` + `WRITE_EXTERNAL_STORAGE` (declared `maxSdkVersion="28"`) on API 26–28.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8 for exact copy).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after new Kotlin files are added.
- [ ] `/spec-check S0115` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0115`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-08 — Initial tactical plan authored by `/spec-tech`.
