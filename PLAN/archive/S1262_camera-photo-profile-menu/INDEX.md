# Tactical Plan: S1262 - camera-photo-profile-menu

**Strategic spec:** [`../S1262_camera-photo-profile-menu.md`](../S1262_camera-photo-profile-menu.md)
**Research inputs:** [`research/01__portrait-bokeh-extension.md`](research/01__portrait-bokeh-extension.md), [`research/02__sport-short-exposure-recipe.md`](research/02__sport-short-exposure-recipe.md)
**Feature:** Photo-profile menu (normal, night, portrait, selfie, macro, sport) replacing the macro button
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | profile-model | - | ✅ Done | 3/3 | [PHASE_01__profile-model.md](PHASE_01__profile-model.md) |
| 02 | session-recipes | 01 | ✅ Done | 4/4 | [PHASE_02__session-recipes.md](PHASE_02__session-recipes.md) |
| 03 | profile-orchestration | 02 | ✅ Done | 4/4 | [PHASE_03__profile-orchestration.md](PHASE_03__profile-orchestration.md) |
| 04 | menu-button-ui | 03 | ✅ Done | 5/5 | [PHASE_04__menu-button-ui.md](PHASE_04__menu-button-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 items are Resolved with research artifacts (see Research inputs).

Deliberate scheduling note (not a formal blocker): the same capture screen carries two tickets awaiting device feedback - S1260 (label rounding, BlockNeedUserTest) and S1261 (sub-1x missing, BlockQuestions). Starting Phase 04 before those verdicts risks rework on the same panel; Phases 01-03 are safe to start any time.

### Pre-release audit, 2026-07-29 - SUPERSEDED 2026-07-31

That audit answered "is this half-finished ticket safe to ship" with "yes, because
`CameraProfileApplyManager` is reachable from nothing and R8 strips it". **That is no longer true.**
Phases 03-05 wired the manager into `CameraCaptureFlowManager` and put its menu on the capture panel:
the profile button replaces the macro and night toggles, `camera_profile_*` strings exist in all three
locales, and `bokehEnabled` / `sportEnabled` now have a real writer. The feature is live in every
flavor from the next build.

What the ticket ships is gated by device capability rather than by a build flag, so a phone that
offers nothing beyond NORMAL hides the button entirely and sees the panel it had before, minus the two
retired toggles. The behaviour on capable hardware is exactly what the `BlockNeedUserTest` round has
to confirm.

The one conclusion still worth keeping from that audit: do not roll Phase 02 back. It retired three
`!!` on `extensionsManager` and its bind-path edits are load-bearing for night and HDR too.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited per-spec (`/skill-release`-owned); the capability record goes to `docs/ALL_FEATURES.jsonl` in Phase 05.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added, roles set).
- [ ] `/spec-check S1262` returns `Verified` - blocked on the device round, see below.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

### Why the ticket stops at BlockNeedUserTest

Every remaining acceptance criterion is a hardware claim no emulator can answer: which profiles a
given phone offers, whether Macro reaches the dedicated close-focus lens, whether Portrait finds the
bokeh extension, and whether Sport visibly freezes motion. The strategic spec also requires owner
sign-off on the offered set and on Sport's behaviour. Probes `S1262:` are in the tree for that round
and come out when the status leaves `BlockNeedUserTest`.

The same trip should carry S1189, S1260 and S1261 - all four are the same screen and all four are
parked on device feedback.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1262`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-07-28 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-31 - Phases 03-05 completed by `/spec-all`; ticket parked at `BlockNeedUserTest`.
