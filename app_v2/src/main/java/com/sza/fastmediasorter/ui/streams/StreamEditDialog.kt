package com.sza.fastmediasorter.ui.streams

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.DialogAddStreamBinding
import com.sza.fastmediasorter.domain.model.StreamTrackLanguage
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.launch

/**
 * S1500: the one edit-a-channel dialog, raised from wherever a channel can be edited.
 *
 * Extracted from StreamsActivity, where it was a private method reading that Activity's ViewModel,
 * because the launcher desktop edits a channel too and strategic ADR-1 of S1424 forbids a second
 * implementation of the same action. While it lived in the Activity the desktop had exactly one
 * honest option, which was to leave the row out - and it did.
 *
 * The caller supplies [Callbacks] rather than a ViewModel, because the two hosts reach persistence
 * through different objects: the streams screen through StreamsViewModel, the desktop through the
 * use cases its own ViewModel holds. That is the same shape [StreamRemoveConfirmation] uses.
 */
object StreamEditDialog {

    /**
     * What the dialog cannot do for itself. [readTrackPreference] is suspend because the stored
     * preference comes off disk, and the dialog binds its rows twice rather than block on it.
     */
    class Callbacks(
        val readTrackPreference: suspend (String) -> StreamTrackPreferenceUseCase.TrackPreference?,
        val writeTrackPreference: (String, String?, String?, Boolean?) -> Unit,
        val onEdit: (StreamSourceEntity, String, String?, String?) -> Unit,
    )

    /**
     * S1145: which type-picker option to pre-select for [source]. The picker mirrors the stored kind
     * directly - AUDIO -> "AUDIO", VIDEO -> "VIDEO" - so an explicit choice stays visible on reopen
     * even when it coincides with what auto-classification would derive; an earlier
     * classify-comparison collapsed such a choice back to "AUTO". RTSP has no explicit option and
     * maps to "AUTO", so an untouched rtsp channel still re-derives to RTSP on save.
     */
    fun kindOptionOf(source: StreamSourceEntity): String = when (source.mediaKind) {
        "AUDIO" -> "AUDIO"
        "VIDEO" -> "VIDEO"
        else -> "AUTO"
    }

    /**
     * S0660: edit a manual channel in place. Reuses the add-stream layout pre-filled with the current
     * url/title; the update use case preserves pin/sort/origin and only surfaces an invalid-url or
     * duplicate message, which is the host's to show.
     */
    fun show(activity: AppCompatActivity, source: StreamSourceEntity, callbacks: Callbacks) {
        // The dialog outlives the tap that asked for it, and the async read below outlives the dialog,
        // so a host already tearing down must not attach a window. The streams screen never hit this;
        // a desktop cell whose host is finishing does.
        if (activity.isFinishing || activity.isDestroyed) return
        val binding = DialogAddStreamBinding.inflate(activity.layoutInflater)
        binding.tilUrl.hint = activity.getString(R.string.streams_add_url_hint)
        binding.tilTitle.isVisible = true
        binding.etUrl.setText(source.url)
        binding.etTitle.setText(source.title)
        // S1145: the type override is edit-only.
        binding.mediaKindContainer.isVisible = true
        when (kindOptionOf(source)) {
            "AUDIO" -> binding.toggleMediaKind.check(R.id.btnKindAudio)
            "VIDEO" -> binding.toggleMediaKind.check(R.id.btnKindVideo)
            else -> binding.toggleMediaKind.check(R.id.btnKindAuto)
        }
        // S1144: pre-fill the channel's stored track preference; the read is async, so the rows are
        // bound with what is on disk as soon as it arrives rather than blocking the dialog.
        var writeTrackPreference = bindTrackPreference(activity, binding, null, callbacks.writeTrackPreference)
        activity.lifecycleScope.launch {
            val stored = callbacks.readTrackPreference(source.url)
            writeTrackPreference = bindTrackPreference(activity, binding, stored, callbacks.writeTrackPreference)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.streams_edit_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val kindOverride = when (binding.toggleMediaKind.checkedButtonId) {
                    R.id.btnKindAudio -> "AUDIO"
                    R.id.btnKindVideo -> "VIDEO"
                    else -> null
                }
                val editedUrl = binding.etUrl.text?.toString().orEmpty().trim()
                callbacks.onEdit(source, editedUrl, binding.etTitle.text?.toString(), kindOverride)
                // The preference is keyed by URL, so it follows whatever URL the row ends up with.
                if (editedUrl.isNotEmpty()) writeTrackPreference(editedUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.showBoundToHost(activity)
        binding.etUrl.requestFocus()
    }

    /**
     * S1144: fills the three per-channel track rows and returns a writer that persists whatever the
     * user left selected. Index 0 is always "Default" = no per-channel preference, so the channel
     * keeps following the global stream defaults. [existing] is null in add mode, where there is
     * nothing stored yet.
     *
     * Public because the add dialog on the streams screen binds the same three rows; a private copy
     * on each side is how the two would drift apart.
     */
    fun bindTrackPreference(
        activity: AppCompatActivity,
        binding: DialogAddStreamBinding,
        existing: StreamTrackPreferenceUseCase.TrackPreference?,
        write: (String, String?, String?, Boolean?) -> Unit,
    ): (String) -> Unit {
        val languages = listOf(
            activity.getString(R.string.language_default),
            activity.getString(R.string.language_english),
            activity.getString(R.string.language_russian),
            activity.getString(R.string.language_ukrainian),
        )
        val subtitleStates = listOf(
            activity.getString(R.string.language_default),
            activity.getString(R.string.stream_channel_subtitles_on),
            activity.getString(R.string.subtitle_off),
        )
        binding.rowChannelAudioLanguage.setEntries(languages)
        binding.rowChannelSubtitleLanguage.setEntries(languages)
        binding.rowChannelSubtitles.setEntries(subtitleStates)
        binding.rowChannelAudioLanguage.setSelection(
            StreamTrackLanguage.fromIsoCode(existing?.audioLang).ordinal
        )
        binding.rowChannelSubtitleLanguage.setSelection(
            StreamTrackLanguage.fromIsoCode(existing?.subtitleLang).ordinal
        )
        binding.rowChannelSubtitles.setSelection(
            when (existing?.subtitlesEnabled) {
                true -> 1
                false -> 2
                null -> 0
            }
        )

        return { url ->
            val audioIndex = binding.rowChannelAudioLanguage.getSelectedIndex().coerceAtLeast(0)
            val subtitleIndex = binding.rowChannelSubtitleLanguage.getSelectedIndex().coerceAtLeast(0)
            val audioIso = StreamTrackLanguage.entries[audioIndex].isoCodeOrNull()
            val subtitleIso = StreamTrackLanguage.entries[subtitleIndex].isoCodeOrNull()
            val subtitlesEnabled = when (binding.rowChannelSubtitles.getSelectedIndex()) {
                1 -> true
                2 -> false
                else -> null
            }
            write(url, audioIso, subtitleIso, subtitlesEnabled)
        }
    }
}
