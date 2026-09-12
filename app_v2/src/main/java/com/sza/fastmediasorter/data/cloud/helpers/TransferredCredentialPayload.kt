package com.sza.fastmediasorter.data.cloud.helpers

/**
 * Payload field names of a `SECRET` entry written by a cloud credential manager (S2101).
 *
 * Part of the persisted format: the build that reads an entry may be older or newer than the one
 * that wrote it, so these literals are spelled once here rather than twice per provider, where a
 * later edit to one copy would orphan every record already stored on a user's device.
 */
internal object TransferredCredentialPayload {

    /** Account the credential belongs to; empty when the writing route never learned it. */
    const val EMAIL: String = "email"

    /** The provider's own serialized credential blob, carrying its refresh token. */
    const val CREDENTIALS: String = "credentials"
}
