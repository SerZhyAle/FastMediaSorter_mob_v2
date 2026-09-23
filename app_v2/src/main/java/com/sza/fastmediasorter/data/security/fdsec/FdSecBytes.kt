package com.sza.fastmediasorter.data.security.fdsec

/**
 * Little-endian integer packing for the FD-SEC container.
 *
 * Every integer in the format - in the header, in the metadata block, inside a nonce context string
 * and inside the associated data of a chunk - is unsigned and little-endian, including the ones
 * embedded in strings that are hashed rather than stored.
 */
object FdSecBytes {

    private const val BYTE_MASK = 0xFF
    private const val U16_SIZE = 2
    private const val U32_SIZE = 4
    private const val U64_SIZE = 8
    private const val BITS_PER_BYTE = 8

    fun u16le(value: Int): ByteArray = pack(value.toLong(), U16_SIZE)

    fun u32le(value: Int): ByteArray = pack(value.toLong(), U32_SIZE)

    fun u64le(value: Long): ByteArray = pack(value, U64_SIZE)

    fun readU16le(source: ByteArray, offset: Int): Int = unpack(source, offset, U16_SIZE).toInt()

    fun readU32le(source: ByteArray, offset: Int): Long = unpack(source, offset, U32_SIZE)

    fun readU64le(source: ByteArray, offset: Int): Long = unpack(source, offset, U64_SIZE)

    fun writeU16le(target: ByteArray, offset: Int, value: Int) {
        u16le(value).copyInto(target, offset)
    }

    fun writeU32le(target: ByteArray, offset: Int, value: Int) {
        u32le(value).copyInto(target, offset)
    }

    fun writeU64le(target: ByteArray, offset: Int, value: Long) {
        u64le(value).copyInto(target, offset)
    }

    /** Constant-time equality, so a comparison never leaks where two digests first differ. */
    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        if (left.size != right.size) {
            return false
        }
        var diff = 0
        for (i in left.indices) {
            diff = diff or (left[i].toInt() xor right[i].toInt())
        }
        return diff == 0
    }

    fun isAllZero(source: ByteArray, offset: Int, length: Int): Boolean {
        var diff = 0
        for (i in offset until offset + length) {
            diff = diff or source[i].toInt()
        }
        return diff == 0
    }

    private fun pack(value: Long, size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) {
            out[i] = ((value ushr (i * BITS_PER_BYTE)) and BYTE_MASK.toLong()).toByte()
        }
        return out
    }

    private fun unpack(source: ByteArray, offset: Int, size: Int): Long {
        var value = 0L
        for (i in 0 until size) {
            value = value or ((source[offset + i].toLong() and BYTE_MASK.toLong()) shl (i * BITS_PER_BYTE))
        }
        return value
    }
}
