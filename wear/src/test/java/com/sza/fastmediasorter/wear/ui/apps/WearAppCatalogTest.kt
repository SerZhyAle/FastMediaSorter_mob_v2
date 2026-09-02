package com.sza.fastmediasorter.wear.ui.apps

import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WearAppCatalogTest {

    @Test
    fun `catalog returns the first set in declaration order`() {
        val ids = WearAppCatalog.apps().map { it.id }

        assertEquals(
            listOf(
                WearAppId.CALCULATOR,
                WearAppId.NETWORK_MONITOR,
                WearAppId.GAME,
                WearAppId.VOICE_RECORDER,
                WearAppId.SYSTEM_INFO
            ),
            ids
        )
    }

    @Test
    fun `every route equals its program canonical key`() {
        WearAppCatalog.apps().forEach { app ->
            assertEquals(app.id.canonicalKey, app.route)
        }
    }

    @Test
    fun `an unavailable record is absent from the rendered list`() {
        val records = listOf(
            WearApp(WearAppId.CALCULATOR, labelRes = 1, route = "calculator"),
            WearApp(WearAppId.GAME, labelRes = 2, route = "game", isAvailable = false)
        )

        val visible = records.filter { it.isAvailable }

        assertEquals(listOf(WearAppId.CALCULATOR), visible.map { it.id })
        assertFalse(visible.any { it.id == WearAppId.GAME })
    }
}
