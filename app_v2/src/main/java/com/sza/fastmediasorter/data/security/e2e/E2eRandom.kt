package com.sza.fastmediasorter.data.security.e2e

import java.security.SecureRandom

/**
 * The one source of randomness of the sealing layer. Production draws from [SecureRandom]; the
 * conformance vectors replace it with a fixed stream so every box, key and record is reproducible.
 */
fun interface E2eRandom {
    fun next(length: Int): ByteArray

    companion object {
        val secure: E2eRandom = object : E2eRandom {
            private val random = SecureRandom()

            override fun next(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)
        }
    }
}
