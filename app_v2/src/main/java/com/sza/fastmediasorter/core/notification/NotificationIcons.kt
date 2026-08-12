package com.sza.fastmediasorter.core.notification

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R

/**
 * S1399: the single status-bar icon for every notification this app posts.
 *
 * It exists for the same reason [NotificationIds] does - thirteen call sites in eleven classes each
 * picked their own drawable, and with no default to reach for, three background workers that have
 * nothing to do with sound reused the audio glyph. The owner saw a music note while the app moved files
 * on a schedule. One value removes the choice: a new notification cannot pick the wrong icon because it
 * is not asked to pick one.
 *
 * Deliberately one value and not a per-family map. The owner ruled the branded icon applies everywhere
 * (strategic ADR-3); families stay distinguishable by their title, text and transport buttons. A family
 * that ever needs its own icon becomes a named exception here, next to the reason - not a fourteenth
 * independent literal.
 */
object NotificationIcons {

    /**
     * Always this, on every notification. The drawable is alpha-only and carries no theme attribute,
     * which is what keeps `startForeground` from throwing outside the app theme (S0405, S0416).
     */
    @DrawableRes
    val STATUS_BAR: Int = R.drawable.ic_notification_app_logo
}
