package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class RenameVirtualResourcesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository
) {
    suspend operator fun invoke() {
        try {
            val currentLang = LocaleHelper.getLanguage(context)
            val virtualResources = resourceRepository.getAllResourcesSync()
                .filter { VirtualPathUtils.isVirtualPath(it.path) }

            var updatedCount = 0
            for (resource in virtualResources) {
                val defaults = VirtualResourceDefaultNames.TABLE[resource.path] ?: continue
                val currentEntry = defaults[currentLang] ?: continue

                val nameNeedsUpdate = defaults.any { (lang, entry) ->
                    lang != currentLang && entry.name == resource.name
                }
                val commentNeedsUpdate = defaults.any { (lang, entry) ->
                    lang != currentLang && entry.comment == resource.comment
                }

                if (nameNeedsUpdate || commentNeedsUpdate) {
                    resourceRepository.updateResource(
                        resource.copy(
                            name = if (nameNeedsUpdate) currentEntry.name else resource.name,
                            comment = if (commentNeedsUpdate) currentEntry.comment else resource.comment
                        )
                    )
                    updatedCount++
                }
            }

            if (updatedCount > 0) {
                Timber.i("RenameVirtualResources: renamed %d resource(s) to lang='%s'", updatedCount, currentLang)
            } else {
                Timber.d("RenameVirtualResources: nothing to rename for lang='%s'", currentLang)
            }
        } catch (e: Exception) {
            Timber.e(e, "RenameVirtualResources: failed")
        }
    }
}
