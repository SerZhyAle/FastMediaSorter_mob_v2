package com.sza.fastmediasorter.ui.settings.helpers

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.databinding.DialogLauncherWallpaperSettingsBinding
import com.sza.fastmediasorter.databinding.ItemLauncherWallpaperModeBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import timber.log.Timber
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * S2730: every binding decision of the wallpaper screen, so the fragment only wires views to it (Rule 3).
 *
 * The mode list is built rather than declared because which modes exist depends on the device - a phone
 * with no camera is offered four of the six - and because each row carries a description that has to stay
 * beside its own label.
 *
 * Slider positions are whole percents while the settings are fractions: a Material slider needs a step
 * size it can land on exactly, and a float step derived from a fraction accumulates error across the
 * range. The conversion is one pair of helpers here rather than at each of the three call sites.
 */
class LauncherWallpaperScreenManager(
    private val host: DialogFragment,
    private val binding: DialogLauncherWallpaperSettingsBinding,
    private val sourceManager: LauncherWallpaperSettingsManager,
    private val currentSettings: () -> AppSettings,
    private val isUpdating: () -> Boolean,
    private val applyMode: (String) -> Unit,
    private val applyTuning: (intensity: Float?, speed: Float?, density: Float?) -> Unit,
    private val applyPalette: (String) -> Unit,
    private val applyScreens: (count: Int?, showNumber: Boolean?) -> Unit,
) {
    private val modeRows = mutableMapOf<String, ItemLauncherWallpaperModeBinding>()

    private val sliderListeners = mutableListOf<Pair<Slider, Slider.OnChangeListener>>()

    fun setup() {
        buildModeRows()
        setupScreensSection()
        setupSliders()
        setupPaletteRow()
        binding.btnWallpaperSourceChange.setOnClickListener {
            sourceManager.beginSourceSelection(currentSettings().launcherWallpaperMode)
        }
    }

    fun render(settings: AppSettings) {
        val mode = settings.launcherWallpaperMode
        modeRows.forEach { (rowMode, rowBinding) -> rowBinding.radioWallpaperMode.isChecked = rowMode == mode }
        renderScreensSection(settings)
        renderSource(settings, mode)
        renderTuning(settings, mode)
        renderPowerState()
    }

    /**
     * S2730: the desktop's screen count and the screen-number badge, the two settings that describe the
     * surfaces themselves rather than what is painted on them.
     */
    private fun setupScreensSection() {
        binding.rowScreenCount.setEntries(SCREEN_COUNT_ENTRIES)
        binding.rowScreenCount.setOnItemSelectedListener { index ->
            if (isUpdating()) return@setOnItemSelectedListener
            applyScreens(index + FIRST_SCREEN_COUNT, null)
        }
        binding.rowShowScreenNumber.setOnCheckedChangeListener { isChecked ->
            if (isUpdating()) return@setOnCheckedChangeListener
            applyScreens(null, isChecked)
        }
    }

    private fun renderScreensSection(settings: AppSettings) {
        val index = (settings.launcherScreenCount - FIRST_SCREEN_COUNT)
            .coerceIn(0, SCREEN_COUNT_ENTRIES.lastIndex)
        binding.rowScreenCount.setSelection(index)
        binding.rowShowScreenNumber.setCheckedSilently(settings.launcherShowScreenNumber)
    }

    private fun buildModeRows() {
        val inflater = LayoutInflater.from(binding.root.context)
        binding.containerWallpaperModes.removeAllViews()
        modeRows.clear()
        sourceManager.offeredModes.forEach { mode ->
            val rowBinding = ItemLauncherWallpaperModeBinding
                .inflate(inflater, binding.containerWallpaperModes, true)
            rowBinding.tvWallpaperModeTitle.setText(LauncherWallpaperSettingsManager.labelOf(mode))
            rowBinding.tvWallpaperModeDescription
                .setText(LauncherWallpaperSettingsManager.descriptionOf(mode))
            rowBinding.root.setOnClickListener { onModeChosen(mode) }
            // The radio button is not focusable - the whole row is one target - so without this a screen
            // reader announces a plain container and never says which of the six is chosen.
            ViewCompat.setAccessibilityDelegate(
                rowBinding.root,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.isCheckable = true
                        info.isChecked = rowBinding.radioWallpaperMode.isChecked
                    }
                },
            )
            modeRows[mode] = rowBinding
        }
    }

    /**
     * A mode needing a source is not stored until the source is chosen: storing it first would leave the
     * desktop on a mode with no image or lens behind it, which the render layer degrades away from anyway.
     */
    private fun onModeChosen(mode: String) {
        if (isUpdating() || mode == currentSettings().launcherWallpaperMode) return
        if (sourceManager.needsSource(mode)) {
            sourceManager.beginSourceSelection(mode)
        } else {
            applyMode(mode)
        }
    }

    private fun setupSliders() {
        bindSlider(binding.sliderWallpaperIntensity) { value -> applyTuning(value, null, null) }
        bindSlider(binding.sliderWallpaperSpeed) { value -> applyTuning(null, value, null) }
        bindSlider(binding.sliderWallpaperDensity) { value -> applyTuning(null, null, value) }
    }

    /**
     * The listener is kept so [release] can hand it back: this class outlives nothing, but the slider it
     * registered on is a view the fragment destroys, and an unremoved listener is what the symmetry gate
     * exists to catch.
     */
    private fun bindSlider(slider: Slider, apply: (Float) -> Unit) {
        val listener = Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdating()) apply(fromPercent(value))
        }
        slider.addOnChangeListener(listener)
        sliderListeners += slider to listener
    }

    private fun setupPaletteRow() {
        binding.rowWallpaperPalette.setEntries(
            listOf(
                host.getText(R.string.launcher_settings_palette_dynamic),
                host.getText(R.string.launcher_settings_palette_green),
                host.getText(R.string.launcher_settings_palette_pink),
                host.getText(R.string.launcher_settings_palette_blue),
            )
        )
        binding.rowWallpaperPalette.setOnItemSelectedListener { index ->
            if (isUpdating()) return@setOnItemSelectedListener
            applyPalette(
                AppSettings.ANIMATION_PALETTE_OPTIONS
                    .getOrElse(index) { AppSettings.ANIMATION_PALETTE_DYNAMIC }
            )
        }
    }

    private fun renderSource(settings: AppSettings, mode: String) {
        val visible = sourceManager.needsSource(mode)
        binding.groupWallpaperSource.isVisible = visible
        if (!visible) return
        val imagePath = settings.launcherWallpaperImagePath
        val hasImage = mode == AppSettings.LAUNCHER_WALLPAPER_IMAGE && imagePath.isNotEmpty()
        binding.imgWallpaperPreview.isVisible = hasImage
        if (hasImage) {
            Glide.with(binding.imgWallpaperPreview).load(File(imagePath)).into(binding.imgWallpaperPreview)
        }
        binding.tvWallpaperSourceValue.text = sourceLabel(settings, mode, hasImage)
    }

    private fun sourceLabel(settings: AppSettings, mode: String, hasImage: Boolean): CharSequence = when {
        hasImage -> File(settings.launcherWallpaperImagePath).name
        mode == AppSettings.LAUNCHER_WALLPAPER_IMAGE -> host.getText(R.string.launcher_wallpaper_source_empty)
        settings.launcherWallpaperCameraId.isNotEmpty() -> settings.launcherWallpaperCameraId
        else -> host.getText(R.string.launcher_wallpaper_source_empty)
    }

    /**
     * The tuning block belongs to the two branded modes only: an image or a camera frame is not drawn by
     * the wave renderer, so none of these three values reaches it. Speed is narrower still - the static
     * mode draws one frame and never advances time.
     */
    private fun renderTuning(settings: AppSettings, mode: String) {
        val branded = mode == AppSettings.LAUNCHER_WALLPAPER_BRANDED ||
            mode == AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES
        binding.groupWallpaperTuning.isVisible = branded
        if (!branded) return
        binding.groupWallpaperSpeed.isVisible = mode == AppSettings.LAUNCHER_WALLPAPER_BRANDED

        val intensity = renderSlider(binding.sliderWallpaperIntensity, settings.launcherWallpaperIntensity)
        binding.tvWallpaperIntensityValue.text =
            host.getString(R.string.launcher_wallpaper_percent_value, intensity)

        renderSlider(binding.sliderWallpaperSpeed, settings.launcherWallpaperAnimationSpeed)
        binding.tvWallpaperSpeedValue.text = host.getString(
            R.string.launcher_wallpaper_speed_value,
            formatMultiplier(settings.launcherWallpaperAnimationSpeed),
        )

        val density = renderSlider(binding.sliderWallpaperDensity, settings.launcherWallpaperParticleDensity)
        binding.tvWallpaperDensityValue.text =
            host.getString(R.string.launcher_wallpaper_percent_value, density)

        val paletteIndex = AppSettings.ANIMATION_PALETTE_OPTIONS.indexOf(settings.launcherAnimationPalette)
        binding.rowWallpaperPalette.setSelection(if (paletteIndex >= 0) paletteIndex else 0)
    }

    /**
     * Read once, on render, rather than subscribed to: the level changes with the charge and the system
     * power saver, neither of which moves while a settings screen is open long enough to matter, and a
     * listener here would have to be unregistered on a path this class does not own.
     */
    private fun renderPowerState() {
        binding.tvWallpaperPowerState.setText(
            when (AnimationPolicy.level) {
                PowerPolicyLevel.NORMAL -> R.string.launcher_wallpaper_power_state_normal
                PowerPolicyLevel.REDUCED -> R.string.launcher_wallpaper_power_state_reduced
                PowerPolicyLevel.SAVING -> R.string.launcher_wallpaper_power_state_saving
            }
        )
    }

    fun release() {
        sliderListeners.forEach { (slider, listener) -> slider.removeOnChangeListener(listener) }
        sliderListeners.clear()
        binding.containerWallpaperModes.removeAllViews()
        modeRows.clear()
    }

    /**
     * S1583: a Material slider rejects any value that is not `valueFrom` plus a whole multiple of
     * `stepSize`, and it validates on the first measure pass rather than on assignment - so a value that
     * misses the track surfaces as an unrelated layout crash long after the write.
     *
     * The stored fractions land off the track in two ordinary ways: the shipped intensity default is
     * 0.56, which is 56% against a 5% track, and a restored backup carries whatever an older build wrote.
     * Fitting the value to the track here is what stops the screen crashing on its very first open.
     *
     * @return the percent actually shown, so the caption beside the slider states the same number.
     */
    private fun renderSlider(slider: Slider, storedFraction: Float): Int {
        val step = slider.stepSize
        val raw = toPercent(storedFraction).toFloat()
        val fitted = if (step <= 0f) {
            raw.coerceIn(slider.valueFrom, slider.valueTo)
        } else {
            val lastStep = ((slider.valueTo - slider.valueFrom) / step).toInt()
            val stepIndex = ((raw - slider.valueFrom) / step).roundToInt().coerceIn(0, lastStep)
            slider.valueFrom + stepIndex * step
        }
        slider.value = fitted
        return fitted.roundToInt()
    }

    private fun formatMultiplier(value: Float): String =
        String.format(Locale.getDefault(), MULTIPLIER_FORMAT, value)

    private companion object {
        const val PERCENT_SCALE = 100f
        const val MULTIPLIER_FORMAT = "%.2f"

        /** The 1..5 range LauncherSettingsStore coerces to; the dropdown shows its index, not the value. */
        val SCREEN_COUNT_ENTRIES = listOf("1", "2", "3", "4", "5")
        const val FIRST_SCREEN_COUNT = 1

        fun toPercent(value: Float): Int = (value * PERCENT_SCALE).roundToInt()

        fun fromPercent(value: Float): Float = value / PERCENT_SCALE
    }
}
