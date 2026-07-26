package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1166: guards the picker-icon slot. The catalog when is exhaustive, so a new action fails to
 * compile without an icon - this test additionally catches a placeholder 0 slipping in.
 */
class ScreenshotGestureActionCatalogIconTest {

    @Test
    fun `every gesture action has a picker icon`() {
        ScreenshotGestureAction.entries.forEach { action ->
            assertNotEquals(
                "Action $action has no picker icon",
                0,
                ScreenshotGestureActionCatalog.metaFor(action).iconRes,
            )
        }
    }
}
