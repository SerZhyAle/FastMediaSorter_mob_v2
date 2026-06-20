package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import timber.log.Timber

/**
 * Single orchestrator for collapsible groups across the app.
 *
 * Binds a [CollapsibleSectionHeader] to its content container: restores expanded state from the
 * [CollapsibleSectionStore] without animation, animates the body open/close on user toggle, and
 * persists every change. Replaces the per-screen copy-pasted orchestrations.
 */
class CollapsibleSectionsManager(
    private val store: CollapsibleSectionStore,
) {

    constructor(context: Context) : this(SharedPreferencesCollapsibleSectionStore(context)) {
        // Fold the legacy per-screen namespaces into the consolidated store once, before any
        // register() reads state. Guarded + idempotent, so later screens pay only one boolean read.
        CollapsibleSectionStateMigration(context).migrateIfNeeded()
    }

    /**
     * Registers one collapsible section.
     *
     * @param key caller-supplied `<screen>__<section>` identifier - the persistence key.
     * @param defaultExpanded state used when [key] has no saved value.
     * @param onExpandedChanged optional hook invoked with the current expanded state on initial
     *   restore and on every user toggle, before the body is shown - e.g. to lazily attach a child
     *   fragment when a section first becomes expanded.
     */
    fun register(
        header: CollapsibleSectionHeader,
        container: View,
        key: String,
        defaultExpanded: Boolean = false,
        onExpandedChanged: ((Boolean) -> Unit)? = null,
    ) {
        val expanded = store.isExpanded(key, defaultExpanded)
        onExpandedChanged?.invoke(expanded)
        // Restore must not animate (avoids flicker on screen entry); only user toggles animate.
        header.setExpanded(expanded, notify = false)
        container.isVisible = expanded
        header.setOnExpandedChangeListener { isExpanded ->
            Timber.d("S0535: unified section '$key' toggled expanded=$isExpanded")
            onExpandedChanged?.invoke(isExpanded)
            (container.parent as? ViewGroup)?.let { parent ->
                TransitionManager.beginDelayedTransition(parent, buildBodyTransition())
            }
            container.isVisible = isExpanded
            store.setExpanded(key, isExpanded)
        }
    }

    private fun buildBodyTransition(): AutoTransition =
        AutoTransition().apply { duration = BODY_TRANSITION_DURATION_MS }

    companion object {
        private const val BODY_TRANSITION_DURATION_MS = 150L
    }
}
