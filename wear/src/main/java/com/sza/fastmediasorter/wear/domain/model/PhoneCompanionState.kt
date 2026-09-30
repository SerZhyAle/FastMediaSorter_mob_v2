package com.sza.fastmediasorter.wear.domain.model

/**
 * S4011: whether the paired phone can serve the rows that only work through FastMediaSorter on it.
 *
 * No verdict is cached across launches: a phone out of range makes those rows useless whatever was
 * seen before, so every process starts at [UNKNOWN] and asks again.
 */
enum class PhoneCompanionState {
    /** Not asked yet in this process - rows hidden, and no hint, since the answer may be seconds away. */
    UNKNOWN,

    /** A connected phone advertises the FastMediaSorter companion capability. */
    PRESENT,

    /** A phone is connected, but none of its nodes carries the capability - the app is not installed. */
    ABSENT,

    /** No phone is connected at all, or the lookup itself failed. */
    PHONE_UNREACHABLE
}

/** S4011: the one line the home screen adds under its rows when phone-bound rows are hidden. */
enum class PhoneCompanionHint {
    CONNECT_PHONE,
    INSTALL_ON_PHONE
}

/**
 * S4011: the one rule that turns two Data Layer answers into a [PhoneCompanionState].
 *
 * Kept free of the Wearable clients so the three cases the strategic spec names are testable without
 * a Data Layer: the capability set is filtered to reachable nodes by the caller, but only a node that
 * is also connected right now counts - a capability answer can outlive the link that produced it.
 */
fun resolvePhoneCompanionState(
    connectedNodeIds: Set<String>,
    capableNodeIds: Set<String>
): PhoneCompanionState = when {
    connectedNodeIds.isEmpty() -> PhoneCompanionState.PHONE_UNREACHABLE
    connectedNodeIds.any { it in capableNodeIds } -> PhoneCompanionState.PRESENT
    else -> PhoneCompanionState.ABSENT
}
