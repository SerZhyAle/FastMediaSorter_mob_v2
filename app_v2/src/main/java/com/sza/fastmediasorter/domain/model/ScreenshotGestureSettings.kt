package com.sza.fastmediasorter.domain.model

/**
 * Screenshot-gesture zone/action/payload fields, nested out of [AppSettings] (S1470): at 239
 * top-level constructor parameters, [AppSettings]'s synthetic all-defaults constructor hit 256
 * argument slots against the JVM's 255-slot method ceiling, so the class failed runtime
 * verification and the app could not start. Grouping these 32 fields here keeps the on-disk
 * DataStore keys and CSV preset columns unchanged - only the Kotlin property path changes.
 */
data class ScreenshotGestureSettings(
    // S0847: four independently-toggleable edge bands (2 left, 2 right at 10-40% / 60-90% of safe height),
    // each with the DOWN/RIGHT/UP triple - up to 12 gestures. LEFT_TOP enabled by default and seeded from
    // the pre-S0847 single-strip bindings; the other three bands are opt-in (disabled, DO_NOT_USE).
    val zoneLeftTopEnabled: Boolean = true,
    val zoneLeftBottomEnabled: Boolean = false,
    val zoneRightTopEnabled: Boolean = false,
    val zoneRightBottomEnabled: Boolean = false,
    // S1008: per-zone visibility of the semi-transparent grey guide on the first 4 px of the band's edge
    // (S0724 generalised from the single left strip to all four bands). Effective only while the band is
    // enabled and the gesture overlay is on. LEFT_TOP migrates the pre-S1008 single strip-visible flag.
    val zoneLeftTopStripVisible: Boolean = false,
    val zoneLeftBottomStripVisible: Boolean = false,
    val zoneRightTopStripVisible: Boolean = false,
    val zoneRightBottomStripVisible: Boolean = false,
    val leftTopDown: ScreenshotGestureAction = ScreenshotGestureAction.SILENT_SCREENSHOT,
    val leftTopRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val leftTopUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val leftBottomDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val leftBottomRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val leftBottomUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val rightTopDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val rightTopRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val rightTopUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val rightBottomDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val rightBottomRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val rightBottomUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    // S1038: generic per-slot string payload, one per zone x direction slot (mirrors the 12 action
    // fields above). Value-agnostic and shared per ADR-3: it holds the target URL for the S1038
    // "open URL" action and the app package for the S1036 app-selection action. Empty string = unset.
    val payloadLeftTopDown: String = "",
    val payloadLeftTopRight: String = "",
    val payloadLeftTopUp: String = "",
    val payloadLeftBottomDown: String = "",
    val payloadLeftBottomRight: String = "",
    val payloadLeftBottomUp: String = "",
    val payloadRightTopDown: String = "",
    val payloadRightTopRight: String = "",
    val payloadRightTopUp: String = "",
    val payloadRightBottomDown: String = "",
    val payloadRightBottomRight: String = "",
    val payloadRightBottomUp: String = ""
)
