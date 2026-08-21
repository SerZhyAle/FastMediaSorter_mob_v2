package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.settings.SettingsActivity

/**
 * S0535 / S0780, extracted from `OperationsSettingsFragment` by S1883: the Operations tab's collapsible
 * groups - which ones exist, which are hidden by the build, and how a deep link opens one of them.
 *
 * The extraction is not cosmetic. The tab already delegates five feature groups to their own managers,
 * and adding the Wear OS group pushed the fragment past detekt's class-size ceiling; the section wiring
 * is the part of it that belongs to no single feature, so it is the honest thing to lift out.
 */
class OperationsSectionsManager(
    private val binding: FragmentSettingsDestinationsBinding,
    context: Context,
) {

    private val sectionsManager = CollapsibleSectionsManager(context)

    /**
     * Registers every group, default collapsed. Both build-dependent groups are decided by the caller
     * rather than read here: CLAUDE.md Rule 14 keeps flavor flags out of shared code, and the caller
     * already holds both answers. The Scheduled group is hidden where the flavor disables scheduled
     * operations; the Wear OS group is not registered at all where the build carries no watch bridge,
     * because a hidden card would otherwise keep writing an expansion state for a group the user can
     * never open.
     */
    fun registerAll(wearAvailable: Boolean, scheduledAvailable: Boolean) {
        register(binding.headerSafety, binding.containerSafety, "operations__safety")
        register(binding.headerCopyMove, binding.containerFileOperations, "operations__file_ops")
        register(binding.headerDestinations, binding.containerDestinations, "operations__destinations")
        if (scheduledAvailable) {
            register(binding.headerScheduled, binding.containerScheduled, "operations__scheduled")
        } else {
            binding.headerScheduled.isVisible = false
            binding.containerScheduled.isVisible = false
        }
        register(binding.headerBehaviour, binding.containerBehaviour, "operations__behaviour")
        register(binding.headerCameraPhotos, binding.containerCameraPhotos, "operations__photography")
        register(binding.headerVideoCapture, binding.containerVideoCapture, "operations__video_recording")
        register(binding.headerMicRecording, binding.containerMicRecording, "operations__voice_recorder")
        register(
            binding.headerScreenRecording,
            binding.containerScreenRecording,
            "operations__screen_recording",
        )
        if (wearAvailable) {
            register(binding.headerWear, binding.containerWear, "operations__wear")
        }
        register(
            binding.headerAdditionalPrograms,
            binding.containerAdditionalPrograms,
            "operations__additional_programs",
        )
        register(binding.headerSystemApps, binding.containerSystemApps, "operations__system_apps")
        register(binding.headerScreenGestures, binding.containerScreenGestures, "operations__screen_gestures")
    }

    /**
     * S0780: deep link from the programs-panel "Configure" item and, since S1883, from the Wear
     * companion's own settings route. The extra is consumed so it does not re-fire on rotation.
     */
    fun handleIntentSection(intent: Intent) {
        val sectionId = intent.getStringExtra(SettingsActivity.EXTRA_EXPAND_SECTION) ?: return
        if (!isHandled(sectionId)) {
            return
        }
        intent.removeExtra(SettingsActivity.EXTRA_EXPAND_SECTION)
        ensureExpanded(sectionId)
    }

    /** Expands the named group and scrolls its header into view. */
    fun ensureExpanded(sectionId: String) {
        val header = headerFor(sectionId) ?: return
        if (!header.isVisible) {
            return
        }
        header.setExpanded(true, notify = true)
        header.post {
            header.requestRectangleOnScreen(Rect(0, 0, header.width, header.height), false)
        }
    }

    private fun isHandled(sectionId: String): Boolean = headerFor(sectionId) != null

    private fun headerFor(sectionId: String): CollapsibleSectionHeader? = when (sectionId) {
        SettingsActivity.SECTION_ADDITIONAL_PROGRAMS -> binding.headerAdditionalPrograms
        SettingsActivity.SECTION_WEAR -> binding.headerWear
        else -> null
    }

    private fun register(header: CollapsibleSectionHeader, container: View, key: String) {
        sectionsManager.register(header, container, key, defaultExpanded = false)
    }
}
