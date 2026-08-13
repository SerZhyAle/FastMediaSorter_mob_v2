# Phase 04 — Overlay Binding

**Strategic spec:** [`../S0021_panel-fps-overlay-landscape.md`](../S0021_panel-fps-overlay-landscape.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Show a small bordered text bubble with the current FPS value in the top-end corner of the flat player whenever `playerShowFps` is on AND a video is actively playing. Hide in pause / no-surface / dialogs / VR-immersive mode.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_player.xml` (or whichever player layout currently exists) | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/res/drawable/bg_fps_overlay.xml` | New | n/a |

> If `PlayerActivity.kt` is currently >1000 LOC, the wiring must go through the existing helpers (e.g. `PlayerObserverManager`) to stay under the cap. Backup before editing if >500 LOC.

---

## Steps

### Step 04.1 — Add the overlay TextView and rounded-corner background drawable

**Files:** `activity_player.xml` + `bg_fps_overlay.xml` (new)
**Depends on:** — start of phase

**Prompt for developer:**

> Add a `TextView` with `android:id="@+id/tvPlayerFpsOverlay"` to the player layout, parented to the same content container that holds the existing playback controls (so insets are inherited). Anchor to top-end with margins consistent with existing toolbar/insets handling. Visibility starts as `View.GONE`. Background = `@drawable/bg_fps_overlay`, padding `@dimen/margin_small`, text size `@dimen/toggler_desc_text_size`, text color white, focusable=false, clickable=false.
>
> Create `bg_fps_overlay.xml` as a `<shape android:shape="rectangle">` with `corners radius=8dp`, `solid color #66000000` (semi-transparent black), `stroke 1dp #80FFFFFF`.

**Verification:**

- `Grep` — `@+id/tvPlayerFpsOverlay` matches exactly once in the player layout file.
- `Glob` — `app_v2/src/main/res/drawable/bg_fps_overlay.xml` exists.
- `Grep` — `android:visibility="gone"` is set on the new view (or `tvPlayerFpsOverlay` line plus its visibility line).

**Status:** `[x]` done

---

### Step 04.2 — Wire `PlayerFpsMeter` lifecycle in `PlayerActivity`

**Files:** `PlayerActivity.kt`
**Depends on:** Step 04.1, Phase 03 ✅

**Prompt for developer:**

> In `PlayerActivity` instantiate a single `PlayerFpsMeter`. Wiring rules:
>
> - In `onResume`, if the current playback state has an active video surface AND `viewModel.settings.value.playerShowFps == true`, call `meter.start()` and set `binding.tvPlayerFpsOverlay.visibility = View.VISIBLE`.
> - In `onPause`, always call `meter.stop()` and set the overlay to `View.GONE`.
> - On dialog show / fallback / non-video file, call `meter.stop()` and `View.GONE`.
> - Collect `meter.fps` in `lifecycleScope.launch { repeatOnLifecycle(STARTED) { meter.fps.collect { binding.tvPlayerFpsOverlay.text = "$it fps" } } }`.
> - Read `viewModel.settings` (StateFlow added in S0023) and re-evaluate visibility on settings change while the activity is in foreground.
>
> Backup `PlayerActivity.kt` to `temp/PlayerActivity.kt.<UTC-timestamp>.backup` before editing if it is over 500 LOC.

**Verification:**

- `Grep` — `PlayerFpsMeter\(\)` or `PlayerFpsMeter()` matches at least once in `PlayerActivity.kt`.
- `Grep` — `meter.start\(\)` and `meter.stop\(\)` each match at least once.
- `Grep` — `tvPlayerFpsOverlay` matches at least 2 times (visibility + text).
- `Grep` — `Log\.d\(` returns zero hits in `PlayerActivity.kt`.
- `Glob` — `temp/PlayerActivity.kt.*.backup` exists if `PlayerActivity.kt` was over 500 LOC pre-edit.

**Status:** `[x]` done

---

### Step 04.3 — Suppress overlay in VR-immersive mode

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `VrPlayerActivity` ensure the inherited `tvPlayerFpsOverlay` View is hidden whenever the activity transitions into immersive (so the existing VR-HUD-FPS handles diagnostics and there's no doubled FPS display). Hook the same place that already toggles the ImmersiveActive flag — set `findViewById<TextView>(R.id.tvPlayerFpsOverlay)?.isVisible = false` on entry; on exit, normal Phase 04 visibility rules apply via the inherited `PlayerActivity` lifecycle.

**Verification:**

- `Grep` — `tvPlayerFpsOverlay` matches at least once in `VrPlayerActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build` for `standard debug` AND `vr debug`.
- [ ] Manual visual check: open a video → bubble appears top-end with rolling fps; pause → bubble disappears; resume → reappears.
- [ ] Dev log entries for layout, drawable, `PlayerActivity.kt`, `VrPlayerActivity.kt`.

---

## Handoff Notes to Next Phase

Phase 05 records the new feature in `docs/FEATURES*.md` and refreshes the catalog.

---

## Rollback Plan

Revert phase commit. The new `PlayerFpsMeter` from Phase 03 becomes dead code; either delete or leave for re-enable.
