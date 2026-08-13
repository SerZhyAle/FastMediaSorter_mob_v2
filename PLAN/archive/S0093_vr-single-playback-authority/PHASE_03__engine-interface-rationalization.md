# Phase 03 — Engine interface rationalization

**Strategic spec:** [`../S0093_vr-single-playback-authority.md`](../S0093_vr-single-playback-authority.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Either narrow `VrPlaybackEngine` to a true XR-specific responsibility or remove redundant transport responsibilities so the interface no longer competes with the shared player path.

---

## Candidate Files

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/VrPlaybackEngine.kt` | Deleted | n/a |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/ExoVrPlaybackEngine.kt` | Deleted | n/a |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt` | Modified | ≤ 30 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 20 |

---

## Planned Steps

### Step 03.1 — Decide whether the interface stays as XR surface preparation only or is removed

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Resolved in favour of removal: the interface had no remaining runtime caller after Phase 02 and only duplicated a competing transport authority.

### Step 03.2 — Align implementation and DI with the chosen boundary

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Removed the injected `VrPlaybackEngine` path from `VrPlayerActivity`, deleted `VrPlaybackEngine` / `ExoVrPlaybackEngine`, and removed the obsolete Hilt binding from `VrModule`.

### Step 03.3 — Compile touched VR slice

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `./gradlew.bat :app_v2:compileVrDebugKotlin` → `BUILD SUCCESSFUL in 37s` after engine-path removal.