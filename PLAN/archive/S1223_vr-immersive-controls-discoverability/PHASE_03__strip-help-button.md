# Phase 03 - HELP button on the media strip

**Strategic spec:** [`../S1223_vr-immersive-controls-discoverability.md`](../S1223_vr-immersive-controls-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Give the strip a HELP button that brings the legend back after the one-time showing, without disturbing the S1232 rule that the two terminal buttons sit at opposite ends.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] `CODE.LOCK` acquired.

---

## Geometry decided before the first edit

The header band already carries EXIT at the far left and HIDE at the far right, with the file name, the FPS reading and the seek time label right-aligned against HIDE. HELP goes immediately left of HIDE, not next to EXIT: EXIT is the destructive action and S1232 isolated it deliberately, whereas a mis-tap between HELP and HIDE costs one trigger pull to undo.

- `HELP_W = 300f` - wider than `TERMINAL_W` because the localized word is longer than HIDE or EXIT in RU and UK, and `drawButton` does not ellipsize.
- `HEADER_RIGHT_ANCHOR = WIDTH - MARGIN - TERMINAL_W - MARGIN - HELP_W` - one constant, so the three right-aligned elements and the seek band all move together.
- `SEEK_RIGHT` becomes `HEADER_RIGHT_ANCHOR - MARGIN - SEEK_TIME_ZONE_W`, shortening the seek track from 1624 px to 1300 px and leaving no horizontal overlap with the new button.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt` | Modified | <= 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudInteractionDispatcher.kt` | Modified | <= 230 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | <= 1300 |

---

## Steps

### Step 03.1 - Add the button to the strip's model and paint

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the two companion constants and the anchor from the "Geometry decided" block above, add `val helpRect` built from `HEADER_RIGHT_ANCHOR`, add `var helpLabel = "HELP"` beside `hideLabel`/`exitLabel`, and draw it with `drawButton(canvas, helpRect, helpLabel, accentPaint)` next to the HIDE draw call.
>
> Then replace every `hideRect.left` used as a right-hand anchor with `helpRect.left`: the name budget and the FPS position in `drawHeaderLine`, and the time-label position in `drawSeekBar`. Leave `hideRect` itself where it is - HIDE keeps the S1228 close-button position, which the class comment records as the only muscle memory that exists on this strip.
>
> Do not add a `@Suppress` anywhere in this file. It carries baselined detekt findings and a new suppression shifts their signatures (S0826).

**Verification:**

- `Grep` - `helpRect` matches in `HudCanvasRenderer.kt`.
- `Grep` - `hideRect.left` matches exactly once in `HudCanvasRenderer.kt` (the `helpRect` definition), and zero times inside `drawHeaderLine` or `drawSeekBar`.
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

### Step 03.2 - Route the click

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudInteractionDispatcher.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `fun onHelpClick() {}` to `InteractionListener` beside `onHideClick`/`onExitClick`, with the same no-op default so the banner-only diagnostic call site is unaffected.
>
> Add a `renderer.helpRect.contains(px, py) -> { listener.onHelpClick(); true }` branch to `dispatchTerminalClick`. Keep the function name - it is private and renaming it would shift this file's detekt baseline signatures - but update its KDoc: it now dispatches the header band's buttons, of which two are terminal and one is not, and all three are checked before the ordinary controls so a press can never fall through to a transport button underneath.

**Verification:**

- `Grep` - `onHelpClick` matches exactly twice in `HudInteractionDispatcher.kt` (declaration plus call).
- `Grep` - `helpRect.contains` matches exactly once.
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

### Step 03.3 - Show the legend from the host

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Override `onHelpClick()` in `hudInteractionListener`: `hapticBridge.triggerClickFeedback()`, the third debug probe `Timber.d("S1223: HUD help button pressed")`, then `legendController.show()`. Also set `hudRenderer.helpLabel = getString(R.string.vr_hud_help)` next to the other caption assignments.
>
> Do not stop the seek ticker here. Its `shouldRun` predicate already gates on `isPanelHudMode() && hudVisible && ..`, and the legend leaves `hudVisible` true - it occupies the same channel rather than hiding it. Confirm by reading the predicate that a tick during the legend cannot repaint the strip over it: `refreshSeekPosition` calls `scheduleHudPanelRepaint`, which would queue the strip texture onto the legend's quad. If the predicate does not already prevent that, extend it with the legend's visibility rather than stopping the ticker, so the bar is not stale when the legend closes.

**Verification:**

- `Grep` - `S1223: HUD help button pressed` matches exactly once.
- `Grep` - `legendController.show()` matches exactly twice in `DiagnosticXrActivity.kt`.
- Read `seekTicker`'s `shouldRun` predicate and record in the step notes whether it needed the legend term - `expected: no strip repaint while the legend is up | actual: <observed>`.
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 fkn` passes.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Phase-boundary audit run - in particular the S1238 worst case, where both track rows and both sliders are painted, is checked against the shortened header budget by reading the layout arithmetic.

---

## Handoff Notes to Next Phase

Every string used by the legend and the button is still an unresolved resource reference. Phase 04 creates all of them in three locales and syncs the docs.

---

## Rollback Plan

Revert the three files. The legend remains reachable on first entry only.
