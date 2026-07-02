---
name: land-player-bottom-band-stacking
description: layout-land player is ConstraintLayout - every bottom band must chain above bottomPanelsContainer, not anchor to parent bottom (S0368, S0852)
metadata:
  type: project
---

Recurring bug class in `layout-land/activity_player_unified.xml`: portrait player root is a vertical `LinearLayout` (bands stack for free), landscape root is a `ConstraintLayout` - any band naively constrained `bottom_toBottomOf=parent` z-overlaps the Copy/Move `bottomPanelsContainer` (also parent-bottom-anchored, and elevated views win regardless of file order). Happened twice: S0368 (draw toolbar pinning) and S0852 (mini Now Playing bar covered "Move to.." buttons).

**Why:** ConstraintLayout has no implicit stacking; portrait/land parity edits (Rule 11) copy the view but not the stacking semantics. `view_mini_now_playing.xml` root carries `elevation=4dp`, which hides the overlap until both bands are visible at runtime.

**How to apply:** When adding/positioning any bottom-docked band in the landscape player, constrain it `bottom_toTopOf=@+id/bottomPanelsContainer` (Copy/Move stays the bottom-most band per S0368; container also carries the `systemBars.bottom` inset padding, so bands above it stay out of the nav/gesture zone). A gone/empty container collapses to the parent bottom, so the band degrades to the screen edge correctly. Check both visible-together combinations before calling land parity done.
