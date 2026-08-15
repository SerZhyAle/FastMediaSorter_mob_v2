# S1238 - The immersive HUD shows controls that do not apply to the current media

**Status:** Archived
**Priority:** 65

## 0. Raw capture

Owner, 2026-07-28:

> - в HUD показывать выбор дорожки - если несколько дорожек
> - в HUD показывать выбор субтитров - если есть субтитры
> - в HUD показывать выбор глубину 3D - если это стереокартика

## 1. Current behaviour

The strip always paints every row. Availability is expressed by **dimming**, not by absence:

- `HudCanvasRenderer.audioRowEnabled` / `subsRowEnabled` pick a disabled paint and a dim text alpha; the caption, both arrows and the value still occupy their full width.
- The STEREO DEPTH slider has no availability flag at all - it is drawn for mono content too, where moving it does nothing meaningful.

Dimming was the S0964 answer when the panel was a tall block with room to spare. On the S1228 strip the whole control set competes for one horizontal row, so a permanently-present dead control costs space that a live one could use.

## 2. Goals

1. The audio row appears only when the media has **more than one** audio track. One track is not a choice.
2. The subtitle row appears only when at least one subtitle track exists.
3. The stereo-depth slider appears only when the resolved layout is stereo - `SIDE_BY_SIDE` or `TOP_BOTTOM`, not `MONO`.
4. Remaining controls reflow to use the freed width rather than leaving holes.

**Non-goals:**

- The seek bar - **S1239**.
- Which controller button summons the strip - **S1240**.

## 3. Design notes

- The layout is currently a set of `private const val` X positions computed at class-init and exposed as `val` rects. Conditional rows mean the rects must be computed per state, not once - the hit-test rects in `HudInteractionDispatcher` read those same properties, so both must be recomputed together or the ray will address stale regions.
- Stereo availability is already known: `VrStereoConfigResolver.resolve()` returns the layout, and `DiagnosticXrActivity` holds it. Track availability is already computed for the dim flags, so no new data source is needed for any of the three.
- Decide whether a row that disappears mid-playback (a track set arriving late from `onTracksChanged`) reflows immediately or waits for the next repaint - the repaint is already debounced ~100 ms, so immediate is probably fine.

## 4. Interaction with pending work

The reflow makes the strip narrower for simple content, which changes what "the quad is 1.40 m wide" should mean. Consider whether the quad width should follow the content or stay fixed - a fixed quad with a centred, narrower strip keeps the ray's muscle memory stable, which matters more than filling the width.

## 5. Related

- **S1228** - the strip being modified.
- **S0964** - introduced the rows and the dimming convention this replaces.

## 6. Implementation (2026-07-28)

- `HudCanvasRenderer`: fixed X anchors replaced by `relayout()` - visible blocks (audio, subs,
  volume, depth) pack across the row area right of the transport trio with evenly distributed
  gaps (justify). Availability setters relayout automatically; hidden blocks get `setEmpty()`
  rects, so `RectF.contains()` misses on top of the dispatcher's flag guards. The dispatcher
  hit-tests the same mutable `RectF` instances, so paint and ray stay in sync by construction
  (spec section 3 concern). Dim machinery (disabled/dim paints and their colour constants)
  deleted - absence replaced dimming (Rule 20).
- Quad width stays fixed, per section 4's recommendation - the strip centres its content.
- `HudInteractionDispatcher`: depth drag gated by `depthRowVisible` (clicks on audio/subs were
  already flag-gated; hidden depth slider must not react to ray drags).
- `DiagnosticXrActivity`: `depthRowVisible = layout != MONO` at all four `setRenderConfig`
  call sites (bundled asset, image decode, playlist navigation, session-ready requeue); flags
  refresh on `onTracksChanged` via the existing `refreshTrackRows()` (semantics already were
  "more than one audio track" / "any subtitle track" - the S1238 change is presence, not policy).
- Late `onTracksChanged` reflow is immediate; the ~100 ms repaint debounce absorbs bursts
  (section 3's open point resolved as the spec suggested).
- Debug probe `Timber.d("S1238: hud rows ..")` at `refreshTrackRows` - removed when the ticket
  leaves BlockNeedUserTest.

## 7. Verification

- Compile: vr flavor (`check-standard-fast -Mode Code -Flavor Vr`) and noLegal (`a.ps1 fkn`) -
  both source sets that ship `src/vr`.
- On-device (Quest, human): mono video with one audio track -> only VOLUME slider next to
  transport; stereo (SBS/TB) file -> STEREO DEPTH appears; multi-audio file -> AUDIO row with
  working `<`/`>`; subtitled file -> SUBS row; rows reflow with no holes and ray hits match
  the painted positions (the S1132/S1228 UV-flip class).
