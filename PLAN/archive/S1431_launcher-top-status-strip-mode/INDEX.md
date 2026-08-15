# Tactical Plan: S1431 - launcher-top-status-strip-mode

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Research inputs:** [`research/01__current-surfaces.md`](research/01__current-surfaces.md)
**Feature:** Launcher top status strip mode - clock left, device indicators right, tray leaves the taskbar
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-foundation | - | ✅ Done | 4/4 | [PHASE_01__settings-foundation.md](PHASE_01__settings-foundation.md) |
| 02 | indicator-placement-seam | 01 | ✅ Done | 5/5 | [PHASE_02__indicator-placement-seam.md](PHASE_02__indicator-placement-seam.md) |
| 03 | strip-zone-contract | 02 | ✅ Done | 5/5 | [PHASE_03__strip-zone-contract.md](PHASE_03__strip-zone-contract.md) |
| 04 | strip-mode-wiring | 01, 02, 03 | ✅ Done | 5/5 | [PHASE_04__strip-mode-wiring.md](PHASE_04__strip-mode-wiring.md) |
| 05 | taskbar-tray-and-recents | 01 | ✅ Done | 4/4 | [PHASE_05__taskbar-tray-and-recents.md](PHASE_05__taskbar-tray-and-recents.md) |
| 06 | settings-ui | 01, 05 | ✅ Done | 5/5 | [PHASE_06__settings-ui.md](PHASE_06__settings-ui.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 carries no Open research item - the three layout questions were ruled on by the owner
2026-08-09 (strategic §4.3) and the seconds-clock cost question was closed against the platform API
(research 01 §2). No blocker.

---

## Ordering Notes

- Phase 02 is a behaviour-preserving refactor: after it the taskbar tray must look and act exactly as
  before, only its renderer is no longer typed to the taskbar layout. Treat any visible change there as
  a phase-02 defect, not as S1431 scope.
- Phases 04 and 05 land the mode's two halves while no switch exists to turn it on. That is deliberate:
  the mode stays unreachable until phase 06, so a half-built mode can never appear on a user's home
  screen mid-implementation.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] Strategic §8's capability recorded in `docs/ALL_FEATURES.jsonl`. `docs/FEATURES*.md` is NOT edited
  here: CLAUDE.md section 11 reserves the showcase for `/skill-release`, which generates it from the
  inventory diff. Corrected during execution - see phase 07 step 07.3.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1431` returns `Verified`.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1431`.

---

## Blockers Log

- 2026-08-09 - `LauncherHomeActivity.kt` carries pre-existing un-baselined detekt debt (`LargeClass`, `CyclomaticComplexMethod`) that fails the diff-scoped gate for every phase touching it. Proven older than this ticket, parked as S1541. Phases 04-06 close with the same residual rather than absorbing S1541.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
