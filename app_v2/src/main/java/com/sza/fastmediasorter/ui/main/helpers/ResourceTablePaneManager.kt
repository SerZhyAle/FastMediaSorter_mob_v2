package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.menu.ResourceMenuAction
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.databinding.ViewResourceTableBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.isFileCountUnknown
import com.sza.fastmediasorter.ui.main.MainState
import com.sza.fastmediasorter.ui.main.ResourceAdapter
import com.sza.fastmediasorter.ui.main.table.ResourceTableAdapter
import com.sza.fastmediasorter.ui.main.table.ResourceTableColumn
import com.sza.fastmediasorter.ui.main.table.ResourceTableKeys
import com.sza.fastmediasorter.ui.main.table.ResourceTableRowsBuilder
import com.sza.fastmediasorter.ui.main.table.ResourceTableSort
import com.sza.fastmediasorter.ui.main.table.ResourceTableWidthPolicy
import com.sza.fastmediasorter.ui.main.table.ResourceViewMode
import com.sza.fastmediasorter.ui.main.table.ResourceViewPreference
import timber.log.Timber

/**
 * S4041: owns the wide-window resource table - the pane, its top-bar search and the details panel.
 *
 * It renders [MainState] and reports intent; it decides nothing about resources. Every row action goes
 * through the list adapter's own catalog and routing ([ResourceAdapter.availableActions] /
 * [ResourceAdapter.performAction]), so the table and the list can never disagree about what a resource
 * offers or what an action does. The pane is a ViewStub, so a phone that never shows it pays nothing.
 */
class ResourceTablePaneManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val resourceAdapter: ResourceAdapter,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onSelect(resource: MediaResource)
        fun onQueryChanged(query: String)
        fun onSortPressed(column: ResourceTableColumn)

        /** The pane appeared or disappeared, so the list's visibility and the command bar must follow. */
        fun onShowingChanged()
    }

    private var pane: ViewResourceTableBinding? = null
    private var tableAdapter: ResourceTableAdapter? = null
    private var appliedQuery: String? = null

    /** Rows currently on screen, in display order. */
    private var rows: List<MediaResource> = emptyList()
    private var selected: MediaResource? = null

    var isShowing: Boolean = false
        private set

    init {
        binding.etResourceTableSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            if (query != appliedQuery) {
                appliedQuery = query
                callbacks.onQueryChanged(query)
            }
        }
    }

    /** Measured from the window, not the device: split screen and free-form windows count as narrow. */
    fun isTableEligible(): Boolean {
        val config = activity.resources.configuration
        return ResourceTableWidthPolicy.isTableEligible(config.screenWidthDp, config.fontScale)
    }

    /** The view the toggle button currently stands for; the button shows the mode it switches to next. */
    fun effectiveMode(state: MainState): ResourceViewMode = ResourceViewMode.effective(
        ResourceViewPreference(grid = state.isResourceGridMode, table = state.isResourceTableMode),
        isTableEligible(),
    )

    /**
     * [listHasContent] is false while the list shows its loading, empty or error state; the table then
     * steps aside so that state stays the one thing on screen, exactly as in the list.
     */
    fun render(state: MainState, listHasContent: Boolean) {
        val show = listHasContent && effectiveMode(state) == ResourceViewMode.TABLE
        setShowing(show)
        if (!show) return
        val view = ensurePane()
        syncSearchField(state.tableQuery)
        rows = ResourceTableRowsBuilder.build(state.resources, state.tableQuery, state.tableSort, ::keysOf)
        // A selection the search hides is not acted on from the panel: the user can no longer see it.
        selected = state.selectedResource?.let { current -> rows.firstOrNull { it.id == current.id } }
        tableAdapter?.submitList(rows)
        tableAdapter?.setSelectedResource(selected?.id)
        view.tvResourceTableNoMatch.isVisible = rows.isEmpty()
        view.tvResourceTableCount.text =
            activity.getString(R.string.resource_table_shown_count, rows.size, state.resources.size)
        renderHeaders(view, state.tableSort)
        renderDetails(view, selected)
    }

    /**
     * Arrow keys reach the table through default focus search; Enter on a focused row is handled by the
     * row itself. This only answers whether a key belongs to the table, so the activity keeps its list
     * navigation from acting on the hidden list.
     */
    fun ownsNavigationKey(keyCode: Int): Boolean = isShowing && keyCode in TABLE_NAVIGATION_KEYS

    /** The resource keyboard shortcuts (delete, copy, rename, menu) act on while the table shows. */
    fun currentResource(): MediaResource? = selected?.takeIf { isShowing }

    /**
     * A key-binding command while the table shows: the context menu opens the selected row's menu, and
     * row-to-row moves are left to default focus search. Null means the command is not the table's.
     */
    fun routeCommand(commandId: String): Boolean? = when {
        !isShowing -> null
        commandId == KeyboardNavigationHandler.CONTEXT_MENU_COMMAND_ID -> showSelectedMenu()
        commandId in FOCUS_MOVE_COMMANDS -> false
        else -> null
    }

    /** The list hides while the table covers its area; INVISIBLE keeps the state views anchored to it. */
    fun applyListVisibility(listHasContent: Boolean) {
        binding.rvResources.visibility = when {
            !listHasContent -> View.GONE
            isShowing -> View.INVISIBLE
            else -> View.VISIBLE
        }
        binding.tabResourceTypes.nextFocusDownId =
            if (isShowing) R.id.btnResourceTableSortName else R.id.rvResources
    }

    /** The toggle shows the view it switches to, so a wide window offers the table as the third step. */
    fun toggleIconFor(state: MainState): Int {
        val eligible = isTableEligible()
        val next = ResourceViewMode.next(
            ResourceViewPreference(grid = state.isResourceGridMode, table = state.isResourceTableMode),
            eligible,
        )
        return when (ResourceViewMode.effective(next, eligible)) {
            ResourceViewMode.LIST -> R.drawable.ic_view_list
            ResourceViewMode.GRID -> R.drawable.ic_view_grid
            ResourceViewMode.TABLE -> R.drawable.ic_view_table
        }
    }

    /** Context-menu key: the selected row's menu, anchored on its overflow when the row is on screen. */
    private fun showSelectedMenu(): Boolean {
        val resource = currentResource()
        val view = pane
        if (resource == null || view == null) return false
        val position = rows.indexOfFirst { it.id == resource.id }
        val holder = view.rvResourceTable.findViewHolderForAdapterPosition(position) as? ResourceTableAdapter.RowHolder
        val anchor: View = holder?.menuAnchor ?: view.rvResourceTable
        resourceAdapter.showActionsMenu(anchor, resource)
        return true
    }

    private fun setShowing(show: Boolean) {
        if (show == isShowing) return
        isShowing = show
        Timber.d("S4041: table pane showing=$show")
        if (show) ensurePane()
        pane?.root?.isVisible = show
        binding.layoutResourceTableSearch.isVisible = show
        callbacks.onShowingChanged()
    }

    private fun ensurePane(): ViewResourceTableBinding {
        pane?.let { return it }
        val inflated = ViewResourceTableBinding.bind(binding.stubResourceTable.inflate())
        val adapter = ResourceTableAdapter(
            onSelect = callbacks::onSelect,
            onOpen = { resource -> resourceAdapter.performAction(ResourceMenuAction.OPEN, resource) },
            onShowMenu = { anchor, resource -> resourceAdapter.showActionsMenu(anchor, resource) },
        )
        inflated.rvResourceTable.adapter = adapter
        headerViews(inflated).forEach { (column, header) ->
            ViewCompat.setAccessibilityHeading(header, true)
            header.setOnClickListener {
                Timber.d("S4041: header sort pressed $column")
                callbacks.onSortPressed(column)
            }
        }
        inflated.btnResourceTableOpen.setOnClickListener {
            selected?.let { resourceAdapter.performAction(ResourceMenuAction.OPEN, it) }
        }
        inflated.btnResourceTableEdit.setOnClickListener {
            selected?.let { resourceAdapter.performAction(ResourceMenuAction.EDIT, it) }
        }
        inflated.btnResourceTableRefresh.setOnClickListener {
            selected?.let { resourceAdapter.performAction(ResourceMenuAction.SCAN, it) }
        }
        pane = inflated
        tableAdapter = adapter
        return inflated
    }

    private fun headerViews(view: ViewResourceTableBinding): List<Pair<ResourceTableColumn, TextView>> = listOf(
        ResourceTableColumn.NAME to view.btnResourceTableSortName,
        ResourceTableColumn.SOURCE to view.btnResourceTableSortSource,
        ResourceTableColumn.FILES to view.btnResourceTableSortFiles,
        ResourceTableColumn.CONTENT to view.btnResourceTableSortContent,
        ResourceTableColumn.PATH to view.btnResourceTableSortPath,
    )

    private fun renderHeaders(view: ViewResourceTableBinding, sort: ResourceTableSort?) {
        val tint = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.text_color_primary)
        )
        headerViews(view).forEach { (column, header) ->
            val label = activity.getString(labelRes(column))
            val active = sort?.takeIf { it.column == column }
            val arrow = when {
                active == null -> 0
                active.ascending -> R.drawable.ic_arrow_upward
                else -> R.drawable.ic_arrow_downward
            }
            // Before the label: the header cell is as wide as its column, so an end arrow would sit beside
            // the next column's title and read as its marker.
            header.setCompoundDrawablesRelativeWithIntrinsicBounds(arrow, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(header, tint)
            header.contentDescription = activity.getString(
                when {
                    active == null -> R.string.resource_table_not_sorted
                    active.ascending -> R.string.resource_table_sorted_ascending
                    else -> R.string.resource_table_sorted_descending
                },
                label,
            )
        }
    }

    private fun renderDetails(view: ViewResourceTableBinding, resource: MediaResource?) {
        view.tvResourceTableNoSelection.isVisible = resource == null
        view.layoutResourceTableDetails.isVisible = resource != null
        if (resource == null) return
        view.tvResourceTableDetailsName.text = resource.name
        view.tvResourceTableDetailsSource.text = ResourceAdapter.sourceLabel(activity, resource)
        view.tvResourceTableDetailsPath.text = fullAddress(resource)
        view.tvResourceTableDetailsFiles.text = ResourceAdapter.formatListFileCount(activity, resource)
        view.tvResourceTableDetailsContent.text = contentSummary(resource)

        val offered = if (resource.id == FAVORITES_ENTRY_ID) {
            emptySet()
        } else {
            resourceAdapter.availableActions(activity, resource)
        }
        renderAction(view.btnResourceTableEdit, view.tvResourceTableEditUnavailable, ResourceMenuAction.EDIT in offered)
        renderAction(
            view.btnResourceTableRefresh,
            view.tvResourceTableRefreshUnavailable,
            ResourceMenuAction.SCAN in offered,
        )
    }

    /** An action the catalog hides stays on the panel, disabled, with a sentence saying why. */
    private fun renderAction(button: TextView, note: TextView, available: Boolean) {
        button.isEnabled = available
        note.isVisible = !available
        if (!available) {
            note.text = activity.getString(R.string.resource_table_action_unavailable, button.text)
        }
    }

    /** A cloud row shows its provider; the panel adds the stored address, so nothing technical is lost. */
    private fun fullAddress(resource: MediaResource): String {
        val shown = ResourceAdapter.displayPath(resource)
        return if (shown == resource.path || resource.path.isBlank()) shown else "$shown\n${resource.path}"
    }

    private fun contentSummary(resource: MediaResource): String {
        if (resource.allFiles) return activity.getString(R.string.all_files)
        return resource.supportedMediaTypes.sortedBy { it.ordinal }
            .mapNotNull { mediaTypeLabelRes(it) }
            .joinToString(", ") { activity.getString(it) }
    }

    private fun keysOf(resource: MediaResource): ResourceTableKeys = ResourceTableKeys(
        name = resource.name,
        source = ResourceAdapter.sourceLabel(activity, resource),
        content = contentSummary(resource),
        path = ResourceAdapter.displayPath(resource),
        fileCount = resource.fileCount.takeUnless { resource.isFileCountUnknown || resource.id == FAVORITES_ENTRY_ID },
    )

    /** Writes the state's query into the field only when it differs, so typing never fights the echo. */
    private fun syncSearchField(query: String) {
        if (appliedQuery == query) return
        appliedQuery = query
        if (binding.etResourceTableSearch.text?.toString() != query) {
            binding.etResourceTableSearch.setText(query)
        }
    }

    private fun labelRes(column: ResourceTableColumn): Int = when (column) {
        ResourceTableColumn.NAME -> R.string.resource_table_column_name
        ResourceTableColumn.SOURCE -> R.string.resource_table_column_source
        ResourceTableColumn.FILES -> R.string.resource_table_column_files
        ResourceTableColumn.CONTENT -> R.string.resource_table_column_content
        ResourceTableColumn.PATH -> R.string.resource_table_column_path
    }

    private companion object {
        // The pseudo-resource the list uses for the Favorites entry; it offers no row actions.
        const val FAVORITES_ENTRY_ID = -100L

        val FOCUS_MOVE_COMMANDS = setOf("navigation.next_file", "navigation.previous_file")

        val TABLE_NAVIGATION_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_PAGE_UP,
            android.view.KeyEvent.KEYCODE_PAGE_DOWN,
            android.view.KeyEvent.KEYCODE_MOVE_HOME,
            android.view.KeyEvent.KEYCODE_MOVE_END,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}
