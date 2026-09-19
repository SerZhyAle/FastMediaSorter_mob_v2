package com.sza.fastmediasorter.ui.welcome.holders

import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.PageWelcomeProfilesBinding
import com.sza.fastmediasorter.domain.launcher.LauncherPrimaryWindow
import com.sza.fastmediasorter.ui.profile.DeviceProfileTileAdapter
import com.sza.fastmediasorter.ui.welcome.WelcomePage
import com.sza.fastmediasorter.ui.welcome.WelcomePrimaryWindowChoiceAdapter

/**
 * Device-profile and primary-window onboarding page (S0399 / S3024).
 * Renders the full profile tiles in an adaptive grid with the recommended profile pre-selected,
 * and a consolidated three-way primary-window selection list when launcher mode is available.
 */
class ProfilesPageViewHolder(
    private val binding: PageWelcomeProfilesBinding
) : RecyclerView.ViewHolder(binding.root) {

    private var tileAdapter: DeviceProfileTileAdapter? = null
    private var primaryWindowAdapter: WelcomePrimaryWindowChoiceAdapter? = null
    private var orderedProfiles: List<DeviceProfileType> = emptyList()
    private var onProfileSelected: ((DeviceProfileType) -> Unit)? = null
    private var onProfileConfirmed: ((DeviceProfileType) -> Unit)? = null
    private var onPrimaryWindowSelected: ((LauncherPrimaryWindow) -> Unit)? = null

    private var renderedRecommended: DeviceProfileType? = null
    private var renderedSelected: DeviceProfileType? = null
    private var hasRecommendedValue = false
    private var autoScrolled = false

    private var showPrimaryWindowChoice = false
    private var renderedPrimaryWindowSelected: LauncherPrimaryWindow? = null
    private var renderedPrimaryWindowRecommended: LauncherPrimaryWindow? = null

    fun bind(page: WelcomePage) {
        orderedProfiles = orderSmallScreenFirst(page.selectableProfiles)
        onProfileSelected = page.onProfileSelected
        onProfileConfirmed = page.onProfileConfirmed
        onPrimaryWindowSelected = page.onPrimaryWindowSelected
        showPrimaryWindowChoice = page.showPrimaryWindowChoice

        renderedRecommended = null
        renderedSelected = null
        hasRecommendedValue = false
        autoScrolled = false

        renderGrid(page.recommendedProfileType, page.selectedProfileType)
        renderPrimaryWindowChoices(page.selectedPrimaryWindow, page.recommendedPrimaryWindow)
    }

    /**
     * Refresh after async detection or user selection.
     */
    fun updateSelection(
        recommendedType: DeviceProfileType?,
        selectedType: DeviceProfileType?,
        selectedPrimaryWindow: LauncherPrimaryWindow? = null,
        recommendedPrimaryWindow: LauncherPrimaryWindow? = null
    ) {
        if (recommendedType != renderedRecommended || !hasRecommendedValue) {
            renderGrid(recommendedType, selectedType)
        } else {
            val target = selectedType ?: recommendedType ?: return
            if (target != renderedSelected) {
                renderedSelected = target
                tileAdapter?.setSelected(target)
                revealTile(target)
            }
        }
        updatePrimaryWindowSelection(selectedPrimaryWindow, recommendedPrimaryWindow)
    }

    private fun renderPrimaryWindowChoices(
        selected: LauncherPrimaryWindow?,
        recommended: LauncherPrimaryWindow?
    ) {
        if (!showPrimaryWindowChoice) {
            binding.layoutPrimaryWindow.isVisible = false
            return
        }
        binding.layoutPrimaryWindow.isVisible = true

        val rec = recommended
            ?: LauncherPrimaryWindow.recommendedFor(renderedSelected ?: renderedRecommended)
        val sel = selected ?: rec

        renderedPrimaryWindowSelected = sel
        renderedPrimaryWindowRecommended = rec

        if (primaryWindowAdapter == null) {
            val adapter = WelcomePrimaryWindowChoiceAdapter(
                selectedChoice = sel,
                recommendedChoice = rec,
                onChoiceSelected = { choice ->
                    renderedPrimaryWindowSelected = choice
                    onPrimaryWindowSelected?.invoke(choice)
                }
            )
            primaryWindowAdapter = adapter
            binding.rvPrimaryWindowChoices.layoutManager = LinearLayoutManager(binding.root.context)
            binding.rvPrimaryWindowChoices.adapter = adapter
        } else {
            primaryWindowAdapter?.updateSelection(sel, rec)
        }
    }

    fun updatePrimaryWindowSelection(
        selected: LauncherPrimaryWindow?,
        recommended: LauncherPrimaryWindow?
    ) {
        if (!showPrimaryWindowChoice) return
        val rec = recommended
            ?: LauncherPrimaryWindow.recommendedFor(renderedSelected ?: renderedRecommended)
        val sel = selected ?: renderedPrimaryWindowSelected ?: rec
        renderedPrimaryWindowSelected = sel
        renderedPrimaryWindowRecommended = rec
        primaryWindowAdapter?.updateSelection(sel, rec)
    }

    private fun revealTile(type: DeviceProfileType) {
        val index = orderedProfiles.indexOf(type)
        if (index < 0) return
        binding.rvProfiles.post { binding.rvProfiles.smoothScrollToPosition(index) }
    }

    private fun renderGrid(recommended: DeviceProfileType?, selected: DeviceProfileType?) {
        renderedRecommended = recommended
        hasRecommendedValue = true
        val context = binding.root.context

        val initialSelected = selected
            ?: recommended
            ?: orderedProfiles.firstOrNull()
            ?: DeviceProfileType.PERSONAL_SMARTPHONE
        renderedSelected = initialSelected

        val adapter = DeviceProfileTileAdapter(
            profiles = orderedProfiles,
            recommended = recommended,
            selected = initialSelected,
            onClick = { type -> onProfileSelected?.invoke(type) },
            onReselect = { type -> onProfileConfirmed?.invoke(type) },
        )
        tileAdapter = adapter

        if (binding.rvProfiles.layoutManager == null) {
            val swDp = context.resources.configuration.smallestScreenWidthDp
            val columns = when {
                swDp >= 720 -> 3
                swDp >= 480 -> 2
                else -> 1
            }
            binding.rvProfiles.layoutManager = GridLayoutManager(context, columns)
        }
        binding.rvProfiles.adapter = adapter

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

    private fun orderSmallScreenFirst(profiles: List<DeviceProfileType>): List<DeviceProfileType> {
        val present = profiles.toSet()
        val ranked = smallScreenFirstOrder.filter { it in present }
        val rest = profiles.filter { it !in smallScreenFirstOrder }
        return ranked + rest
    }

    private companion object {
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
