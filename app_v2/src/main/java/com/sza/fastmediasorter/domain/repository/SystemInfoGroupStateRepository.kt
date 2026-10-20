package com.sza.fastmediasorter.domain.repository

/**
 * Domain port for the remembered expanded state of the System information groups.
 *
 * A group is addressed by its locale-independent state key (`SystemInfoSection.stateKey`), never by its
 * localized title, so the state survives a language change. A group nobody has touched is collapsed.
 */
interface SystemInfoGroupStateRepository {

    fun isExpanded(stateKey: String): Boolean

    fun setExpanded(stateKey: String, expanded: Boolean)
}
