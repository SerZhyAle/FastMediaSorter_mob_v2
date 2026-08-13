# Phase 1 — Adaptive Row Distribution

## What

Replace the hardcoded lookup table in `populateDestinationButtons` with a width-aware check
that collapses all buttons into one row when the available space allows it.

## Where

`DestinationButtonsManager.kt` — standard mode branch (maxRecipients ≤ 10).

## Logic

```
availableWidthDp = screenWidthDp - PANEL_PADDING_DP
maxPerRow = floor((availableWidthDp - DOT_DOT_WIDTH_DP - BUTTON_MARGIN_DP) / (MIN_BUTTON_WIDTH_DP + BUTTON_MARGIN_DP))
if count <= maxPerRow → distribution = listOf(count)   // single row
else → existing lookup table (unchanged)
```

Pure helper: `computeMaxPerRow(availableWidthDp: Float): Int` in companion object.

## Verification

- 360 dp: `maxPerRow = floor((356 - 44 - 4) / 62) = floor(308/62) = 4` → 5 buttons does NOT fit single row → lookup table → `listOf(5)` as before. ✓
- Wait — `count=5, maxPerRow=4`: lookup table returns `listOf(5)`. That's correct (1 row with 5).
- `count=6, maxPerRow=4`: lookup returns `listOf(3,3)`. ✓
- 600 dp: `maxPerRow = floor((596 - 44 - 4) / 62) = floor(548/62) = 8` → `count=6 ≤ 8` → `listOf(6)`. ✓
- 768 dp: `maxPerRow = floor((764 - 44 - 4) / 62) = floor(716/62) = 11` → `count=10 ≤ 11` → `listOf(10)`. ✓

## Notes

- `computeMaxPerRow` is a pure function — takes `Float`, returns `Int`. Covered by unit tests.
- Extended mode branch (maxRecipients > 10) remains unchanged.
