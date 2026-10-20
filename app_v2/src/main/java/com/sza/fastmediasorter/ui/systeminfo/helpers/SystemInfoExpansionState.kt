package com.sza.fastmediasorter.ui.systeminfo.helpers

import com.sza.fastmediasorter.core.systeminfo.SystemInfoSection
import com.sza.fastmediasorter.domain.repository.SystemInfoGroupStateRepository

/**
 * Expanded state of the System information groups as the screen uses it: toggling one group and
 * expanding or collapsing all of them. What is remembered, and under which key, is the repository's.
 */
class SystemInfoExpansionState(private val repository: SystemInfoGroupStateRepository) {

    fun isExpanded(stateKey: String): Boolean = repository.isExpanded(stateKey)

    fun toggle(stateKey: String) {
        repository.setExpanded(stateKey, !repository.isExpanded(stateKey))
    }

    fun allExpanded(sections: List<SystemInfoSection>): Boolean = sections.all { isExpanded(it.stateKey) }

    fun setAll(sections: List<SystemInfoSection>, expanded: Boolean) {
        sections.forEach { repository.setExpanded(it.stateKey, expanded) }
    }
}
