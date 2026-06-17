package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import java.io.IOException

/**
 * Signals that a delivered set's native payload is byte-correct (it passed SHA-256/size integrity,
 * ADR-3) but the device cannot load it - typically an ABI mismatch (e.g. an arm64 `.so` on an x86
 * emulator or a non-arm64 device) or an unsatisfiable transitive dependency. This is an expected
 * device-capability fallback, not a corrupt delivery, so consumers degrade the feature to
 * "unavailable" and the set's install marker is left intact: re-downloading the same architecture
 * cannot help (unlike [DeliveredPayloadCorruptException], which self-invalidates and offers reinstall).
 *
 * Subclasses [IOException] so existing `catch (e: IOException)` / `catch (e: Exception)` sites catch
 * it and degrade gracefully instead of letting the underlying `UnsatisfiedLinkError` (an `Error`)
 * escape uncaught.
 */
class DeliveredNativeLibraryIncompatibleException(
    val set: DeliverableSet,
    val reason: String
) : IOException("Delivered native library for set $set not loadable on this device: $reason")
