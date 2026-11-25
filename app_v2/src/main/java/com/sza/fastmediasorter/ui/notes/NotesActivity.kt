package com.sza.fastmediasorter.ui.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityNotesBinding
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.notes.helpers.NotesRowActionManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S4568: host of the Notes sub-program - every note of the notes folder, a search field and a create
 * action. Notes open in the player's text-edit mode, the same editor Browse uses.
 */
@AndroidEntryPoint
class NotesActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var sendToMenuManager: SendToMenuManager

    private val viewModel: NotesViewModel by viewModels()
    private lateinit var binding: ActivityNotesBinding
    private lateinit var adapter: NotesListAdapter

    /** S4865: enabled only while notes are selected, so back leaves the selection mode before the screen. */
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.clearSelection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ColorThemePrefs.applyThemeOverlay(this)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetPadding()

        val rowActions = NotesRowActionManager(
            activity = this,
            sendToMenu = sendToMenuManager,
            settings = viewModel::currentSettings,
            onDeleteConfirmed = viewModel::delete,
            onSelect = viewModel::toggleSelection,
            onDeleteSelectedConfirmed = viewModel::deleteSelected,
        )
        adapter = NotesListAdapter(
            onOpen = viewModel::open,
            onMore = rowActions::show,
            onToggle = viewModel::toggleSelection,
        )
        binding.notesRecyclerView.adapter = adapter
        // The arrow and the system back share one path, so the selection mode is left before the screen is.
        binding.notesToolbar.setUpNavigation(this)
        onBackPressedDispatcher.addCallback(this, selectionBackCallback)
        binding.notesToolbar.inflateMenu(R.menu.menu_notes)
        binding.notesToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_notes_sync_now -> viewModel.syncNow()
                R.id.action_notes_select_all -> viewModel.selectAll()
                R.id.action_notes_delete_selected ->
                    rowActions.confirmDeleteSelected(viewModel.state.value.selected.size)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        binding.notesNewButton.setOnClickListener { viewModel.createNote() }
        binding.notesSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.onQueryChanged(newText.orEmpty())
                return true
            }
        })

        collectOnLifecycle(viewModel.state) { state ->
            adapter.tooLarge = state.tooLarge
            adapter.selected = state.selected
            adapter.submitList(state.notes)
            val empty = !state.loading && state.notes.isEmpty()
            binding.notesEmptyText.visibility = if (empty) View.VISIBLE else View.GONE
            renderSelection(state.selected.size)
            renderSync(state.sync, selecting = state.selected.isNotEmpty())
        }
        collectOnLifecycle(viewModel.events) { event -> handle(event) }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("S4721: focus=${currentFocus?.javaClass?.simpleName} touch=${window.decorView.isInTouchMode}")
        // Returning from the editor is the moment a note was created or changed, so the list is re-read.
        viewModel.start(getString(R.string.notes_title))
    }

    /** S4865: the title bar, the New note button and the back gesture follow the selection mode. */
    private fun renderSelection(count: Int) {
        val selecting = count > 0
        selectionBackCallback.isEnabled = selecting
        val toolbar = binding.notesToolbar
        toolbar.title = if (selecting) {
            getString(R.string.notes_selected_count, count)
        } else {
            getString(R.string.notes_title)
        }
        toolbar.setNavigationIcon(if (selecting) R.drawable.ic_clear else R.drawable.ic_arrow_back)
        toolbar.navigationContentDescription = if (selecting) getString(R.string.notes_selection_cancel) else null
        toolbar.menu.findItem(R.id.action_notes_select_all)?.isVisible = selecting
        toolbar.menu.findItem(R.id.action_notes_delete_selected)?.isVisible = selecting
        binding.notesNewButton.visibility = if (selecting) View.GONE else View.VISIBLE
    }

    private fun renderSync(sync: NotesSyncStatus, selecting: Boolean) {
        binding.notesToolbar.menu.findItem(R.id.action_notes_sync_now)?.isVisible =
            sync != NotesSyncStatus.Off && !selecting
        binding.notesToolbar.subtitle = when (sync) {
            NotesSyncStatus.Off -> null
            NotesSyncStatus.Syncing -> getString(R.string.notes_sync_status_syncing)
            is NotesSyncStatus.Synced -> getString(R.string.notes_sync_status_synced, sync.host)
            NotesSyncStatus.Failed -> getString(R.string.notes_sync_status_failed)
            NotesSyncStatus.ServerNoE2e -> getString(R.string.exchange_failure_server_no_e2e)
            NotesSyncStatus.LimitReached -> getString(R.string.notes_sync_limit_reached)
        }
    }

    private fun handle(event: NotesEvent) {
        when (event) {
            is NotesEvent.OpenEditor -> startActivity(
                PlayerActivity.createPanelIntent(this, event.resourceId, initialFilePath = event.path)
                    .putExtra(PlayerActivity.EXTRA_TEXT_EDIT_MODE_ON_OPEN, true),
            )
            is NotesEvent.ShowError -> {
                val message = when (event.kind) {
                    NotesError.FOLDER_UNAVAILABLE -> R.string.notes_error_folder
                    NotesError.DELETE_FAILED -> R.string.notes_error_delete
                    NotesError.DELETE_SELECTED_FAILED -> R.string.notes_error_delete_selected
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            is NotesEvent.ShowSyncMessage -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F1 &&
            HelpOpenManager.openIfPresent(this, NotesActivity::class.java.simpleName, null)
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, NotesActivity::class.java)
    }
}
