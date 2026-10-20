package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer

/**
 * What one shared folder is, whatever channel reaches it (contract DEVICE-EXCHANGE item BA): the producer's
 * canonical SFTP host-key fingerprint and the root opened under it. Hosts, ports and tunnels are channels of the
 * same record, so they take no part in it. A record without a fingerprint has no identity and is matched the old
 * way by its caller.
 */
data class ResourceIdentity(val fingerprint: String, val root: String) {

    companion object {

        fun of(resource: MediaResource): ResourceIdentity? {
            val root = SftpPathUtils.parseSftpPath(resource.path)?.remotePath
                ?.takeIf { resource.type == ResourceType.SFTP }
            return root?.let { of(resource.hostKeyFingerprint, it) }
        }

        /** [root] in any of the forms a record, a code or a path carries it: `Photos`, `/Photos/`, empty for `/`. */
        fun of(fingerprint: String?, root: String?): ResourceIdentity? =
            SshFingerprintNormalizer.canonical(fingerprint)?.let { ResourceIdentity(it, normalizedRoot(root)) }

        private fun normalizedRoot(root: String?): String = ROOT + root.orEmpty().trim().trim('/')

        private const val ROOT = "/"
    }
}
