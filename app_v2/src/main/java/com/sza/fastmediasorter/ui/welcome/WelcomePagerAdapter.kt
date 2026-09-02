package com.sza.fastmediasorter.ui.welcome

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.PageWelcomeBinding
import com.sza.fastmediasorter.databinding.PageWelcomeDefaultPlayerBinding
import com.sza.fastmediasorter.databinding.PageWelcomeEnhancedBinding
import com.sza.fastmediasorter.databinding.PageWelcomeFunctionalityBinding
import com.sza.fastmediasorter.databinding.PageWelcomeNetworksBinding
import com.sza.fastmediasorter.databinding.PageWelcomePermissionsBinding
import com.sza.fastmediasorter.databinding.PageWelcomeProfilesBinding
import com.sza.fastmediasorter.ui.dialog.UiLanguagePickerItems
import com.sza.fastmediasorter.ui.welcome.holders.FunctionalityPageViewHolder
import com.sza.fastmediasorter.ui.welcome.holders.PermissionsPageViewHolder
import com.sza.fastmediasorter.ui.welcome.holders.ProfilesPageViewHolder
import timber.log.Timber

class WelcomePagerAdapter(
    private val pages: List<WelcomePage>,
    private val mediaCapabilities: MediaCapabilities
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_PROFILES = 1
        private const val VIEW_TYPE_FUNCTIONALITY = 2
        private const val VIEW_TYPE_ENHANCED = 3
        private const val VIEW_TYPE_DEFAULT_PLAYER = 4
        private const val VIEW_TYPE_NETWORKS = 5
        private const val VIEW_TYPE_PERMISSIONS = 6

        /** Opaque end of the 8-bit alpha channel, for turning [WelcomePagePalette.PANEL_ALPHA] into a colour. */
        private const val MAX_ALPHA = 255
    }

    /** The currently-bound device-profile page holder, kept so the Activity can refresh the grid
     *  selection directly (ViewPager2 does not reliably rebind the visible page on notifyItemChanged). */
    private var profilesHolder: ProfilesPageViewHolder? = null

    // Latest profile recommendation/selection from the (async) detector. Cached here so it is applied
    // even when it resolves BEFORE the profiles holder binds - onBindViewHolder replays it on bind.
    private var latestRecommended: DeviceProfileType? = null
    private var latestSelected: DeviceProfileType? = null
    private var hasLatestProfiles = false

    /** Refresh the device-profile grid selection directly after detection resolves or a pick (S0399). */
    fun refreshProfiles(recommendedType: DeviceProfileType?, selectedType: DeviceProfileType?) {
        latestRecommended = recommendedType
        latestSelected = selectedType
        hasLatestProfiles = true
        profilesHolder?.updateSelection(recommendedType, selectedType)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            pages[position].isProfilesPage -> VIEW_TYPE_PROFILES
            pages[position].isFunctionalityPage -> VIEW_TYPE_FUNCTIONALITY
            pages[position].isPermissionsPage -> VIEW_TYPE_PERMISSIONS
            pages[position].isNetworksPage -> VIEW_TYPE_NETWORKS
            pages[position].isDefaultPlayerPage -> VIEW_TYPE_DEFAULT_PLAYER
            pages[position].featureCards.isNotEmpty() -> VIEW_TYPE_ENHANCED
            else -> VIEW_TYPE_NORMAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PROFILES ->
                ProfilesPageViewHolder(PageWelcomeProfilesBinding.inflate(inflater, parent, false))
            VIEW_TYPE_FUNCTIONALITY ->
                FunctionalityPageViewHolder(PageWelcomeFunctionalityBinding.inflate(inflater, parent, false))
            VIEW_TYPE_PERMISSIONS ->
                PermissionsPageViewHolder(PageWelcomePermissionsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_NETWORKS ->
                NetworksViewHolder(PageWelcomeNetworksBinding.inflate(inflater, parent, false))
            VIEW_TYPE_DEFAULT_PLAYER ->
                DefaultPlayerViewHolder(
                    PageWelcomeDefaultPlayerBinding.inflate(inflater, parent, false),
                    mediaCapabilities
                )
            VIEW_TYPE_ENHANCED ->
                EnhancedViewHolder(PageWelcomeEnhancedBinding.inflate(inflater, parent, false))
            else ->
                WelcomeViewHolder(PageWelcomeBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        applyContentPanel(holder.itemView, position)
        when (holder) {
            is ProfilesPageViewHolder -> {
                profilesHolder = holder
                holder.bind(pages[position])
                // Replay the latest detector result if it already resolved before this bind, so the
                // recommended badge + auto-scroll are not lost to the bind/detection race.
                if (hasLatestProfiles) holder.updateSelection(latestRecommended, latestSelected)
            }
            is FunctionalityPageViewHolder -> holder.bind(pages[position])
            is PermissionsPageViewHolder -> holder.bind(pages[position])
            is NetworksViewHolder -> holder.bind(pages[position])
            is DefaultPlayerViewHolder -> holder.bind(pages[position])
            is EnhancedViewHolder -> holder.bind(pages[position])
            is WelcomeViewHolder -> holder.bind(pages[position])
        }
    }

    override fun getItemCount(): Int = pages.size

    /**
     * S1234: gives the page copy a translucent backing tinted with this page's colour, so it stays
     * legible over the brand animation while the pages remain visually distinct. Applied here, for
     * every view type at once, because the container id is shared across all page layouts - doing it
     * per view holder would mean seven copies of the same three lines.
     */
    private fun applyContentPanel(itemView: View, position: Int) {
        val panel = itemView.findViewById<View>(R.id.layoutContent) ?: return
        val base = ContextCompat.getColor(itemView.context, WelcomePagePalette.colorResFor(position))
        val alpha = (WelcomePagePalette.PANEL_ALPHA * MAX_ALPHA).toInt()
        panel.setBackgroundResource(R.drawable.bg_welcome_content_panel)
        panel.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(base, alpha))
    }

    class WelcomeViewHolder(
        private val binding: PageWelcomeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            binding.ivIcon.setImageResource(page.iconRes)
            binding.tvTitle.text = binding.root.context.getString(page.titleRes)
            binding.tvDescription.text = binding.root.context.getString(page.descriptionRes)
            bindDetails(binding.tvDetails, page)

            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 300L)
        }
    }

    class DefaultPlayerViewHolder(
        private val binding: PageWelcomeDefaultPlayerBinding,
        private val mediaCapabilities: MediaCapabilities
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            // Gate button visibility by flavor support and wire click callbacks.
            binding.btnSetDefaultAudio.isVisible = mediaCapabilities.supportsAudio
            binding.btnSetDefaultVideo.isVisible = mediaCapabilities.supportsVideo
            binding.btnSetDefaultImages.isVisible = mediaCapabilities.supportsImages
            binding.btnSetDefaultDocs.isVisible = mediaCapabilities.supportsDocuments

            binding.btnSetDefaultAudio.setOnClickListener {
                page.onSetDefaultForTypeClick?.invoke("audio/*")
            }
            binding.btnSetDefaultVideo.setOnClickListener {
                page.onSetDefaultForTypeClick?.invoke("video/*")
            }
            binding.btnSetDefaultImages.setOnClickListener {
                page.onSetDefaultForTypeClick?.invoke("image/*")
            }
            binding.btnSetDefaultDocs.setOnClickListener {
                page.onSetDefaultForTypeClick?.invoke("application/pdf")
            }

            binding.tvHint?.text = HtmlCompat.fromHtml(
                binding.root.context.getString(com.sza.fastmediasorter.R.string.welcome_default_player_hint),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )

            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 250L)
            binding.tvHint?.let { animateEntrance(it, 320L) }
            animateEntrance(binding.layoutTypeButtons, 400L)
        }
    }

    class NetworksViewHolder(
        private val binding: PageWelcomeNetworksBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            // S0391: WelcomeRemoteSourcesController owns the three group toggles (SMB / (S)FTP / Cloud)
            // and their persistence; the page stays a thin renderer (mirrors onBindFunctionality).
            page.onBindNetworks?.invoke(binding)

            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 250L)
        }
    }

    class EnhancedViewHolder(
        private val binding: PageWelcomeEnhancedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            val context = binding.root.context
            binding.ivIcon.setImageResource(page.iconRes)
            binding.tvTitle.text = context.getString(page.titleRes)
            binding.tvDescription.text = context.getString(page.descriptionRes)
            bindDetails(binding.tvDetails, page)
            populateFeatureGrid(binding.gridFeatures, page.featureCards)

            // S1190: language wiring. The control only reports the tap - the Activity owns the picker,
            // because choosing a language recreates it and an adapter cannot survive that.
            if (page.showLanguagePicker) {
                val currentLanguage = LocaleHelper.getLanguage(context)
                binding.btnWelcomeLanguage.visibility = View.VISIBLE
                binding.btnWelcomeLanguage.text = UiLanguagePickerItems.label(context, currentLanguage)
                binding.btnWelcomeLanguage.setOnClickListener { page.onLanguagePickerRequested?.invoke() }
            } else {
                binding.btnWelcomeLanguage.visibility = View.GONE
            }

            // Theme picker wiring: mirrors the language picker. Pre-checks the button for the current
            // ColorThemePrefs mode; the dual-write (DataStore + mirror) and immediate recreate happen
            // in the Activity callback.
            if (page.showThemePicker) {
                binding.layoutThemePicker.visibility = View.VISIBLE
                binding.tvThemeAppliesHint.visibility = View.VISIBLE
                binding.layoutThemePicker.clearOnButtonCheckedListeners()
                val currentMode = ColorThemePrefs.getMode(binding.root.context)
                val initialThemeId = when (currentMode) {
                    "LIGHT" -> R.id.btnThemeLight
                    "DARK" -> R.id.btnThemeDark
                    else -> R.id.btnThemeAuto
                }
                binding.layoutThemePicker.check(initialThemeId)
                binding.layoutThemePicker.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener
                    val mode = when (checkedId) {
                        R.id.btnThemeLight -> "LIGHT"
                        R.id.btnThemeDark -> "DARK"
                        else -> "AUTO"
                    }
                    if (mode != ColorThemePrefs.getMode(binding.root.context)) {
                        page.onThemeSelected?.invoke(mode)
                    }
                }
            } else {
                binding.layoutThemePicker.visibility = View.GONE
                binding.tvThemeAppliesHint.visibility = View.GONE
            }

            bindLauncherToggle(page)

            // Staggered entrance animations
            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 250L)
            animateEntrance(binding.gridFeatures, 400L)
        }

        // S0404 / S1104: launcher-mode toggle - only on the first page, only in builds shipping the surface.
        // Canonical SettingsToggleRow (switch-left): whole-row tap + focus are owned by the widget.
        private fun bindLauncherToggle(page: WelcomePage) {
            if (!page.showLauncherModeToggle) {
                binding.rowWelcomeLauncherMode.visibility = View.GONE
                return
            }
            binding.rowWelcomeLauncherMode.visibility = View.VISIBLE
            // Restore the visual from the surviving ViewModel flag WITHOUT firing the callback, so a
            // recreate (language/theme pick, fold) never silently drops the user's ON choice.
            binding.rowWelcomeLauncherMode.setCheckedSilently(page.launcherModeChecked)
            binding.rowWelcomeLauncherMode.setOnCheckedChangeListener { isChecked ->
                page.onLauncherModeToggled?.invoke(isChecked)
            }
        }
    }
}

