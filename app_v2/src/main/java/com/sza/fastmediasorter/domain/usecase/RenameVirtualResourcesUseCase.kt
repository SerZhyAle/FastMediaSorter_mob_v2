package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.res.Configuration
import android.os.Environment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.UiLanguageCatalog
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1265: keeps the names/comments of app-provisioned resources in the UI language. Provisioning
 * stores localized text in the DB at insert time, so a later locale switch leaves stale text
 * behind. This pass rewrites a stored value only when it still equals another supported
 * language's DEFAULT (resolved from string resources per language via
 * [Context.createConfigurationContext]) - a user-customized name never matches and is preserved.
 *
 * Defaults deliberately come from the same string resources provisioning uses; the previous
 * hardcoded table drifted from strings.xml (ellipsis vs house-style two dots in comments) and
 * silently stopped matching, which is exactly the bug this class exists to prevent.
 *
 * Covered: the six virtual:// resources, the predefined All Files resource
 * (identified by [ResourceProfile.ALL_FILES], never by name), and the Downloads destination.
 */
class RenameVirtualResourcesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository
) {

    private data class LocalizedEntry(val name: String, val comment: String?)

    private val virtualStringKeys: Map<String, Pair<Int, Int>> = mapOf(
        LocalMediaScanner.VIRTUAL_PATH_RECENT to
            (R.string.recent_media to R.string.virtual_comment_recent),
        LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO to
            (R.string.virtual_all_music to R.string.virtual_comment_all_music),
        LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO to
            (R.string.virtual_all_video to R.string.virtual_comment_all_video),
        LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES to
            (R.string.virtual_all_images to R.string.virtual_comment_all_images),
        LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS to
            (R.string.virtual_all_docs to R.string.virtual_comment_all_docs),
        LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS to
            (R.string.virtual_camera_photos to R.string.virtual_comment_camera_photos),
    )

    suspend operator fun invoke() {
        try {
            UiLanguageCatalog.ensureInitialized(context)
            val languages = UiLanguageCatalog.supportedTags.ifEmpty { listOf("en") }
            val currentLang = LocaleHelper.getLanguage(context)
            val allResources = resourceRepository.getAllResourcesSync()
            var updatedCount = 0

            val virtualResources = allResources.filter { VirtualPathUtils.isVirtualPath(it.path) }
            for (resource in virtualResources) {
                val (nameRes, commentRes) = virtualStringKeys[resource.path] ?: continue
                val defaults = languages.associateWith { lang ->
                    LocalizedEntry(
                        name = getStringForLanguage(nameRes, lang),
                        comment = getStringForLanguage(commentRes, lang),
                    )
                }
                if (applyLocalizedDefaults(resource, defaults, currentLang)) updatedCount++
            }

            val allFiles = allResources.filter {
                it.profile == ResourceProfile.ALL_FILES && !VirtualPathUtils.isVirtualPath(it.path)
            }
            for (resource in allFiles) {
                val defaults = languages.associateWith { lang ->
                    LocalizedEntry(name = getStringForLanguage(R.string.all_files, lang), comment = null)
                }
                if (applyLocalizedDefaults(resource, defaults, currentLang)) updatedCount++
            }

            val downloadsPath = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
            val downloadsDefaults = languages.associateWith { lang ->
                LocalizedEntry(name = getStringForLanguage(R.string.resource_name_downloads, lang), comment = null)
            }
            for (resource in allResources.filter { it.isDestination && it.path == downloadsPath }) {
                if (applyLocalizedDefaults(resource, downloadsDefaults, currentLang)) updatedCount++
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

    /**
     * Rewrites name/comment to [currentLang]'s default when the stored value still equals some
     * OTHER supported language's default. Returns true when a DB update was written.
     */
    private suspend fun applyLocalizedDefaults(
        resource: MediaResource,
        defaults: Map<String, LocalizedEntry>,
        currentLang: String,
    ): Boolean {
        val currentEntry = defaults[currentLang]
        val nameNeedsUpdate = currentEntry != null &&
            resource.name != currentEntry.name &&
            defaults.any { (lang, entry) -> lang != currentLang && entry.name == resource.name }
        val commentNeedsUpdate = currentEntry?.comment != null &&
            resource.comment != currentEntry.comment &&
            defaults.any { (lang, entry) ->
                lang != currentLang && entry.comment != null && entry.comment == resource.comment
            }
        val needsUpdate = nameNeedsUpdate || commentNeedsUpdate
        if (currentEntry != null && needsUpdate) {
            resourceRepository.updateResource(
                resource.copy(
                    name = if (nameNeedsUpdate) currentEntry.name else resource.name,
                    comment = if (commentNeedsUpdate) currentEntry.comment else resource.comment
                )
            )
        }
        return needsUpdate
    }

    private fun getStringForLanguage(resId: Int, languageCode: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag(languageCode))
        return context.createConfigurationContext(configuration).getString(resId)
    }
}
