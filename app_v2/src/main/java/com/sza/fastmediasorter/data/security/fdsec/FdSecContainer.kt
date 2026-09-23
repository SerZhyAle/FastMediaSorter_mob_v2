package com.sza.fastmediasorter.data.security.fdsec

import org.bouncycastle.crypto.digests.Blake2bDigest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * The FD-SEC suite-1 writer and reader.
 *
 * Framework-free on purpose: the contract asks for one crypto module with no product dependencies,
 * and nothing here logs, because the credential and the sealed true name are the two things the
 * format exists to keep out of a log.
 *
 * Both test seams - [randomSource] and the schedule's KDF profile - exist only so the published
 * conformance vectors can be reproduced; no shipped caller passes anything but the defaults.
 */
class FdSecContainer(
    private val schedule: FdSecKeySchedule = FdSecKeySchedule(),
    private val randomSource: FdSecRandomSource = SystemRandomSource(),
    private val chunkSize: Int = FdSecFormat.DEFAULT_CHUNK_SIZE,
    private val alignment: Int = FdSecFormat.DEFAULT_ALIGNMENT,
) {

    private sealed interface ReadResult {
        data class Ok(val metadata: FdSecMetadata) : ReadResult
        data class Refused(val outcome: FdSecOutcome) : ReadResult
    }

    /**
     * Writes [source] into a container at [destination] through a temporary file, proves the result
     * by reading it back to the payload digest, and only then renames it into place.
     *
     * The original is never touched. Deleting it is the caller's separate, explicit act, and the
     * contract allows it only after this call has returned [FdSecOutcome.Packed].
     */
    fun pack(
        source: File,
        destination: File,
        credential: CharArray,
        stamps: FdSecStamps = FdSecStamps.of(source),
    ): FdSecOutcome {
        val refusal = refuseUnpackableSource(source)
        if (refusal != null) {
            return refusal
        }
        val credentialUtf8 = schedule.normalizeCredential(credential)
        val temporary = File(destination.parentFile, destination.name + TEMP_SUFFIX)
        return try {
            writeContainer(source, temporary, credentialUtf8, stamps)
            proveAndRename(temporary, destination, credentialUtf8)
        } catch (e: IOException) {
            temporary.delete()
            FdSecOutcome.Failed(e.message ?: "I/O failure while packing")
        }
    }

    /**
     * Opens [container] and restores the original beside it in [destinationDirectory], under the
     * name sealed inside the container, through a temporary file and a rename.
     */
    fun unpack(container: File, destinationDirectory: File, credential: CharArray): FdSecOutcome {
        val credentialUtf8 = schedule.normalizeCredential(credential)
        return when (val peek = read(container, credentialUtf8, sink = null)) {
            is ReadResult.Refused -> peek.outcome
            is ReadResult.Ok -> restoreChecked(container, destinationDirectory, credentialUtf8, peek.metadata)
        }
    }

    private fun restoreChecked(
        container: File,
        destinationDirectory: File,
        credentialUtf8: ByteArray,
        metadata: FdSecMetadata,
    ): FdSecOutcome =
        if (isRestorableName(metadata.originalName)) {
            restore(container, destinationDirectory, metadata.originalName, credentialUtf8, metadata)
        } else {
            // Refused rather than sanitised: the sealed name is attacker-controlled the moment
            // somebody else made the container, and a silent rename would hide what it was doing.
            FdSecOutcome.Failed("the sealed name is not a plain file name")
        }

    /** Reads the sealed metadata without restoring anything. */
    fun readMetadata(container: File, credential: CharArray): FdSecOutcome {
        val credentialUtf8 = schedule.normalizeCredential(credential)
        return when (val result = read(container, credentialUtf8, sink = null)) {
            is ReadResult.Refused -> result.outcome
            is ReadResult.Ok -> FdSecOutcome.Unpacked(container, result.metadata)
        }
    }

    private fun restore(
        container: File,
        destinationDirectory: File,
        name: String,
        credentialUtf8: ByteArray,
        metadata: FdSecMetadata,
    ): FdSecOutcome {
        val target = File(destinationDirectory, name)
        val temporary = File(destinationDirectory, name + TEMP_SUFFIX)
        return try {
            val outcome = FileOutputStream(temporary).use { out -> read(container, credentialUtf8, out) }
            if (outcome is ReadResult.Refused) {
                temporary.delete()
                outcome.outcome
            } else {
                finishRestore(temporary, target, metadata)
            }
        } catch (e: IOException) {
            temporary.delete()
            FdSecOutcome.Failed(e.message ?: "I/O failure while restoring")
        }
    }

    private fun finishRestore(temporary: File, target: File, metadata: FdSecMetadata): FdSecOutcome {
        if (target.exists() || !temporary.renameTo(target)) {
            temporary.delete()
            return FdSecOutcome.Failed("cannot write ${target.name} without overwriting an existing file")
        }
        metadata.modifiedAtMillis?.let { target.setLastModified(it) }
        return FdSecOutcome.Unpacked(target, metadata)
    }

    private fun refuseUnpackableSource(source: File): FdSecOutcome? {
        val nameBytes = FdSecMetadata.normalizeName(source.name).toByteArray(Charsets.UTF_8).size
        return when {
            !source.isFile -> FdSecOutcome.Failed("only a regular file can be packed")
            nameBytes > FdSecFormat.MAX_NAME_BYTES ->
                FdSecOutcome.Failed("the original name exceeds ${FdSecFormat.MAX_NAME_BYTES} UTF-8 bytes")
            else -> null
        }
    }

    @Suppress("ReturnCount")
    private fun proveAndRename(temporary: File, destination: File, credentialUtf8: ByteArray): FdSecOutcome {
        val proof = read(temporary, credentialUtf8, sink = null)
        if (proof is ReadResult.Refused) {
            temporary.delete()
            return FdSecOutcome.Failed("the written container did not read back: ${proof.outcome}")
        }
        if (destination.exists() || !temporary.renameTo(destination)) {
            temporary.delete()
            return FdSecOutcome.Failed("cannot write ${destination.name} without overwriting an existing file")
        }
        return FdSecOutcome.Packed(destination, originalKept = true)
    }

    private fun writeContainer(source: File, temporary: File, credentialUtf8: ByteArray, stamps: FdSecStamps) {
        val realSize = source.length()
        val payloadDigest = digestOf(source)
        val salt = randomSource.nextBytes(FdSecFormat.SALT_SIZE)
        val fileKey = randomSource.nextBytes(FdSecFormat.FILE_KEY_SIZE)
        val wrapNonce = randomSource.nextBytes(FdSecFormat.WRAP_NONCE_SIZE)
        val root = schedule.deriveRoot(credentialUtf8, salt)
        val headerDigest = FdSecHead.headerDigest(
            FdSecHead.build(salt, chunkSize, alignment, wrapNonce, ByteArray(FdSecFormat.WRAPPED_KEY_SIZE)),
        )
        val wrapped = XChaCha20Poly1305.seal(
            schedule.kek(root),
            wrapNonce,
            FdSecHead.slot0AssociatedData(headerDigest),
            fileKey,
        )
        val head = FdSecHead.build(salt, chunkSize, alignment, wrapNonce, wrapped)
        val masked = FdSecHead.applyMask(head, schedule.headKeystream(schedule.maskKey(root), FdSecFormat.MASK_SPAN))
        val metadata = metadataOf(source, realSize, payloadDigest, stamps)
        val sealedMetadata = XChaCha20Poly1305.seal(
            fileKey,
            schedule.metadataNonce(fileKey),
            FdSecHead.metadataAssociatedData(headerDigest),
            FdSecMetadata.encode(metadata, randomSource),
        )
        FileOutputStream(temporary).use { out ->
            out.write(masked)
            out.write(sealedMetadata)
            val padding = FdSecFormat.preLen(alignment) - FdSecFormat.HEAD_SIZE - sealedMetadata.size
            out.write(randomSource.nextBytes(padding))
            writePayload(source, out, fileKey, headerDigest, realSize)
        }
    }

    private fun writePayload(
        source: File,
        out: OutputStream,
        fileKey: ByteArray,
        headerDigest: ByteArray,
        realSize: Long,
    ) {
        val capacity = FdSecFormat.chunkCapacity(chunkSize)
        val count = FdSecFormat.chunkCount(realSize, chunkSize)
        var written = 0L
        source.inputStream().buffered().use { input ->
            for (index in 0 until count) {
                val remaining = realSize - index * capacity
                val length = if (remaining < capacity) remaining.toInt() else capacity
                val plain = ByteArray(length)
                var read = 0
                while (read < length) {
                    val step = input.read(plain, read, length - read)
                    if (step < 0) {
                        throw IOException("the original shrank while it was being packed")
                    }
                    read += step
                }
                val associatedData = FdSecHead.chunkAssociatedData(headerDigest, index, count, length)
                out.write(XChaCha20Poly1305.seal(fileKey, schedule.chunkNonce(fileKey, index), associatedData, plain))
                written += length + FdSecFormat.TAG_SIZE
            }
        }
        val total = FdSecFormat.preLen(alignment) + written
        val padding = ((total + alignment - 1) / alignment) * alignment - total
        out.write(randomSource.nextBytes(padding.toInt()))
    }

    private fun metadataOf(
        source: File,
        realSize: Long,
        payloadDigest: ByteArray,
        stamps: FdSecStamps,
    ): FdSecMetadata = FdSecMetadata(
        originalName = source.name,
        realSize = realSize,
        encryptedAtFileTime = stamps.encryptedAtFileTime,
        createdFileTime = stamps.createdFileTime,
        accessedFileTime = stamps.accessedFileTime,
        modifiedFileTime = stamps.modifiedFileTime,
        payloadDigest = payloadDigest,
    )

    private fun digestOf(source: File): ByteArray {
        val digest = Blake2bDigest(FdSecFormat.DIGEST_SIZE * Byte.SIZE_BITS)
        val buffer = ByteArray(STREAM_BUFFER)
        source.inputStream().buffered().use { input ->
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }

    /**
     * The reader, in the contract's order: authenticate slot 0, then interpret, then open the
     * metadata, then check the length arithmetic, then the chunks, then the final digest.
     */
    private fun read(container: File, credentialUtf8: ByteArray, sink: OutputStream?): ReadResult {
        val length = container.length()
        if (length < FdSecFormat.MIN_CONTAINER_LENGTH || length % FdSecFormat.MIN_ALIGNMENT != 0L) {
            return ReadResult.Refused(FdSecOutcome.Damaged("length $length cannot be a container"))
        }
        return RandomAccessFile(container, "r").use { file -> readOpened(file, length, credentialUtf8, sink) }
    }

    @Suppress("ReturnCount")
    private fun readOpened(
        file: RandomAccessFile,
        length: Long,
        credentialUtf8: ByteArray,
        sink: OutputStream?,
    ): ReadResult {
        val head = ByteArray(FdSecFormat.HEAD_SIZE)
        file.readFully(head)
        val view = when (val opened = FdSecHead.open(head, credentialUtf8, schedule)) {
            is FdSecHeadResult.Refused -> return ReadResult.Refused(opened.outcome)
            is FdSecHeadResult.Ok -> opened.view
        }
        val metadata = openMetadata(file, view) ?: return ReadResult.Refused(FdSecOutcome.WrongCredentialOrTamper)
        val damaged = lengthMismatch(length, view, metadata.realSize)
        return if (damaged != null) {
            ReadResult.Refused(FdSecOutcome.Damaged(damaged))
        } else {
            readPayload(file, view, metadata, sink)
        }
    }

    private fun openMetadata(file: RandomAccessFile, view: FdSecHeadView): FdSecMetadata? {
        val sealed = ByteArray(FdSecFormat.METADATA_ON_DISK_SIZE)
        file.seek(FdSecFormat.HEAD_SIZE.toLong())
        file.readFully(sealed)
        val block = XChaCha20Poly1305.open(
            view.fileKey,
            schedule.metadataNonce(view.fileKey),
            FdSecHead.metadataAssociatedData(view.headerDigest),
            sealed,
        ) ?: return null
        return FdSecMetadata.decode(block)
    }

    private fun lengthMismatch(length: Long, view: FdSecHeadView, realSize: Long): String? {
        val capacity = FdSecFormat.chunkCapacity(view.chunkSize).toLong()
        val count = FdSecFormat.chunkCount(realSize, view.chunkSize)
        val last = realSize - capacity * (count - 1)
        val minimum = FdSecFormat.preLen(view.alignment) + (count - 1) * view.chunkSize + last + FdSecFormat.TAG_SIZE
        return when {
            last < 0L || last > capacity -> "payload length arithmetic does not add up"
            length < minimum || length >= minimum + view.alignment -> "file length $length against expected $minimum"
            length % view.alignment != 0L -> "file length $length is not a multiple of ${view.alignment}"
            else -> null
        }
    }

    private fun readPayload(
        file: RandomAccessFile,
        view: FdSecHeadView,
        metadata: FdSecMetadata,
        sink: OutputStream?,
    ): ReadResult {
        val capacity = FdSecFormat.chunkCapacity(view.chunkSize)
        val count = FdSecFormat.chunkCount(metadata.realSize, view.chunkSize)
        val preLen = FdSecFormat.preLen(view.alignment).toLong()
        val digest = Blake2bDigest(FdSecFormat.DIGEST_SIZE * Byte.SIZE_BITS)
        for (index in 0 until count) {
            val remaining = metadata.realSize - index * capacity
            val plainLength = if (remaining < capacity) remaining.toInt() else capacity
            val sealed = ByteArray(plainLength + FdSecFormat.TAG_SIZE)
            file.seek(preLen + index * view.chunkSize)
            file.readFully(sealed)
            val associatedData = FdSecHead.chunkAssociatedData(view.headerDigest, index, count, plainLength)
            val nonce = schedule.chunkNonce(view.fileKey, index)
            val plain = XChaCha20Poly1305.open(view.fileKey, nonce, associatedData, sealed)
                ?: return ReadResult.Refused(FdSecOutcome.WrongCredentialOrTamper)
            digest.update(plain, 0, plain.size)
            sink?.write(plain)
        }
        val recovered = ByteArray(digest.digestSize)
        digest.doFinal(recovered, 0)
        return if (FdSecBytes.constantTimeEquals(recovered, metadata.payloadDigest)) {
            ReadResult.Ok(metadata)
        } else {
            ReadResult.Refused(FdSecOutcome.Damaged("the recovered bytes do not match the sealed digest"))
        }
    }

    /**
     * Path separators, the two directory names, a trailing dot or space and the reserved device
     * names are refused rather than stripped. A silent rename would hide what a container was
     * trying to do, and `invoice.exe ` becomes `invoice.exe` on a host that trims while a naive
     * extension check sees `.exe ` and finds it in no list.
     */
    private fun isRestorableName(name: String): Boolean =
        name.isNotEmpty() &&
            name.none { it == '/' || it == '\\' } &&
            name != "." &&
            name != ".." &&
            !name.endsWith(".") &&
            !name.endsWith(" ") &&
            name.substringBefore('.').uppercase() !in RESERVED_DEVICE_NAMES

    companion object {
        private const val TEMP_SUFFIX = ".fdsec-part"
        private const val STREAM_BUFFER = 64 * 1024

        /** Deliberately wider than what this platform executes - the list guards a restored name. */
        private val RESERVED_DEVICE_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        )
    }
}
