package com.sza.fastmediasorter.data.security.fdsec

import java.io.File

/**
 * The outcome classes of the FD-SEC contract, modelled as distinct types at the bottom of the stack.
 *
 * The contract forbids reporting one as another: cryptographically a wrong key and a flipped byte
 * fail an AEAD tag identically, so a surface that prints "wrong password" for [WrongCredentialOrTamper]
 * is wrong - that class always carries all three of its readings and chooses none.
 */
sealed interface FdSecOutcome {

    /** The container was written, verified by read-back and renamed into place. */
    data class Packed(val container: File, val originalKept: Boolean) : FdSecOutcome

    /** The container was opened and its payload restored in full. */
    data class Unpacked(val restored: File, val metadata: FdSecMetadata) : FdSecOutcome

    /**
     * Slot 0 did not authenticate, or a sealed region failed its tag on a structurally intact file.
     * Three readings, none of them selectable: a wrong credential, a file that never was a
     * container, or tampering.
     */
    object WrongCredentialOrTamper : FdSecOutcome

    /**
     * A file too short to hold a head, a structural rule broken after slot 0 authenticated, length
     * arithmetic that does not add up, or a final digest mismatch.
     */
    data class Damaged(val detail: String) : FdSecOutcome

    /** An unknown version, suite, flag bit or key-slot type in a container that did authenticate. */
    data class Unsupported(val detail: String) : FdSecOutcome

    /** A missing source, no space, a permission denial, or a refused input. */
    data class Failed(val detail: String) : FdSecOutcome
}
