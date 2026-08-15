# Phase 3 — Unit Tests

## Target

`app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManagerTest.kt`

## Test cases — computeMaxPerRow

- 360 dp → maxPerRow = 4
- 412 dp → maxPerRow = 5
- 600 dp → maxPerRow = 8
- 768 dp → maxPerRow = 11

## Test cases — computeFontSizeSp

- buttonWidthDp = MIN_BUTTON_WIDTH_DP → SP_MIN
- buttonWidthDp = 200f → SP_MAX
- buttonWidthDp = 106f → ~12 sp (within [SP_MIN, SP_MAX])
- buttonWidthDp < MIN_BUTTON_WIDTH_DP → clamped to SP_MIN
- buttonWidthDp > 200f → clamped to SP_MAX

## Test cases — distribution logic (integration)

Using the adaptive logic on top of lookup table:
- screenWidthDp=360, count=5 → listOf(5) (lookup table — maxPerRow=4 < 5, but lookup still returns single row of 5)
- screenWidthDp=360, count=6 → listOf(3,3)
- screenWidthDp=600, count=6 → listOf(6) (adaptive single row)
- screenWidthDp=768, count=10 → listOf(10) (adaptive single row)

Note: 360 dp + 5 buttons: `maxPerRow = 4`, `count=5 > 4` → lookup table → `when(5) { listOf(5) }`. Correct.
