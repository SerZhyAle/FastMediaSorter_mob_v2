package com.sza.fastmediasorter.domain.model

/**
 * S2533: the horizontal swipe slots over a Browse file row, plus the mapping from a slot to the
 * settings field holding its action.
 *
 * The mapping lives here because more than one caller needs it - the settings rows that write it and
 * the touch callback that reads it - and parallel `when` blocks per caller is how a direction ends up
 * reading one field and writing another.
 *
 * Nothing outside this enum may touch [AppSettings.browseSwipeLeftAction] or
 * [AppSettings.browseSwipeRightAction] directly, and nothing may assume the slot count is two.
 */
enum class BrowseSwipeDirection(val default: BrowseSwipeAction) {
    LEFT(default = BrowseSwipeAction.DELETE),
    RIGHT(default = BrowseSwipeAction.SEND_TO),
    ;

    fun actionOf(settings: AppSettings): BrowseSwipeAction = when (this) {
        LEFT -> settings.browseSwipeLeftAction
        RIGHT -> settings.browseSwipeRightAction
    }

    fun withAction(settings: AppSettings, action: BrowseSwipeAction): AppSettings = when (this) {
        LEFT -> settings.copy(browseSwipeLeftAction = action)
        RIGHT -> settings.copy(browseSwipeRightAction = action)
    }
}
