package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.ExchangeAdminInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds
import com.sza.fastmediasorter.domain.model.ExchangeAdminOwnLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replies transcribed from `REMOTE-ADMIN.md` `administration-operations`, the only oracle until vectors exist. */
class DeviceExchangeAdminCodecTest {

    private fun json(text: String) = JsonParser.parseString(text)

    @Test
    fun `status reads every member and keeps host settings read only`() {
        val status = DeviceExchangeAdminCodec.readStatus(
            json(
                """{"accounts":3,"maxAccounts":10,"devices":5,"online":2,"resources":7,"broadcasts":1,
                "tombstones":0,"clipboards":2,"clipboardBytes":4096,"version":"0.31","keepAwakeActive":false,
                "keepAwake":true,"logRetentionDays":14,"sealed":true,"future":"ignored"}"""
            )
        )
        assertEquals(3, status.accounts)
        assertEquals(10, status.maxAccounts)
        assertEquals(4096L, status.clipboardBytes)
        assertEquals("0.31", status.version)
        assertTrue(status.keepAwake)
        assertTrue(status.sealed)
    }

    @Test
    fun `account list maps own limits and effective limits each to its own names`() {
        val accounts = DeviceExchangeAdminCodec.readAccounts(
            json(
                """[{"login":"anna","createdAt":"2026-10-18T10:00:00Z","devices":2,"kind":"guest",
                "limits":{"devicesPerAccount":4,"clipboardMaxBytes":1024},
                "effective":{"devices":4,"resources":20,"broadcasts":2,"broadcastsPerDevice":1,
                "clipboardBytes":1024}}]"""
            )
        )
        val anna = accounts.single()
        assertEquals(ExchangeAdminKinds.GUEST, anna.kind)
        assertEquals(ExchangeAdminOwnLimits(devicesPerAccount = 4, clipboardMaxBytes = 1024L), anna.ownLimits)
        assertEquals(4, anna.effective.devices)
        assertEquals(20, anna.effective.resources)
        assertEquals(1024L, anna.effective.clipboardBytes)
    }

    @Test
    fun `an absent kind reads as member and absent own limits stay null`() {
        val account = DeviceExchangeAdminCodec.readAccounts(
            json(
                """[{"login":"bob","createdAt":"2026-10-18T10:00:00Z","devices":0,
                "effective":{"devices":3,"resources":10,"broadcasts":1,"broadcastsPerDevice":1,"clipboardBytes":0}}]"""
            )
        ).single()
        assertEquals(ExchangeAdminKinds.MEMBER, account.kind)
        assertNull(account.ownLimits)
    }

    @Test(expected = DeviceExchangeWireException::class)
    fun `effective limits are never read with own names`() {
        DeviceExchangeAdminCodec.readAccounts(
            json(
                """[{"login":"bob","createdAt":"2026-10-18T10:00:00Z","devices":0,
                "effective":{"devicesPerAccount":3,"resourcesPerAccount":10,"broadcastsPerAccount":1,
                "broadcastsPerDevice":1,"clipboardMaxBytes":0}}]"""
            )
        )
    }

    @Test(expected = DeviceExchangeWireException::class)
    fun `a missing required member is a wire violation`() {
        DeviceExchangeAdminCodec.readAccountAdded(json("""{"overCap":true}"""))
    }

    @Test
    fun `devices, invitations, shared invitation and policy read their shapes`() {
        val device = DeviceExchangeAdminCodec.readDevices(
            json(
                """[{"deviceId":"d1","deviceName":"Phone","product":"fms-android","roles":["consumer"],
                "enrolledAt":"2026-10-01T00:00:00Z","online":true}]"""
            )
        ).single()
        assertEquals(listOf("consumer"), device.roles)
        assertTrue(device.online)
        assertFalse(device.signedOut)

        val invitation = DeviceExchangeAdminCodec.readInvitations(
            json("""[{"codeHash":"ab12","kind":"member","createdAt":"2026-10-18T10:00:00Z","used":false}]""")
        ).single()
        assertEquals("ab12", invitation.codeHash)

        val shared = DeviceExchangeAdminCodec.readSharedInvitation(json("""{"enabled":false}"""))
        assertFalse(shared.enabled)
        assertNull(shared.kind)

        val policy = DeviceExchangeAdminCodec.readPolicy(
            json(
                """{"maxAccounts":10,"accounts":3,"persisted":false,
                "defaults":{"devices":3,"resources":10,"broadcasts":1,"broadcastsPerDevice":1,"clipboardBytes":2048}}"""
            )
        )
        assertEquals(2048L, policy.defaults.clipboardBytes)
        assertFalse(policy.persisted)
        assertEquals(5, DeviceExchangeAdminCodec.readSignedOut(json("""{"signedOut":5}""")))
    }

    @Test
    fun `a failed reply keeps the code and never the error sentence`() {
        val envelope = DeviceExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"admin-reply","id":7,"ok":false,"code":"exists","error":"account exists"}"""
        )
        val reply = DeviceExchangeAdminCodec.readReply(envelope)
        assertEquals(DeviceExchangeAdminReply.Failed(7, "exists"), reply)
        assertFalse(reply.toString().contains("account exists"))
    }

    @Test
    fun `a successful reply without data carries null`() {
        val envelope = DeviceExchangeEnvelope.decode("""{"schemaVersion":2,"type":"admin-reply","id":3,"ok":true}""")
        assertEquals(DeviceExchangeAdminReply.Ok(3, null), DeviceExchangeAdminCodec.readReply(envelope))
    }

    @Test
    fun `account limits with nothing set writes an empty limits object`() {
        val request = DeviceExchangeAdminCodec.request(1, "t", DeviceExchangeAdminCodec.OP_ACCOUNT_LIMITS) {
            addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, "anna")
            add(DeviceExchangeAdminCodec.KEY_LIMITS, DeviceExchangeAdminCodec.ownLimitsJson(ExchangeAdminOwnLimits()))
        }
        val body = JsonParser.parseString(request.encode()).asJsonObject
        assertEquals("admin-request", body.get("type").asString)
        assertEquals("account.limits", body.get("op").asString)
        assertEquals(0, body.getAsJsonObject("limits").size())
    }

    @Test
    fun `a created invitation prints neither the code nor the string`() {
        val created = DeviceExchangeAdminCodec.readCreatedInvitation(
            json(
                """{"code":"SECRETCODE","connectionString":"FMSEXCH1;h=x;i=SECRETCODE",
                "invitation":{"codeHash":"ab12","kind":"member","createdAt":"2026-10-18T10:00:00Z","used":false}}"""
            )
        )
        assertEquals(
            ExchangeAdminInvitation("ab12", "member", "2026-10-18T10:00:00Z", null, false, null, null),
            created.invitation,
        )
        assertFalse(created.toString().contains("SECRETCODE"))
    }
}
