package com.sza.fastmediasorter.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * The `auth` member of a protected broadcast's descriptor (contract LIVE-BROADCAST item M): the scheme and the user
 * name a listener answers the challenge with. It never carries a password, a hash or anything a reader could test a
 * guess against, which is why every hand-off channel may carry it.
 */
data class BroadcastAuthDto(
    @SerializedName("scheme") val scheme: String = SCHEME_PASSWORD_V1,
    @SerializedName("user") val user: String,
) {

    /** True for the one scheme this revision knows; an unknown scheme is a broadcast this app cannot answer. */
    val isPasswordV1: Boolean get() = scheme == SCHEME_PASSWORD_V1

    companion object {
        const val SCHEME_PASSWORD_V1 = "password-v1"
    }
}

/**
 * The own pair an account device finds inside the directory record's sealed box (contract DEVICE-EXCHANGE item BF),
 * carried on the opened descriptor in memory only so the import can keep it in the secret store. A descriptor a
 * person hands over never has it; [toString] never prints the password.
 */
data class BroadcastCredentialsDto(
    @SerializedName("user") val user: String,
    @SerializedName("password") val password: String,
) {
    override fun toString(): String = "BroadcastCredentialsDto(user=$user, password=***)"
}
