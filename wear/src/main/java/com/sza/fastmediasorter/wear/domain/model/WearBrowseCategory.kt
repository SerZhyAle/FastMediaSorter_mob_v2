package com.sza.fastmediasorter.wear.domain.model

/**
 * One entry in the row of content categories a source offers.
 *
 * S2130: before this type the same three facts - what a category stands for, the token it travels by
 * and whether it opens flat or walks folders - were split between a private data class on each of
 * three screens and a string literal at each navigation call. Nothing tied them together, so a screen
 * could rename a category without the request handler ever hearing about it.
 *
 * The label and the glyph stay out of here, in `ui/common/BrowseCategoryPresentation`, exactly as
 * [WearContentType] keeps its tone and glyph out: this module holds resource ids and Compose types
 * outside the domain layer, and composition changes for different reasons than appearance.
 */
data class WearBrowseCategory(
    val type: WearContentType,
    val token: String,
    val shape: WearListShape
)

/**
 * Where the files behind a category come from.
 *
 * The origin is what the availability rule is asked about - the watch can present different types
 * depending on where they live, and that is a property of the source, not of the screen showing it.
 */
enum class WearCategoryOrigin {
    LOCAL,
    PHONE,
    NETWORK_SOURCE
}

/** Whether a category opens a flat list of files or a walk through folders. */
enum class WearListShape {
    FLAT_MEDIA,
    FOLDER_WALK
}
