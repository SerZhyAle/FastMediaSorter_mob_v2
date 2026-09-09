package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.scrollAway
import kotlin.math.sqrt

/**
 * Share of the shorter screen edge kept clear of controls on a round display. A chord near the top
 * or bottom of a circle is far shorter than the diameter, so an overlay that reaches an edge loses
 * its ends unless it is inset in proportion to the screen instead of by a fixed dp value.
 */
private const val ROUND_INSET_FRACTION = 0.10f

/**
 * Lightness at which a pinned container stops being dark. Only screens that opt out of the wallpaper
 * pass an opaque background, and their content has to oppose the colour actually painted rather than
 * the one the palette declares (S2522).
 */
private const val PINNED_LUMINANCE_MIDPOINT = 0.5f

/** A square screen clips nothing, so the inset only has to keep content off the bezel. */
private val SQUARE_INSET = 4.dp

/**
 * Share of the shorter edge a square may take before its corners leave a round display.
 *
 * The largest square inscribed in a circle has a side of the diameter divided by the square root of
 * two, about 0.707; 0.70 keeps a margin under that. [ROUND_INSET_FRACTION] alone is not enough for a
 * square: it leaves a content box of 0.8 of the diameter, whose half-diagonal is 0.566 against a
 * glass radius of 0.5, so every corner is outside the display (S2008).
 */
private const val ROUND_SQUARE_FRACTION = 0.70f

/**
 * Shorter screen edge below which a screen is compact.
 *
 * Declared as a const rather than as a `val ..: Dp` because detekt's MagicNumber is active on this
 * module's main sources and exempts a constant declaration but not a property one.
 */
private const val COMPACT_SCREEN_BREAKPOINT_DP = 225

/**
 * The two scroll positions a browse-style screen owns: its list, and the state block that stands in
 * for the list when there is nothing to list.
 *
 * Carried as one value because the branch helpers that need both already take a presentation and an
 * action carrier, and detekt caps a function at eight parameters - the grouping [WearRefineMenuScreen]
 * already applies for the same reason.
 */
data class WearScreenScrolls(
    val list: ScalingLazyListState,
    val stateBlock: ScrollState
)

/**
 * Common root for every screen in the module: a Wear [Scaffold] that always draws [TimeText].
 *
 * Every screen composes through here so the clock survives every state, and so no screen paints its
 * own root background.
 *
 * @param positionIndicator scroll indicator for a scrolling screen, omitted by the ones whose
 * content is full-bleed and does not scroll.
 * @param pageIndicator page dots for a paged screen, usually a `HorizontalPageIndicator`. It belongs
 * here and never among the content: both of its styles measure the whole frame - the linear one is a
 * `fillMaxSize` row, the curved one an arc at the rim - so as an unweighted child of a column it takes
 * the full height and leaves a weighted pager beside it exactly zero, which draws nothing at all
 * (S2056).
 * @param scrollState the scrolling list state when content can move beneath the clock. The clock
 * scrolls away with the list so stationary content is never obscured after a scroll.
 * @param showTimeText false only for the screen-off mode of S1683, where a lit clock would be the one
 * thing still drawn on a screen the user asked to go dark.
 * @param contentPadding defaults to the round-safe inset, so a screen added later is safe without
 * asking for it. Two cases pass `PaddingValues(0.dp)` instead: a full-bleed screen, which wants no
 * inset at all, and a scrolling screen, which hands [wearScreenInsets] to its own list's
 * @param background background color for the screen container, defaulting to Color.Transparent so the
 * window wallpaper is visible beneath navigation screens.
 */
