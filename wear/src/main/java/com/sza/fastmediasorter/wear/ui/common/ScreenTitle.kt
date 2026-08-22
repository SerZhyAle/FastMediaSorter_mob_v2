package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes

/**
 * A screen title the view model can name without holding a Context: either text that came from the
 * user's own data, or a resource the screen resolves.
 *
 * Shared by every watch screen rather than owned by one of them: a second screen needing the same
 * two forms would otherwise import a type belonging to another feature package, or declare its own
 * copy - and a second copy is a second place to fix.
 */
sealed interface ScreenTitle {
    data class Text(val value: String) : ScreenTitle
    data class Resource(@param:StringRes val id: Int) : ScreenTitle
}
