# Phase 03 - Audio Pre-Cache

**Strategic spec:** [`../S0252_bugfix-sftp-audio-car-playback-resilience.md`](../S0252_bugfix-sftp-audio-car-playback-resilience.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Bound SFTP audio pre-cache waits and keep direct streaming and next-track prefetch recoverable.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Strategic §6.2 and §6.5 are Resolved.
- [ ] Read inline comments and KDoc in all affected files before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManager.kt` | Not modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt` | Modified | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManagerTest.kt` | New / Modified | ≤ 300 |

> `PlayerMediaLoaderManager.kt` is already over 500 lines. Create a timestamped backup in `temp/` before editing and do not add new responsibilities there; extract to helper if the line count grows materially.

---

## Steps

### Step 03.1 - Define audio startup pre-cache budget

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add a source-aware audio startup pre-cache policy for SFTP. Keep video behavior unchanged and avoid new user settings.

**Verification:**

- `Grep` - SFTP audio policy is represented separately from video policy.
- `Grep` - no new `BuildConfig.SUPPORT_*` or `BuildConfig.IS_*` guard was added under `src/main/java`.
- `Grep` - `Log.d(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `PrefetchPolicyManager.kt`. SFTP audio has `SFTP_AUDIO_STARTUP_PRECACHE_TIMEOUT_MS` and `audioStartupPolicyFor(path, isAudio = true)` separate from non-audio/video default policy; no new `BuildConfig.SUPPORT_*` or `BuildConfig.IS_*`; `Log.d(` absent.

### Step 03.2 - Apply early direct-stream fallback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Use the SFTP audio startup budget when launching MP3 playback. If pre-cache exceeds the budget, fallback to direct streaming without blocking the user for the full 20 s path.

**Verification:**

- `Grep` - existing `preCacheNetworkAudio: timed out after 20000ms` path is no longer the startup-only decision for SFTP audio.
- `Grep` - fallback log includes source type and reason.
- `Grep` - `Log.d(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `PlayerMediaLoaderManager.kt`. `preCacheNetworkAudio` now uses source-aware `startupPolicy.timeoutMs`; SFTP timeout fallback log includes `source=` and `reason=sftp-audio-early-direct-stream`; `Log.d(` absent.

### Step 03.3 - Keep next-track prefetch recoverable

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Make `prefetchNextAudio` failure leave an explicit recovery state instead of a bare `FAILED` line. The current track must remain independent from next-track prefetch failure.

**Verification:**

- `Grep` - next-track prefetch failure records retry/degrade decision.
- `Grep` - current track playback path does not depend on prefetch success.
- `Grep` - `Log.d(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `PlayerMediaLoaderManager.kt`, `PrefetchPolicyManager.kt`. `prefetchNextAudio` is owned by `PlayerMediaLoaderManager.kt`, not `PlayerPrefetchManager.kt`; failure now logs `RECOVERABLE failure` with `currentTrackUnaffected=true` and `retryOnDemand=true`. Current track playback does not branch on next-track prefetch success.

### Step 03.4 - Test pre-cache fallback decisions

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManagerTest.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add tests for SFTP audio early fallback, video unaffected behavior, and next-track prefetch failure recovery state.

**Verification:**

- `Grep` - tests mention SFTP audio early fallback.
- `Grep` - tests mention next-track prefetch failure.
- `/build` - run affected unit tests through the project build skill.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `PlayerPrefetchManagerTest.kt`. Tests cover `SFTP audio early fallback`, SFTP non-audio/video default behavior, and `next-track prefetch failure`. Command passed: `.\gradlew.bat --% :app_v2:testStandardDebugUnitTest --tests com.sza.fastmediasorter.ui.player.helpers.PlayerPrefetchManagerTest -Pchaquopy.enabled=false`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`. `build-debug.PS1` produced `assembleStandardDebug` success on 2026-05-19.
- [x] `Grep` for `TODO(phase-03)` returns zero hits in S0252 files.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 validates memory headroom and S0207 ownership after playback timing is bounded.

---

## Rollback Plan

Revert phase commit(s) and restore the `temp/` backup if `PlayerMediaLoaderManager.kt` was edited.
