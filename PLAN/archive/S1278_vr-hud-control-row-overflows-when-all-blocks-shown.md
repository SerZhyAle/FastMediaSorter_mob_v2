# S1278 - The VR HUD control row runs off the strip when every block is shown

**Status:** Archived
**Priority:** 45

<!-- parked by /spec-draft from S1239 - 2026-07-29 -->

## 0. Raw capture

Found while measuring the strip's horizontal budget for **S1239** (the seek bar), which had to know whether the control row had width to spare. It did not - and the row is already 20 px over its own edge before anything is added.

Arithmetic, from the constants in `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt`:

- Reflow area: `ROW_AREA_LEFT` = 700, `ROW_AREA_RIGHT` = `WIDTH - MARGIN` = 2536, so 1836 px usable.
- Worst-case content: audio block 608 + subs block 608 + volume slider 240 + depth slider 240 = 1696 px.
- `relayout()` computes `free` = 1836 - 1696 = 140 and then `gap = maxOf(MIN_BLOCK_GAP, free / (blocks.size + 1))` = `maxOf(40, 28)` = **40**.
- Placing four blocks with five 40 px gaps needs 1696 + 200 = 1896 px in a 1836 px area, so the last slider's right edge lands at **2556** - 20 px past the panel's rounded background and 4 px from the texture edge.

Reproduced by evaluating the same formula against the same constants (2026-07-29); the numbers above are computed, not estimated.

## 1. Why it happens

`gap` is floored at `MIN_BLOCK_GAP` unconditionally. When the visible blocks genuinely do not fit, that floor stops being a minimum and becomes an over-allocation: the packer keeps a 40 px gap it cannot afford and pushes the overflow onto the last block instead of onto the gaps it was sizing.

The other two cases have room to spare and are unaffected:

- audio + volume + depth: 1088 px of content, 187 px gaps, last edge 2349.
- volume + depth only: 480 px of content, 452 px gaps, last edge 2084.

So this only bites the fullest strip: a stereo film with multiple audio tracks and subtitles.

## 2. Impact

The depth slider - the rightmost block - is drawn partly outside the panel background, floating on the transparent part of the quad. Its ray hit test still works, because `dispatchSliderDrag` tests the rect rather than the background, so this reads as a cosmetic defect rather than a broken control. Nothing collides with the hide button, which sits in the header band, not this row.

## 3. Decision (2026-08-13)

Taken by the agent, not by the owner: the four options were not equivalent once the arithmetic was checked against every case rather than only the failing one.

**Chosen: let the gap shrink - that is, drop the `MIN_BLOCK_GAP` floor from the gap expression.**

The floor never binds except in the one case where it causes the defect:

| Visible blocks | Content | `free / (n + 1)` | Floor active? |
|---|---:|---:|---|
| volume + depth | 480 | 452 | no - 452 > 40 |
| audio + volume + depth | 1088 | 187 | no - 187 > 40 |
| audio + subs + volume + depth | 1696 | 28 | **yes - and this is the overflow** |

So the floor is not a spacing guarantee that this fix trades away; it is dead in both healthy cases and harmful in the third. Removing it leaves the other two layouts byte-identical and makes the full strip fit exactly: blocks land at 728, 1364, 2000 and 2268, the last right edge at 2508 against `ROW_AREA_RIGHT` = 2536, with the trailing 28 px gap intact.

The other three options were rejected for a common reason - each trades away information to protect whitespace. Narrowing `VALUE_ZONE_W` or `CAPTION_ZONE_W` ellipsizes track names harder, and shrinking `SLIDER_W` shortens a control's travel, both to preserve a gap that is only a minimum. Wrapping or dropping a block contradicts S1238's premise that every applicable control stays reachable, as the capture already noted.

One thing the original expression did protect and the replacement must keep: `free` goes negative if content ever exceeds the area, which would make the gap negative and stack the blocks backwards. The replacement clamps at zero, so a future overflow degrades to blocks sitting flush rather than overlapping in reverse.

## 4. Related

- **S1238** `vr-hud-adaptive-controls` - owns `relayout()`; currently `BlockNeedUserTest`. Its status note asks the tester to confirm "rows reflow without holes" and says nothing about the right edge, so a headset tester could see the spill and not know it was unintended.
- **S1239** `vr-hud-seek-bar` - measured this while placing the seek band; it deliberately took the empty band above the row instead of competing for the row's width, so it neither causes nor worsens the overflow.
- **S1228** `vr-hud-strip-rework` - set `WIDTH`, `MARGIN` and the strip geometry the arithmetic is against.
