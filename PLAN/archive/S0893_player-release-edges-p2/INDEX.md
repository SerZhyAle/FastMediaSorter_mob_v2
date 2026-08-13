# Tactical Plan: S0893 - player-release-edges-p2

**Strategic spec:** [`../S0893_player-release-edges-p2.md`](../S0893_player-release-edges-p2.md)
**Research inputs:** none (research folded into strategic §0 findings + live-code re-verification below)
**Feature:** Player release contract - background/foreground codec lifecycle
**Tier:** 3 - Moderate
**Priority:** 45
**Status:** Not started
**Phases:** 0 / 4 done
**Last updated:** 2026-07-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in strategic spec.

---

## Pre-flight re-verification (skeptic pass over the 10 static findings)

Static findings were re-checked against live code before planning. Verdicts:

1. `AudioEmptyStateController.kt:120` - CONFIRMED. `onIsPlayingChanged()`'s video-start branch gates only on `isPrepared`; nothing tracks host visibility.
2. `AudioEmptyStateController.kt:154` - CONFIRMED. No `onStop`/`onStart` in this class; only `onPause`/`onResume`/`release()` exist.
3. `AudioEmptyStateController.kt:242` - CONFIRMED. `hide()` does not clear `videoView.surfaceTextureListener`; `onSurfaceTextureDestroyed` returns `false` with no `SurfaceTexture.release()` call anywhere.
4. `AudioEmptyStateController.kt:310` - CONFIRMED. `mediaPlayer = MediaPlayer().apply { .. }` only assigns the field after the whole chain succeeds; a throw inside `apply{}` (e.g. `setDataSource`) orphans the constructed instance - the catch block releases only the `Surface`.
5. `VideoPlayerLifecycleHelper.kt:20` - **STALE, no action.** `releasePlayer()` already calls `manager.stopPositionSaving()` at its top (comment cites S0854, a prior fix). No step in this plan touches this line.
6. `VideoPlayerLifecycleHelper.kt:34` - CONFIRMED, and root cause identified. `loadControl` (`PauseAwareLoadControl` / `BandwidthAdaptiveLoadControl`-backed stream listener) is always a local `val` in `createPlayer()` / `CloudPlaybackHelper` / `FtpPlaybackHelper` / `SftpPlaybackHelper` / `SmbPlaybackHelper` / `StreamPlaybackHelper` - never stored on `VideoPlayerManager`, so `releasePlayer()` has no reference to remove it even if it wanted to.
7. `VideoPlayerLifecycleHelper.kt:35` - CONFIRMED. `onDestroy()` already detaches `currentPlayerView?.player = null` before `release()`; `releasePlayer()` (the non-destroy teardown path) does not. Neither method drains `setVideoEffects(emptyList())` first, which `StandaloneViewManager.releaseVideoPlayer()` (S0859) documents as required to avoid a Media3 1.2.1 GL-pipeline release hang (androidx/media #1139, #2098) - same risk applies here since `applyConfiguredVideoEffects()` installs the same kind of pipeline.
8. `VideoPlayerLifecycleHelper.kt:52` / `StandalonePlayerActivity.kt:445` / `PhotoVideoStandaloneActivity.kt:1013` - CONFIRMED, three hosts share the same gap: `VideoPlayerManager` self-registers as `DefaultLifecycleObserver` (`lifecycle.addObserver(this)`) but overrides only `onPause`/`onResume`/`onDestroy`; `StandalonePlayerActivity`/`PhotoVideoStandaloneActivity` (and `PlayerActivity`, `VideoPlayerManager`'s host) have zero `onStop`/`onStart` overrides anywhere. Official guidance confirmed via WebSearch: release in `onStop`/recreate in `onStart` for API 24+, keep `onPause`/`onResume` below 24 ([developer.android.com/media/media3/exoplayer/lifecycle](https://developer.android.com/media/media3/exoplayer)).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | audio-empty-state-lifecycle | - | ⬜ Not started | 0/3 | [PHASE_01__audio-empty-state-lifecycle.md](PHASE_01__audio-empty-state-lifecycle.md) |
| 02 | video-lifecycle-helper-release | - | ⬜ Not started | 0/4 | [PHASE_02__video-lifecycle-helper-release.md](PHASE_02__video-lifecycle-helper-release.md) |
| 03 | standalone-video-host-release | - | ⬜ Not started | 0/3 | [PHASE_03__standalone-video-host-release.md](PHASE_03__standalone-video-host-release.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ⬜ Not started | 0/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01-03 touch disjoint file sets (no cross-phase symbol dependency) - order is by risk, not by topology: 01 is self-contained (single class + two call sites), 02 is the widest blast radius (7 files), 03 is the smallest (3 files, reuses 02's `Build.VERSION_CODES.N` convention only by pattern, not by import).

---

## Pre-Implementation Blockers

None. All ambiguity resolved during the pre-flight re-verification pass above (see strategic spec for full reasoning on the onStop/onStart design and the API24+ scoping decision).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (internal release-contract fix, no user-visible feature copy).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (no new public classes, but touched-file timestamps refresh).
- [ ] `/spec-check S0893` returns `Verified` or a documented `BlockNeedUserTest` (on-device background/foreground behavior cannot be fully proven by build alone).
- [ ] Strategic spec `Status:` advanced accordingly.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. All done: flip `Status:` to `Done`, run `/spec-check S0893`.

---

## Blockers Log

(none)

---

## Change Log

- 2026-07-03 - Initial tactical plan authored (COMPLEX path - 12 files across 3 functional clusters, exceeds PRIMITIVE's 3-file/no-new-behavior ceiling).
