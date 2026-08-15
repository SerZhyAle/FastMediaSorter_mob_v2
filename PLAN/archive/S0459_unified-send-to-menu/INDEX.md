# Tactical Plan: S0459 - unified-send-to-menu

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Research inputs:** [`research/01__surface-audit.md`](research/01__surface-audit.md) · [`research/02__type-applicability-model.md`](research/02__type-applicability-model.md) · [`research/03__bottomsheet-tv-focus.md`](research/03__bottomsheet-tv-focus.md) · [`research/04__messenger-recipient-feasibility.md`](research/04__messenger-recipient-feasibility.md) · [`research/05__email-send-action.md`](research/05__email-send-action.md)
**Feature:** Unified «Send to..» menu
**Tier:** 2 - Significant (epic)
**Priority:** 55
**Status:** In Progress (all code phases done; on-device BlockNeedUserTest sweep pending)
**Phases:** 8 / 8 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-model | - | ✅ Done | 4/4 | [PHASE_01__foundation-model.md](PHASE_01__foundation-model.md) |
| 02 | new-receiver-handlers | 01 | ✅ Done | 4/4 | [PHASE_02__new-receiver-handlers.md](PHASE_02__new-receiver-handlers.md) |
| 03 | registry-population | 01, 02 | ✅ Done | 4/4 | [PHASE_03__registry-population.md](PHASE_03__registry-population.md) |
| 04 | unified-menu-ui | 01, 03 | ✅ Done | 6/6 | [PHASE_04__unified-menu-ui.md](PHASE_04__unified-menu-ui.md) |
| 05 | consolidate-player | 04 | ✅ Done | 4/4 | [PHASE_05__consolidate-player.md](PHASE_05__consolidate-player.md) |
| 06 | consolidate-standalone | 04 | ✅ Done | 3/3 | [PHASE_06__consolidate-standalone.md](PHASE_06__consolidate-standalone.md) |
| 07 | consolidate-browse | 04 | ✅ Done | 3/3 | [PHASE_07__consolidate-browse.md](PHASE_07__consolidate-browse.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (artifacts under `research/`). No research blockers - Phase 01 may start.

Phase 05 carries a cross-ticket prerequisite (not a research blocker): S0431 and S0362 (ad-hoc Keep entry points being re-homed) must reach `Verified` before their ad-hoc commands are removed. See Phase 05 Prerequisites.

**Gate resolution (2026-06-17):** owner WAIVED the S0431/S0362 Keep re-home gate (S0362 is Archived; S0431 remains BlockNeedUserTest). Keep-text/Keep-drawing were re-homed into the unified menu in this pass; the standalone-text ad-hoc Keep overflow item was removed. S0431's own device-test cycle is independent.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).
- [ ] `/spec-check S0459` returns `Verified` (run after on-device sweep).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0459`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-17 - Post-audit remediation pass (4 parallel auditors over phases 01-08). Closed 5 CRITICAL + 5 MAJOR + minors that the first pass left:
  - ADR-2/ADR-8: overflow now builds a native `addSubMenu` (`CommandPanelController` + new `onSendToOverflowSubMenuRequested`); `buildOverflowSubMenu` was dead before.
  - Consolidation leftovers folded: in-app Office share (`PlayerShareManager`) and draw-editor Keep (`ImageDrawOverlayManager`/`DrawKeepExportHelper`) now route through the unified menu; `draw_overflow_keep` item + strings removed.
  - ADR-10: Print gated at menu-build via `ShareTargetHandler.isSupportedBy`; `PlayerActivity` implements `SharePrintHost` (no more silent no-op on non-print hosts).
  - Lens `<queries>` added (API 30+ visibility); ADR-4 Instagram `batchCapable=false`; send-error toast + try/catch in `SendToMenuManager.dispatch`; §2 ticket id stripped from 3 permanent `Timber.w`.
  - Dead-weight: orphan `menu_google_lens` hidden in `StandalonePlayerActivity`; dead `IsShareTargetEnabledUseCase` dep removed; `GoogleKeepAvailabilityChecker.isKeepAvailable` removed; `GoogleLensShare` catches narrowed.
  - Parked separable debt: S0467 (deprecated PackageManager int-flag APIs). StandalonePlayerActivity full removal stays under S0393.
  - Gates: `.\a.ps1 fk` BUILD SUCCESSFUL; neuroslop delta 0; ticket-log actual 0; localization EN/RU/UK parity OK. Spec stays BlockNeedUserTest for the on-device sweep.
- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-17 - Phases 05-08 executed; Phase 04 review findings fixed (batchCapable model field, app-resolved labels in both menu presentations, ADR-9 frequency order, bottom-sheet multi-file hint + first-file scoping). S0431 Keep gate waived by owner. §11.7 audit drove extra consolidation (browse Lens fold, standalone overflow folds) + dead-code removal (orphaned player share cluster, dead Telegram/Keep helpers). All 8 phases ✅ Done; spec stays BlockNeedUserTest pending one on-device sweep across player/standalone/browse/editors.
