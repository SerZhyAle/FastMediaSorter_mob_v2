package com.sza.fastmediasorter.domain.model

/**
 * The reply shapes of contract DEVICE-EXCHANGE 1.4 `REMOTE-ADMIN.md` `administration-operations`. Times stay the
 * RFC 3339 strings the wire carries. The two limits objects spell their members differently on the wire and are
 * kept as two types so that one is never read with the other's names.
 */
data class ExchangeAdminStatus(
    val accounts: Int,
    val maxAccounts: Int,
    val devices: Int,
    val online: Int,
    val resources: Int,
    val broadcasts: Int,
    val tombstones: Int,
    val clipboards: Int,
    val clipboardBytes: Long,
    val version: String?,
    val keepAwakeActive: Boolean,
    val keepAwake: Boolean,
    val logRetentionDays: Int,
    val sealed: Boolean,
)

/** What an account carries by itself, and what a request sends; a null member is not set. */
data class ExchangeAdminOwnLimits(
    val devicesPerAccount: Int? = null,
    val resourcesPerAccount: Int? = null,
    val broadcastsPerAccount: Int? = null,
    val broadcastsPerDevice: Int? = null,
    val clipboardMaxBytes: Long? = null,
) {
    val isEmpty: Boolean
        get() = listOf(
            devicesPerAccount,
            resourcesPerAccount,
            broadcastsPerAccount,
            broadcastsPerDevice,
            clipboardMaxBytes,
        ).all { it == null }
}

/** What is in force for an account, or the defaults of a new one. */
data class ExchangeAdminEffectiveLimits(
    val devices: Int,
    val resources: Int,
    val broadcasts: Int,
    val broadcastsPerDevice: Int,
    val clipboardBytes: Long,
)

data class ExchangeAdminAccount(
    val login: String,
    val createdAt: String,
    val devices: Int,
    val kind: String,
    val ownLimits: ExchangeAdminOwnLimits?,
    val effective: ExchangeAdminEffectiveLimits,
)

data class ExchangeAdminAccountAdded(
    val login: String,
    val overCap: Boolean,
)

data class ExchangeAdminDevice(
    val deviceId: String,
    val deviceName: String,
    val product: String,
    val productVersion: String?,
    val platform: String?,
    val roles: List<String>,
    val enrolledAt: String,
    val lastSeen: String?,
    val online: Boolean,
    val signedOut: Boolean,
)

data class ExchangeAdminInvitation(
    val codeHash: String,
    val kind: String,
    val createdAt: String,
    val expectedLogin: String?,
    val used: Boolean,
    val usedAt: String?,
    val usedBy: String?,
)

/** The one reply that carries the one-time code and the connection string; it is shown once and never kept. */
data class ExchangeAdminCreatedInvitation(
    val code: String,
    val invitation: ExchangeAdminInvitation,
    val connectionString: String,
) {
    override fun toString(): String = "ExchangeAdminCreatedInvitation(kind=${invitation.kind})"
}

data class ExchangeAdminSharedInvitation(
    val enabled: Boolean,
    val kind: String?,
)

data class ExchangeAdminPolicy(
    val maxAccounts: Int,
    val accounts: Int,
    val defaults: ExchangeAdminEffectiveLimits,
    /** False when a change holds for this run of the server only. */
    val persisted: Boolean,
)

object ExchangeAdminKinds {
    const val MEMBER = "member"
    const val GUEST = "guest"
}
