# Tactical Spec: S0227 — Copy/Move Panel Adaptive Layout

**Ticket:** S0227
**Status:** Tactical
**Strategic spec:** `PLAN/S0227_copy-move-panel-adaptive-layout.md`

## Phases

- [Phase 1 — Adaptive row distribution](phase1_distribution.md)
- [Phase 2 — Font scaling by button width](phase2_font_scaling.md)
- [Phase 3 — Unit tests](phase3_tests.md)

## Resolved research items

- **§6.1 — landscape panel width:** `bottomPanelsContainer` uses `<include layout="@layout/player_bottom_panels_container_content"/>` with `layout_width="match_parent"` in both portrait and landscape. Panel padding is `2dp` each side (`layout_spacing_small`). Formula: `availableWidthDp = screenWidthDp - 4dp`. No special landscape correction needed.
- **§6.2 — dead code (extended mode, maxRecipients > 10):** left as-is per spec §5.1 — do not delete, do not activate.

## Target file

`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt`

## Constants (companion object)

- `MIN_BUTTON_WIDTH_DP = 58f` — ensures 360 dp phone with 5 buttons keeps one row
- `DOT_DOT_WIDTH_DP = 44f` — wrap_content width of «..» button (~40–48 dp)
- `BUTTON_MARGIN_DP = 4f` — total horizontal margin per button (2dp × 2)
- `PANEL_PADDING_DP = 4f` — total panel horizontal padding (2dp × 2)
- `SP_MIN = 10f`
- `SP_MAX = 16f`

## Acceptance criteria mapping

1. 360 dp + 5 destinations → 1 row (no regression).
2. 360 dp + 6 destinations → 2 rows (no regression).
3. 600 dp + 6–8 destinations → 1 row.
4. 768 dp + 10 destinations → 1 row.
5. Button on 600 dp screen shows larger font than on 412 dp screen (same count).
6. Touch target height ≥ 44 dp on any screen.
