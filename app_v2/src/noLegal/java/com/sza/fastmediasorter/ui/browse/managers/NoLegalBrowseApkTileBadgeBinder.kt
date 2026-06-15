package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoLegalBrowseApkTileBadgeBinder @Inject constructor(
    private val classificationCache: VrApkClassificationCache,
) : VariantBrowseApkTileBadgeBinder {

    override fun bind(root: View, mediaFile: MediaFile) {
        val badgeContainer = root.findViewById<View>(R.id.browseApkVrBadgeContainer) ?: return
        val requestToken = advanceRequestToken(root)
        root.setTag(R.id.browseApkVrBadgeBoundPath, mediaFile.path)
        renderBadge(badgeContainer, VrApkClassification.NOT_VR)

        if (mediaFile.isDirectory || !mediaFile.name.isApkFile()) {
            return
        }

        // VR classification requires reading the APK manifest; for non-local paths that means
        // downloading the whole file, which can block for minutes on slow network mounts.
        // Badge is only meaningful for locally-accessible APKs where the read is instant.
        if (!PathUtils.isLocalPath(mediaFile.path)) {
            return
        }

        classificationCache.peek(mediaFile)?.let { cached ->
            renderBadge(badgeContainer, cached)
            return
        }

        classificationCache.requestClassification(mediaFile) { classification ->
            val activeToken = root.getTag(R.id.browseApkVrBadgeRequestToken) as? Long
            val activePath = root.getTag(R.id.browseApkVrBadgeBoundPath) as? String
            if (activeToken != requestToken || activePath != mediaFile.path) {
                return@requestClassification
            }
            renderBadge(badgeContainer, classification)
        }
    }

    override fun onViewRecycled(root: View) {
        advanceRequestToken(root)
        root.setTag(R.id.browseApkVrBadgeBoundPath, null)
        root.findViewById<View>(R.id.browseApkVrBadgeContainer)?.let { badgeContainer ->
            renderBadge(badgeContainer, VrApkClassification.NOT_VR)
        }
    }

    private fun advanceRequestToken(root: View): Long {
        val nextToken = ((root.getTag(R.id.browseApkVrBadgeRequestToken) as? Long) ?: 0L) + 1L
        root.setTag(R.id.browseApkVrBadgeRequestToken, nextToken)
        return nextToken
    }

    private fun renderBadge(
        badgeContainer: View,
        classification: VrApkClassification,
    ) {
        badgeContainer.isVisible = classification.isVrCapable
    }
}

private fun String.isApkFile(): Boolean = substringAfterLast('.', "").equals("apk", ignoreCase = true)