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
