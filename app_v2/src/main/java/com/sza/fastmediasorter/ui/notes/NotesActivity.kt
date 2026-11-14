package com.sza.fastmediasorter.ui.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
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
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * S4568: host of the Notes sub-program - every note of the notes folder, a search field and a create
 * action. Notes open in the player's text-edit mode, the same editor Browse uses.
 */
@AndroidEntryPoint
class NotesActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val viewModel: NotesViewModel by viewModels()
    private lateinit var binding: ActivityNotesBinding
    private lateinit var adapter: NotesListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Timber.d("S4568: Notes screen opened")
        ColorThemePrefs.applyThemeOverlay(this)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetPadding()

        val rowActions = NotesRowActionManager(this, onDeleteConfirmed = viewModel::delete)
        adapter = NotesListAdapter(onOpen = viewModel::open, onMore = rowActions::show)
        binding.notesRecyclerView.adapter = adapter
        binding.notesToolbar.setNavigationOnClickListener { finish() }
        binding.notesNewButton.setOnClickListener { viewModel.createNote() }
        binding.notesSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.onQueryChanged(newText.orEmpty())
                return true
            }
        })

        collectOnLifecycle(viewModel.state) { state ->
            adapter.submitList(state.notes)
            val empty = !state.loading && state.notes.isEmpty()
            binding.notesEmptyText.visibility = if (empty) View.VISIBLE else View.GONE
        }
        collectOnLifecycle(viewModel.events) { event -> handle(event) }
    }

    override fun onResume() {
        super.onResume()
        // Returning from the editor is the moment a note was created or changed, so the list is re-read.
        viewModel.start(getString(R.string.notes_title))
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
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
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
