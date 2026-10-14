package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.exchange.ExchangeCastCoordinator
import com.sza.fastmediasorter.domain.model.ExchangeCastResult
import com.sza.fastmediasorter.domain.model.ExchangeCastRules
import com.sza.fastmediasorter.domain.model.ExchangeCastTarget
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S4116 phase 07, strategic 6.4: the broadcast panel's "Cast to my devices" - a multi-select list of the receivers
 * that can play this broadcast, with presence in words, one `cast` per chosen receiver, and every `cast-result`
 * explained (item R). Shown while a live broadcast has an outgoing exchange entry to travel on.
 */
class BroadcastCastManager @Inject constructor(
    private val coordinator: ExchangeCastCoordinator,
    private val controller: BroadcastSourceController,
) {

    private var available = false
    private var live = false
    private var listing = false
    private var names: Map<String, String> = emptyMap()

    fun setup(activity: AppCompatActivity, button: MaterialButton) {
        button.setOnClickListener { openPicker(activity, button) }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    coordinator.castAvailable.collect {
                        available = it
                        render(button)
                    }
                }
                launch {
                    controller.state.collect {
                        live = it is BroadcastState.Live
                        render(button)
                    }
                }
                launch { coordinator.results.collect { showResult(activity, it) } }
            }
        }
    }

    private fun render(button: MaterialButton) {
        button.visibility = if (available && live) View.VISIBLE else View.GONE
        button.isEnabled = !listing
    }

    private fun openPicker(activity: AppCompatActivity, button: MaterialButton) {
        if (listing) return
        listing = true
        render(button)
        activity.lifecycleScope.launch {
            val targets = try {
                coordinator.castTargets()
            } finally {
                listing = false
                render(button)
            }
            when {
                targets == null -> toast(activity, R.string.exchange_cast_list_failed)
                targets.isEmpty() -> toast(activity, R.string.exchange_cast_no_receivers)
                else -> showChoice(activity, targets)
            }
        }
    }

    private fun showChoice(activity: AppCompatActivity, targets: List<ExchangeCastTarget>) {
        names = targets.associate { it.deviceId to it.name }
        val labels = targets.map { target ->
            val presence = activity.getString(
                if (target.online) R.string.exchange_devices_device_online else R.string.exchange_devices_device_offline
            )
            activity.getString(R.string.exchange_devices_device_details, target.name, presence)
        }.toTypedArray()
        val checked = BooleanArray(targets.size)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_cast_pick_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.exchange_cast_send) { _, _ ->
                val chosen = targets.filterIndexed { index, _ -> checked[index] }.map { it.deviceId }
                if (chosen.isEmpty()) {
                    toast(activity, R.string.exchange_cast_nothing_chosen)
                } else {
                    activity.lifecycleScope.launch { onSent(activity, coordinator.cast(chosen), chosen.size) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private fun onSent(activity: AppCompatActivity, sent: Int, chosen: Int) {
        if (sent < chosen) {
            toast(activity, R.string.exchange_cast_send_failed)
        } else {
            Toast.makeText(activity, activity.getString(R.string.exchange_cast_sent, sent), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResult(activity: AppCompatActivity, result: ExchangeCastResult) {
        val name = names[result.receiverDeviceId] ?: activity.getString(R.string.exchange_cast_unknown_device)
        val text = if (result.accepted) {
            activity.getString(R.string.exchange_cast_result_accepted, name)
        } else {
            activity.getString(reasonText(result.reason), name)
        }
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    // An unknown reason reads as the generic refusal (7.10).
    @StringRes
    private fun reasonText(reason: String?): Int = when (reason) {
        ExchangeCastRules.REASON_UNAVAILABLE -> R.string.exchange_cast_result_unavailable
        ExchangeCastRules.REASON_DECLINED -> R.string.exchange_cast_result_declined
        ExchangeCastRules.REASON_TIMEOUT -> R.string.exchange_cast_result_timeout
        ExchangeCastRules.REASON_UNSUPPORTED -> R.string.exchange_cast_result_unsupported
        else -> R.string.exchange_cast_result_refused
    }

    private fun toast(activity: AppCompatActivity, @StringRes text: Int) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }
}
