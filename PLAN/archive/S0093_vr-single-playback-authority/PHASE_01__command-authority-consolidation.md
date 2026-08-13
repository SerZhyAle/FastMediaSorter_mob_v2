# Phase 01 — Command authority consolidation

**Strategic spec:** [`../S0093_vr-single-playback-authority.md`](../S0093_vr-single-playback-authority.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Make the VR command router use a single transport authority for seek and other playback controls instead of splitting `SeekTo` away from the existing shared ExoPlayer path.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrPlayerCommandRouter.kt` | Modified | ≤ 10 |

---

## Steps

### Step 01.1 — Route SeekTo through the canonical shared player path

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrPlayerCommandRouter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the direct `VrPlaybackEngine.seekTo(...)` routing for `PlaybackCommand.SeekTo` with the same shared ExoPlayer authority already used by the rest of the VR render/session stack. Keep the change local to the router in this phase; do not widen it into `VrPlayerActivity` or engine interface cleanup yet.

**Verification:**

- `VrPlayerCommandRouter.kt` no longer calls `vrPlaybackEngineInternal`.
- `PlaybackCommand.SeekTo` now routes through the shared player path.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `PlaybackCommand.SeekTo` rewired from `activity.vrPlaybackEngineInternal.seekTo(...)` to `videoPlayerManager.exoPlayer?.seekTo(...)` with an inline WHY-comment documenting the temporary authority choice.

---

### Step 01.2 — Compile the touched VR slice

**Files:** none (validation only)
**Depends on:** Step 01.1

**Prompt for developer:**

> Run a focused Kotlin compile for `app_v2` debug after the router change. If it fails, fix only errors introduced by this phase and rerun the same compile.

**Verification:**

- Compile command exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `./gradlew.bat :app_v2:compileVrDebugKotlin` → `BUILD SUCCESSFUL in 29s`.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Step 01.2 is `[x] done`.
- [x] The router no longer splits seek authority across two backends.

---

## Rollback Plan

Revert the single router edit and keep S0093 tactical docs intact.