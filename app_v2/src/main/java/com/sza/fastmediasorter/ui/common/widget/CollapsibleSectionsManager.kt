package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible

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

    /** S1967: container view id -> its header, so a row's ancestors can name the section to expand. */
    private val headersByContainerId = mutableMapOf<Int, CollapsibleSectionHeader>()

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
        if (container.id != View.NO_ID) {
            headersByContainerId[container.id] = header
        }
        val expanded = store.isExpanded(key, defaultExpanded)
        onExpandedChanged?.invoke(expanded)
        // Restore must not animate (avoids flicker on screen entry); only user toggles animate.
        header.setExpanded(expanded, notify = false)
        container.isVisible = expanded
        header.setOnExpandedChangeListener { isExpanded ->
            onExpandedChanged?.invoke(isExpanded)
            (container.parent as? ViewGroup)?.let { parent ->
                TransitionManager.beginDelayedTransition(parent, buildBodyTransition())
            }
            container.isVisible = isExpanded
            store.setExpanded(key, isExpanded)
        }
    }

    /**
     * S1967: expands whichever registered section encloses a row, named by that row's ancestor view
     * ids rather than by a section name.
     *
     * The persistence key cannot serve here: one settings layout carries eight sections under a
     * single indexed name, so the name identifies the screen and not the section. The container's own
     * view id does identify it, and the search index already records every ancestor of every row.
     *
     * @return true when a registered section was found among [ancestorIds] - false means the row sits
     *   in no collapsible section on this screen, which is a normal answer and not a failure.
     */
    fun expandSectionContaining(ancestorIds: List<Int>): Boolean {
        val header = ancestorIds.firstNotNullOfOrNull { headersByContainerId[it] } ?: return false
        // Already-expanded is a no-op inside the header itself, so this neither re-animates nor
        // re-persists a section the user had open.
        header.setExpanded(true)
        return true
    }

    private fun buildBodyTransition(): AutoTransition =
        AutoTransition().apply { duration = BODY_TRANSITION_DURATION_MS }

    companion object {
        private const val BODY_TRANSITION_DURATION_MS = 150L
    }
}