@Composable
@Suppress("LongParameterList")
fun WearScreenScaffold(
    modifier: Modifier = Modifier,
    positionIndicator: (@Composable () -> Unit)? = null,
    pageIndicator: (@Composable () -> Unit)? = null,
    scrollState: ScalingLazyListState? = null,
    showTimeText: Boolean = true,
    contentPadding: PaddingValues = wearScreenInsets(),
    background: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    val wallpaperState = LocalWearWallpaperState.current
    // S2522: a screen that pins its own opaque container has stepped outside the scheme's background,
    // so its content opposes what is actually painted instead of what the palette declares. Without
    // this the two wallpaper-less screens draw a light scheme's near-black content onto pinned black.
    val contentColor = when {
        background == Color.Transparent -> MaterialTheme.colors.onBackground
        background.luminance() < PINNED_LUMINANCE_MIDPOINT -> Color.White
        else -> Color.Black
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        positionIndicator = positionIndicator,
        pageIndicator = pageIndicator,
        timeText = if (showTimeText) {
            {
                // S2522: the colour is passed explicitly because the clock does not follow the palette
                // on its own - the library Scaffold does not wrap this slot in a content colour, so
                // TimeText resolves to the hardcoded white below LocalContentColor. The theme now
                // provides that local (strategic ADR-6), but this slot sits outside the content Box,
                // so it is coloured here as well - and from the same value, so a screen that pins its
                // own container gets a clock that opposes the container rather than the palette.
                val textStyle = TimeTextDefaults.timeTextStyle().copy(
                    color = contentColor,
                    shadow = Shadow(
                        // The shadow opposes the text for the same reason the wallpaper scrim does:
                        // the clock is drawn over an uncontrolled picture.
                        color = if (contentColor.luminance() < PINNED_LUMINANCE_MIDPOINT) {
                            Color.White
                        } else {
                            Color.Black
                        },
                        offset = Offset(1f, 1f),
                        blurRadius = 4f
                    )
                )
                TimeText(
                    timeTextStyle = textStyle,
                    modifier = if (scrollState == null) Modifier else Modifier.scrollAway(scrollState)
                )
            }
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (background == Color.Transparent && wallpaperState.showsWallpaper) {
                WearAppBackground(
                    background = wallpaperState.background,
                    running = wallpaperState.isResumed
                )
            }
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Background before padding: the colour has to reach the frame edge, while the
                        // content stops short of it. The reverse order leaves an unpainted ring at the
                        // rim.
                        .background(background)
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                    content = content
                )
            }
        }
    }
}

/**
 * Padding that keeps content inside the visible area of the current screen.
 *
 * Derived from the shape and size the platform reports, never from the one watch this project
 * happens to own - a padding tuned to a single device breaks silently on the next one.
 */
@Composable
fun wearScreenInsets(): PaddingValues {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) {
        val shorterEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
        PaddingValues(shorterEdge * ROUND_INSET_FRACTION)
    } else {
        PaddingValues(SQUARE_INSET)
    }
}

/**
 * Side of the largest square this display can draw whole.
 *
 * The module's second statement about screen shape, kept beside the first so the two are read
 * together: [wearScreenInsets] insets a rectangle proportionally, which is right for text and rows,
 * and is not enough for a square, whose corners reach further from the centre than its edges do. A
 * screen drawing a square - a game board, a grid, a dial - caps it with this rather than inventing a
 * fraction of its own.
 */
@Composable
fun wearMaxSquareSide(): Dp {
    val configuration = LocalConfiguration.current
    val shorterEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    return if (configuration.isScreenRound) {
        shorterEdge * ROUND_SQUARE_FRACTION
    } else {
        shorterEdge - SQUARE_INSET * 2
    }
}

/**
 * Clearance between the largest square [wearMaxSquareSide] admits and the edge of the display.
 *
 * The module's third statement about screen shape, kept beside the other two. Safe only at the
 * middle of a side: that is where the circle stands furthest from the square it circumscribes, and
 * the same clearance collapses to nothing at the square's corners, which touch the glass. A caller
 * placing something in this band therefore has four usable segments and no usable corners.
 */
@Composable
fun wearRingInset(): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) {
        val shorterEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
        (shorterEdge - wearMaxSquareSide()) / 2
    } else {
        SQUARE_INSET
    }
}

/**
 * Clearance a control needs when it stands at the middle of the left or right side.
 *
 * The module's fourth statement about screen shape, and the only one that is not about a rectangle:
 * [wearScreenInsets], [wearMaxSquareSide] and [wearRingInset] each measure a box against the glass,
 * so each answers with the clearance a CORNER needs. A control at the middle of a side has no corner
 * out there - the chord at that height is the full diameter - so paying the square's clearance
 * pushes it about a seventh of the display inwards, which lands it on the content band that starts
 * at [wearScreenInsets] instead of in the free ring beside it (S2472: the affordance was reported
 * overhanging the list rather than standing to the left of it).
 *
 * What such a control does need is the sagitta over its own height: at its top and bottom edges the
 * circle has already fallen away from its leftmost point by `r - sqrt(r^2 - (height/2)^2)`.
 * [SQUARE_INSET] is the floor, because for a finger-sized control that sagitta is under 2 dp and a
 * mark touching the glass reads as clipped even while it is whole.
 *
 * @param controlHeight height of the control being placed; the sagitta is measured at its ends.
 */
