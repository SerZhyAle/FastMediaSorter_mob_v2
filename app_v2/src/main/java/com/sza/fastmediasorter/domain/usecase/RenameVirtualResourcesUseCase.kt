package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.res.Configuration
import android.os.Environment
import com.sza.fastmediasorter.R
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
    private val supportedLanguages = listOf("en", "ru", "uk")

    suspend operator fun invoke() {
        try {
            val currentLang = LocaleHelper.getLanguage(context)
            val allResources = resourceRepository.getAllResourcesSync()
            val virtualResources = allResources.filter { VirtualPathUtils.isVirtualPath(it.path) }
            val downloadsPath = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
            val downloadsLocalizedNames = supportedLanguages.associateWith { languageCode ->
                getStringForLanguage(R.string.resource_name_downloads, languageCode)
            }

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

            val downloadsCurrentName = downloadsLocalizedNames[currentLang]
            if (downloadsCurrentName != null) {
                allResources
                    .filter { it.isDestination && it.path == downloadsPath }
                    .forEach { resource ->
                        val nameNeedsUpdate = downloadsLocalizedNames.any { (lang, name) ->
                            lang != currentLang && name == resource.name
                        }
                        if (nameNeedsUpdate) {
                            resourceRepository.updateResource(resource.copy(name = downloadsCurrentName))
                            updatedCount++
                        }
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

    private fun getStringForLanguage(resId: Int, languageCode: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag(languageCode))
        return context.createConfigurationContext(configuration).getString(resId)
    }
}
