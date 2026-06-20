package com.sza.fastmediasorter.ui.common.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputHelpFirstRunHintTest {

    @Test
    fun `shows on a non-touch device when not yet shown`() {
        assertTrue(InputHelpFirstRunHint.shouldShow(isNonTouch = true, alreadyShown = false))
    }

    @Test
    fun `never shows on a touch-only device`() {
        assertFalse(InputHelpFirstRunHint.shouldShow(isNonTouch = false, alreadyShown = false))
    }

    @Test
    fun `does not show again once already shown`() {
        assertFalse(InputHelpFirstRunHint.shouldShow(isNonTouch = true, alreadyShown = true))
    }
}
