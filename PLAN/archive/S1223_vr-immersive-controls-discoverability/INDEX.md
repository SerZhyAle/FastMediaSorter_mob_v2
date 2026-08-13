# Tactical Plan: S1223 - vr-immersive-controls-discoverability

**Strategic spec:** [`../S1223_vr-immersive-controls-discoverability.md`](../S1223_vr-immersive-controls-discoverability.md)
**Mapping source:** [`../S1240_vr-controller-input-mapping/research/03__proposed-mapping-table.md`](../S1240_vr-controller-input-mapping/research/03__proposed-mapping-table.md)
**Feature:** One-time in-headset controls legend plus a permanent HELP button on the immersive media strip
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done - awaiting Quest verification
**Phases:** 4 / 4 done
**Last updated:** 2026-07-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | legend-surface | - | ✅ Done | 3/3 | [PHASE_01__legend-surface.md](PHASE_01__legend-surface.md) |
| 02 | host-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__host-wiring.md](PHASE_02__host-wiring.md) |
| 03 | strip-help-button | 02 | ✅ Done | 3/3 | [PHASE_03__strip-help-button.md](PHASE_03__strip-help-button.md) |
| 04 | strings-docs-closure | 03 | ✅ Done | 4/4 | [PHASE_04__strings-docs-closure.md](PHASE_04__strings-docs-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Ground truth this plan is built on

Read once here rather than repeated per phase.

- The HUD is one texture channel. `queueHud(rgba, w, h)` sets its content and `setHudQuadSize(w, h, dy)` sets its world geometry; both are runtime overrides that persist in native state for the process, so every size change needs a matching restore.
- The media strip is a 2560x360 texture on a 1.40x0.197 m quad at dy -0.30, asserted in `DiagnosticXrActivity.onRenderThreadSessionReady`.
- The strip is only used on `FILE_URI` launches (`isPanelHudMode()`); `DIAGNOSTIC_PLAYLIST` keeps the S0291 filename banner and must not grow a legend.
- Two JNI callbacks reach Kotlin: `onNativeInputEvent(eventType)` for events 1..5 and `onNativeRayInteraction(uvX, uvY, isHover, isClick)` for the ray. Thumbstick Y zoom is applied natively and never arrives here.
- Panel repaints must stay state-driven. `renderPanelHud` is the only `queueHud` call site for the strip and is debounced through `scheduleHudPanelRepaint`.
- `src/vr/` compiles into two shipping flavors - `vr` owns it, `noLegal` borrows it - so both must build.

---

## Pre-Implementation Blockers

None. The two upstream tickets have landed their implementation halves and are awaiting Quest verification only:

- **S1232** - hidden HUD state, trigger summon, HIDE/EXIT buttons. Present in `xr_session.cpp`, `HudCanvasRenderer`, `HudInteractionDispatcher`.
- **S1240** - seek axis, grip modifier, `HudPlaybackController.seekBy`. Present in `xr_input.*`, `xr_session.cpp`, `DiagnosticXrActivity`.

The legend text is authored from what those two actually wired, not from S1240's full table: the menu-button settings row belongs to S1271 and is deliberately absent.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited per-spec (`/skill-release`-owned); the capability record `vr.immersive-controls-legend` went to `docs/ALL_FEATURES.jsonl` in Phase 04, flavors `vr,noLegal` read off `supportsVrMediaControls`.
- [x] `dev/CHANGELOG.md` has an entry for the change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (three new classes, role and `status=new` set).
- [x] `.\a.ps1 fk` and `.\a.ps1 fkn` both pass - `src/vr/` ships in two flavors.
- [x] `Timber.d("S1223: ..)` probes present at the three changed flow entries before the status flips to `BlockNeedUserTest`.
- [ ] `/spec-check S1223` returns `Verified` after the Quest device test - blocked, no Quest attached.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log, and set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S1223`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-07-29 - Initial tactical plan authored by `/spec-all` stage F2.
- 2026-07-29 - All four phases implemented in one `/spec-all` F3 pass. Evidence: `.\a.ps1 fkn`
  BUILD SUCCESSFUL (exit 0); `assert-detekt -Gate` PASS [scoped]; `a.ps1 fg` all gates PASS after the
  ticket-log gate was re-run against `BlockNeedUserTest`. Ticket parked for its Quest 3 session.

## Deviations from the plan as written

- **Phase 03 step 03.3 asked whether the seek ticker needed a legend term.** It did. `shouldRun`
  gates on `hudVisible`, which the legend leaves true because it shares the channel rather than
  hiding it, so a tick would have queued the strip texture onto the legend's quad. Closed at the
  choke point instead of in the predicate: `renderPanelHud` returns early while the legend is up,
  which covers every repaint trigger (ticker, track change, slider drag), and `showLegend` stops the
  ticker so the work is not queued in the first place.
- **The docs sync in Phase 04 step 04.2 grew.** The first two bullets of "Where things stand today"
  still claimed the trigger steps between files and that any other input exits - the pre-S1240 map -
  which directly contradicted the legend being shipped. Rewritten in all three locales alongside the
  three edits the step named.
- **Two findings parked rather than fixed here:** `S1280` (the string audit does not scan flavor res
  directories, so step 04.1's verification passed vacuously and parity was hand-counted instead) and
  `S1281` (`HudAutoHideController` is orphaned and its KDoc describes the collapse pill S1232
  removed).
