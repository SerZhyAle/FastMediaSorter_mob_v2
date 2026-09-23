package com.sza.fastmediasorter.data.security.fdsec

import java.security.SecureRandom

/**
 * Every random byte a container carries - the salt, the file key, the wrap nonce and both pads -
 * comes from here.
 *
 * The seam exists so the contract's conformance vectors, which fix every random input, can be
 * reproduced byte for byte in a test. No shipped path constructs anything but [SystemRandomSource].
 */
interface FdSecRandomSource {
    fun nextBytes(size: Int): ByteArray
}

/** The operating system CSPRNG. Never a seeded PRNG, not even for the padding. */
class SystemRandomSource(private val random: SecureRandom = SecureRandom()) : FdSecRandomSource {
    override fun nextBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        random.nextBytes(out)
        return out
    }
}