/**
 * Helper function to apply fade+slide-up entrance animation with a start delay.
 */
private fun animateEntrance(view: View, delayMs: Long) {
    if (!AnimationPolicy.isAnimationAllowed) {
        view.clearAnimation()
        view.alpha = 1f
        view.translationY = 0f
        Timber.d("S2250: welcome entrance skipped")
        return
    }
    val anim = AnimationUtils.loadAnimation(view.context, R.anim.welcome_fade_slide_up)
    anim.startOffset = delayMs
    view.startAnimation(anim)
}

/**
 * Bind the optional scrollable "details" block: shows the multi-paragraph copy when
 * [WelcomePage.detailDescriptionRes] is non-zero, otherwise collapses the view.
 */
private fun bindDetails(view: TextView, page: WelcomePage) {
    if (page.detailDescriptionRes != 0) {
        view.text = view.context.getString(page.detailDescriptionRes)
        view.visibility = View.VISIBLE
        animateEntrance(view, 400L)
    } else {
        view.visibility = View.GONE
    }
}

/**
 * Fill [grid] with one row per [cards] entry (badged icon + bold title + one short detail line).
 * The column count comes from @integer/welcome_feature_grid_columns - one column on a phone so the
 * rows read as a list, two on a tablet or in landscape; cells share the row width evenly via column
 * weight. Collapses the grid when [cards] is empty.
 */
