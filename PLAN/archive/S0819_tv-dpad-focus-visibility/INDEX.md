# Tactical Plan: S0819 - tv-dpad-focus-visibility

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Research inputs:** [`research/01__current-focus-infrastructure.md`](research/01__current-focus-infrastructure.md)
**Feature:** App-wide travelling focus frame for D-pad / TV / gamepad
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** BlockNeedUserTest (player D-pad focus - on-device verification)
**Phases:** 5 / 6 done
**Last updated:** 2026-07-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Architecture summary (one screen)

- `FocusFrameOverlay` is a `Drawable` added to `window.decorView.overlay` - draws one animated rounded accent-coloured border around a target `Rect`. It is not part of layout and cannot intercept touch (closes §7 input-interception risk).
- `FocusFrameController` attaches one overlay per `Window`, listens to `ViewTreeObserver.OnGlobalFocusChangeListener` + `addOnTouchModeChangeListener`, repositions via `OnPreDrawListener`, hides in touch mode.
- `FocusFrameActivityCallbacks` (registered once in `FastMediaSorterApp`) attaches a controller to every Activity window - zero per-Activity edits, covers all 26 `BaseActivity` subclasses AND direct `AppCompatActivity` screens.
- `FocusFrameFragmentCallbacks` extends coverage to `DialogFragment` / bottom-sheet windows.
- Frame colour resolves `?attr/colorPrimary` -> correct per accent theme automatically.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 4/4 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | app-wide-attach | 01 | ✅ Done | 3/3 | [PHASE_02__app-wide-attach.md](PHASE_02__app-wide-attach.md) |
| 03 | dialog-coverage | 01, 02 | ✅ Done | 3/3 | [PHASE_03__dialog-coverage.md](PHASE_03__dialog-coverage.md) |
| 04 | reconcile-existing | 01, 02 | ✅ Done | 2/2 | [PHASE_04__reconcile-existing.md](PHASE_04__reconcile-existing.md) |
| 05 | player-focus-nav | 01, 02 | ✅ Done | 5/5 | [PHASE_05__player-focus-nav.md](PHASE_05__player-focus-nav.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All strategic §6 items resolved to design decisions (dialog attach = FragmentLifecycleCallbacks; player transport relocation = existing on-screen buttons; VR excluded; existing focus mechanics reconciled in Phase 04). Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - NOT edited here (owned by `/skill-release`). Capability recorded in `docs/ALL_FEATURES.jsonl` by `/spec-dev` on `Implemented`.
- [ ] `dev/CHANGELOG.md` has an entry for every logical change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S0819` returns `Verified` (after device test).
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`, update `Phases: X/6 done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes.
3. On phase completion: confirm every step `[x]` and the Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a Blockers Log bullet; set journal status if the whole spec is blocked.
5. Player phase ends in on-device verification -> status `BlockNeedUserTest`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-01 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-01 - Phase 05 (player-focus-nav) completed via `/spec-dev`: freed bare D-pad keys in `default_bindings.json`; made both `custom_player_controls*.xml` transport rows focusable with the S0289 stroke + nextFocus; gated player controls auto-hide off in non-touch mode; fixed `getInitialFocusView` to show the overlay and focus a visible transport control; inserted the two S0819 device-test probes. Spec -> `BlockNeedUserTest`. Phase 06 (docs/catalog cleanup) remains.
