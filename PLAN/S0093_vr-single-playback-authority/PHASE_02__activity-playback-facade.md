# Phase 02 — Activity playback facade

**Strategic spec:** [`../S0093_vr-single-playback-authority.md`](../S0093_vr-single-playback-authority.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Hide direct helper dependence on `videoPlayerManagerInternal` / `exoPlayer` behind explicit activity-level playback facades so future phases can change internals without touching every VR helper.

---

## Candidate Files

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrPlayerCommandRouter.kt` | Modified | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrSessionLifecycleManager.kt` | Modified | ≤ 20 |

---

## Planned Steps

### Step 02.1 — Introduce explicit activity-level playback facade methods

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Added explicit playback facade methods in `VrPlayerActivity` for progress snapshot, seek, speed, audio track selection, surface binding, and immersive-state propagation.

### Step 02.2 — Update helpers to use the facade instead of raw manager access where practical

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `VrPlayerCommandRouter`, `VrRenderPipelineManager`, and `VrSessionLifecycleManager` switched from raw `videoPlayerManagerInternal` / `exoPlayer` access to explicit activity playback facade calls.

### Step 02.3 — Compile touched VR slice

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `./gradlew.bat :app_v2:compileVrDebugKotlin` failed once on missing seek helper imports in `VrPlayerActivity`, then passed after the local import fix.