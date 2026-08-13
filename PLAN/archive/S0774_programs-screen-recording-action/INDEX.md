# Tactical Plan: S0774 - programs-screen-recording-action

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Research inputs:** none (research performed inline by `/spec-tech`; findings folded into phases)
**Feature:** Screen video recording scenario in the programs block
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (rework implemented - awaiting device retest)
**Phases:** 12 / 12 done
**Last updated:** 2026-07-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | mainactivity-menu-extraction | - | ✅ Done | 2/2 | [PHASE_01__mainactivity-menu-extraction.md](PHASE_01__mainactivity-menu-extraction.md) |
| 02 | settings-data-foundation | - | ✅ Done | 4/4 | [PHASE_02__settings-data-foundation.md](PHASE_02__settings-data-foundation.md) |
| 03 | trilingual-strings | - | ✅ Done | 1/1 | [PHASE_03__trilingual-strings.md](PHASE_03__trilingual-strings.md) |
| 04 | recording-state-contract | - | ✅ Done | 3/3 | [PHASE_04__recording-state-contract.md](PHASE_04__recording-state-contract.md) |
| 05 | recording-engine-service | 02, 03, 04 | ✅ Done | 5/5 | [PHASE_05__recording-engine-service.md](PHASE_05__recording-engine-service.md) |
| 06 | settings-ui-rows | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_06__settings-ui-rows.md](PHASE_06__settings-ui-rows.md) |
| 07 | programs-scenario-wiring | 01, 02, 03, 04, 05 | ✅ Done | 5/5 | [PHASE_07__programs-scenario-wiring.md](PHASE_07__programs-scenario-wiring.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |
| 09 | recording-pause-state-contract | - | ✅ Done | 3/3 | [PHASE_09__recording-pause-state-contract.md](PHASE_09__recording-pause-state-contract.md) |
| 10 | recording-engine-pause-impl | 09 | ✅ Done | 2/2 | [PHASE_10__recording-engine-pause-impl.md](PHASE_10__recording-engine-pause-impl.md) |
| 11 | compact-recording-indicator-ui | 09 | ✅ Done | 6/6 | [PHASE_11__compact-recording-indicator-ui.md](PHASE_11__compact-recording-indicator-ui.md) |
| 12 | docs-catalog-cleanup-rework | 09, 10, 11 | ✅ Done | 2/2 | [PHASE_12__docs-catalog-cleanup-rework.md](PHASE_12__docs-catalog-cleanup-rework.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 lists no open research items (forks resolved via `/spec-quiz`); the in-app stop-control placement was resolved with the owner on 2026-06-28: **floating card mirroring the voice-recorder dialog** (`MainVoiceCaptureManager`).
- Phases 09-12 added 2026-07-03 for the device-test rework (see strategic spec Quiz decisions 2026-07-03): the floating card was a full-screen modal `AlertDialog`, violating strategic §3.1.2. No open research items for the rework either - owner decisions already recorded in strategic §3.3 and Quiz decisions.

---

## Owner / design decisions baked into this plan

- **In-app stop UX** = a non-modal recording card built programmatically like `MainVoiceCaptureManager.showRecordingDialog()` (timer in the message, "Stop recording" positive button). No new layout XML, no main-window layout strip - so no `layout-land` parity work for the indicator.
- **Re-entry** = the card is driven by a shared `ScreenRecordingStateController` (`StateFlow<Boolean>` + start timestamp). The recording outlives the Activity (foreground service), so the card is shown/hidden whenever `MainActivity` observes the state, not tied to a single dialog instance.
- **Recorder** = `MediaRecorder` in surface mode (`setVideoSource(SURFACE)` + `setAudioSource(MIC)` → single MP4) fed by a `VirtualDisplay`. Hardware H.264 + AAC; avoids a hand-rolled `MediaCodec`/`MediaMuxer` pipeline.
- **Destination** = new `CaptureDestinationPolicy.resolveScreenRecordingDestination(resource)`; empty selection → public **Downloads** (per strategic §11.5, not Movies).
- **Consent** = dedicated `ScreenVideoRecordingConsentActivity` in `src/screenCapture` with its own disclosure flag `screenRecordingDisclosureAccepted` (continuous-recording disclosure differs from the one-shot screenshot one). Reuses `MediaProjectionManager.createScreenCaptureIntent()`.
- **Flavor gating** = no `BuildConfig` guard in `src/main`. Availability is governed by the `Set<ScreenVideoRecordingController>` multibinding being empty (lite/photos/legacy) vs non-empty (`src/screenCapture`: standard `fms.screenCapture=on` + noLegal) - the exact `MenuScreenshotLauncher` pattern.
- **Stop command** = the foreground-service notification carries a `Stop` `PendingIntent` straight to the service (`ACTION_STOP`); the in-app card stops via `ScreenVideoRecordingController.requestStop(context)`.
- **MainActivity** is already 1540 LOC (> the 1500 limit). Phase 01 extracts the programs-menu orchestration into `MainProgramsMenuCoordinator` (behaviour-preserving) before any scenario wiring is added.

### Rework decisions (2026-07-03, phases 09-12)

- **Compact indicator replaces the modal card** - `MainScreenRecordingManager` and `MainVoiceCaptureManager` both drop `MaterialAlertDialogBuilder` for one shared non-modal `RecordingIndicatorOverlayManager` (top-end corner, within systemBars/cutout safe bounds).
- **Pause bookkeeping location differs per feature** - screen recording survives Activity recreation via the foreground service, so pause/resume timestamps live in the `@Singleton ScreenRecordingStateController` (recomputed from a fixed start instant, matching its existing `startedAtElapsedRealtimeMs` design). Voice capture has no service (Activity-scoped, `release()`-on-pause), so it reuses the relocated `RecordingElapsedTimer` (ex-`CameraRecordingTimer`, S0566) in-manager accumulate-and-freeze pattern exactly like `CameraCaptureActivity` already does.
- **Shared timer utility relocated, not duplicated** - `CameraRecordingTimer` moves from `ui/cameracapture/helpers/` to `util/RecordingElapsedTimer.kt` (Context-free, already generic) so voice capture can depend on it without an unrelated feature-package dependency.
- **No new layout-land file** - the indicator layout is orientation-agnostic (gravity-anchored corner pill); only the two existing `activity_main.xml` variants (portrait + land) gain the same container addition.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - **skip**: strategic §8 defers the showcase sentence to `/skill-release` from the `ALL_FEATURES` diff. The delivered capability is recorded in `docs/ALL_FEATURES.jsonl` (Phase 08).
- [ ] `dev/CHANGELOG.md` has an entry for every logical change (one per phase, via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).
- [ ] `docs/settings/settings-manifest.json` + `settings-annotations.json` + `SETTINGS_REFERENCE*.md` regenerated (new setting - Rule 22).
- [ ] `/spec-check S0774` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] Phases 09-12 (rework): `docs/ALL_FEATURES.jsonl` S0774 entry reviewed for the pause addition (Phase 12).

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log, and set the journal status to the matching `Block*` with a `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0774`.

---

## Blockers Log

- 2026-07-03 - All 12 phases done; journal status set to `BlockNeedUserTest`. Next: owner device-tests the compact indicator + pause/resume/discard on both screen recording and voice capture (see strategic spec `Status note`), then `/spec-test-device S0774` + `/spec-check S0774`.

---

## Change Log

- 2026-06-28 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-03 - Phases 09-12 added by `/spec-tech` for the device-test rework (compact non-modal indicator + pause, shared with `MainVoiceCaptureManager`). Plan self-check: PASS - 4 new inventory lines (goal 3 pause, goal 7 shared indicator, §3.3 pause/shared-component owner inputs, §11 criteria 4/7) mapped to phases 09-12, 0 reorders.
- 2026-07-03 - `/spec-dev` executed all 13 steps across phases 09-12. Two topology fixes made mid-run (both logged in their phase's Step Log): interface pause/resume methods given default bodies (Phase 09.3, avoids breaking the not-yet-updated impl); a third "discard" button added to the shared indicator component (Phase 11.5, avoids silently dropping voice capture's existing cancel-without-saving action). Discovered and fixed: two other files touched by this rework (`ScreenVideoRecordingService.kt`, `MainScreenRecordingManager.kt`) had zero detekt baseline coverage since their original S0774 creation - cleaned up all pre-existing findings while there rather than leave them gate-red. Status -> `BlockNeedUserTest`.
