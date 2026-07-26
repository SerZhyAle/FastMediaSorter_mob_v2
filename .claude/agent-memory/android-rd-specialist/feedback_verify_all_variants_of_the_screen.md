---
name: verify-all-variants-of-the-screen
description: Device-verify every variant of a changed screen (both media kinds, both filters, both display modes) - not just the branch you touched
metadata:
  type: feedback
---

When verifying a screen change on a device, walk every variant the user can land on - not only the one your change touches.

**Why:** on 2026-07-26 (S1154 atlas) I verified the streams grid with the VIDEO filter only, because the atlas tier is video-only, and reported it working. The owner opened the same grid with radio channels and saw rows of empty grey tiles: a tile with no captured frame, no atlas tile and no favicon rendered nothing at all. The defect was one branch away from what I tested, and he found it in seconds. Roughly a third of catalog channels have no favicon, so it was not an edge case.

**How to apply:** before claiming a screen works, enumerate its axes (media kind AUDIO/VIDEO, list vs grid, filter on/off, empty vs populated data, portrait vs landscape) and open the combinations that the change could touch, including the "no data at all" one. A screenshot per variant costs a minute; a wrong "verified" costs the owner's trust.
