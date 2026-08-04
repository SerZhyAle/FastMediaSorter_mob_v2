package com.sza.fastmediasorter.ui.welcome.helpers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.welcome.FeatureCard

/**
 * Builds the pitch shown on the first welcome page.
 *
 * The order is the pitch itself, not a grid: what the app opens, then where it reads from, then what
 * it does with it.
 *
 * S1389: this is the one surface in the wizard that makes promises rather than offering a toggle, so
 * every card answers to the build's own capabilities. A source the build cannot open loses its card
 * outright; a card that belongs on every build but names an absent media type keeps its place and
 * swaps to wording that stays true. The flags are the same ones that decide which wizard pages are
 * added, so the pitch and the pages cannot disagree about what the build can do.
 */
object WelcomeFeatureCards {

    fun build(capabilities: MediaCapabilities): List<FeatureCard> = buildList {
        val hasVideo = capabilities.supportsVideo
        add(
            FeatureCard(
                R.drawable.ic_image,
                if (hasVideo) R.string.welcome_feature_photos else R.string.welcome_feature_photos_images_only,
                if (hasVideo) {
                    R.string.welcome_feature_photos_detail
                } else {
                    R.string.welcome_feature_photos_detail_images_only
                }
            )
        )
        add(
            FeatureCard(
                R.drawable.ic_resource_local,
                R.string.welcome_feature_local_folders,
                R.string.welcome_feature_local_folders_detail
            )
        )
        if (capabilities.supportsLocalNetworkSources) {
            add(
                FeatureCard(
                    R.drawable.ic_resource_smb,
                    R.string.welcome_feature_network,
                    R.string.welcome_feature_network_detail
                )
            )
        }
        if (capabilities.supportsCloud) {
            add(
                FeatureCard(
                    R.drawable.ic_resource_cloud,
                    R.string.welcome_feature_cloud,
                    R.string.welcome_feature_cloud_detail
                )
            )
        }
        add(
            FeatureCard(
                R.drawable.ic_swap_horizontal,
                R.string.welcome_feature_sorting,
                R.string.welcome_feature_sorting_detail
            )
        )
        add(
            FeatureCard(
                R.drawable.ic_slideshow,
                R.string.welcome_feature_slideshow,
                if (capabilities.supportsAudio) {
                    R.string.welcome_feature_slideshow_detail
                } else {
                    R.string.welcome_feature_slideshow_detail_no_audio
                }
            )
        )
    }
}
