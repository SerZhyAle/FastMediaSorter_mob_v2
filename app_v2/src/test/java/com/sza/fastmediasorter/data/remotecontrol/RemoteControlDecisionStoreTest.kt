package com.sza.fastmediasorter.data.remotecontrol

import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlDecisionStoreTest {
    @Test
    fun `the store is not the exported settings store`() {
        assertNotEquals("settings", RemoteControlDecisionStore.STORE_NAME)
    }

    @Test
    fun `a new grant carries only safe with always`() {
        val grant = RemoteControllerGrant("dev", "Laptop")

        assertEquals(mapOf(RemoteRiskClass.SAFE to RemoteConsentMode.ALWAYS), grant.classes)
        assertEquals(RemoteConsentMode.ASK, RemoteControllerGrant.defaultMode(RemoteRiskClass.LEAVES_MACHINE))
    }

    @Test
    fun `grants round-trip through the codec`() {
        val grants = listOf(
            RemoteControllerGrant(
                "dev",
                "Laptop",
                mapOf(
                    RemoteRiskClass.SAFE to RemoteConsentMode.ALWAYS,
                    RemoteRiskClass.LEAVES_MACHINE to RemoteConsentMode.ASK,
                ),
            ),
        )

        assertEquals(grants, RemoteControllerGrantCodec.decode(RemoteControllerGrantCodec.encode(grants)))
    }

    @Test
    fun `an unreadable mode reads as ask and an unknown class is dropped`() {
        val raw = """{"v":1,"grants":[{"deviceId":"dev","classes":{"safe":"sometimes","teleport":"always"}}]}"""

        val grant = RemoteControllerGrantCodec.decode(raw).single()

        assertEquals(mapOf(RemoteRiskClass.SAFE to RemoteConsentMode.ASK), grant.classes)
    }

    @Test
    fun `an unknown version or broken text grants nothing`() {
        assertTrue(RemoteControllerGrantCodec.decode("""{"v":2,"grants":[{"deviceId":"dev"}]}""").isEmpty())
        assertTrue(RemoteControllerGrantCodec.decode("not json").isEmpty())
    }
}