@Composable
fun wearSideBandInset(controlHeight: Dp): Dp {
    val configuration = LocalConfiguration.current
    if (!configuration.isScreenRound) {
        return SQUARE_INSET
    }
    val radius = wearScreenRadius()
    return sagitta(radius, controlHeight.value / 2).dp.coerceAtLeast(SQUARE_INSET)
}

/**
 * Horizontal clearance a full-width element needs when its worst edge stands [edgeOffset] from the
 * top or the bottom of the display.
 *
 * The module's fifth statement about screen shape, and the one the first four could not make.
 * [wearScreenInsets] states a single proportional clearance for the whole screen, which is true at the
 * vertical middle, where the chord is the full diameter, and too small everywhere else; [wearRingInset]
 * and [wearSideBandInset] answer for a box and for a control at the middle of a side. None of them
 * answers for a band that reaches the full width near an edge. On a 227 dp watch the chord 24 dp below
 * the top is about 136 dp, so a band inset by that screen's own tenth still runs about 22 dp past the
 * glass on each side - which is what Google Play rejected the watch build for on 2026-09-08 (S2273),
 * on two screens that were already applying [wearScreenInsets].
 *
 * Measure at the element's WORST edge - the top edge of a band near the top, the bottom edge of one
 * near the bottom - because that is where its chord is shortest. [SQUARE_INSET] is the floor and the
 * whole answer on a screen that is not round.
 *
 * @param edgeOffset distance from the nearer of the top and bottom edges of the display to the edge of
 * the element being placed.
 */
@Composable
fun wearChordInset(edgeOffset: Dp): Dp {
    val configuration = LocalConfiguration.current
    if (!configuration.isScreenRound) {
        return SQUARE_INSET
    }
    val radius = wearScreenRadius()
    val distanceFromCentre = (radius - edgeOffset.value).coerceIn(0f, radius)
    return sagitta(radius, distanceFromCentre).dp.coerceAtLeast(SQUARE_INSET)
}

/**
 * How far from the top or the bottom edge of the display a band of fixed width [bandWidth] has to
 * stand before the glass is wide enough to hold it.
 *
 * The module's sixth statement about screen shape, and [wearChordInset] read backwards: the same
 * sagitta over the same chord, asked as "how far down must this move" instead of "how narrow must this
 * become". The two answers are not interchangeable to a caller, which is why both exist. A row of
 * finger-sized buttons cannot pay the first one: taking the inset out of its own width drops every
 * target under the 48 dp minimum the module protects everywhere else, so the only move left is to
 * lower the row until the chord admits it (S2273).
 *
 * Exact inverses, so lowering a band to this offset and then asking [wearChordInset] for that offset
 * returns the padding that leaves exactly [bandWidth] between its two sides.
 *
 * @param bandWidth width the element cannot give up.
 */
@Composable
fun wearBandEdgeOffset(bandWidth: Dp): Dp {
    val configuration = LocalConfiguration.current
    if (!configuration.isScreenRound) {
        return SQUARE_INSET
    }
    val radius = wearScreenRadius()
    return sagitta(radius, bandWidth.value / 2).dp.coerceAtLeast(SQUARE_INSET)
}

/**
 * Whether this screen is small enough to need a different set of children rather than smaller ones.
 *
 * The module's fifth statement about screen shape, and the only one that does not return a
 * measurement. The other four scale a number to the glass, which is right whenever the content fits
 * and only has to be placed; this one answers the case where it does not fit at all, and a
 * proportion cannot remove a child (S2766). The threshold is Google's own break between a small and
 * a large round watch, and it separates the profiles this module verifies against - 192 dp below
 * it, 227 dp and 240 dp above.
 */
@Composable
fun wearIsCompactScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return minOf(configuration.screenWidthDp, configuration.screenHeightDp) < COMPACT_SCREEN_BREAKPOINT_DP
}

/** Radius of the glass in dp, from the shape the platform reports rather than from a known watch. */
@Composable
private fun wearScreenRadius(): Float {
    val configuration = LocalConfiguration.current
    return minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat() / 2
}

/**
 * Rise of the arc over a chord whose half-length is [halfChord]: `r - sqrt(r^2 - halfChord^2)`.
 *
 * The one piece of geometry the shape helpers share, so a correction lands in one place. Clamped at
 * zero because a caller may name a chord wider than the display, and a negative square root would
 * answer that with a crash rather than with the largest inset the screen can give.
 */
private fun sagitta(radius: Float, halfChord: Float): Float =
    radius - sqrt((radius * radius - halfChord * halfChord).coerceAtLeast(0f))
