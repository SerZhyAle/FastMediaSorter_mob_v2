package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S4238: one exchange account entry as a backup carries it - the address, the login and the accepted
 * certificate pin the sign-in form is refilled from. It holds no token and no device id: the token is
 * encrypted by a key that never leaves the phone, and a device id restored onto a second phone would
 * collide with the first one on the server, so the restored entry takes a fresh id and waits for the
 * account password.
 */
data class BackupExchangeAccount(
    @SerializedName("host")
    val host: String = "",
    @SerializedName("port")
    val port: Int = 0,
    @SerializedName("login")
    val login: String = "",
    @SerializedName("pinnedCertificate")
    val pinnedCertificate: String? = null,
    @SerializedName("autoAttach")
    val autoAttach: Boolean = false,
    @SerializedName("outgoing")
    val outgoing: Boolean = false,
)
