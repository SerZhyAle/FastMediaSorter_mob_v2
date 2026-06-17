package com.sza.fastmediasorter.ui.welcome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.PageWelcomeBinding
import com.sza.fastmediasorter.databinding.PageWelcomeDefaultPlayerBinding
import com.sza.fastmediasorter.databinding.PageWelcomeEnhancedBinding
import com.sza.fastmediasorter.databinding.PageWelcomeFunctionalityBinding
import com.sza.fastmediasorter.databinding.PageWelcomeNetworksBinding
import com.sza.fastmediasorter.databinding.PageWelcomePermissionsBinding
import com.sza.fastmediasorter.databinding.PageWelcomeProfilesBinding
import com.sza.fastmediasorter.ui.welcome.holders.FunctionalityPageViewHolder
import com.sza.fastmediasorter.ui.welcome.holders.PermissionsPageViewHolder
import com.sza.fastmediasorter.ui.welcome.holders.ProfilesPageViewHolder

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

            // Language picker wiring
            if (page.showLanguagePicker) {
                binding.layoutLanguagePicker.visibility = View.VISIBLE
                binding.layoutLanguagePicker.clearOnButtonCheckedListeners()
                val currentLang = LocaleHelper.getLanguage(binding.root.context)
                val initialId = when (currentLang) {
                    "ru" -> R.id.btnLangRu
                    "uk" -> R.id.btnLangUk
                    else -> R.id.btnLangEn
                }
                binding.layoutLanguagePicker.check(initialId)
                binding.layoutLanguagePicker.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener
                    val code = when (checkedId) {
                        R.id.btnLangRu -> "ru"
                        R.id.btnLangUk -> "uk"
                        else -> "en"
                    }
                    if (code != LocaleHelper.getLanguage(binding.root.context)) {
                        page.onLanguageSelected?.invoke(code)
                    }
                }
            } else {
                binding.layoutLanguagePicker.visibility = View.GONE
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

            // Staggered entrance animations
            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 250L)
            animateEntrance(binding.gridFeatures, 400L)
        }
    }
}

/**
 * Helper function to apply fade+slide-up entrance animation with a start delay.
 */
private fun animateEntrance(view: View, delayMs: Long) {
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
 * Fill [grid] with one tile per [cards] entry (tinted icon + ≤2-line label). The column count
 * comes from @integer/welcome_feature_grid_columns (adapts per screen-width / orientation); cells
 * share the row width evenly via column weight. Collapses the grid when [cards] is empty.
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
    val margin = context.resources.getDimensionPixelSize(R.dimen.welcome_feature_card_margin)
    val inflater = LayoutInflater.from(context)
    cards.forEachIndexed { index, card ->
        val tile = inflater.inflate(R.layout.item_welcome_feature_tile, grid, false)
        tile.findViewById<ImageView>(R.id.ivFeatureTileIcon).setImageResource(card.iconRes)
        val label = context.getString(card.labelRes)
        tile.findViewById<TextView>(R.id.tvFeatureTileLabel).text = label
        tile.contentDescription = label
        val params = GridLayout.LayoutParams(
            GridLayout.spec(index / columns),
            GridLayout.spec(index % columns, 1, GridLayout.FILL, 1f)
        )
        params.width = 0
        params.setMargins(margin, margin, margin, margin)
        tile.layoutParams = params
        grid.addView(tile)
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
    /** Show the language picker strip. Only set on the first Welcome page. */
    val showLanguagePicker: Boolean = false,
    /** Invoked with the ISO-639-1 language code when the user taps a picker button. */
    val onLanguageSelected: ((code: String) -> Unit)? = null,
    /** Show the colour-theme picker strip (Auto/Light/Dark). Only set on the first Welcome page. */
    val showThemePicker: Boolean = false,
    /** Invoked with "AUTO"|"LIGHT"|"DARK" when the user taps a theme button. */
    val onThemeSelected: ((mode: String) -> Unit)? = null,
    // ── S0399 device-profile page ────────────────────────────────────────────
    /** Marks the dedicated device-profile selection page (full tile grid). */
    val isProfilesPage: Boolean = false,
    /** Profiles selectable in this flavor (from DeviceProfileAvailability); ordered by the page holder. */
    val selectableProfiles: List<DeviceProfileType> = emptyList(),
    val recommendedProfileType: DeviceProfileType? = null,
    val selectedProfileType: DeviceProfileType? = null,
    val onProfileSelected: ((DeviceProfileType) -> Unit)? = null,
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
    val labelRes: Int
)
