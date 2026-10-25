package com.sza.fastmediasorter.ui.scheduledops.helpers

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogScheduledOperationBinding
import com.sza.fastmediasorter.domain.model.ScheduledOperation

/** S4248: what the "Show on watch" form holds; the picture is a persisted document URI. */
data class WatchMessageFields(val title: String, val text: String?, val imageUri: String?)

/**
 * S4248: the "Show on watch" block of the scheduled-operation editor - title, optional text and an
 * optional picture chosen through the system document picker.
 *
 * The two text fields are restored by the view hierarchy; the picked picture lives outside the views,
 * so it rides the dialog's own saved state through [saveState] and [restoreState].
 */
class ScheduledOpWatchMessageFormManager(
    private val b: DialogScheduledOperationBinding,
    private val onPickImage: () -> Unit,
) {

    private var imageUri: String? = null
    private var imageName: String? = null

    fun bind(onTitleChanged: () -> Unit) {
        b.btnWatchPickImage.setOnClickListener { onPickImage() }
        b.btnWatchClearImage.setOnClickListener { clearImage() }
        b.etWatchTitle.doAfterTextChanged { onTitleChanged() }
    }

    fun setVisible(visible: Boolean) {
        b.containerWatchMessage.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun fill(op: ScheduledOperation) {
        b.etWatchTitle.setText(op.watchTitle.orEmpty())
        b.etWatchText.setText(op.watchText.orEmpty())
        // The display name was not stored; the document id's last segment is the best label left.
        setImage(op.watchImageUri, op.watchImageUri?.let { nameFromUri(it) })
    }

    fun hasTitle(): Boolean = !b.etWatchTitle.text.isNullOrBlank()

    /** Shows the "enter a title" error while the field is empty; true when the form can be saved. */
    fun validate(): Boolean {
        val ok = hasTitle()
        b.tilWatchTitle.error =
            if (ok) null else b.root.context.getString(R.string.scheduled_ops_error_watch_title_required)
        return ok
    }

    fun clearValidation() {
        b.tilWatchTitle.error = null
    }

    fun read(): WatchMessageFields = WatchMessageFields(
        title = b.etWatchTitle.text?.toString()?.trim().orEmpty(),
        text = b.etWatchText.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        imageUri = imageUri,
    )

    fun setImage(uri: String?, displayName: String?) {
        imageUri = uri
        imageName = displayName.takeIf { uri != null }
        b.tvWatchImageName.text = imageName ?: b.root.context.getString(R.string.scheduled_ops_watch_no_image)
        b.btnWatchClearImage.visibility = if (uri != null) View.VISIBLE else View.GONE
    }

    fun clearImage() = setImage(null, null)

    fun saveState(state: Bundle) {
        state.putString(STATE_IMAGE_URI, imageUri)
        state.putString(STATE_IMAGE_NAME, imageName)
    }

    fun restoreState(state: Bundle) {
        setImage(state.getString(STATE_IMAGE_URI), state.getString(STATE_IMAGE_NAME))
    }

    private fun nameFromUri(uri: String): String =
        Uri.parse(uri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: uri

    private companion object {
        const val STATE_IMAGE_URI = "scheduled_op_watch_image_uri"
        const val STATE_IMAGE_NAME = "scheduled_op_watch_image_name"
    }
}
