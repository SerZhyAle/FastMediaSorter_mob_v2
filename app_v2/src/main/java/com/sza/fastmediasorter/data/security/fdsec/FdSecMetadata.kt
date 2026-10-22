package com.sza.fastmediasorter.data.security.fdsec

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * The sealed metadata of a container: the original's true name, its real size, its four timestamps
 * and the digest of its bytes.
 *
 * Deliberately not a data class. A generated `toString` would print the sealed true name, which is
 * the one thing the format exists to hide and which the contract forbids writing into any log.
 */
class FdSecMetadata(
    val originalName: String,
    val realSize: Long,
    val encryptedAtFileTime: Long,
    val createdFileTime: Long,
    val accessedFileTime: Long,
    val modifiedFileTime: Long,
    val payloadDigest: ByteArray,
) {

    /** Milliseconds since the Unix epoch, or null when the container recorded the time as unknown. */
    val modifiedAtMillis: Long? get() = fileTimeToMillis(modifiedFileTime)

    companion object {

        private const val NAME_LENGTH_FIELD_SIZE = 2
        private const val TIMESTAMP_COUNT = 4
        private const val SIZE_FIELD_SIZE = 8
        private const val TIMESTAMP_FIELD_SIZE = 8

        /** A FILETIME of zero means unknown and must round-trip as unknown rather than as 1601. */
        fun millisToFileTime(millis: Long?): Long =
            if (millis == null || millis <= 0L) {
                0L
            } else {
                millis * FdSecFormat.FILETIME_TICKS_PER_MILLI + FdSecFormat.FILETIME_EPOCH_OFFSET
            }

        fun fileTimeToMillis(fileTime: Long): Long? =
            if (fileTime == 0L) {
                null
            } else {
                (fileTime - FdSecFormat.FILETIME_EPOCH_OFFSET) / FdSecFormat.FILETIME_TICKS_PER_MILLI
            }

        fun normalizeName(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)

        /**
         * Builds the fixed 4096-byte plaintext block. The block is always the same size whatever the
         * name is, so the name's length leaks nothing; the tail is random padding, never zeros.
         */
        fun encode(metadata: FdSecMetadata, randomSource: FdSecRandomSource): ByteArray {
            val nameBytes = normalizeName(metadata.originalName).toByteArray(Charsets.UTF_8)
            require(nameBytes.isNotEmpty() && nameBytes.size <= FdSecFormat.MAX_NAME_BYTES) {
                "Original name must be 1..${FdSecFormat.MAX_NAME_BYTES} UTF-8 bytes"
            }
            val block = ByteArray(FdSecFormat.METADATA_PLAINTEXT_SIZE)
            FdSecBytes.writeU16le(block, 0, nameBytes.size)
            nameBytes.copyInto(block, NAME_LENGTH_FIELD_SIZE)
            var offset = NAME_LENGTH_FIELD_SIZE + nameBytes.size
            FdSecBytes.writeU64le(block, offset, metadata.realSize)
            offset += SIZE_FIELD_SIZE
            for (value in timestampsOf(metadata)) {
                FdSecBytes.writeU64le(block, offset, value)
                offset += TIMESTAMP_FIELD_SIZE
            }
            metadata.payloadDigest.copyInto(block, offset)
            offset += FdSecFormat.DIGEST_SIZE
            randomSource.nextBytes(FdSecFormat.METADATA_PLAINTEXT_SIZE - offset).copyInto(block, offset)
            return block
        }

        /**
         * Returns null when the block cannot hold the fields it declares, or when the sealed name is
         * not valid UTF-8 - a damaged container. Use [hasMalformedName] to tell the two apart.
         */
        fun decode(block: ByteArray): FdSecMetadata? {
            if (block.size != FdSecFormat.METADATA_PLAINTEXT_SIZE) {
                return null
            }
            val nameLength = FdSecBytes.readU16le(block, 0)
            return if (isLayoutValid(nameLength)) decodeChecked(block, nameLength) else null
        }

        /**
         * True when the block's layout is sound but the sealed name bytes are not valid UTF-8, which
         * FDSEC-FORMAT section 22 classes as damage in every suite. Kept apart from [decode] because
         * a lossy decode would substitute U+FFFD and restore the file under a different name.
         */
        fun hasMalformedName(block: ByteArray): Boolean {
            if (block.size != FdSecFormat.METADATA_PLAINTEXT_SIZE) {
                return false
            }
            val nameLength = FdSecBytes.readU16le(block, 0)
            return isLayoutValid(nameLength) && strictName(block, nameLength) == null
        }

        private fun isLayoutValid(nameLength: Int): Boolean {
            if (nameLength < 1 || nameLength > FdSecFormat.MAX_NAME_BYTES) {
                return false
            }
            val fixed = NAME_LENGTH_FIELD_SIZE + SIZE_FIELD_SIZE +
                TIMESTAMP_COUNT * TIMESTAMP_FIELD_SIZE + FdSecFormat.DIGEST_SIZE
            return fixed + nameLength <= FdSecFormat.METADATA_PLAINTEXT_SIZE
        }

        /** A legitimate U+FFFD (EF BF BD) decodes fine; only a malformed byte sequence yields null. */
        private fun strictName(block: ByteArray, nameLength: Int): String? =
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(block, NAME_LENGTH_FIELD_SIZE, nameLength))
                    .toString()
            } catch (ignored: CharacterCodingException) {
                null
            }

        private fun decodeChecked(block: ByteArray, nameLength: Int): FdSecMetadata? {
            val name = strictName(block, nameLength) ?: return null
            var offset = NAME_LENGTH_FIELD_SIZE + nameLength
            val realSize = FdSecBytes.readU64le(block, offset)
            offset += SIZE_FIELD_SIZE
            val stamps = LongArray(TIMESTAMP_COUNT)
            for (i in 0 until TIMESTAMP_COUNT) {
                stamps[i] = FdSecBytes.readU64le(block, offset)
                offset += TIMESTAMP_FIELD_SIZE
            }
            val digest = block.copyOfRange(offset, offset + FdSecFormat.DIGEST_SIZE)
            return FdSecMetadata(
                originalName = name,
                realSize = realSize,
                encryptedAtFileTime = stamps[0],
                createdFileTime = stamps[1],
                accessedFileTime = stamps[2],
                modifiedFileTime = stamps[3],
                payloadDigest = digest,
            )
        }

        private fun timestampsOf(metadata: FdSecMetadata): LongArray = longArrayOf(
            metadata.encryptedAtFileTime,
            metadata.createdFileTime,
            metadata.accessedFileTime,
            metadata.modifiedFileTime,
        )
    }
}
