package com.sza.fastmediasorter.wear.domain.model

/**
 * What the user asked to do with the current selection.
 *
 * Deliberately free of Android types: the policy that judges it and the engine that runs it are
 * unit-testable without a device or a paired phone.
 */
sealed interface WearFileOperation {

    /** Copy to the paired phone over the existing transfer channel, leaving the watch copy alone. */
    data object SendToPhone : WearFileOperation

    /** Send, then remove the watch copy - and only once the send came back confirmed. */
    data object MoveToPhone : WearFileOperation

    /** Remove the watch copy for good; the watch keeps no trash and has no restore screen. */
    data object Delete : WearFileOperation

    /** Rename in place, inside the file's own directory. */
    data class Rename(val newName: String) : WearFileOperation

    /**
     * Ask the paired phone to show its own original of this file, which the watch only holds a copy of.
     *
     * [token] is the address the phone's browse protocol issued for that original. The watch never
     * invents one, so only a surface that received the token can request the open - which is the same
     * surface the copy came from.
     */
    data class OpenOnPhone(val token: String) : WearFileOperation

    /**
     * Hand the file to one of the «Send to..» receivers the phone published.
     *
     * [receiverId] is the same string the phone persists for that receiver's own toggle, so the watch
     * never invents an address and a receiver switched off there cannot be asked for here.
     *
     * Which side actually serves it is not part of the request: strategic 5.3 makes that an answer on
     * the receiver's declaration, so one operation covers both branches and the menu keeps one word
     * for both.
     */
    data class SendToReceiver(val receiverId: String) : WearFileOperation
}

/**
 * The discriminator the capability policy answers in, separate from [WearFileOperation] because a
 * policy names a kind of action while an operation carries the arguments of one particular request.
 */
enum class WearFileOperationKind {
    SEND_TO_PHONE,
    MOVE_TO_PHONE,
    DELETE,
    RENAME,
    OPEN_ON_PHONE,
    SEND_TO_RECEIVER
}

/** The kind this request belongs to, so a caller never re-derives the mapping. */
fun WearFileOperation.kind(): WearFileOperationKind = when (this) {
    WearFileOperation.SendToPhone -> WearFileOperationKind.SEND_TO_PHONE
    WearFileOperation.MoveToPhone -> WearFileOperationKind.MOVE_TO_PHONE
    WearFileOperation.Delete -> WearFileOperationKind.DELETE
    is WearFileOperation.Rename -> WearFileOperationKind.RENAME
    is WearFileOperation.OpenOnPhone -> WearFileOperationKind.OPEN_ON_PHONE
    is WearFileOperation.SendToReceiver -> WearFileOperationKind.SEND_TO_RECEIVER
}
