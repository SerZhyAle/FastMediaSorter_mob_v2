package com.sza.fastmediasorter.ui.remotecontrol.helpers

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.externalControlLabel
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import com.sza.fastmediasorter.ui.browse.AdapterFileInfoFormatter
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.settings.helpers.RemoteCommandLogText

internal interface RemoteControllerChoiceListener {
    fun grant(deviceId: String, enabled: Boolean)
    fun choose(deviceId: String, risk: RemoteRiskClass, mode: RemoteConsentMode?)
}

internal class RemoteControllerRowView(
    private val context: Context,
    private val deviceId: String,
    commands: List<RemoteVerbSpec>,
    listener: RemoteControllerChoiceListener,
) {
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val header = CollapsibleSectionHeader(context)
    private val grantSwitch = MaterialCheckBox(context)
    private val classes = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val error = TextView(context).apply { setText(R.string.external_control_save_failed) }
    private var foldedBeforeBind = false
    private var initialized = false
    private val rows = RemoteRiskClass.entries.mapNotNull { risk ->
        val verbs = commands.filter { it.riskClass == risk }
        if (verbs.isEmpty()) {
            null
        } else {
            risk to ExternalControlClassRow(context, risk, verbs) { mode ->
                listener.choose(deviceId, risk, mode)
            }
        }
    }.toMap()

    init {
        root.addView(header)
        root.addView(grantSwitch)
        root.addView(classes)
        root.addView(error)
        grantSwitch.setText(R.string.external_control_allow_device)
        grantSwitch.setOnClickListener {
            val requested = grantSwitch.isChecked
            grantSwitch.isChecked = !requested
            if (requested) header.setExpanded(true)
            listener.grant(deviceId, requested)
        }
        rows.values.forEach { classes.addView(it.root) }
        header.setOnExpandedChangeListener {
            foldedBeforeBind = !it
            classes.isVisible = it && grantSwitch.isChecked
        }
    }

    fun bind(device: SftpRendezvousDevice, grant: RemoteControllerGrant?, failed: Boolean) {
        val presence = if (device.isOnline) {
            context.getString(R.string.external_control_online)
        } else {
            context.getString(
                R.string.external_control_offline_since,
                AdapterFileInfoFormatter.formatTimestamp(context, device.updatedAtMs),
            )
        }
        header.setTitle(
            context.getString(R.string.external_control_device_label, device.deviceName, program(device), presence)
        )
        val allowLabel = context.getString(R.string.external_control_allow_device)
        grantSwitch.contentDescription = "$allowLabel, ${device.deviceName}"
        grantSwitch.isChecked = grant != null
        if (!initialized) {
            foldedBeforeBind = grant == null
            header.setExpanded(!foldedBeforeBind, notify = false)
            initialized = true
        }
        classes.isVisible = grant != null && !foldedBeforeBind
        rows.forEach { (risk, row) -> row.bind(grant?.modeFor(risk)) }
        error.isVisible = failed
    }

    private fun program(device: SftpRendezvousDevice): String {
        val name = when (device.product) {
            "fms-android", "fms-wear" -> R.string.external_control_program_fms_android
            "fms-windows" -> R.string.external_control_program_fms_windows
            "fms-web" -> R.string.external_control_program_fms_web
            "streamsplayer-android", "streamsplayer-wear" -> R.string.external_control_program_streamsplayer_android
            "streamsplayer-windows" -> R.string.external_control_program_streamsplayer_windows
            "cyrflip-windows" -> R.string.external_control_program_cyrflip_windows
            else -> R.string.external_control_program_other
        }
        return context.getString(name)
    }
}

private class ExternalControlClassRow(
    context: Context,
    risk: RemoteRiskClass,
    verbs: List<RemoteVerbSpec>,
    onChoice: (RemoteConsentMode?) -> Unit,
) {
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val choices = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
    private val ids = mutableMapOf<RemoteConsentMode?, Int>()
    private var selected: RemoteConsentMode? = null

    init {
        val classLabel = context.getString(risk.externalControlLabel())
        root.addView(
            TextView(context).apply {
                text = classLabel
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            }
        )
        root.addView(
            TextView(context).apply {
                text = verbs.joinToString(", ") { RemoteCommandLogText.summary(context, it.name, it.summary) }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )
        val labels = linkedMapOf(
            null to R.string.external_control_no_access,
            RemoteConsentMode.ASK to R.string.external_control_ask,
            RemoteConsentMode.ALWAYS to R.string.external_control_always,
        )
        val buttons = labels.map { (mode, label) ->
            val button = MaterialRadioButton(context).apply {
                id = View.generateViewId()
                setText(label)
                contentDescription = "$classLabel, $text"
                setOnClickListener {
                    bind(selected)
                    if (mode != selected) onChoice(mode)
                }
            }
            choices.addView(button)
            ids[mode] = button.id
            button
        }
        buttons.zipWithNext().forEach { (previous, next) ->
            previous.nextFocusDownId = next.id
            next.nextFocusUpId = previous.id
        }
        root.addView(choices)
    }

    fun bind(mode: RemoteConsentMode?) {
        selected = mode
        choices.check(ids.getValue(mode))
    }
}