private fun populateFeatureGrid(grid: GridLayout, cards: List<FeatureCard>) {
    grid.removeAllViews()
    if (cards.isEmpty()) {
        grid.visibility = View.GONE
        return
    }
    grid.visibility = View.VISIBLE
    val context = grid.context
    val columns = context.resources.getInteger(R.integer.welcome_feature_grid_columns).coerceAtLeast(1)
    grid.columnCount = columns
    val gutter = context.resources.getDimensionPixelSize(R.dimen.welcome_feature_row_gutter)
    val spacing = context.resources.getDimensionPixelSize(R.dimen.welcome_feature_row_spacing)
    val inflater = LayoutInflater.from(context)
    cards.forEachIndexed { index, card ->
        val row = inflater.inflate(R.layout.item_welcome_feature_tile, grid, false)
        row.findViewById<ImageView>(R.id.ivFeatureTileIcon).setImageResource(card.iconRes)
        val label = context.getString(card.labelRes)
        val detail = context.getString(card.detailRes)
        row.findViewById<TextView>(R.id.tvFeatureTileLabel).text = label
        row.findViewById<TextView>(R.id.tvFeatureTileDetail).text = detail
        // Two TextViews would otherwise be announced as two separate items; the row is one idea.
        row.contentDescription = "$label. $detail"
        val params = GridLayout.LayoutParams(
            GridLayout.spec(index / columns),
            GridLayout.spec(index % columns, 1, GridLayout.FILL, 1f)
        )
        params.width = 0
        params.setMargins(gutter, spacing, gutter, spacing)
        row.layoutParams = params
        grid.addView(row)
    }
}

