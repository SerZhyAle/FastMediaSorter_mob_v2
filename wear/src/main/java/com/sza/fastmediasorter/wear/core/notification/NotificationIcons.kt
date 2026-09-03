package com.sza.fastmediasorter.wear.core.notification

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R

/**
 * S1399 / S1961: the single status-bar icon for every notification this wear module posts.
 */
object NotificationIcons {

    /**
     * Always this, on every notification. The drawable is alpha-only and carries no theme attribute.
     */
    @DrawableRes
    val STATUS_BAR: Int = R.drawable.ic_notification_app_logo
}
