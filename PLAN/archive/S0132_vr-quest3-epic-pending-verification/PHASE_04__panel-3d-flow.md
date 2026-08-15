# Phase 04 — Panel → 3D Flow

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 0 / 1
**Started:** —
**Completed:** —

---

## Objective

Verify the full panel↔3D flow (ex-S0019) on Quest 3: prev/next in immersive without session restart, "exit to panel" single-window behaviour, "apply + enter 3D" one-tap button, and the complete round-trip scenario.

The missing discoverable entry point from the image player to immersive (toolbar VR button + user-initiated stereo detection) is **out of scope** for this phase — it is tracked separately under `S0238 image-player-vr-entry-button`. After S0238 reaches Verified, sub-scenario B of Step 04.1 below can also exercise that path.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (interactive panel verified — required for HUD-based prev/next and the "apply + enter 3D" button).
- [ ] Phase 03 is ✅ Done (finishAndRemoveTask fix in place — required so "exit to panel" does not clone windows).
- [ ] VR build installed with all Phase 02 + 03 changes.
- [ ] Quest 3 with at least 3 stereo files in a folder (video + photo mix).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| *(none — verification-only phase; no code changes expected)* | — | — |

> If a defect is found, add the fix file here before committing.

---

## Steps

### Step 04.1 — Verify full panel → 3D scenario and prev/next in immersive

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` (read), `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt` (read)
**Depends on:** — start of phase (phases 02 + 03 are the real prerequisites)

**Prompt for developer:**

> Run the following sub-scenarios on Quest 3:
>
> **A — Prev/next in immersive (video):**
> Open a stereo video folder. Enter immersive. Use prev/next HUD buttons to switch files without exiting immersive. XR session must NOT restart between files (no black gap + re-initialization). Repeat for ≥ 3 files.
>
> **B — Prev/next in immersive (photo):**
> Same, with VR photo files. Confirm video-only HUD elements (seekbar, rewind, speed, audio, subtitle) are hidden for photo items. (Image-to-immersive entry button is covered by `S0238`, not by this step.)
>
> **C — "Apply + enter 3D" one-tap:**
> Open a stereo video in panel player. Open the playback control dialog. Change stereo format. Tap "Apply + enter 3D". Confirm: dialog closes, playback switches to immersive, format takes effect — all in one tap, no extra screens.
>
> **D — Full round-trip:**
> Browse → open stereo file → immersive → exit to panel ("exit to panel" button in HUD) → panel shows same file at same position → change format in dialog → tap "Apply + enter 3D" → immersive with new format → no repeated file selection required at any point.
>
> **E — String check:**
> Confirm button/label text in EN, RU, UK matches the destination: "Exit to panel", "Exit to browser", "Show controls", "Apply", "Apply + enter 3D" (or their locale equivalents) are unambiguous.

**Verification:**

- On-device A: prev/next switches file without XR session restart (no black-screen gap between files).
- On-device B: photo items hide seekbar, rewind, speed, audio, subtitle HUD elements.
- On-device C: "Apply + enter 3D" completes the action in exactly one tap.
- On-device D: full round-trip completes without repeated file picker.
- On-device E: EN/RU/UK button labels correctly describe the destination action (no "Open 3D" label on an "exit to panel" button, etc.).
- `Grep -rn "apply.*3d\|applyAndEnterVr\|applyAndImmersive"` (case-insensitive) — matches at least one string resource key in `res/values/strings.xml`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` if any fix was committed.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file modified (if any) via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if any `.kt` changed.

---

## Handoff Notes to Next Phase

Final phase before cleanup is Phase 05 (low-priority items). Phase 05 is independent and may run in parallel with Phase 04.

---

## Rollback Plan

Verification-only under normal path — no rollback needed. If a fix was committed, revert that commit; no data or schema change.

---

## Change Log

- 2026-05-14 — Phase authored (`/spec-tech`).
- 2026-05-17 — Briefly added Step 04.0 (image-toolbar entry button + StereoDetector user-initiated overload), then reverted same day: scope moved to standalone ticket `S0238 image-player-vr-entry-button` to avoid mixing new code into an already on-device-blocked epic. Phase remains verification-only.
