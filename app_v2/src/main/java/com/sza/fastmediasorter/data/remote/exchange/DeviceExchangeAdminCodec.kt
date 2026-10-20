package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccountAdded
import com.sza.fastmediasorter.domain.model.ExchangeAdminCreatedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminDevice
import com.sza.fastmediasorter.domain.model.ExchangeAdminEffectiveLimits
import com.sza.fastmediasorter.domain.model.ExchangeAdminInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds
import com.sza.fastmediasorter.domain.model.ExchangeAdminOwnLimits
import com.sza.fastmediasorter.domain.model.ExchangeAdminPolicy
import com.sza.fastmediasorter.domain.model.ExchangeAdminSharedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminStatus

/** One `admin-reply`: its `data` on success, its code on failure. The server's `error` sentence is never kept. */
sealed interface DeviceExchangeAdminReply {
    val id: Long

    data class Ok(override val id: Long, val data: JsonElement?) : DeviceExchangeAdminReply

    data class Failed(override val id: Long, val code: String) : DeviceExchangeAdminReply
}

/**
 * The request and reply members of contract DEVICE-EXCHANGE 1.4 `REMOTE-ADMIN.md` `administration-operations`.
 * Only the operations a phone carries are named: no `device.pairing-code` (a phone has no use for it), no grant
 * changes (not on the wire yet) and no host settings (never on the wire). Unknown members are ignored.
 */
object DeviceExchangeAdminCodec {

    const val OP_STATUS = "status"
    const val OP_ACCOUNT_LIST = "account.list"
    const val OP_ACCOUNT_ADD = "account.add"
    const val OP_ACCOUNT_PASSWD = "account.passwd"
    const val OP_ACCOUNT_KIND = "account.kind"
    const val OP_ACCOUNT_REMOVE = "account.remove"
    const val OP_ACCOUNT_LIMITS = "account.limits"
    const val OP_DEVICE_LIST = "device.list"
    const val OP_DEVICE_REVOKE = "device.revoke"
    const val OP_INVITATION_CREATE = "invitation.create"
    const val OP_INVITATION_LIST = "invitation.list"
    const val OP_INVITATION_WITHDRAW = "invitation.withdraw"
    const val OP_SHARED_INVITATION_STATUS = "shared_invitation.status"
    const val OP_SHARED_INVITATION_SET = "shared_invitation.set"
    const val OP_POLICY_STATUS = "policy.status"
    const val OP_POLICY_SET = "policy.set"

    const val KEY_LOGIN = "login"
    const val KEY_PASSWORD = "password"
    const val KEY_KIND = "kind"
    const val KEY_LIMITS = "limits"
    const val KEY_DEVICE_ID = "deviceId"
    const val KEY_EXPECTED_LOGIN = "expectedLogin"
    const val KEY_CODE_HASH = "codeHash"
    const val KEY_MAX_ACCOUNTS = "maxAccounts"

    private const val KEY_ID = "id"
    private const val KEY_TOKEN = "token"
    private const val KEY_OP = "op"
    private const val KEY_OK = "ok"
    private const val KEY_DATA = "data"
    private const val KEY_CODE = "code"
    private const val CODE_UNKNOWN = "internal"

