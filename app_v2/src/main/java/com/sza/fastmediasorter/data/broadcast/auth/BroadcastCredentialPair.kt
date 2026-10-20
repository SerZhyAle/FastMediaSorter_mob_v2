package com.sza.fastmediasorter.data.broadcast.auth

/**
 * One user and password that may listen to a protected broadcast (contract DEVICE-EXCHANGE item BF). The password is
 * a secret: [toString] never prints it, so a pair that reaches a log line by accident leaks only its user name.
 */
data class BroadcastCredentialPair(val user: String, val password: String) {

    override fun toString(): String = "BroadcastCredentialPair(user=$user, password=***)"
}
