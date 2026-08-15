# Phase 1 - StreamQualityStepDownController (pure Kotlin) + tests

## Steps

1. Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`.
   - Pure Kotlin, no Media3 imports (mirrors `StreamPlaybackDiagnostics` testability contract).
   - Public model: `data class Rendition(val widthPx: Int, val heightPx: Int, val bitrateBps: Int)` and `data class Cap(val maxWidthPx: Int, val maxHeightPx: Int, val maxBitrateBps: Int)`.
   - `fun setRenditions(renditions: List<Rendition>)`: sort ascending by `(bitrateBps, heightPx)` with `NO_VALUE`(-1)/0 bitrate sorted by height; store; reset ceiling index to top (`size - 1`) and stall counter to 0. Deduplicate identical renditions.
   - `fun registerStall(): Cap?`: increment internal stall counter; if `renditionCount <= 1` return null (single-quality, nothing below); if counter `< STALL_STEP_THRESHOLD` return null; else reset counter, and if ceiling index `> 0` decrement it and return the new ceiling rendition as a `Cap` (bitrate cap = that rendition's bitrate when known, else `Int.MAX_VALUE` so only size caps); if already at index 0 return null (at floor).
   - `val isSingleQuality: Boolean`, `val currentCeilingIndex: Int`, `val renditionCount: Int` for logging/tests.
   - `private companion object { const val STALL_STEP_THRESHOLD = 2 }`.
   - **Verification:** file compiles under `./a.ps1 fu` (test task pulls main). No Media3 symbol referenced (grep for `androidx.media3` in the file -> 0).

2. Create `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownControllerTest.kt`.
   - Cases: (a) single-quality (1 rendition) -> registerStall never returns Cap; (b) 3-rendition ladder -> stall below threshold returns null, at threshold returns next-lower Cap; (c) repeated bursts cascade down one rung each threshold-batch and stop at floor (index 0) returning null; (d) hysteresis: counter resets after a step so a lone induced stall does not immediately step again; (e) `NO_VALUE` bitrate renditions sort by height and cap by size; (f) empty list -> inert.
   - **Verification:** `./a.ps1 fu` (or targeted `--tests *StreamQualityStepDownControllerTest`) exit 0.

## Phase-boundary audit

- Confirm controller holds no Android/Media3 type (pure unit-testable core).
- Confirm threshold + floor + single-quality branches all covered by a test.
