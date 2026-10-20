package com.sza.fastmediasorter.domain.model.transfer

/**
 * S1565: the ways a transfer refuses, each carrying enough for the UI to name a reason.
 *
 * They live beside the transfer model rather than beside the use cases because `domain/usecase` is
 * reserved for `*UseCase` types (CLAUDE.md Rule 6, enforced by the `class-architecture-naming`
 * dimension of `scripts/quality/assert-source-gates.ps1`).
 */

/** A Drive operation the user can act on - not signed in, no network, or the API refused. */
class DriveTransferFailure(message: String) : Exception(message)

/** The kind could not be serialized at all - nothing to hand to either medium. */
class TransferPayloadUnavailable(kind: TransferDataKind) :
    Exception("no transferable content for ${kind.name}")

/** The bytes are not this kind's format, or come from a build that writes a newer one. */
class IncompatibleTransferFile(kind: TransferDataKind, cause: Throwable? = null) :
    Exception("not a readable ${kind.name} transfer file", cause)

/** S3040: the packet left the cross-device queue - another device claimed and deleted it. */
class PacketNotInQueue(packetId: String) : Exception("packet $packetId is no longer in the Drive queue")

/** This kind's import shows the user a preview first, so it is driven from the UI layer. */
class PreviewedKindNotAppliedHere(kind: TransferDataKind) :
    Exception("${kind.name} is applied through its previewing use case, not here")

/** S4231: the Google account must sign in again; nothing in the queue was deleted (DEVICE-EXCHANGE item CT). */
class DriveSignInRequired : Exception("Google Drive sign-in required")

/** S4231: Drive's daily upload limit; reported, never retried in a loop (item CS). */
class DriveDailyLimit : Exception("Google Drive daily upload limit reached")

/** S4231: the packet would not fit with 1% of the limit to spare; refused before any byte (item CS). */
class DriveQueueFull(val freeBytes: Long?) : Exception("not enough space on Google Drive")

/** S4231: the packet exceeds this device's `packetBytes` (item CS). */
class PacketTooLarge(val limitBytes: Long) : Exception("packet exceeds $limitBytes bytes")

/** S4231: Drive did not answer or asked to slow down; the caller may retry with back-off (item CQ). */
class DriveTemporarilyUnavailable(cause: Throwable?) : Exception("Google Drive temporarily unavailable", cause)

/** S4231: a received file differs from its manifest's size or digest; the packet stays (item CN). */
class PacketIntegrityFailure(name: String) : Exception("$name does not match its manifest")

/** S4231: the Drive channel, or its clipboard option, is turned off on this device. */
class DriveChannelOff : Exception("the Drive channel is turned off")
