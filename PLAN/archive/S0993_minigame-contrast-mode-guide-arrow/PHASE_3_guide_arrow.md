# PHASE 3 - Start-of-level guide arrow (all modes)

**Ticket:** S0993
Goal: during the existing start-highlight window (~1 s at level start), draw an arrow from the player to the nearest exit, in every mode. Reuses the intro-window lifecycle - no new timer (ADR-3).

## Steps

1. `GameBoardRenderMapper` - add a render-state field carrying the arrow target:
   - New data class `GameBoardGuideArrow(val fromRow: Int, val fromColumn: Int, val toRow: Int, val toColumn: Int)`.
   - Add `val guideArrow: GameBoardGuideArrow?` to `GameBoardRenderState`.
   - In `map`, compute the nearest exit to the player by Manhattan distance over `board.exitPositions()`; if at least one exit exists, set `guideArrow` from player -> nearest exit, else `null`. Reuse `GamePosition.manhattanDistanceTo`.
   - Verification: with player at (r,c) and two exits, `guideArrow.to` is the closer exit; with no exit, `guideArrow == null`.

2. `GameBoardView` - add an arrow paint field (STROKE, rounded cap/join, a distinct colour readable over all skins, e.g. amber `#FFFFA000`) and reuse `actorPath` for the arrowhead. Add `drawGuideArrow(canvas, scale, arrow)`:
   - Compute player-centre and exit-centre from `scale` (same maths as `drawDefeatConnection`).
   - Draw the shaft line, then an arrowhead at the exit end: two short segments at +-`ARROW_HEAD_ANGLE` from the shaft direction, length `scale.cellSize * ARROW_HEAD_FACTOR`. Use `atan2` for direction; guard the zero-length case (player already on the exit) by skipping the arrow.
   - Stroke width `max(MIN_ARROW_WIDTH, scale.cellSize * ARROW_WIDTH_FACTOR)`.
   - Verification: arrow points from player to the nearest exit with a visible head; no draw when player is on the exit.

3. `GameBoardView.onDraw` - call `drawGuideArrow` only inside the intro window, i.e. within the same `SystemClock.uptimeMillis() < introHighlightUntilMs` guard used by `drawIntroHighlights`, and only when `currentRenderState.guideArrow != null`. Draw it after `drawIntroHighlights` (on top of the start-highlight cells) and before the board border. Since `introHighlightUntilMs` only re-arms when `introHighlightKey` changes (new level/seed), the arrow naturally shows once per level start and never after the first move.
   - Add companion constants `ARROW_HEAD_ANGLE`, `ARROW_HEAD_FACTOR`, `ARROW_WIDTH_FACTOR`, `MIN_ARROW_WIDTH`.
   - Verification: arrow visible for ~1 s at level start in all three modes, gone after the window / after the first move; `postInvalidateOnAnimation` already driven by `drawIntroHighlights` keeps it animating during the window.

## Notes

- Multiple-exit target rule = nearest (strategic §6.3). Arrow is mode-agnostic (not theme-gated) - it renders identically in Classic/Kryvavitsa/Contrast.
- Colour is a Canvas paint in code, not a layout hex (Rule 19 unaffected).

## Done when

- Standard debug compiles.
- Every mode shows the player -> nearest-exit arrow during the start window and hides it afterwards.
