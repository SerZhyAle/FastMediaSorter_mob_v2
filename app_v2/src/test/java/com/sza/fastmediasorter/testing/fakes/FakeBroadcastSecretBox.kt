package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.data.broadcast.BroadcastSecretBox

/**
 * S4675: a reversible stand-in for the Keystore box. Its text differs on every call like the real one, so a test can
 * never pass by comparing two stored texts, and each failure the real box can show is one switch here.
 */
class FakeBroadcastSecretBox : BroadcastSecretBox {
    var sealOk = true
    var openOk = true
    var seals = 0
        private set
    var opens = 0
        private set

    private var generation = 0

    /** Runs inside [seal], before it answers - where a test slips a concurrent write between read and replace. */
    var onSeal: (() -> Unit)? = null

    /** The device key is replaced: what was sealed before can no longer be opened, what is sealed now can. */
    fun loseKey() {
        generation++
    }

    override fun seal(plain: String): String? {
        seals++
        onSeal?.invoke()
        if (!sealOk) return null
        return "g$generation.n$seals:" + plain.reversed().toByteArray().joinToString("") { "%02x".format(it) }
    }

    override fun open(box: String): String? {
        opens++
        if (!openOk || !box.startsWith("g$generation.")) return null
        val bytes = box.substringAfter(':').chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return String(bytes).reversed()
    }
}
