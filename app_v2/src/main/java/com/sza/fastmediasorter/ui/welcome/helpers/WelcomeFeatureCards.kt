package com.sza.fastmediasorter.ui.welcome.helpers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.welcome.FeatureCard

/**
 * Builds the pitch shown on the first welcome page.
 *
 * S1386: the pitch names the app's roles, not a sample of its capabilities. A literal list of
 * features reads as a truncation at any length, while the core roles and a final umbrella card
 * cover the app without requiring one card per capability.
 *
 * Every role belongs on every build - each one reads local storage, opens at least images, moves
 * files and sorts - so no card is ever dropped. What varies is the second line: it names only the
 * media types, protocols and services this build can actually open, so the pitch cannot promise
 * what the flavor gates away (S1389).
 *
 * S2310: the home-screen role is the exception - it exists only where the build ships the launcher
 * surface, so it is added conditionally and leads the list, because a build that can replace the
 * device shell is pitching that first and the media roles second.
 *
 * S2310 (owner review): streams and the watch companion are separate products of the app, not
 * footnotes of the umbrella card. Both are flavor-gated, so each is added only where its surface
 * is compiled in, and the umbrella card's second line no longer names streams - a build without
 * them must not advertise them, and a build with them shows the role itself.
 */
object WelcomeFeatureCards {

    fun build(
        capabilities: MediaCapabilities,
        launcherAvailable: Boolean,
        streamsAvailable: Boolean,
        wearAvailable: Boolean,
    ): List<FeatureCard> {
        val cards = buildList {
            if (launcherAvailable) {
                add(
                    FeatureCard(
                        R.drawable.ic_launcher_mode,
                        R.string.welcome_role_home_screen,
                        R.string.welcome_role_home_screen_detail
                    )
                )
            }
            add(
                FeatureCard(
                    R.drawable.ic_folder_open_24,
                    R.string.welcome_role_file_manager,
                    R.string.welcome_role_file_manager_detail
                )
            )
            add(
                FeatureCard(
                    R.drawable.ic_play,
                    playerTitle(capabilities),
                    playerDetail(capabilities)
                )
            )
            add(
                FeatureCard(
                    R.drawable.ic_resource,
                    R.string.welcome_role_sources,
                    sourcesDetail(capabilities)
                )
            )
            if (streamsAvailable) {
                add(
                    FeatureCard(
                        R.drawable.ic_cast,
                        R.string.welcome_role_streams,
                        R.string.welcome_role_streams_detail
                    )
                )
            }
            add(
                FeatureCard(
                    R.drawable.ic_swap_horizontal,
                    R.string.welcome_role_sorting,
                    R.string.welcome_role_sorting_detail
                )
            )
            if (wearAvailable) {
                add(
                    FeatureCard(
                        R.drawable.ic_watch,
                        R.string.welcome_role_wear,
                        R.string.welcome_role_wear_detail
                    )
                )
            }
            add(
                FeatureCard(
                    R.drawable.ic_apps,
                    R.string.welcome_role_more,
                    R.string.welcome_role_more_detail
                )
            )
        }
        return cards
    }

    private fun playerTitle(capabilities: MediaCapabilities): Int =
        if (capabilities.supportsVideo || capabilities.supportsAudio) {
            R.string.welcome_role_player
        } else {
            R.string.welcome_role_player_images_only
        }

    private fun playerDetail(capabilities: MediaCapabilities): Int = when {
        capabilities.supportsVideo && capabilities.supportsAudio && capabilities.supportsDocuments ->
            R.string.welcome_role_player_detail

        capabilities.supportsVideo && capabilities.supportsAudio ->
            R.string.welcome_role_player_detail_no_documents

        capabilities.supportsVideo -> R.string.welcome_role_player_detail_no_audio
        else -> R.string.welcome_role_player_detail_images_only
    }

    private fun sourcesDetail(capabilities: MediaCapabilities): Int = when {
        capabilities.supportsLocalNetworkSources && capabilities.supportsCloud ->
            R.string.welcome_role_sources_detail

        capabilities.supportsLocalNetworkSources -> R.string.welcome_role_sources_detail_network_only
        capabilities.supportsCloud -> R.string.welcome_role_sources_detail_cloud_only
        else -> R.string.welcome_role_sources_detail_local_only
    }
}
