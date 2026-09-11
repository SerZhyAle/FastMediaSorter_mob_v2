package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import timber.log.Timber
import javax.inject.Inject

/**
 * S2868: the one name this watch calls itself by on every phone surface that renders it.
 *
 * Two paths send a watch name across the bridge - the sources-export payload and the broadcast
 * descriptor the phone turns into a stream row - and each had named the device its own way, so the
 * phone's main screen showed `SM-L310` while its settings row showed `Galaxy Watch7`. One name needs
 * one owner, which is this use case.
 *
 * The bridge renders the node as "Galaxy Watch7 (8CRZ)"; the parenthetical is a serial fragment out
 * of diagnostics and never belongs in a user-visible label. The model code stays the fallback for a
 * bridge that cannot name the node at all, where a factory code still beats an empty caption.
 *
 * It reads the platform through [WearSystemInfoDataSource] rather than `Build` and `Wearable`
 * directly, because this module runs its unit tests on the plain JVM where every `android.*` call
 * returns a stub - a use case touching either could not be tested at all.
 */
class GetWatchDisplayNameUseCase @Inject constructor(
    private val systemInfo: WearSystemInfoDataSource
) {

    suspend operator fun invoke(): String {
        val nodeName = systemInfo.localNode()
            ?.displayName
            ?.takeIf { it.isNotBlank() }
            ?.let(::withoutModelCode)
        val name = nodeName
            ?: systemInfo.model?.takeIf { it.isNotBlank() }
            ?: FALLBACK_NAME
        Timber.d("S2868: watch names itself '%s' (from node: %s)", name, nodeName != null)
        return name
    }

    /**
     * A name without a parenthetical passes through unchanged, and a name that is nothing but one
     * keeps its original text: an empty caption is worse than a technical one.
     */
    private fun withoutModelCode(displayName: String): String {
        val stripped = displayName.substringBefore('(').trim()
        return stripped.ifBlank { displayName.trim() }
    }

    private companion object {
        /** Neither the bridge nor the platform would name the device, and a label is still owed. */
        const val FALLBACK_NAME = "Watch"
    }
}
