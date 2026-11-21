package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.R.attr.materialButtonOutlinedStyle
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerChoice
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass

@StringRes
internal fun RemoteRiskClass.labelRes(): Int = when (this) {
    RemoteRiskClass.SAFE -> R.string.remote_control_class_safe
    RemoteRiskClass.READS_USER_DATA -> R.string.remote_control_class_reads_user_data
    RemoteRiskClass.WRITES_USER_DATA -> R.string.remote_control_class_writes_user_data
    RemoteRiskClass.LEAVES_MACHINE -> R.string.remote_control_class_leaves_machine
}

@StringRes
internal fun RemoteConsentMode.labelRes(): Int = when (this) {
    RemoteConsentMode.ASK -> R.string.remote_control_mode_ask
    RemoteConsentMode.ALWAYS -> R.string.remote_control_mode_always
}

/** What one row of the controllers dialog asks for; the dialog manager turns each into a view-model call. */
internal interface RemoteControllerRowListener {
    fun onGranted(deviceId: String, granted: Boolean)

    fun onClassAllowed(deviceId: String, riskClass: RemoteRiskClass, allowed: Boolean)

    fun onModeTapped(deviceId: String, riskClass: RemoteRiskClass)
}

/**
 * One account device in the controllers dialog: a switch for the device, and under it - while granted - one switch
 * per risk class with a button showing, and changing, "Ask me first" or "Always allow". Built in code so the
 * dialog can update rows in place: a rebuild after every tap would drop the focus of a keyboard or TalkBack user.
 * Views update through [bind]; the owner ignores the listener calls that a bind itself causes.
 */
internal class RemoteControllerRowView(
    private val context: Context,
    private val deviceId: String,
    listener: RemoteControllerRowListener,
) {
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val grantSwitch = MaterialSwitch(context)
    private val goneText = TextView(context)
    private val classContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val classRows = RemoteRiskClass.entries.associateWith { riskClass ->
        ClassRowView(context, riskClass).also { row ->
            row.onAllowed = { allowed -> listener.onClassAllowed(deviceId, riskClass, allowed) }
            row.onModeTapped = { listener.onModeTapped(deviceId, riskClass) }
        }
    }

    init {
        val gap = context.resources.getDimensionPixelSize(R.dimen.margin_large)
        root.setPadding(0, gap, 0, 0)
        grantSwitch.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP)
        grantSwitch.setOnCheckedChangeListener { _, granted -> listener.onGranted(deviceId, granted) }
        goneText.setText(R.string.remote_control_controller_gone)
        classContainer.setPadding(gap, 0, 0, 0)
        classRows.values.forEach { classContainer.addView(it.root) }
        root.addView(grantSwitch)
        root.addView(goneText)
        root.addView(classContainer)
    }

    fun bind(choice: RemoteControllerChoice) {
        grantSwitch.text = choice.deviceName
        grantSwitch.contentDescription =
            context.getString(R.string.remote_control_controller_toggle_description, choice.deviceName)
        grantSwitch.isChecked = choice.grant != null
        goneText.isVisible = !choice.onAccount
        classContainer.isVisible = choice.grant != null
        classRows.forEach { (riskClass, row) -> row.bind(choice.grant?.modeFor(riskClass)) }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

private class ClassRowView(private val context: Context, private val riskClass: RemoteRiskClass) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    var onAllowed: (Boolean) -> Unit = {}
    var onModeTapped: () -> Unit = {}

    private val allowSwitch = MaterialSwitch(context)
    private val modeButton = MaterialButton(context, null, materialButtonOutlinedStyle)

    init {
        allowSwitch.setText(riskClass.labelRes())
        allowSwitch.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        allowSwitch.setOnCheckedChangeListener { _, allowed -> onAllowed(allowed) }
        modeButton.setOnClickListener { onModeTapped() }
        root.addView(allowSwitch)
        root.addView(modeButton)
    }

    fun bind(mode: RemoteConsentMode?) {
        allowSwitch.isChecked = mode != null
        modeButton.isVisible = mode != null
        if (mode == null) return
        modeButton.setText(mode.labelRes())
        modeButton.contentDescription = context.getString(
            R.string.remote_control_mode_button_description,
            context.getString(riskClass.labelRes()),
            context.getString(mode.labelRes()),
        )
    }
}
