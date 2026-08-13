# S1587 / research 01 - Group packing vs grid width

**Question (§6.1):** fit each starter group to a multiple of the grid width, or forbid upward bleed and accept a trailing partial row?

## Evidence

- Column count is continuous, not a breakpoint: `LauncherGridGeometry.columns(availableWidthDp, densityFactor)` = `floor(width / (96 / density))`, clamped to `3..12` (`app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt:25-29`).
- Observed device: 1080x2400 @450dpi = 384dp usable width, density factor 1.0 -> **4 columns**. A 360dp phone -> 3 columns. A landscape phone -> 7-8. The same starter set is packed at every one of those widths.
- The packer scans from row 0 on every item: `firstFreeAnchor` starts at `row = 0` and returns the first fitting anchor (`LauncherStarterSets.kt:405-413`). It has no notion of a group, so any hole left by a previous group is filled by a later item.
- Group sizes are not constant either: `commonResources` yields 0-6 items depending on which virtual resources resolved, `commonThirdPartyApps` 0-2 depending on installed packages, `commonFeatures` 0-6 depending on build flavor (`LauncherStarterSets.kt:218-251`).

## Verdict

**Forbid upward bleed; accept a trailing partial row.** Fitting groups to a width multiple cannot work:

- The width is unknown at authoring time and legitimately ranges 3..12, so a set sized for 4 leaves a two-slot hole at 3 and a five-slot hole at 7.
- Group membership is runtime-conditional (installed apps, resolved resources, flavor gates), so no static count is stable even at a fixed width.
- The bleed is not only cosmetic: section membership is positional by row (`LauncherSectionMembership`, contract of S1428), so an item that backfills a hole above its own header **joins the wrong section** and collapses with it. That makes this a correctness fix, not a taste call.

Rule to implement: a section header starts a new packing floor. Items placed after a header may never anchor above that header's row. Inside a group, packing stays dense first-free, so no hole appears *within* a group.

Residual cost: the last row of a group may be partly empty. That is honest whitespace under a heading and reads as grouping, unlike the current arbitrary holes.

## Consequences for the plan

- The fix lives in `LauncherStarterSets.place` and is a pure function, so `LauncherStarterSetsTest` covers it without a device.
- Test at 3, 4 and 8 columns: no item anchors above the last preceding header, and no hole exists inside a group's occupied rows other than the trailing row.
