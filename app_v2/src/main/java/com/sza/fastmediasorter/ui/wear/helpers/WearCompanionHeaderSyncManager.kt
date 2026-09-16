package com.sza.fastmediasorter.ui.wear.helpers

import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject

/** S3185: a window whose toolbar carries the companion's settings sync button and its caption. */
interface WearCompanionHeaderHost {
    val headerSyncButton: Button
    val headerSyncCaption: TextView
}

/**
 * S3185: the settings sync button and its last-synced caption in the companion window's toolbar.
 *
 * The press only asks the view model for a push; the Compose island owns the edited settings and
 * answers it, so this class never builds a payload of its own.
 *
 * S2093: the caption reads the stored sync time, not the press - a press that reached nothing leaves
 * the previous time standing instead of reading as a successful sync. The unit system is observed
 * rather than read once, so flipping the setting behind the window rewrites the clock format.
 */
class WearCompanionHeaderSyncManager @Inject constructor(
    private val quantityFormatter: QuantityFormatter,
    private val unitSystemProvider: UnitSystemProvider
) {

    fun bind(owner: LifecycleOwner, viewModel: WearSyncViewModel, button: Button, caption: TextView) {
        button.setOnClickListener {
            Timber.d("S3185: header sync button pressed")
            viewModel.requestSettingsPush()
        }
        owner.collectOnLifecycle(viewModel.uiState) { state ->
            button.isEnabled = state !is WearSyncUiState.Sending
        }
        val captionText = combine(viewModel.lastSyncedAt, unitSystemProvider.current) { syncedAt, system ->
            if (syncedAt > 0L) {
                caption.context.getString(
                    R.string.wear_settings_last_synced,
                    quantityFormatter.format(Quantity.DateTime(syncedAt), system)
                )
            } else {
                caption.context.getString(R.string.wear_settings_sync_never)
            }
        }
        owner.collectOnLifecycle(captionText) { text -> caption.text = text }
    }
}
