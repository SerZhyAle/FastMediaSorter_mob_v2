package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.data.local.preferences.CollapsibleSectionStore
import com.sza.fastmediasorter.data.local.preferences.SharedPreferencesCollapsibleSectionStore

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

    /**
     * S4394: every header handed to [register], in registration order - the collapse-all affordance
     * and its visibility need the page's full section set, which the id map alone cannot answer
     * (a section whose container carries no id is still collapsible).
     */
    private val registeredHeaders = linkedSetOf<CollapsibleSectionHeader>()

    /**
     * S4394: invoked with the page's aggregate expansion state after each register-restore and after
     * every header toggle - the host's toolbar affordance tracks this instead of polling.
     */
    var onAnyExpandedChanged: ((Boolean) -> Unit)? = null

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
     * @param persistState S3355: `false` makes the section's state live only as long as the view.
     *   A screen keeps the default and remembers how the user left it; a one-off dialog passes
     *   `false`, because a collapsed state saved there reopens the dialog with nothing in it and
     *   [defaultExpanded] alone cannot undo that - the saved value always wins over it.
     * @param onExpandedChanged optional hook invoked with the current expanded state on initial
     *   restore and on every user toggle, before the body is shown - e.g. to lazily attach a child
     *   fragment when a section first becomes expanded.
     */
    fun register(
        header: CollapsibleSectionHeader,
        container: View,
        key: String,
        defaultExpanded: Boolean = false,
        persistState: Boolean = true,
        onExpandedChanged: ((Boolean) -> Unit)? = null,
    ) {
        if (container.id != View.NO_ID) {
            headersByContainerId[container.id] = header
        }
        registeredHeaders.add(header)
        val expanded = if (persistState) store.isExpanded(key, defaultExpanded) else defaultExpanded
        onExpandedChanged?.invoke(expanded)
        // Restore must not animate (avoids flicker on screen entry); only user toggles animate.
        header.setExpanded(expanded, notify = false)
        container.isVisible = expanded
        // S4394: a restore-only register still moves the aggregate state, so the host hears it
        // without waiting for the first user toggle.
        onAnyExpandedChanged?.invoke(hasExpandedSection())
        header.setOnExpandedChangeListener { isExpanded ->
            onExpandedChanged?.invoke(isExpanded)
            (container.parent as? ViewGroup)?.let { parent ->
                if (AnimationPolicy.isAnimationAllowed) {
                    TransitionManager.beginDelayedTransition(parent, buildBodyTransition())
                }
            }
            container.isVisible = isExpanded
            if (persistState) {
                store.setExpanded(key, isExpanded)
            }
            onAnyExpandedChanged?.invoke(hasExpandedSection())
        }
    }

    /**
     * S4394: collapses every registered section. Each header collapses through its own
     * [CollapsibleSectionHeader.setExpanded], so the per-header listener path animates and
     * persists exactly as a user tap would - this must not bypass it.
     */
    fun collapseAll() {
        for (header in registeredHeaders) {
            header.setExpanded(false)
        }
    }

    /** S4394: whether at least one registered section is expanded right now. */
    fun hasExpandedSection(): Boolean = registeredHeaders.any { it.isExpanded() }

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

    /**
     * Drops every registered header. A host that keeps this manager beyond its view (a Fragment
     * field) calls it from `onDestroyView`, otherwise the old headers - and through their parents the
     * whole old view tree - stay reachable until the next [register] overwrites them.
     */
    fun clear() {
        headersByContainerId.clear()
        registeredHeaders.clear()
    }

    private fun buildBodyTransition(): AutoTransition =
        AutoTransition().apply { duration = BODY_TRANSITION_DURATION_MS }

    companion object {
        private const val BODY_TRANSITION_DURATION_MS = 150L
    }
}
