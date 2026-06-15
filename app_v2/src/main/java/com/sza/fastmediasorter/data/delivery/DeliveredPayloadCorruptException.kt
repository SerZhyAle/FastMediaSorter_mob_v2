package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import java.io.IOException

/**
 * Signals that a delivered set's on-device payload failed integrity verification - the payload file
 * is missing, smaller than its pinned minimum, or its SHA-256 does not match the app-pinned hash
 * (S0432). The loader self-invalidates the set's install marker before throwing this, so a consumer
 * can distinguish a recoverable corruption (offer reinstall) from a generic load failure.
 *
 * Subclasses [IOException] so existing `catch (e: IOException)` / `catch (e: Exception)` sites stay
 * backward-compatible while letting consumers match this specific type.
 */
class DeliveredPayloadCorruptException(
    val set: DeliverableSet,
    val reason: String
) : IOException("Delivered payload corrupt for set $set: $reason")
