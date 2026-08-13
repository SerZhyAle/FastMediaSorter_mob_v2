# Tactical Plan: S1144 - video-stream-tracks-subtitles-program

**Strategic spec:** [`../S1144_video-stream-tracks-subtitles-program.md`](../S1144_video-stream-tracks-subtitles-program.md)
**Research inputs:** [`research/01__streams-track-metadata-architecture.md`](research/01__streams-track-metadata-architecture.md)
**Feature:** Per-channel audio/subtitle track memory + live program-name overlay for video streams
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 5 done (03 spun out to S1158)
**Last updated:** 2026-07-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | db-schema-migration | - | ✅ Done | 3/3 | [PHASE_01__db-schema-migration.md](PHASE_01__db-schema-migration.md) |
| 02 | track-preference-apply-writeback | 01 | ✅ Done | 4/4 | [PHASE_02__track-preference-apply-writeback.md](PHASE_02__track-preference-apply-writeback.md) |
| 03 | program-name-overlay | - | ⏭️ Spun out → S1158 | 0/2 | [PHASE_03__program-name-overlay.md](PHASE_03__program-name-overlay.md) |
| 04 | settings-surfaces | 01, 02 | ✅ Done | 3/3 | [PHASE_04__settings-surfaces.md](PHASE_04__settings-surfaces.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 Q1/Q2/Q6 resolved by quiz 2026-07-23; research 01 Resolved. Room migration slot 42→43 named in ADR-3.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 mandates showcase only via `/skill-release`.
- [ ] Settings docs regenerated (Rule 22) - manifest + reference + annotations (Phase 04 adds a setting).
- [ ] `dev/CHANGELOG.md` has an entry per modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new repository/usecase classes).
- [ ] `/spec-check S1144` returns `Verified` (device-verify of overlay + track-recall gated via `BlockNeedUserTest`).

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1144`.

---

## Blockers Log

- 2026-07-23 (`/spec-dev`) - Phase 01 ✅ Done (migration 42->43 + DAO, built green). Phase 02 steps 02.1 (`StreamTrackPreferenceUseCase`) + 02.4 (unit test, 5/5 pass) ✅ Done. Steps 02.2/02.3, and Phases 03/04/05 deferred - the tactical plan is architecturally under-specified for the player/UI integration. Ticket set `BlockQuestions`. `/spec-update` must resolve, before resuming:
  - **02.2 apply-at-start:** name the DI path to thread the Hilt `StreamTrackPreferenceUseCase` into the bundle-constructed `VideoPlayerManager` stream-start path (`VideoPlayerStoreDependencies` + `PlayerViewerFactory` + provider); reconcile the `buildUponParameters(` predicate (a `DefaultTrackSelector` method) with `VideoTrackSelectionManager`'s `player.trackSelectionParameters.buildUpon()`; note the global-default read it overlays needs Phase 04's `AppSettings` keys first.
  - **02.3 write-back:** `TrackInfo` exposed to the dialog carries no language code, only group/track indices + a display label - specify how the selected track's language is resolved (extend `TrackInfo` with `language` across all `VideoPlayerHandle` mappers, or re-read `player.currentTracks`); specify the current-channel-URL accessor on the player host; `PlaybackControlDialogFragment` is not `@AndroidEntryPoint`.
  - **03 program overlay:** the plan places `onMediaMetadataChanged` on `VideoPlayerManager.playerListener`, but the stream player provably bypasses that listener (only `streamPlaybackListener` in `StreamPlaybackHelper.kt` is attached; ICY flows through its `onMetadata`). The overlay `TextView` is owned by the Activity (`activityBinding.tvFileNameOverlay`), not the manager. Correct implementation needs `StreamPlaybackHelper.kt` (stream listener) + a new `PlayerCallback` method + `PlayerPlaybackCallbackImpl` render owner (mirroring the existing `onStreamWaitPhase`/`streamWaitLabel` pattern) + both `activity_player_unified.xml` orientations - none listed in the phase's Files Touched. Update Files Touched + prompt.
  - **04 settings surfaces:** depends on 02; the channel-editor class and the global `AppSettings` keys are unresolved in the prompt.
  - **05:** depends on all above.

---

## Change Log

- 2026-07-24 (`/spec-quiz`) - Owner split the scope: Phase 03 (program-name overlay) spun out to **S1158** (independent, `Depends on: -`). S1144 narrows to track-memory + settings (Phases 02/04/05). Engineering under-specification of 02.2/02.3/04 routes to `/spec-update`. Ticket restored `BlockQuestions` → `In Progress`.
- 2026-07-23 - Initial tactical plan authored by `/spec-tech` (5 phases; reuses S1142 `NowPlayingMetadata`/`StreamTitleFormatter` for the overlay).
