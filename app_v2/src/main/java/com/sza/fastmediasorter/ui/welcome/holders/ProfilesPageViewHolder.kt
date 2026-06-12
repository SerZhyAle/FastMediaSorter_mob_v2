package com.sza.fastmediasorter.ui.welcome.holders

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.PageWelcomeProfilesBinding
import com.sza.fastmediasorter.ui.profile.DeviceProfileTileAdapter
import com.sza.fastmediasorter.ui.welcome.WelcomePage

/**
 * Device-profile onboarding page (S0399). Renders the full profile tiles (icon + title + 2-line
 * description) in an adaptive grid, ordered small-screen-first, with the recommended profile
 * pre-selected, badged and auto-scrolled into view.
 *
 * Detection is asynchronous: the page is bound (it is adjacent to page 0) before
 * WelcomeViewModel.detectDeviceProfile() resolves, so [bind] often runs with a null recommendation.
 * When the recommendation later arrives, [updateSelection] rebuilds the grid once so the recommended
 * badge appears and the grid auto-scrolls to it; subsequent user picks only restyle the selection.
 *
 * Keep the constructor signature `(PageWelcomeProfilesBinding)` and the [updateSelection] signature
 * stable: WelcomePagerAdapter constructs the holder and calls [updateSelection] from refreshProfiles().
 */
class ProfilesPageViewHolder(
    private val binding: PageWelcomeProfilesBinding
) : RecyclerView.ViewHolder(binding.root) {

    private var tileAdapter: DeviceProfileTileAdapter? = null
    private var orderedProfiles: List<DeviceProfileType> = emptyList()
    private var onProfileSelected: ((DeviceProfileType) -> Unit)? = null
    /** The recommendation currently rendered; a change (null -> resolved) triggers a grid rebuild. */
    private var renderedRecommended: DeviceProfileType? = null
    private var hasRecommendedValue = false
    private var autoScrolled = false

    fun bind(page: WelcomePage) {
        orderedProfiles = orderSmallScreenFirst(page.selectableProfiles)
        onProfileSelected = page.onProfileSelected
        renderedRecommended = null
        hasRecommendedValue = false
        autoScrolled = false
        renderGrid(page.recommendedProfileType, page.selectedProfileType)
    }

    /**
     * Refresh after async detection / a user pick. When the recommendation changes (e.g. first
     * arrival), rebuild the grid so the recommended badge shows and the grid auto-scrolls; otherwise
     * just restyle the selection (cheap, no rebuild) - ViewPager2 will not rebind the visible page.
     */
    fun updateSelection(recommendedType: DeviceProfileType?, selectedType: DeviceProfileType?) {
        if (recommendedType != renderedRecommended || !hasRecommendedValue) {
            renderGrid(recommendedType, selectedType)
        } else {
            val target = selectedType ?: recommendedType ?: return
            tileAdapter?.setSelected(target)
        }
    }

    private fun renderGrid(recommended: DeviceProfileType?, selected: DeviceProfileType?) {
        renderedRecommended = recommended
        hasRecommendedValue = true
        val context = binding.root.context

        // DeviceProfileTileAdapter requires a non-null selection. Prefer the explicit selection, then
        // the recommended profile, then the first tile - matching the visible pre-selected highlight.
        val initialSelected = selected
            ?: recommended
            ?: orderedProfiles.firstOrNull()
            ?: DeviceProfileType.PERSONAL_SMARTPHONE

        val adapter = DeviceProfileTileAdapter(
            profiles = orderedProfiles,
            recommended = recommended,
            selected = initialSelected,
            onClick = { type -> onProfileSelected?.invoke(type) },
        )
        tileAdapter = adapter

        if (binding.rvProfiles.layoutManager == null) {
            // Span follows the picker-dialog rule (orientation-independent, scales with the smallest
            // width): a phone shows one column, a tablet two, a large tablet/TV three.
            val swDp = context.resources.configuration.smallestScreenWidthDp
            val columns = when {
                swDp >= 720 -> 3
                swDp >= 480 -> 2
                else -> 1
            }
            binding.rvProfiles.layoutManager = GridLayoutManager(context, columns)
        }
        binding.rvProfiles.adapter = adapter

        // Auto-scroll once the recommended (else selected) tile is known, so it is on screen without
        // manual scrolling. Done a single time so a later user pick does not yank the grid around.
        if (!autoScrolled) {
            val target = recommended ?: selected
            val targetIndex = target?.let { orderedProfiles.indexOf(it) } ?: -1
            if (targetIndex > 0) {
                binding.rvProfiles.post {
                    (binding.rvProfiles.layoutManager as? GridLayoutManager)
                        ?.scrollToPositionWithOffset(targetIndex, 0)
                }
                autoScrolled = true
            }
        }
    }

    /**
     * Reorder [profiles] so single-hand / small-screen device profiles surface first, keeping only
     * entries actually selectable in this flavor. Profiles not in [smallScreenFirstOrder] are
     * appended in their original order, so a new enum value still renders without a code change here.
     */
    private fun orderSmallScreenFirst(profiles: List<DeviceProfileType>): List<DeviceProfileType> {
        val present = profiles.toSet()
        val ranked = smallScreenFirstOrder.filter { it in present }
        val rest = profiles.filter { it !in smallScreenFirstOrder }
        return ranked + rest
    }

    private companion object {
        /** Page-local display order (small-screen profiles first). The global DeviceProfileUi order is
         *  intentionally left untouched so the Settings picker dialog keeps its own ordering. */
        val smallScreenFirstOrder = listOf(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            DeviceProfileType.PHOTO_FRAME,
            DeviceProfileType.EBOOK_READER,
            DeviceProfileType.AUDIO_PLAYER,
            DeviceProfileType.VIDEO_PLAYER,
            DeviceProfileType.MEDIA_PLAYER,
            DeviceProfileType.HOME_TABLET,
            DeviceProfileType.TV_MEDIA_BOX,
            DeviceProfileType.CAR_HEAD_UNIT,
            DeviceProfileType.VR_HEADSET,
            DeviceProfileType.OTHER,
        )
    }
}
