package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import java.io.IOException

/** The server answered `refused`; [reason] is a 7.10 reason or `unknown`. */
class DeviceExchangeRefusedException(val reason: String) : IOException("exchange server refused: $reason")

/** The entry holds no device token: it was never enrolled, or its token was revoked and forgotten. */
class DeviceExchangeNotEnrolledException : IOException("no device token for this exchange account entry")

private const val KEY_ACCOUNT = "account"
private const val KEY_PUBLIC_ENDPOINT = "publicEndpoint"
private const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
private const val KEY_FEATURES = "features"
private const val KEY_SERVER_VERSION = "serverVersion"
private const val KEY_ACCOUNT_KIND = "accountKind"

/** The members of `welcome` (7.2), which `enrolled` carries as well. */
internal fun DeviceExchangeEnvelope.toWelcome(): DeviceExchangeWelcome = DeviceExchangeWelcome(
    account = string(KEY_ACCOUNT),
    publicEndpoint = string(KEY_PUBLIC_ENDPOINT),
    keepaliveSeconds = int(KEY_KEEPALIVE_SECONDS),
    features = members.get(KEY_FEATURES)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
        .orEmpty(),
    serverVersion = string(KEY_SERVER_VERSION),
    // 19 item AO: a server that sends no kind is a 0.20 server, and every account on it is `member`.
    accountKind = string(KEY_ACCOUNT_KIND) ?: DeviceExchangeAccountEntry.ACCOUNT_KIND_MEMBER,
)
