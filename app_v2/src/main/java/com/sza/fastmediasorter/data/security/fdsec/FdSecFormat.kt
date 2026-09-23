package com.sza.fastmediasorter.data.security.fdsec

/**
 * Fixed constants of the FD-SEC container - format version 1, suite 1.
 *
 * Source of truth is the `secure-container` contract in the shared contract catalog. The format is
 * frozen: a different value here is a new format version, never a per-file option, so nothing in
 * this object may be made configurable.
 */
object FdSecFormat {

    const val FORMAT_VERSION: Int = 1
    const val SUITE_ID: Int = 1

    /** Bit 0 is the conformance bit and must be set; every other bit is reserved and zero. */
    const val FLAGS: Int = 0x0001

    const val SALT_SIZE: Int = 16
    const val HEADER_SIZE: Int = 28
    const val KEY_SLOT_COUNT: Int = 8
    const val KEY_SLOT_SIZE: Int = 80
    const val KEY_SLOTS_SIZE: Int = KEY_SLOT_COUNT * KEY_SLOT_SIZE
    const val HEAD_SIZE: Int = HEADER_SIZE + KEY_SLOTS_SIZE

    /** The head is masked from the end of the salt to the end of the last key slot. */
    const val MASK_SPAN: Int = HEAD_SIZE - SALT_SIZE

    const val METADATA_PLAINTEXT_SIZE: Int = 4096
    const val TAG_SIZE: Int = 16
    const val METADATA_ON_DISK_SIZE: Int = METADATA_PLAINTEXT_SIZE + TAG_SIZE

    const val FILE_KEY_SIZE: Int = 32
    const val NONCE_SIZE: Int = 24
    const val DIGEST_SIZE: Int = 32
    const val ROOT_KEY_SIZE: Int = 32

    /** Refused above this, never truncated. */
    const val MAX_NAME_BYTES: Int = 3000

    const val DEFAULT_ALIGNMENT: Int = 4096
    const val DEFAULT_CHUNK_SIZE: Int = 1048576
    const val MIN_ALIGNMENT: Int = 512
    const val MAX_ALIGNMENT: Int = 2097152

    /**
     * The smallest container the format can produce - alignment 512 over an empty original. With the
     * length-is-a-whole-number-of-clusters rule this is the only statement a reader may make about a
     * file without the credential.
     */
    const val MIN_CONTAINER_LENGTH: Long = 5632L

    const val CONTAINER_EXTENSION: String = "fd-sec"
    const val CONTAINER_SUFFIX: String = ".fd-sec"

    const val SLOT_TYPE_EMPTY: Int = 0
    const val SLOT_TYPE_CREDENTIAL_WRAP: Int = 1
    const val WRAP_NONCE_SIZE: Int = 24
    const val WRAPPED_KEY_SIZE: Int = FILE_KEY_SIZE + TAG_SIZE
    const val SLOT_RESERVED_ZEROS: Int = KEY_SLOT_SIZE - 1 - WRAP_NONCE_SIZE - WRAPPED_KEY_SIZE

    const val CTX_MASK: String = "FDSEC1/mask"
    const val CTX_KEK: String = "FDSEC1/kek"
    const val CTX_NONCE_PREFIX: String = "FDSEC1/nonce/"
    const val NONCE_CTX_META: String = "meta"
    const val NONCE_CTX_CHUNK_PREFIX: String = "chunk:"
    const val AD_SLOT0: String = "FDSEC1/slot0"
    const val AD_META: String = "FDSEC1/meta"
    const val AD_CHUNK: String = "FDSEC1/chunk"

    /** 100-nanosecond intervals between 1601-01-01 UTC and the Unix epoch. */
    const val FILETIME_EPOCH_OFFSET: Long = 116_444_736_000_000_000L
    const val FILETIME_TICKS_PER_MILLI: Long = 10_000L

    /** Offsets inside the 28 unmasked header bytes. */
    const val OFFSET_VERSION: Int = 16
    const val OFFSET_SUITE: Int = 17
    const val OFFSET_FLAGS: Int = 18
    const val OFFSET_CHUNK_SIZE: Int = 20
    const val OFFSET_ALIGNMENT: Int = 24

    /** Pre-payload length: head, sealed metadata and the random pad, rounded up to a cluster. */
    fun preLen(alignment: Int): Int {
        val used = HEAD_SIZE + METADATA_ON_DISK_SIZE
        return ((used + alignment - 1) / alignment) * alignment
    }

    /** Plaintext capacity of a full chunk slot. */
    fun chunkCapacity(chunkSize: Int): Int = chunkSize - TAG_SIZE

    /** Chunk count. An empty original still has one chunk, of length zero. */
    fun chunkCount(realSize: Long, chunkSize: Int): Long {
        val capacity = chunkCapacity(chunkSize).toLong()
        val count = (realSize + capacity - 1) / capacity
        return if (count < 1L) 1L else count
    }

    fun isAlignmentValid(alignment: Int): Boolean =
        alignment in MIN_ALIGNMENT..MAX_ALIGNMENT && (alignment and (alignment - 1)) == 0

    fun isChunkSizeValid(chunkSize: Int, alignment: Int): Boolean =
        chunkSize >= alignment && chunkSize % alignment == 0
}

/**
 * The KDF profile belongs to the format version, not to the file - the head is masked with a
 * credential-derived keystream, so a work factor a reader could read first would have to sit in the
 * clear and would name the format.
 *
 * The non-version-1 values exist only so a test corpus stays affordable; no shipped path constructs
 * anything but [V1].
 */
data class FdSecKdfProfile(
    val thresholdBytes: Int,
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    companion object {
        val V1: FdSecKdfProfile = FdSecKdfProfile(
            thresholdBytes = 64,
            memoryKib = 65_536,
            iterations = 3,
            parallelism = 4,
        )
    }
}
