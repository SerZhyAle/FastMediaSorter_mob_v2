# Phase 04 - detection-geometry

**Goal:** Rewrite `ScreenGestureOverlayManager` from a single left-edge strip into 4 independently-shown edge-band views (2 left, 2 right at 10-40% / 60-90% of safe height), each classifying an inward drag into DOWN/RIGHT/UP and reporting `(zone, direction)`. Right-edge bands mirror the drag (inward = leftward).

**Depends on:** 03.
**Source set:** `src/screenCapture` (shared by standard + noLegal hosts).

---

## Steps

### [ ] 04.1 - Per-zone strip spec + placement

- Replace the single `stripView`/`computeStripSpec` with a per-zone model. For each enabled zone add its own overlay `View`:
  - `LEFT_TOP`: `x=0`, `Gravity.START|TOP`, `top = safeTop + safeHeight*0.10`, `height = safeHeight*0.30`.
  - `LEFT_BOTTOM`: `x=0`, `top = safeTop + safeHeight*0.60`, `height = safeHeight*0.30`.
  - `RIGHT_TOP`: `x = screenWidth - stripWidth` (`Gravity.END|TOP` or explicit x), `top = safeTop + safeHeight*0.10`, `height = safeHeight*0.30`.
  - `RIGHT_BOTTOM`: right anchor, `top = safeTop + safeHeight*0.60`, `height = safeHeight*0.30`.
- Extract the band fractions to named constants: `BAND_TOP_START=0.10f`, `BAND_BOTTOM_START=0.60f`, `BAND_HEIGHT=0.30f`.
- `show(...)` takes the 4 enabled flags (from settings) and adds only enabled bands; `hide()` removes all added views.
- **Verification:** with all 4 enabled, 4 overlay windows are added at the specified rects; with only LEFT_TOP enabled, exactly one window at the legacy-equivalent top-left band.

### [ ] 04.2 - Per-band touch + inward drag with right-edge mirroring

- Each band's touch listener knows its zone (capture zone in the listener closure or a per-view tag). On ACTION_MOVE compute `dx,dy`. For left zones require `dx>0`; for right zones require `dx<0` and negate `dx` before the angle math so the same classifier applies.
  ```kotlin
  val inwardDx = if (zone.isRightEdge) -dx else dx
  if (inwardDx <= 0f) return false
  if (hypot(inwardDx, dy) < GESTURE_DISTANCE_PX) return true
  val direction = directionForAngle(Math.toDegrees(atan2(dy, inwardDx).toDouble())) ?: return false
  onGestureMatched(zone, direction)
  ```
- Keep the existing UP/RIGHT/DOWN angle windows and `GESTURE_DISTANCE_PX`; they are device-test tuning candidates (Step 04.4).
- **Verification:** a rightward drag on a left band and a leftward drag on a right band both classify into the same direction set; opposite-direction drags return false (fall through to the app).

### [ ] 04.3 - Visible-edge guide per band

- Reuse `EdgeGuideDrawable`; when `screenshotGestureStripVisible` is on, draw the 4px guide on the inner edge of each band (right bands draw the guide on their left/inner edge). Transparent otherwise.
- **Verification:** with strip-visible on, each enabled band shows a 4px guide on its inner edge; hidden = fully transparent.

### [ ] 04.4 - Device tuning on emulator-5556 (BlockNeedUserTest gate)

- Insert `Timber.d("S0847: edge band <zone> matched <direction>")` at `onGestureMatched` (single probe) before the phase build.
- Manual (device): enable all 4 zones, bind a distinct visible action per zone (e.g. OPEN_PANEL) and confirm each band fires only its own zone; confirm the middle 40-60% gap and the opposite edge do not trigger; confirm right-edge inward (leftward) swipes classify UP/RIGHT/DOWN correctly.
- **Verification:** logcat shows the expected `zone/direction` per band; no cross-zone misfires. Recorded in `## Last Audit`.

---

## Phase Done Criteria

- [ ] 4 band views placed per the 10-40% / 60-90% geometry; only enabled ones shown.
- [ ] Inward-drag classification works on both edges (right-edge mirrored).
- [ ] `S0847:` probe present (removed on transition out of BlockNeedUserTest).
- [ ] `.\a.ps1 fk` passes.
