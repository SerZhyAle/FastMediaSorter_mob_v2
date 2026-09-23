package com.sza.fastmediasorter.wear.tile

import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.appIdFor
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import com.sza.fastmediasorter.wear.ui.apps.WearAppAccentCatalog
import com.sza.fastmediasorter.wear.ui.apps.WearAppIconCatalog
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.home.HomeSectionIconCatalog
import kotlin.math.pow

/**
 * S2751: the glyph a tile cell wears, chosen where the tile is drawn rather than where it is composed.
 *
 * Every arm delegates to the table the matching screen already draws from - never a drawable literal.
 * One entity wears one glyph on every entrance, and a second literal table beside those two is exactly
 * how a tile and its screen come to disagree about what a program looks like.
 *
 * Exhaustive with no else branch on purpose: a new destination must be given a glyph here rather than
 * silently inherit some default.
 */
@DrawableRes
internal fun tileShortcutIconFor(destination: WearDestinationId): Int {
    // A program is answered through appIdFor, which collapses twelve identical arms into one line and
    // is what keeps this function under detekt's complexity ceiling as the program list grows. The
    // watch broadcast is both a section and a program and is answered as a program - the two
    // entrances are equal and carry the same glyph, so either table gives the same drawable (S2509).
    val program = appIdFor(destination)
    if (program != null) {
        return WearAppIconCatalog.iconFor(program)
    }
    return when (destination) {
        WearDestinationId.RESOURCES -> HomeSectionIconCatalog.iconFor(HomeSectionId.RESOURCES)
        WearDestinationId.PHONE -> HomeSectionIconCatalog.iconFor(HomeSectionId.PHONE)
        WearDestinationId.LOCAL -> HomeSectionIconCatalog.iconFor(HomeSectionId.LOCAL)
        WearDestinationId.STREAMS -> HomeSectionIconCatalog.iconFor(HomeSectionId.STREAMS)
        WearDestinationId.APPS -> HomeSectionIconCatalog.iconFor(HomeSectionId.APPS)
        WearDestinationId.FAVOURITES -> HomeSectionIconCatalog.iconFor(HomeSectionId.FAVOURITES)
        // S2551: a Home section only - it is no program of the Apps grid, so the section table is the
        // only one that answers for it.
        WearDestinationId.PHONE_CAMERA -> HomeSectionIconCatalog.iconFor(HomeSectionId.PHONE_CAMERA)
        // S2511: no catalog answers for the overflow cell - it stands for no entity, it is the way out
        // of the grid into the screen that lists the rest, which is what this glyph says. A home
        // section added without a glyph lands here too, which is the same honest "open elsewhere".
        else -> R.drawable.ic_open_in_new
    }
}

/**
 * S3434: the hue of the plate a tile cell draws its glyph on - ICON-RENDER 0.10 section 10 puts a watch
 * tile on the decorated look, and the hue is the one the same entity already wears on the watch's own
 * screens, so a cell and its row read as one thing.
 *
 * A program answers with its Apps-list accent. A section answers with its home-row tone. Favourites has
 * no tone of its own on the home row, where its star keeps its own amber, so the plate takes that amber.
 * The overflow cell stands for no entity and takes the neutral `OTHER` tone.
 */
@ColorRes
internal fun tilePlateHueFor(destination: WearDestinationId): Int {
    val program = appIdFor(destination)
    val section = HomeSectionId.entries.firstOrNull { id -> destinationFor(id) == destination }
    return when {
        program != null -> WearAppAccentCatalog.accentFor(program)
        section == HomeSectionId.FAVOURITES -> R.color.color_program_accent_amber
        else -> ContentTypeCatalog.tintFor(
            section?.let(HomeSectionIconCatalog::contentTypeFor) ?: WearContentType.OTHER
        )
    }
}

/**
 * The glyph colour on a plate, per ICON-RENDER 0.10 item D: white wherever white reaches 3:1 against the
 * plate, dark otherwise - so the amber and yellow plates, too light for white, still carry a legible glyph.
 */
@ColorInt
internal fun onPlateColorFor(@ColorInt plateArgb: Int): Int {
    val contrastWithWhite = (WHITE_LUMINANCE + LUMINANCE_FLARE) / (relativeLuminance(plateArgb) + LUMINANCE_FLARE)
    return if (contrastWithWhite >= MIN_ON_PLATE_CONTRAST) ON_PLATE_LIGHT else ON_PLATE_DARK
}

/** ICON-RENDER 0.10 item E: the glyph's 24 grid spans this share of the visible plate side. */
internal const val TILE_PLATE_GLYPH_RATIO = 0.6f

private fun relativeLuminance(@ColorInt argb: Int): Double {
    val red = linearChannel((argb shr RED_SHIFT) and CHANNEL_MASK)
    val green = linearChannel((argb shr GREEN_SHIFT) and CHANNEL_MASK)
    val blue = linearChannel(argb and CHANNEL_MASK)
    return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
}

private fun linearChannel(channel: Int): Double {
    val srgb = channel / CHANNEL_MAX
    return if (srgb <= SRGB_LINEAR_KNEE) {
        srgb / SRGB_LINEAR_SLOPE
    } else {
        ((srgb + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)
    }
}

private const val MIN_ON_PLATE_CONTRAST = 3.0
private const val ON_PLATE_LIGHT = 0xFFFFFFFF.toInt()
private const val ON_PLATE_DARK = 0xFF1F1F1F.toInt()
private const val WHITE_LUMINANCE = 1.0
private const val LUMINANCE_FLARE = 0.05
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val CHANNEL_MAX = 255.0
private const val SRGB_LINEAR_KNEE = 0.03928
private const val SRGB_LINEAR_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_GAMMA = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
