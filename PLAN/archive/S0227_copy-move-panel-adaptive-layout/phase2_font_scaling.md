# Phase 2 — Font Scaling by Button Width

## What

Compute actual button width from available space and row count, then interpolate font size
in `[SP_MIN, SP_MAX]`. Remove `@Suppress("UNUSED_PARAMETER")` from `buttonsInRow`.

## Where

`DestinationButtonsManager.kt` — `createDestinationButton` and its callers.

## Parameters added

`populateDestinationButtons` computes `availableWidthDp` once, then passes it to
`createDestinationButton` alongside `buttonsInRow`.

## Logic

```
// After distribution is determined:
val buttonsInFirstRow = distribution.first()
val buttonWidthDp = (availableWidthDp - DOT_DOT_WIDTH_DP - BUTTON_MARGIN_DP * buttonsInFirstRow) / buttonsInFirstRow
// clamp: buttonWidthDp in [MIN_BUTTON_WIDTH_DP .. 200f]
val fontSizeSp = computeFontSizeSp(buttonWidthDp)
```

Pure helper: `computeFontSizeSp(buttonWidthDp: Float): Float` — linear interpolation:
```
t = (buttonWidthDp - MIN_BUTTON_WIDTH_DP) / (200f - MIN_BUTTON_WIDTH_DP)
result = SP_MIN + t * (SP_MAX - SP_MIN)
```
Clamped to `[SP_MIN, SP_MAX]`.

## Signature change

```kotlin
private fun createDestinationButton(
    destination: MediaResource,
    index: Int,
    isCopy: Boolean,
    buttonsInRow: Int,
    fontSizeSp: Float   // NEW — replaces length-based calculation
): MaterialButton
```

Existing `@Suppress("UNUSED_PARAMETER")` annotations on `index` and `buttonsInRow` removed.
`index` is still unused but retained for future use; keep suppress on index only.

## Verification

- 412 dp, 5 buttons: `buttonWidthDp = (408 - 44 - 20) / 5 = 68.8` → `t ≈ 0.076` → `~10.5 sp`. Comparable to current 12 sp (acceptable — current logic is length-based, not consistent).
- 600 dp, 5 buttons, single row: `buttonWidthDp = (596 - 44 - 20) / 5 = 106.4` → `t ≈ 0.337` → `~12 sp`.
- 600 dp, 6 buttons, single row (S0227 new path): `buttonWidthDp = (596 - 44 - 24) / 6 = 88` → `t ≈ 0.21` → `~11.3 sp`. Larger than the 2-row variant with 412 dp.
- 768 dp, 10 buttons: `buttonWidthDp = (764 - 44 - 40) / 10 = 68` → `t ≈ 0.07` → `~10.4 sp`.
