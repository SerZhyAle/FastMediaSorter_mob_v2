# Phase 06 — Transitional Guard (Y-fix + first-run HUD cheatsheet)

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Stop `OpenFileOps` / `OpenControls` / `ShowCheatsheet` from pausing playback and showing invisible panels while the immersive session is active AND `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED = false`. Replace the behaviour with a short HUD banner explaining the panel is not yet available in immersive. Show a one-shot first-run HUD cheatsheet summarising the basic controller mapping. Add the new user-facing strings in all three locales.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Phase 05 is `✅ Done` — `VrHudSink.showBannerText` and slot-based HUD are live.
- [ ] Strategic spec §6.5 start-default (short HUD cheatsheet reuses the existing "shown" flag) is confirmed.
- [ ] `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED` still defaults to `false` — Phase 01 put it there.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified (add 3 keys) | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified (add 3 keys) | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified (add 3 keys) | — |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrCheatsheetOverlayManager.kt` | Modified | ≤ 200 |

> No new Kotlin files. `VrPlayerActivity.kt` stays under 1500 after Phase 05 — if it crossed the boundary, Phase 05's `VrHudHostManager` extraction already pulled the fat out; do not re-grow it here.

---

## Steps

### Step 6.1 — Add three new string resources across all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add three new keys with identical naming across all three files. Author style applies: `..` not `...` in RU/UK values. Use `ё` in RU where grammatically correct.
>
> ```xml
> <!-- values/strings.xml (English) -->
> <string name="vr_hud_guard_file_ops">File panel unavailable in immersive. Exit immersive to use it.</string>
> <string name="vr_hud_guard_controls">Control dialog unavailable in immersive. Exit immersive to open it.</string>
> <string name="vr_hud_first_run_cheat">A pause/play · L stick seek · R stick file/volume · grip zoom · Menu settings</string>
> ```
>
> RU and UK mirrors provide a localized equivalent of the same three keys. Banner text budget ~80 glyphs; the composer wraps at 60 to two lines.

**Verification:**

- `Grep` — pattern `vr_hud_guard_file_ops` in `app_v2/src/main/res/values/strings.xml` returns exactly one hit.
- `Grep` — pattern `vr_hud_guard_file_ops` in `app_v2/src/main/res/values-ru/strings.xml` returns exactly one hit.
- `Grep` — pattern `vr_hud_guard_file_ops` in `app_v2/src/main/res/values-uk/strings.xml` returns exactly one hit.
- Same checks for `vr_hud_guard_controls` and `vr_hud_first_run_cheat` across all three locales (nine grep hits total — three per key).
- `/build` skill compiles `vrDebug`.

**Status:** `[ ]` not done

---

### Step 6.2 — Guard `OpenFileOps` / `OpenControls` / `ShowCheatsheet` in `handleVrCommand`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 6.1

**Prompt for developer:**

> Backup first. Locate `handleVrCommand(command: PlaybackCommand, source: VrCommandSource)`. Add a small private helper at the top of the function body:
>
> ```kotlin
> fun isImmersiveUiLocked(): Boolean =
>     vrRenderingActive && !BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED
> ```
>
> Then wrap the three relevant branches:
>
> - `PlaybackCommand.OpenFileOps` → if `isImmersiveUiLocked()`: call `vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_file_ops))` and `return`. Else continue with the existing `vrFileOpsManager?.show()` call.
> - `PlaybackCommand.OpenControls` → if `isImmersiveUiLocked()`: banner `vr_hud_guard_controls`, return.
> - `PlaybackCommand.ShowCheatsheet` → if `isImmersiveUiLocked()`: banner `vr_hud_first_run_cheat`, return. Else the existing `vrCheatsheetManager?.toggleManual()` path.
>
> Do NOT change any other `when` branch. Do NOT remove the existing panel calls — they remain live for phone-fallback and for the future `VR_UI_COMPOSITION_LAYER_ENABLED = true` state.

**Verification:**

- `Grep` — pattern `isImmersiveUiLocked` in `VrPlayerActivity.kt` returns at least four hits (declaration + three branch checks).
- `Grep` — pattern `vr_hud_guard_file_ops` in `VrPlayerActivity.kt` returns exactly one hit.
- `Grep` — pattern `vr_hud_guard_controls` in `VrPlayerActivity.kt` returns exactly one hit.
- `Grep` — pattern `vr_hud_first_run_cheat` in `VrPlayerActivity.kt` returns exactly one hit.
- On-device test: short-press Y on the left controller in immersive — playback does NOT pause, a banner appears for ~3 s reading the localized equivalent of "File panel unavailable..". Long-press Y (> 0.8 s) — banner reads the first-run cheat summary. Press Menu (left) — banner for controls.

**Status:** `[ ]` not done

---

### Step 6.3 — Redirect first-run auto-cheatsheet through the HUD banner in immersive

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrCheatsheetOverlayManager.kt`
**Depends on:** Step 6.2

