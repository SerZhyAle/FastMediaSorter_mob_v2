package com.sza.fastmediasorter.wear.ui.common.testlaunch

import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugWearTestLaunchOverrideParserTest {

    @Test
    fun `a valid mode and size are both read`() {
        assertEquals(
            WearTestLaunchOverride(WearGeometryMode.STORE, 192),
            parseWearTestLaunchOverride("store", 192)
        )
    }

    @Test
    fun `an unknown mode is dropped and the size still applies`() {
        assertEquals(WearTestLaunchOverride(null, 227), parseWearTestLaunchOverride("ROUND", 227))
    }

    @Test
    fun `a size outside the watch range is dropped`() {
        assertEquals(
            WearTestLaunchOverride(WearGeometryMode.ORIGINAL, null),
            parseWearTestLaunchOverride("ORIGINAL", 40)
        )
    }

    @Test
    fun `no usable parameter is a plain launch`() {
        assertNull(parseWearTestLaunchOverride(null, null))
        assertNull(parseWearTestLaunchOverride("nope", 9000))
    }
}
