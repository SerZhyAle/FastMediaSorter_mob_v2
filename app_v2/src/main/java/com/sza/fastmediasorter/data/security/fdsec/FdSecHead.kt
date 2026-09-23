package com.sza.fastmediasorter.data.security.fdsec

/** The authenticated view of a container's head, available only after slot 0 has unwrapped. */
class FdSecHeadView(
    val chunkSize: Int,
    val alignment: Int,
    val headerDigest: ByteArray,
    val fileKey: ByteArray,
)

/** Either an opened head or the outcome class that refused it. */
sealed interface FdSecHeadResult {
    data class Ok(val view: FdSecHeadView) : FdSecHeadResult
    data class Refused(val outcome: FdSecOutcome) : FdSecHeadResult
}

/**
 * The 668-byte head: 28 header bytes and eight 80-byte key slots, everything after the salt masked
 * with a credential-derived keystream.
 *
 * The reader here authenticates before it interprets. Under a wrong credential every unmasked byte
 * is noise, so reading a version or an alignment out of it first would turn a wrong credential into
 * a damage report - the one substitution the contract forbids outright.
 */
object FdSecHead {

    fun build(
        salt: ByteArray,
        chunkSize: Int,
        alignment: Int,
        wrapNonce: ByteArray,
        wrappedKey: ByteArray,
    ): ByteArray {
        val head = ByteArray(FdSecFormat.HEAD_SIZE)
        salt.copyInto(head, 0)
        head[FdSecFormat.OFFSET_VERSION] = FdSecFormat.FORMAT_VERSION.toByte()
        head[FdSecFormat.OFFSET_SUITE] = FdSecFormat.SUITE_ID.toByte()
        FdSecBytes.writeU16le(head, FdSecFormat.OFFSET_FLAGS, FdSecFormat.FLAGS)
        FdSecBytes.writeU32le(head, FdSecFormat.OFFSET_CHUNK_SIZE, chunkSize)
        FdSecBytes.writeU32le(head, FdSecFormat.OFFSET_ALIGNMENT, alignment)
        head[FdSecFormat.HEADER_SIZE] = FdSecFormat.SLOT_TYPE_CREDENTIAL_WRAP.toByte()
        wrapNonce.copyInto(head, FdSecFormat.HEADER_SIZE + 1)
        wrappedKey.copyInto(head, FdSecFormat.HEADER_SIZE + 1 + FdSecFormat.WRAP_NONCE_SIZE)
        return head
    }

    fun headerDigest(plainHead: ByteArray): ByteArray =
        FdSecKeySchedule.blake2b256(plainHead.copyOf(FdSecFormat.HEADER_SIZE))

    /** XORs the keystream over bytes [16, 668) - the same operation in both directions. */
    fun applyMask(head: ByteArray, keystream: ByteArray): ByteArray {
        val out = head.copyOf()
        for (i in 0 until FdSecFormat.MASK_SPAN) {
            val at = FdSecFormat.SALT_SIZE + i
            out[at] = (out[at].toInt() xor keystream[i].toInt()).toByte()
        }
        return out
    }

    fun slot0AssociatedData(headerDigest: ByteArray): ByteArray =
        FdSecFormat.AD_SLOT0.toByteArray(Charsets.UTF_8) + headerDigest

    fun metadataAssociatedData(headerDigest: ByteArray): ByteArray =
        FdSecFormat.AD_META.toByteArray(Charsets.UTF_8) + headerDigest

    fun chunkAssociatedData(headerDigest: ByteArray, index: Long, count: Long, length: Int): ByteArray {
        val last = if (index == count - 1L) 1 else 0
        return FdSecFormat.AD_CHUNK.toByteArray(Charsets.UTF_8) +
            headerDigest +
            FdSecBytes.u64le(index) +
            FdSecBytes.u64le(count) +
            byteArrayOf(last.toByte()) +
            FdSecBytes.u64le(length.toLong())
    }

