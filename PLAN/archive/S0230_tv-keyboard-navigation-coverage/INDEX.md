# Tactical Plan: S0230 — tv-keyboard-navigation-coverage

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Feature:** Ревизия покрытия системы универсального ввода (8 модальностей)
**Tier:** 3 — Moderate
**Priority:** 60
**Status:** Tactical
**Phases:** 6 / 6 done (round 2 — 5 legacy phases archived under `legacy/`)
**Status (journal):** BlockNeedUserTest — awaiting device-test on TV emulator / Panasonic MX700 / BT headset / Android Auto.
**Last updated:** 2026-05-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> Legacy round 1 (Welcome D-pad + TvKeyRouter scaffolding) completed; see `legacy/INDEX-historical.md` summary below. Round 2 plan below covers the remaining audit + accessibility + cross-modality polish work after the §6 research items were resolved (2026-05-17).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | coverage-matrix | — | ✅ Done | 3/3 | [PHASE_01__coverage-matrix.md](PHASE_01__coverage-matrix.md) |
| 02 | list-focus-polish | 01 | ✅ Done | 4/4 | [PHASE_02__list-focus-polish.md](PHASE_02__list-focus-polish.md) |
| 03 | mouse-safety | 01 | ✅ Done (no fixes needed) | 3/3 | [PHASE_03__mouse-safety.md](PHASE_03__mouse-safety.md) |
| 04 | dialog-talkback-helper | 01 | ✅ Done | 4/4 | [PHASE_04__dialog-talkback-helper.md](PHASE_04__dialog-talkback-helper.md) |
| 05 | a11y-content-audit | 01 | ✅ Done (audit-only, device-test deferred) | 3/3 | [PHASE_05__a11y-content-audit.md](PHASE_05__a11y-content-audit.md) |
| 06 | docs-catalog-cleanup | 02..05 | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Legacy round 1 — historical reference

The original 2026-05-16 tactical plan delivered the TvKeyRouter scaffolding + WelcomeActivity fix. Files preserved in `legacy/` for traceability. Status: all 5 implementation phases ✅ DONE; original Phase 6 (device verification) is superseded by the round 2 device-test gate at the end of round 2.

- `legacy/phase1_audit_matrix.md` — initial 6-modality audit (now extended to 8 modalities in round 2).
- `legacy/phase2_tv_key_router.md` — `TvKeyRouter` + `TvNavAction` created (since extended).
- `legacy/phase3_base_activity_integration.md` — `dispatchKeyEvent`, `onTvNavigation`, `getInitialFocusView`, `isTvDevice` added.
- `legacy/phase4_welcome_dpad.md` — `WelcomeActivity.onTvNavigation`, initial focus, focusable buttons.
- `legacy/phase5_activity_audit.md` — per-Activity audit (round 1 scope).
- `legacy/phase6_device_verification.md` — original device-test gate (superseded).

Round 2 deltas relative to round 1:
- `TvNavAction` extended with `Nav` / `Media` / `Hardware` sub-interfaces and 13 new subtypes (PlayPause, Play, Pause, Stop, MediaNext, MediaPrev, FastForward, Rewind, VolumeUp, VolumeDown, VolumeMute, Menu, Search).
- `TvKeyRouter.route()` extended to map MEDIA_*, HEADSETHOOK, VOLUME_*, MENU, SEARCH.
- `BaseActivity.isTvDevice()` extended to OR-combine `PackageManager.FEATURE_LEANBACK` with `UI_MODE_TYPE_TELEVISION` (per §6.3 best practice).
- §6 research items 1..6 resolved with best-practice answers cited inline.

---

## Architecture decisions carried into implementation

- **ADR-1 (round 1, unchanged):** `TvKeyRouter` is a standalone `@Singleton` component called from `BaseActivity.dispatchKeyEvent()`. `BaseActivity` only owns the call site + the `onTvNavigation(action)` hook override point.
- **ADR-2 (round 2 revision):** router scope is **all non-gamepad sources** — `SOURCE_KEYBOARD`, `SOURCE_DPAD`, and any other source carrying media or hardware keycodes (car steering wheel, Bluetooth HID, Android Auto). `SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` continue to route via `GamepadInputManager` exclusively.
- **ADR-3 (round 2 new):** TV mode detection is `hasSystemFeature(FEATURE_LEANBACK) || uiMode == UI_MODE_TYPE_TELEVISION`. Source: `developer.android.com/training/tv/get-started/hardware`.
- **ADR-4 (round 2 new):** Dialog accessibility focus is set via `AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED` posted with `Handler.postDelayed`, not via `View.requestFocus()`. Source: Microsoft Mobile Engineering accessibility guide; canonical fix for Material `AlertDialog` issue #1400.

---

## Pre-Implementation Blockers

All §6 research items were resolved 2026-05-17 — no remaining research blockers.

- [x] **Research §6.1** — RecyclerView D-pad best practice — resolved (see strategic §6.1).
- [x] **Research §6.2** — Player DPAD vs focus traversal — resolved.
- [x] **Research §6.3** — TV mode detection reliability — resolved + applied to `BaseActivity.isTvDevice()`.
- [x] **Research §6.4** — Mouse-blocking custom touch handlers — resolved.
- [x] **Research §6.5** — TalkBack focus in dialogs — resolved.
- [x] **Research §6.6** — Voice Access with custom Views — resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — **skip** (strategic §8 says "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (handled by Phase 06).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (handled by Phase 06).
- [ ] `/spec-check S0230` returns `Verified` (after device-test gate via `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0230`.

---

## Blockers Log

(empty)

---

## Change Log

- 2026-05-17 — Round 2 tactical plan authored by `/spec-tech`. Legacy round 1 phases archived under `legacy/` for historical reference. New scope covers coverage matrix (§11.1), list-screen focus polish (§6.1 best practice), mouse safety (§6.4), dialog TalkBack helper (§6.5), accessibility content audit (§6.6), and final docs-catalog cleanup.
