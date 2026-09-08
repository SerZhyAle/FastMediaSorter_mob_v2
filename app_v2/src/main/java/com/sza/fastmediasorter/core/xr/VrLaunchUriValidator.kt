package com.sza.fastmediasorter.core.xr

/**
 * S1218 (ADR-1): the one decision that says whether a VIDEO launch address is openable, and why not
 * when it is not.
 *
 * It lives in src/main rather than beside its only caller in src/vr so the table can be unit-tested
 * without a flavor-scoped test source set - the rule it encodes is the launch contract's, and the
 * contract is shared by every flavor.
 *
 * RTSP is separated from a malformed address on purpose: strategic S1218 §6 Q3 keeps it as a
 * continuation, so it is a transport the immersive host does not open YET, which is a different
 * sentence to show the user than "this address is wrong".
 */
internal fun validateVideoLaunchUri(
    uri: String,
    sourceKind: VrLaunchSourceKind,
): VrLaunchUnavailableReason? {
    val lower = uri.lowercase()
    return when (sourceKind) {
        VrLaunchSourceKind.LOCAL_FILE -> {
            val isLocal = uri.startsWith("file://") || uri.startsWith("/")
            if (isLocal) null else VrLaunchUnavailableReason.InvalidUri
        }

        VrLaunchSourceKind.NETWORK_STREAM -> when {
            lower.startsWith("http://") || lower.startsWith("https://") -> null
            lower.startsWith("rtsp://") -> VrLaunchUnavailableReason.NotYetSupported
            else -> VrLaunchUnavailableReason.InvalidUri
        }
    }
}