    /**
     * Derives the schedule from the credential and the container's own salt, unmasks the head,
     * unwraps slot 0 and only then interprets the header fields.
     */
    @Suppress("ReturnCount")
    fun open(diskHead: ByteArray, credentialUtf8: ByteArray, schedule: FdSecKeySchedule): FdSecHeadResult {
        if (diskHead.size != FdSecFormat.HEAD_SIZE) {
            return FdSecHeadResult.Refused(FdSecOutcome.Damaged("head shorter than ${FdSecFormat.HEAD_SIZE} bytes"))
        }
        val root = schedule.deriveRoot(credentialUtf8, diskHead.copyOf(FdSecFormat.SALT_SIZE))
        val plain = applyMask(diskHead, schedule.headKeystream(schedule.maskKey(root), FdSecFormat.MASK_SPAN))
        val digest = headerDigest(plain)
        val nonceFrom = FdSecFormat.HEADER_SIZE + 1
        val wrappedFrom = nonceFrom + FdSecFormat.WRAP_NONCE_SIZE
        val wrapNonce = plain.copyOfRange(nonceFrom, wrappedFrom)
        val wrapped = plain.copyOfRange(wrappedFrom, wrappedFrom + FdSecFormat.WRAPPED_KEY_SIZE)
        val fileKey = XChaCha20Poly1305.open(schedule.kek(root), wrapNonce, slot0AssociatedData(digest), wrapped)
            ?: return FdSecHeadResult.Refused(FdSecOutcome.WrongCredentialOrTamper)
        return interpret(plain, digest, fileKey)
    }

    private fun interpret(plain: ByteArray, digest: ByteArray, fileKey: ByteArray): FdSecHeadResult {
        val unsupported = unsupportedReason(plain)
        if (unsupported != null) {
            return FdSecHeadResult.Refused(FdSecOutcome.Unsupported(unsupported))
        }
        val alignment = FdSecBytes.readU32le(plain, FdSecFormat.OFFSET_ALIGNMENT).toInt()
        val chunkSize = FdSecBytes.readU32le(plain, FdSecFormat.OFFSET_CHUNK_SIZE).toInt()
        val structural = structuralReason(plain, alignment, chunkSize)
        return if (structural != null) {
            FdSecHeadResult.Refused(FdSecOutcome.Damaged(structural))
        } else {
            FdSecHeadResult.Ok(FdSecHeadView(chunkSize, alignment, digest, fileKey))
        }
    }

    /** An unknown version, suite, flag bit or slot type - refused by name, never called damage. */
    private fun unsupportedReason(plain: ByteArray): String? {
        val version = plain[FdSecFormat.OFFSET_VERSION].toInt() and BYTE_MASK
        val suite = plain[FdSecFormat.OFFSET_SUITE].toInt() and BYTE_MASK
        val flags = FdSecBytes.readU16le(plain, FdSecFormat.OFFSET_FLAGS)
        return when {
            version != FdSecFormat.FORMAT_VERSION -> "format version $version"
            suite != FdSecFormat.SUITE_ID -> "suite $suite"
            flags != FdSecFormat.FLAGS -> "flag bits $flags"
            plain[FdSecFormat.HEADER_SIZE].toInt() != FdSecFormat.SLOT_TYPE_CREDENTIAL_WRAP ->
                "key-slot type ${plain[FdSecFormat.HEADER_SIZE].toInt() and BYTE_MASK} in slot 0"
            !areReservedSlotTypesEmpty(plain) -> "key-slot type in a reserved slot"
            else -> null
        }
    }

    private fun structuralReason(plain: ByteArray, alignment: Int, chunkSize: Int): String? {
        val trailingFrom = FdSecFormat.HEADER_SIZE + 1 + FdSecFormat.WRAP_NONCE_SIZE + FdSecFormat.WRAPPED_KEY_SIZE
        return when {
            !FdSecBytes.isAllZero(plain, trailingFrom, FdSecFormat.SLOT_RESERVED_ZEROS) ->
                "slot 0 reserved bytes are not zero"
            !areReservedSlotPayloadsZero(plain) -> "a reserved key slot carries a payload"
            !FdSecFormat.isAlignmentValid(alignment) -> "cluster alignment $alignment"
            !FdSecFormat.isChunkSizeValid(chunkSize, alignment) -> "chunk size $chunkSize"
            else -> null
        }
    }

    private fun areReservedSlotTypesEmpty(plain: ByteArray): Boolean =
        reservedSlotOffsets().all { plain[it].toInt() == FdSecFormat.SLOT_TYPE_EMPTY }

    private fun areReservedSlotPayloadsZero(plain: ByteArray): Boolean =
        reservedSlotOffsets().all { FdSecBytes.isAllZero(plain, it + 1, FdSecFormat.KEY_SLOT_SIZE - 1) }

    private fun reservedSlotOffsets(): List<Int> =
        (1 until FdSecFormat.KEY_SLOT_COUNT).map { FdSecFormat.HEADER_SIZE + it * FdSecFormat.KEY_SLOT_SIZE }

    private const val BYTE_MASK = 0xFF
}
