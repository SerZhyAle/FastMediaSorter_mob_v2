---
name: shrinking-card-scales-never-hides
description: Owner ruling - when a resizable card shrinks, scale every line and keep the format; never hide a secondary line or drop a field by size
metadata:
  type: feedback
---

When a resizable surface (launcher gadget, tile, card) is made smaller, its content **scales** - it never disappears and never changes shape. Both rejected shapes came up in the S1610 quiz on 2026-08-14 and both were refused:

- No visibility threshold for a secondary line. The date line scales with the time inside a narrow band (9sp..16sp) and the rest of the height goes to the primary line, instead of hiding below some size.
- No size-dependent format. Seconds stay in the clock at every size, including the 2x1 floor.

**Why:** a line that vanishes reads to the user as loss, and a format that changes with the card reads as a bug, not as adaptation. Both of my recommendations (hide below a threshold, drop seconds when small) were the launcher-industry default and both were overruled.

**How to apply:** when specifying or reviewing any resizable card, propose a scaling band for the secondary line first, and keep the data format constant across sizes. Only ask about hiding if the owner raises it. The house gadget pattern (primary value autosized, caption small and fixed) still holds for cards that cannot shrink; this ruling narrows it for any card whose resize floor is below its seed size.