    /** The first envelope of an administration stream; the caller drops [password] once it is written. */
    fun authEnvelope(password: CharArray): DeviceExchangeEnvelope =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ADMIN_AUTH) {
            addProperty(KEY_PASSWORD, String(password))
        }

    /** The session token of `admin-auth-ok`. */
    fun readToken(envelope: DeviceExchangeEnvelope): String =
        envelope.string(KEY_TOKEN)?.takeIf(String::isNotEmpty) ?: throw missing(KEY_TOKEN)

    fun request(id: Long, token: String, op: String, members: JsonObject.() -> Unit = {}): DeviceExchangeEnvelope =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ADMIN_REQUEST) {
            addProperty(KEY_ID, id)
            addProperty(KEY_TOKEN, token)
            addProperty(KEY_OP, op)
            members()
        }

    /** The own limits object with only the members that are set; an empty object clears an account's limits. */
    fun ownLimitsJson(limits: ExchangeAdminOwnLimits): JsonObject = JsonObject().apply {
        limits.devicesPerAccount?.let { addProperty(OWN_DEVICES, it) }
        limits.resourcesPerAccount?.let { addProperty(OWN_RESOURCES, it) }
        limits.broadcastsPerAccount?.let { addProperty(OWN_BROADCASTS, it) }
        limits.broadcastsPerDevice?.let { addProperty(BROADCASTS_PER_DEVICE, it) }
        limits.clipboardMaxBytes?.let { addProperty(OWN_CLIPBOARD, it) }
    }

    fun readReply(envelope: DeviceExchangeEnvelope): DeviceExchangeAdminReply {
        val id = envelope.members.long(KEY_ID) ?: throw missing(KEY_ID)
        val ok = envelope.boolean(KEY_OK) ?: throw missing(KEY_OK)
        return if (ok) {
            DeviceExchangeAdminReply.Ok(id, envelope.members.get(KEY_DATA)?.takeUnless { it.isJsonNull })
        } else {
            DeviceExchangeAdminReply.Failed(id, envelope.string(KEY_CODE) ?: CODE_UNKNOWN)
        }
    }

    fun readStatus(data: JsonElement?): ExchangeAdminStatus = data.objectOrThrow().run {
        ExchangeAdminStatus(
            accounts = requireInt("accounts"),
            maxAccounts = requireInt(KEY_MAX_ACCOUNTS),
            devices = requireInt("devices"),
            online = requireInt("online"),
            resources = requireInt("resources"),
            broadcasts = requireInt("broadcasts"),
            tombstones = requireInt("tombstones"),
            clipboards = requireInt("clipboards"),
            clipboardBytes = requireLong(EFFECTIVE_CLIPBOARD),
            version = text("version"),
            keepAwakeActive = flag("keepAwakeActive"),
            keepAwake = flag("keepAwake"),
            logRetentionDays = requireInt("logRetentionDays"),
            sealed = flag("sealed"),
        )
    }

    fun readAccounts(data: JsonElement?): List<ExchangeAdminAccount> = data.arrayOrThrow().map { element ->
        element.objectOrThrow().run {
            ExchangeAdminAccount(
                login = requireText(KEY_LOGIN),
                createdAt = requireText("createdAt"),
                devices = requireInt("devices"),
                kind = text(KEY_KIND) ?: ExchangeAdminKinds.MEMBER,
                ownLimits = obj(KEY_LIMITS)?.toOwnLimits(),
                effective = (obj("effective") ?: throw missing("effective")).toEffectiveLimits(),
            )
        }
    }

    fun readAccountAdded(data: JsonElement?): ExchangeAdminAccountAdded = data.objectOrThrow().run {
        ExchangeAdminAccountAdded(login = requireText(KEY_LOGIN), overCap = flag("overCap"))
    }

    /** `account.passwd`: how many devices were signed out - every device of the account. */
    fun readSignedOut(data: JsonElement?): Int = data.objectOrThrow().requireInt("signedOut")

    /** `account.kind`. */
    fun readChanged(data: JsonElement?): Boolean = data.objectOrThrow().flag("changed")

    fun readDevices(data: JsonElement?): List<ExchangeAdminDevice> = data.arrayOrThrow().map { element ->
        element.objectOrThrow().run {
            ExchangeAdminDevice(
                deviceId = requireText(KEY_DEVICE_ID),
                deviceName = requireText("deviceName"),
                product = requireText("product"),
                productVersion = text("productVersion"),
                platform = text("platform"),
                roles = get("roles")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
                    ?.mapNotNull { role -> role.takeIf(JsonElement::isJsonPrimitive)?.asString }
                    .orEmpty(),
                enrolledAt = requireText("enrolledAt"),
                lastSeen = text("lastSeen"),
                online = flag("online"),
                signedOut = flag("signedOut"),
            )
        }
    }

    fun readCreatedInvitation(data: JsonElement?): ExchangeAdminCreatedInvitation = data.objectOrThrow().run {
        ExchangeAdminCreatedInvitation(
            code = requireText("code"),
            invitation = (obj("invitation") ?: throw missing("invitation")).toInvitation(),
            connectionString = requireText("connectionString"),
        )
    }

    fun readInvitations(data: JsonElement?): List<ExchangeAdminInvitation> =
        data.arrayOrThrow().map { it.objectOrThrow().toInvitation() }

    fun readSharedInvitation(data: JsonElement?): ExchangeAdminSharedInvitation = data.objectOrThrow().run {
        ExchangeAdminSharedInvitation(enabled = flag("enabled"), kind = text(KEY_KIND))
    }

    fun readPolicy(data: JsonElement?): ExchangeAdminPolicy = data.objectOrThrow().run {
        ExchangeAdminPolicy(
            maxAccounts = requireInt(KEY_MAX_ACCOUNTS),
            accounts = requireInt("accounts"),
            defaults = (obj("defaults") ?: throw missing("defaults")).toEffectiveLimits(),
            persisted = flag("persisted"),
        )
    }

    // Own names only: reading `devices` here would show a limit the account does not carry.
    private fun JsonObject.toOwnLimits() = ExchangeAdminOwnLimits(
        devicesPerAccount = int(OWN_DEVICES),
        resourcesPerAccount = int(OWN_RESOURCES),
        broadcastsPerAccount = int(OWN_BROADCASTS),
        broadcastsPerDevice = int(BROADCASTS_PER_DEVICE),
        clipboardMaxBytes = long(OWN_CLIPBOARD),
    )

    // Effective names only: reading `devicesPerAccount` here is the mix-up that shows zeros.
    private fun JsonObject.toEffectiveLimits() = ExchangeAdminEffectiveLimits(
        devices = requireInt("devices"),
        resources = requireInt("resources"),
        broadcasts = requireInt("broadcasts"),
        broadcastsPerDevice = requireInt(BROADCASTS_PER_DEVICE),
        clipboardBytes = requireLong(EFFECTIVE_CLIPBOARD),
    )

    private fun JsonObject.toInvitation() = ExchangeAdminInvitation(
        codeHash = requireText(KEY_CODE_HASH),
        kind = text(KEY_KIND) ?: ExchangeAdminKinds.MEMBER,
        createdAt = requireText("createdAt"),
        expectedLogin = text(KEY_EXPECTED_LOGIN),
        used = flag("used"),
        usedAt = text("usedAt"),
        usedBy = text("usedBy"),
    )

    private fun JsonElement?.objectOrThrow(): JsonObject =
        this?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: throw DeviceExchangeWireException("reply data is not an object")

    private fun JsonElement?.arrayOrThrow(): JsonArray =
        this?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: throw DeviceExchangeWireException("reply data is not an array")

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive

    private fun JsonObject.text(key: String): String? = primitive(key)?.takeIf(JsonPrimitive::isString)?.asString

    private fun JsonObject.long(key: String): Long? = primitive(key)?.takeIf(JsonPrimitive::isNumber)?.asLong

    private fun JsonObject.int(key: String): Int? = primitive(key)?.takeIf(JsonPrimitive::isNumber)?.asInt

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    // An absent boolean is false: `ok:false` aside, the contract only ever adds a true flag.
    private fun JsonObject.flag(key: String): Boolean =
        primitive(key)?.takeIf(JsonPrimitive::isBoolean)?.asBoolean ?: false

    private fun JsonObject.requireText(key: String): String = text(key) ?: throw missing(key)

    private fun JsonObject.requireInt(key: String): Int = int(key) ?: throw missing(key)

    private fun JsonObject.requireLong(key: String): Long = long(key) ?: throw missing(key)

    private fun missing(key: String) = DeviceExchangeWireException("admin reply has no $key")

    private const val OWN_DEVICES = "devicesPerAccount"
    private const val OWN_RESOURCES = "resourcesPerAccount"
    private const val OWN_BROADCASTS = "broadcastsPerAccount"
    private const val OWN_CLIPBOARD = "clipboardMaxBytes"
    private const val BROADCASTS_PER_DEVICE = "broadcastsPerDevice"
    private const val EFFECTIVE_CLIPBOARD = "clipboardBytes"
}
