package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader
import com.sza.fastmediasorter.domain.model.PermissionRow
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1436: the single grouping rule for every permission list - required entries under their group
 * headings, optional ones under the optional heading, empty groups skipped.
 *
 * The caller passes its own entry set rather than naming a screen: `getEntries()` from Settings,
 * `getWelcomeEntries()` from onboarding. That is what keeps the composition difference a property of
 * the registry while the grouping exists exactly once.
 */
@Singleton
class BuildPermissionRowsUseCase @Inject constructor(
    private val registry: PermissionRegistryRepository,
    private val checkStatus: CheckPermissionStatusUseCase,
) {

    operator fun invoke(entries: List<PermissionEntry>, context: Context): List<PermissionRow> {
        Timber.d("S1436: building permission rows from ${entries.size} registry entries")
        val rows = mutableListOf<PermissionRow>()
        val required = entries.filterNot { it.optional }
        registry.getGroups().forEach { header ->
            val groupEntries = required.filter { it.group == header.group }
            if (groupEntries.isEmpty()) return@forEach
            rows += PermissionRow.Header(header)
            groupEntries.forEach { entry -> rows += PermissionRow.Entry(entry, checkStatus(context, entry)) }
        }

        val optional = entries.filter { it.optional }
        if (optional.isNotEmpty()) {
            // The optional heading borrows the first optional entry's group only to satisfy the header
            // type; what the user reads is the title, and these rows are deliberately not regrouped.
            rows += PermissionRow.Header(
                PermissionGroupHeader(
                    group = optional.first().group,
                    titleRes = R.string.perm_group_optional,
                ),
            )
            optional.forEach { entry -> rows += PermissionRow.Entry(entry, checkStatus(context, entry)) }
        }
        return rows
    }
}
