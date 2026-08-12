package com.sza.fastmediasorter.core.notification

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1399: pins that the shared status-bar icon points at a real resource.
 *
 * That is all a JVM test can honestly assert here. Whether the drawable is free of a `?attr` tint -
 * the property that decides whether `startForeground` throws - is not visible from a unit test; it is
 * covered by the gate in phase 03 and by the drawable's own review.
 */
class NotificationIconsTest {

    @Test
    fun `shared status bar icon resolves to a real drawable`() {
        assertNotEquals(0, NotificationIcons.STATUS_BAR)
    }
}
