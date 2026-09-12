package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.WearNodeDescriptor
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import javax.inject.Inject

/**
 * What this watch is linked to, and by which route.
 *
 * This section used to be one word - connected, or not. That answered the only question the old report
 * could ask and none of the ones a user actually has: which device, reachable how, and known to the
 * phone under what name (S2165 §2 goal 3). Proximity is the fact that carries most of it: a node that
 * is not nearby is reachable only by relay through the cloud, which behaves nothing like a Bluetooth
 * link and explains most of what looks like a broken pairing.
 */
class PhoneLinkContributor @Inject constructor(
    private val dataSource: WearSystemInfoDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.PHONE_LINK

    override suspend fun sections(): List<WearSystemInfoSection> {
        val nodes = dataSource.connectedNodes()
        return listOf(
            WearSystemInfoSection(
                titleRes = R.string.system_info_section_phone,
                fields = if (nodes.isNullOrEmpty()) emptyList() else fields(nodes),
                emptyReasonRes = emptinessReason(nodes)
            )
        )
    }

    /**
     * Three different answers, and the report says which: the service did not answer, it answered that
     * nothing is paired, or it listed what is.
     */
    private fun emptinessReason(nodes: List<WearNodeDescriptor>?): Int? = when {
        nodes == null -> R.string.system_info_empty_unreadable
        nodes.isEmpty() -> R.string.system_info_link_none
        else -> null
    }

    private suspend fun fields(nodes: List<WearNodeDescriptor>): List<WearSystemInfoField> =
        buildList {
            nodes.forEach { node ->
                text(R.string.system_info_phone_link, node.displayName)?.let(::add)
                add(label(R.string.system_info_link_route, routeLabel(node)))
                text(R.string.system_info_link_node_id, node.id)?.let(::add)
            }
            dataSource.localNode()?.let { local ->
                text(R.string.system_info_link_this_watch, local.id)?.let(::add)
            }
            dataSource.pairCapabilities()?.takeIf { names -> names.isNotEmpty() }?.let { names ->
                add(
                    WearSystemInfoField(
                        R.string.system_info_link_capabilities,
                        WearSystemInfoValue.Enumerated(names)
                    )
                )
            }
        }

    /**
     * The route gets a line of its own rather than being appended to the name in brackets. It has to:
     * "nearby" and "via cloud" are translated words, and this class holds no Context - a value it
     * concatenated itself could only ever be English.
     */
    private fun routeLabel(node: WearNodeDescriptor): Int = if (node.isNearby) {
        R.string.system_info_link_nearby
    } else {
        R.string.system_info_link_cloud
    }
}
