package com.sza.fastmediasorter.ui.settings.helpers

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.usecase.launcher.PlaceHomeWidgetOnLauncherDesktopUseCase
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
import com.sza.fastmediasorter.widget.registry.HomeWidgetCatalog
import com.sza.fastmediasorter.widget.registry.HomeWidgetEntry
import com.sza.fastmediasorter.widget.registry.HomeWidgetPinner
import kotlinx.coroutines.launch

/**
 * Settings manager for the "Add widget to home screen" action.
 *
 * Opens an availability-filtered picker of pinnable widgets ([HomeWidgetCatalog.availableEntries])
 * and pins the chosen one through the system flow ([HomeWidgetPinner]), with an explicit
 * unsupported-launcher fallback toast. Binding-agnostic (takes the button view directly) so any
 * settings fragment can host the action; the fragment owns no business logic (Rule 3).
 */
class HomeWidgetSettingsHelper(
    private val button: MaterialButton,
    private val fragment: Fragment,
    private val catalog: HomeWidgetCatalog,
    private val pinner: HomeWidgetPinner,
    // S1170: the second destination. Null keeps every caller that only pins to the Android home screen
    // working unchanged, and keeps this helper usable from a build with no launcher surface at all.
    private val launcherButton: MaterialButton? = null,
    private val launcherModeContract: LauncherModeContract? = null,
    private val launcherRoleManager: LauncherRoleManager? = null,
    private val placeOnLauncherDesktop: PlaceHomeWidgetOnLauncherDesktopUseCase? = null,
) {

    fun setup() {
        button.setOnClickListener { onAddWidgetClicked(Destination.ANDROID_HOME) }
        setupLauncherButton()
    }

    /**
     * The launcher button appears only when the surface exists in this build AND the user actually
     * turned launcher mode on - offering "add to the launcher desktop" on a device whose home screen is
     * someone else's would place a cell the user cannot reach. The gate is the injected contract, never
     * a BuildConfig flavor guard: this file is `src/main` (Rule 14).
     */
    private fun setupLauncherButton() {
        val target = launcherButton ?: return
        if (launcherModeContract?.isAvailableInBuild != true || placeOnLauncherDesktop == null) {
            target.isVisible = false
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val enabled = launcherRoleManager?.isModeEnabled() == true
            if (!fragment.isAdded || fragment.view == null) return@launch
            target.isVisible = enabled
            if (enabled) target.setOnClickListener { onAddWidgetClicked(Destination.LAUNCHER_DESKTOP) }
        }
    }

    private fun onAddWidgetClicked(destination: Destination) {
        val context = fragment.requireContext()
        // The system pin flow is the Android-home path's problem only; the launcher desktop is ours.
        if (destination == Destination.ANDROID_HOME && !pinner.isSupported()) {
            Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val entries = catalog.availableEntries()
            if (!fragment.isAdded || fragment.view == null) return@launch
            // Calculator has no flavor or setting gate, so the list is never empty in practice;
            // the guard is purely defensive against an unexpected empty result.
            if (entries.isEmpty()) return@launch
            showPickerDialog(entries, destination)
        }
    }

    private fun placeOnDesktop(entry: HomeWidgetEntry) {
        val place = placeOnLauncherDesktop ?: return
        val context = fragment.requireContext()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val orientation = if (
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            ) {
                LauncherOrientation.LANDSCAPE
            } else {
                LauncherOrientation.PORTRAIT
            }
            val placed = place(entry, orientation, System.currentTimeMillis())
            if (!fragment.isAdded) return@launch
            // Silence would read as success; a desktop with no free slot must say so.
            val message = if (placed != null) {
                R.string.launcher_widget_placed
            } else {
                R.string.launcher_widget_no_room
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private enum class Destination { ANDROID_HOME, LAUNCHER_DESKTOP }

    private fun showPickerDialog(entries: List<HomeWidgetEntry>, destination: Destination) {
        val context = fragment.requireContext()
        ListSelectionDialog<HomeWidgetEntry>(
            context,
            ListSelectionConfig(
                title = context.getString(R.string.widget_picker_dialog_title),
                lifecycleOwner = fragment.viewLifecycleOwner,
                loader = { entries },
                formatter = object : ListSelectionAdapter.ItemFormatter<HomeWidgetEntry> {
                    override fun getDisplayName(item: HomeWidgetEntry): String =
                        context.getString(item.labelRes)

                    // S1165: show the same glyph the widget carries on the home screen, so the row
                    // is recognised by its picture rather than read.
                    override fun getIcon(item: HomeWidgetEntry): Drawable? =
                        ContextCompat.getDrawable(context, item.iconRes)
                },
                hasSelection = false,
                isSelected = { false },
                allowClear = false,
                emptyMessageRes = R.string.widget_pin_not_supported,
                errorMessageRes = R.string.widget_pin_not_supported,
                // The one list, shared: the owner asked for the same picker, and two copies would drift.
                // Only the outcome branches.
                onSelected = { entry ->
                    entry?.let {
                        when (destination) {
                            Destination.ANDROID_HOME -> {
                                val requested = pinner.requestPin(it.component(context), null)
                                if (!requested) {
                                    Toast.makeText(
                                        context,
                                        R.string.widget_pin_not_supported,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            Destination.LAUNCHER_DESKTOP -> placeOnDesktop(it)
                        }
                    }
                },
            ),
        ).show()
    }
}
