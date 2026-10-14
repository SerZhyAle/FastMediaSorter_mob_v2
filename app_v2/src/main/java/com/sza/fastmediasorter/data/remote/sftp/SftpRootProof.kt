package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues

/**
 * What the `pin-v1` proof presents besides the shared password (SHARE-SESSION 1.2, `keyboard-interactive`
 * fields `Resource`, `Password`, `PIN`): the virtual root's [root] name and the root's original [pin].
 * Neither value is ever printed - the whole proof is a credential.
 */
class SftpRootProof(val root: String, val pin: String) {

    init {
        PrivateDiagnosticValues.register(pin)
    }

    override fun equals(other: Any?): Boolean = other is SftpRootProof && other.root == root && other.pin == pin

    override fun hashCode(): Int = 31 * root.hashCode() + pin.hashCode()

    override fun toString(): String = "SftpRootProof(root=***, pin=***)"
}
