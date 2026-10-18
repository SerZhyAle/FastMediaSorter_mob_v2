package com.sza.fastmediasorter.wear.domain.openin

import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidate
import com.sza.fastmediasorter.wear.domain.model.WearOpenInRole
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget

/**
 * S4381: the order of the "Open in.." list (strategic ADR-4): the last choice for this kind of object, the default
 * browser, the default maps application, then every other row by name.
 *
 * A pinned package that is not among the candidates pins nothing - the list never shows a row that cannot take
 * the object. A package pinned once is not repeated below, and holds the first role that claimed it.
 */
object WearOpenInOrdering {

    fun order(
        candidates: List<WearOpenInCandidate>,
        defaultBrowser: String?,
        defaultMaps: String?,
        lastUsed: String?,
    ): List<WearOpenInTarget> {
        val unique = candidates.distinctBy { it.packageName }
        val pinned = mutableListOf<WearOpenInTarget>()
        listOf(
            lastUsed to WearOpenInRole.LAST_USED,
            defaultBrowser to WearOpenInRole.BROWSER,
            defaultMaps to WearOpenInRole.MAPS,
        ).forEach { (packageName, role) ->
            val found = unique.firstOrNull { it.packageName == packageName }
            if (found != null && pinned.none { it.packageName == found.packageName }) {
                pinned += found.toTarget(role)
            }
        }
        val rest = unique
            .filter { candidate -> pinned.none { it.packageName == candidate.packageName } }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .map { it.toTarget(WearOpenInRole.OTHER) }
        return pinned + rest
    }

    private fun WearOpenInCandidate.toTarget(role: WearOpenInRole) =
        WearOpenInTarget(packageName, activityName, label, action, role)
}
