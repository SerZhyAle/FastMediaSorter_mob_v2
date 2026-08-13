# PHASE 2 - Contrast solid-cube rendering + legible movement

**Ticket:** S0993
Goal: in CONTRAST, draw floor/wall/exit/actors as solid contrasting cells, and make a move readable frame by frame despite identical cubes. All new drawing reuses cached `Paint`/`Path` - no per-frame allocation.

## Steps

1. `GameBoardView.applyTheme` - the theme already drives `floorPaint.color`/`wallPaint.color`. No change needed there; contrast floor/wall come from the theme (Phase 1). Add a solid exit fill paint field (FILL, colour set from `theme.filledExitColor` in `applyTheme`), e.g. `private val exitFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }`.
   - Verification: field compiles; colour applied in `applyTheme` when `theme.filledExitColor != null`.

2. `GameBoardView.onDraw` exit branch - when `theme.filledExitColor != null`, draw the exit as a solid fill (`canvas.drawRect(cellRect, exitFillPaint)`) instead of `drawExit`/door. Keep the grid line. Classic/Kryvavitsa keep current behaviour (door drawable or framed square).
   - Verification: in CONTRAST the exit cell is a solid green block; in CLASSIC unchanged (framed square); in KRYVAVITSA unchanged (door).

3. `GameBoardView.drawActor` - when `theme.filledActors`, fill the cell (inset slightly so the grid stays visible) with the actor paint (`playerPaint`/`kryvavitsaPaint`/`shadowPaint`) via `canvas.drawRect`, and return before the primitive/drawable path. Reuse a small inset constant. Colours inherit the existing actor hues (blue/red/purple) - satisfies "наследуют существующие".
   - Verification: in CONTRAST player/enemies render as solid colour squares; other modes unchanged.

4. `GameBoardView.drawAnimatedActors` - add a stepped path when `theme.steppedMove`:
   - Quantize `progress` to discrete frames: `val stepped = floor(progress * STEP_FRAMES) / STEP_FRAMES` and interpolate position with `stepped` instead of continuous `progress`, so the cube visibly jumps through a few intermediate points (direction becomes readable).
   - Draw a fading ghost of the actor at the source cell while `progress < GHOST_UNTIL` (e.g. 0.5): same `drawActor` at the from-cell with a temporarily lowered alpha, restored after. Ghost + stepped hop together make "who went where" legible.
   - Non-stepped modes keep the current smooth slide (and `stomp` hop for Kryvavitsa) unchanged.
   - Add companion constants `STEP_FRAMES` (e.g. 4) and `GHOST_UNTIL` (e.g. 0.5f). Use `TimeUnit`/`const` values, no bare literals in logic beyond named consts.
   - Verification: in CONTRAST a move shows a stepped hop with a brief source ghost; CLASSIC slide and KRYVAVITSA stomp unchanged; `onDraw` allocation-free (no `new` in the hot path).

## Notes

- Movement-frame style is a tunable UX point (strategic §6.2). This phase ships the default (stepped hop + source ghost); final tuning happens at device test.
- Keep every new colour out of XML layout - these are Canvas paints in code, not layout attributes (Rule 19 concerns `res/layout*`).

## Done when

- Standard debug compiles.
- CONTRAST board renders solid cubes; a move is directionally readable; other modes visually unchanged.