data class WelcomePage(
    // Header icon/title/description: 0 on pages that render their own content (networks, profiles,
    // functionality, permissions, default-player).
    val iconRes: Int = 0,
    val titleRes: Int = 0,
    val descriptionRes: Int = 0,
    /** Optional string resource for the scrollable "details" block below the header; 0 = no details. */
    val detailDescriptionRes: Int = 0,
    val isDefaultPlayerPage: Boolean = false,
    /** Marks the networks page (SMB / (S)FTP / Cloud group toggles). */
    val isNetworksPage: Boolean = false,
    /** Hands the page binding to WelcomeRemoteSourcesController, which owns the three group toggles. */
    val onBindNetworks: ((PageWelcomeNetworksBinding) -> Unit)? = null,
    /** Called with the MIME type when the user taps a type-specific default-player button. */
    val onSetDefaultForTypeClick: ((mimeType: String) -> Unit)? = null,
    val featureCards: List<FeatureCard> = emptyList(),
    /** Show the interface-language control. Only set on the first Welcome page. */
    val showLanguagePicker: Boolean = false,
    /** Invoked when the user taps that control; the host opens the searchable picker. */
    val onLanguagePickerRequested: (() -> Unit)? = null,
    /** Show the colour-theme picker strip (Auto/Light/Dark). Only set on the first Welcome page. */
    val showThemePicker: Boolean = false,
    /** Invoked with "AUTO"|"LIGHT"|"DARK" when the user taps a theme button. */
    val onThemeSelected: ((mode: String) -> Unit)? = null,
    /** S0404: show the "use as home screen" toggle. Only set on the first page, capability-gated. */
    val showLauncherModeToggle: Boolean = false,
    /** S0404: current launcher-mode choice, so a rebind after an Activity recreate restores the switch. */
    val launcherModeChecked: Boolean = false,
    /** S0404: invoked with the switch state when the user flips the launcher-mode toggle. */
    val onLauncherModeToggled: ((Boolean) -> Unit)? = null,
    // ── S0399 device-profile page ────────────────────────────────────────────
    /** Marks the dedicated device-profile selection page (full tile grid). */
    val isProfilesPage: Boolean = false,
    /** Profiles selectable in this flavor (from DeviceProfileAvailability); ordered by the page holder. */
    val selectableProfiles: List<DeviceProfileType> = emptyList(),
    val recommendedProfileType: DeviceProfileType? = null,
    val selectedProfileType: DeviceProfileType? = null,
    val onProfileSelected: ((DeviceProfileType) -> Unit)? = null,
    /** S1383: a tap on the tile that is already selected - apply the pick and move on. */
    val onProfileConfirmed: ((DeviceProfileType) -> Unit)? = null,
    // ── S0400 functionality page ─────────────────────────────────────────────
    /** Marks the functionality (capability toggles + downloads) page. */
    val isFunctionalityPage: Boolean = false,
    /** Hands the page binding to WelcomeFunctionalityController, which owns all toggle/download logic. */
    val onBindFunctionality: ((PageWelcomeFunctionalityBinding) -> Unit)? = null,
    // ── S0402 permissions page ───────────────────────────────────────────────
    /** Marks the adaptive permissions page hosted in the pager. */
    val isPermissionsPage: Boolean = false,
    /** Hands the page binding to WelcomePermissionsManager, which owns the adaptive set + grant-all. */
    val onBindPermissions: ((PageWelcomePermissionsBinding) -> Unit)? = null,
)

data class FeatureCard(
    val iconRes: Int,
    val labelRes: Int,
    /** One-line benefit shown under the title - what this capability buys the user, not what it is. */
    val detailRes: Int
)