**Prompt for developer:**

> In `VrCheatsheetOverlayManager`, locate the first-run auto-show path (grep for `first-run auto-show` in the file — current log message). Wrap the Android-view `show()` call so that when `activity is VrPlayerActivity && vrPlayerActivity.isImmersiveUiLocked()`:
>
> - Do NOT attach the full cheatsheet view to decorView.
> - Call `vrPlayerActivity.vrHudManager?.showBannerText(activity.getString(R.string.vr_hud_first_run_cheat))` instead.
> - Still flip the "first-run shown" persistent flag so this path does not fire again on next launch.
>
> Expose `vrHudManager` on `VrPlayerActivity` as `internal` if it is currently `private` — other manager classes need read access. Do not expose its setter.

**Verification:**

- `Grep` — pattern `isImmersiveUiLocked` in `VrCheatsheetOverlayManager.kt` returns at least one hit.
- `Grep` — pattern `vr_hud_first_run_cheat` in `VrCheatsheetOverlayManager.kt` returns exactly one hit.
- On-device test on a fresh install: first immersive launch shows the banner summary, not the Android-view cheatsheet; the "first-run shown" flag is set, so subsequent launches do not repeat.
- `/build` skill compiles `vrDebug`.

**Status:** `[ ]` not done

---

### Step 6.4 — Smoke-test: Y-button no longer freezes playback

**Files:** —
**Depends on:** Step 6.3

**Prompt for developer:**

> Run a manual device test reproducing the original bug:
>
> 1. Launch immersive video.
> 2. Press A (pause) to ensure baseline pause works.
> 3. Press A (play) to resume.
> 4. Short-press Y on the left controller. Verify: playback does NOT pause, a banner appears.
> 5. Long-press Y. Verify: playback does NOT pause, first-run banner appears briefly (or the short-form one if already seen).
> 6. Press Menu (left). Verify: banner appears, no Android-view dialog emerges.
> 7. Press A → playback toggles pause/play exactly as if Y/Menu had never been pressed.
> 8. Collect a `.log` and grep for `handling VR command OpenFileOps` — it should still appear (command is received), but no subsequent `autoPauseIfNeeded` / `resumePlayback` pair follows.
>
> Record the logcat excerpt in the phase file (link below) for future regressions.

**Verification:**

- Logcat excerpt pasted into `temp/phase06_smoke.log` (committed if the project allows it, else linked).
- No `VrFileOpsOverlayManager: show` lines during immersive test.
- No `pausePlayback` lines triggered by Y / Menu.

**Status:** `[ ]` not done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 6.*` above is `[x] done`.
- [ ] Project compiles — `/build` on `vrDebug`.
- [ ] On-device: Y-button no longer pauses playback in immersive; banner is shown instead; A still toggles pause/play reliably.
- [ ] First-run cheatsheet shows as HUD banner in immersive; persistent flag prevents repeat.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED` still defaults to `false` — no premature flip to `true` from this phase.

---

## Handoff Notes to Next Phase

Phase 07 assumes:

- All code paths for the feature are merged; only documentation + catalog regeneration remain.
- When the future spec B (`spec_vr-ui-composition-layer`) lands and is ready to flip the flag to `true`, Phase 6's guard automatically stops triggering — no code change required in this spec.

---

## Rollback Plan

Revert the phase commit(s). Strings can safely linger across all three locales without a consumer; no user-facing regression from the strings alone. Without the guard the Y-button freeze returns — so revert only if the flag default is flipped to `true` simultaneously via the forthcoming spec B.
