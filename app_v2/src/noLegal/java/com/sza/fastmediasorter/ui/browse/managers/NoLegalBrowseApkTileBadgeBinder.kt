package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoLegalBrowseApkTileBadgeBinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classificationCache: VrApkClassificationCache,
) : VariantBrowseApkTileBadgeBinder {

    override fun bind(root: View, mediaFile: MediaFile) {
        val badgeContainer = root.findViewById<View>(R.id.browseApkVrBadgeContainer) ?: return
        val requestToken = advanceRequestToken(root)
        root.setTag(R.id.browseApkVrBadgeBoundPath, mediaFile.path)
        renderBadge(badgeContainer, VrApkClassification.NOT_VR)

        // VR classification requires reading the APK manifest; for non-local paths that means
        // downloading the whole file, which can block for minutes on slow network mounts.
        // Badge is only meaningful for locally-accessible APKs where the read is instant.
        if (!isBadgeEligible(mediaFile)) {
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

    override fun reservedTopBandPx(mediaFile: MediaFile): Int =
        if (isBadgeEligible(mediaFile)) {
            context.resources.getDimensionPixelSize(R.dimen.browse_apk_vr_badge_band)
        } else {
            0
        }

    /**
     * Whether [mediaFile] can carry the badge at all. Shared with [bind] so the strip reserved for the
     * badge and the strip the badge actually lands in cannot describe different sets of files.
     */
    private fun isBadgeEligible(mediaFile: MediaFile): Boolean =
        !mediaFile.isDirectory &&
            mediaFile.name.isApkFile() &&
            PathUtils.isLocalPath(mediaFile.path)

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