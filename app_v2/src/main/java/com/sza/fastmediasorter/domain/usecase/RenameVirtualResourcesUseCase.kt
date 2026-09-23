package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.UiLanguageCatalog
import com.sza.fastmediasorter.core.util.UriPathResolver
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
 *
 * S2627: the current language is resolved BEFORE any comparison, and the remaining declared
 * languages only when the stored value already differs from it. The pass moved off the
 * thirty-second deferred worker onto the startup path, where resolving all thirteen declared tags
 * for every record on every launch - the shape this class had while it ran once, late, in the
 * background - would be paid by every cold start instead.
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
            val languages = UiLanguageCatalog.supportedTags.ifEmpty { listOf(UiLanguageCatalog.DEFAULT_TAG) }
            val currentLang = LocaleHelper.getLanguage(context)
            // The rewrite only ever replaces a value that equals some OTHER declared language's
            // default, so a current language outside the declared set has nothing to compare against
            // and no value it may safely claim. Unreachable while LocaleHelper resolves through the
            // same catalog; kept because the alternative to skipping is rewriting on no evidence.
            if (currentLang !in languages) {
                Timber.w("RenameVirtualResources: lang='%s' is not declared, nothing to compare", currentLang)
                return
            }
            val allResources = resourceRepository.getAllResourcesSync()
            var updatedCount = 0

            val virtualResources = allResources.filter { VirtualPathUtils.isVirtualPath(it.path) }
            for (resource in virtualResources) {
                val (nameRes, commentRes) = virtualStringKeys[resource.path] ?: continue
                if (applyLocalizedDefaults(resource, nameRes, commentRes, languages, currentLang)) updatedCount++
            }

            val allFiles = allResources.filter {
                it.profile == ResourceProfile.ALL_FILES && !VirtualPathUtils.isVirtualPath(it.path)
            }
            for (resource in allFiles) {
                if (applyLocalizedDefaults(resource, R.string.all_files, null, languages, currentLang)) updatedCount++
            }

            val downloadsPath = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
            val downloads = allResources.filter { it.isDestination && isDownloadsPath(it.path, downloadsPath) }
            for (resource in downloads) {
                val renamed = applyLocalizedDefaults(
                    resource,
                    R.string.resource_name_downloads,
                    null,
                    languages,
                    currentLang
                )
                if (renamed) updatedCount++
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

    private fun isDownloadsPath(path: String, downloadsPath: String): Boolean =
        path == downloadsPath ||
            (path.startsWith("content://") && UriPathResolver.getPath(context, Uri.parse(path)) == downloadsPath)

    /**
     * Rewrites name/comment to [currentLang]'s default when the stored value still equals some
     * OTHER declared language's default. Returns true when a DB update was written.
     *
     * [commentRes] is null for the records that carry no comment (All Files, Downloads), which is
     * why a null comment can never be judged stale rather than being compared against nothing.
     */
    private suspend fun applyLocalizedDefaults(
        resource: MediaResource,
        nameRes: Int,
        commentRes: Int?,
        languages: List<String>,
        currentLang: String,
    ): Boolean {
        val currentEntry = LocalizedEntry(
            name = getStringForLanguage(nameRes, currentLang),
            comment = commentRes?.let { getStringForLanguage(it, currentLang) },
        )
        val nameDiffers = resource.name != currentEntry.name
        val commentDiffers = currentEntry.comment != null && resource.comment != currentEntry.comment
        // The record already speaks the current language: the other declared tags are never resolved,
        // which is what makes this affordable on the startup path (S2627).
        if (!nameDiffers && !commentDiffers) return false

        val otherEntries = languages.filter { it != currentLang }.map { lang ->
            LocalizedEntry(
                name = getStringForLanguage(nameRes, lang),
                comment = commentRes?.let { getStringForLanguage(it, lang) },
            )
        }
        val nameNeedsUpdate = nameDiffers && otherEntries.any { it.name == resource.name }
        val commentNeedsUpdate = commentDiffers &&
            otherEntries.any { it.comment != null && it.comment == resource.comment }
        val needsUpdate = nameNeedsUpdate || commentNeedsUpdate
        if (needsUpdate) {
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
