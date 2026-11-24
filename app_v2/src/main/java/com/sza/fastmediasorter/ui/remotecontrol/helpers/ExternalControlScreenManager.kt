package com.sza.fastmediasorter.ui.remotecontrol.helpers

import android.content.DialogInterface
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnDetach
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.RemoteCommandConsentPrompt
import com.sza.fastmediasorter.core.notification.externalControlLabel
import com.sza.fastmediasorter.databinding.ActivityExternalControlBinding
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.exchange.ExchangeSignInActivity
import com.sza.fastmediasorter.ui.remotecontrol.ExternalControlActivity
import com.sza.fastmediasorter.ui.remotecontrol.ExternalControlState
import com.sza.fastmediasorter.ui.remotecontrol.ExternalControlViewModel
import com.sza.fastmediasorter.ui.settings.helpers.RemoteCommandLogText
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal class ExternalControlScreenManager(
    private val activity: ExternalControlActivity,
    private val binding: ActivityExternalControlBinding,
    private val viewModel: ExternalControlViewModel,
) : RemoteControllerChoiceListener {
    private val sections = binding.externalControlSections
    private val deviceRows = LinkedHashMap<String, RemoteControllerRowView>()
    private var fullLog = false
    private var entries = emptyList<RemoteCommandLogEntry>()
    private var lastHelpSection = SURFACE
    private val logBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            setFullLog(false)
            sections.btnExternalControlFullLog.requestFocus()
        }
    }

    fun bind(openControllers: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.toolbar.setUpNavigation(activity)
        activity.onBackPressedDispatcher.addCallback(activity, logBack)
        bindSections()
        bindActions()
        bindHelpFocus()
        renderCommands()
        observe()
        if (openControllers) {
            binding.externalControlScroll.post { revealControllers() }
        }
    }

    private fun bindSections() {
        section(sections.headerExternalControlThis, sections.externalControlThisBody, expanded = true)
        section(sections.headerExternalControlWaiting, sections.externalControlWaitingBody, expanded = true)
        section(sections.headerExternalControlControllers, sections.externalControlControllersBody, expanded = true)
        section(sections.headerExternalControlCommands, sections.externalControlCommandRows, expanded = false)
        section(sections.headerExternalControlLog, sections.externalControlLogBody, expanded = true)
    }

    private fun section(header: CollapsibleSectionHeader, body: View, expanded: Boolean) {
        header.setExpanded(expanded, notify = false)
        body.isVisible = expanded
        header.setOnExpandedChangeListener { body.isVisible = it }
    }

    private fun bindActions() {
        sections.rowExternalControlListening.setOnCheckedChangeListener(viewModel::setListening)
        sections.btnExternalControlDecline.setOnClickListener { answer(accepted = false) }
        sections.btnExternalControlAllow.setOnClickListener { answer(accepted = true) }
        sections.btnExternalControlFullLog.setOnClickListener { setFullLog(true) }
    }

    private fun bindHelpFocus() {
        val groups = mapOf(
            sections.externalControlThis to "$SURFACE.t1",
            sections.externalControlWaiting to "$SURFACE.t2",
            sections.externalControlControllers to "$SURFACE.t3",
            sections.externalControlCommands to "$SURFACE.t4",
            sections.externalControlLog to "$SURFACE.t5",
        )
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            groups.entries.firstOrNull { (group, _) -> containsFocus(group, focused) }
                ?.let { lastHelpSection = it.value }
        }
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        binding.root.doOnDetach { it.viewTreeObserver.removeOnGlobalFocusChangeListener(listener) }
    }

    private fun containsFocus(group: View, focus: View?): Boolean =
        generateSequence(focus) { it.parent as? View }.any { it === group }

    fun focusedHelpSurface(): String = lastHelpSection

    fun handleKey(event: KeyEvent): Boolean {
        val key = event.keyCode
        val inPrompt = viewModel.request.value != null &&
            containsFocus(sections.externalControlWaiting, activity.currentFocus)
        return when {
            inPrompt && (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_ESCAPE) -> {
                if (event.action == KeyEvent.ACTION_UP) answer(accepted = false)
                true
            }
            key == KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_UP) activity.onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> false
        }
    }

    private fun observe() {
        activity.collectOnLifecycle(viewModel.state, block = ::renderState)
        activity.collectOnLifecycle(viewModel.log) {
            entries = it
            renderLog()
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.request.collectLatest(::renderPrompt)
            }
        }
    }

    private fun renderState(state: ExternalControlState) {
        sections.rowExternalControlListening.setCheckedSilently(state.listening)
        sections.textExternalControlWriteError.isVisible = ExternalControlViewModel.LISTENING in state.failedRows
        renderConnection(state)
        renderControllers(state)
    }

    private fun renderConnection(state: ExternalControlState) {
        val directory = state.directory
        val action = sections.btnExternalControlStateAction
        val needsExchange = directory.checked && !directory.supported
        action.isVisible = state.listening && (needsExchange || directory.signInAgain != null)
        val label = when {
            !state.listening -> R.string.external_control_state_off
            !directory.checked && !directory.reachable -> R.string.remote_control_controllers_loading
            !directory.supported -> R.string.external_control_state_no_server
            directory.signInAgain != null -> R.string.external_control_state_sign_in
            !directory.reachable -> R.string.external_control_state_offline
            else -> R.string.external_control_state_ready
        }
        sections.textExternalControlState.setText(label)
        val signIn = directory.signInAgain
        if (signIn == null) {
            action.setText(R.string.external_control_open_exchange)
            action.setOnClickListener { activity.finish() }
        } else {
            action.setText(R.string.external_control_sign_in)
            action.setOnClickListener { activity.startActivity(ExchangeSignInActivity.createIntent(activity, signIn)) }
        }
    }

    private fun renderControllers(state: ExternalControlState) {
        val directory = state.directory
        val devices = directory.devices.takeIf { state.listening }.orEmpty()
        val label = when {
            !state.listening -> R.string.external_control_t3_off
            devices.isNotEmpty() -> null
            !directory.checked -> R.string.remote_control_controllers_loading
            directory.supported && !directory.reachable -> R.string.remote_control_controllers_failed
            else -> R.string.external_control_t3_empty
        }
        sections.textExternalControlControllers.isVisible = label != null
        label?.let(sections.textExternalControlControllers::setText)

        val ids = devices.mapTo(HashSet()) { it.deviceId }
        val container = sections.externalControlDeviceRows
        deviceRows.entries.removeAll { (id, row) ->
            (id !in ids).also { removed -> if (removed) container.removeView(row.root) }
        }
        val grants = state.grants.associateBy { it.deviceId }
        devices.forEach { device ->
            val id = device.deviceId
            val row = deviceRows.getOrPut(id) {
                RemoteControllerRowView(activity, id, viewModel.commands, this).also { container.addView(it.root) }
            }
            row.bind(device, grants[id], id in state.failedRows)
        }
    }

    private fun revealControllers() {
        val header = sections.headerExternalControlControllers
        val bounds = Rect()
        header.getDrawingRect(bounds)
        binding.externalControlScroll.offsetDescendantRectToMyCoords(header, bounds)
        binding.externalControlScroll.scrollTo(0, bounds.top)
        header.requestFocus()
    }

    override fun grant(deviceId: String, enabled: Boolean) = viewModel.setGranted(deviceId, enabled)

    override fun choose(deviceId: String, risk: RemoteRiskClass, mode: RemoteConsentMode?) {
        if (mode == RemoteConsentMode.ALWAYS && risk != RemoteRiskClass.SAFE) {
            val name = viewModel.state.value.directory.devices.firstOrNull { it.deviceId == deviceId }?.deviceName
                ?: return
            confirmAlways(name) { viewModel.setClass(deviceId, risk, mode) }
        } else {
            viewModel.setClass(deviceId, risk, mode)
        }
    }

    private fun confirmAlways(name: String, allow: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.external_control_always_title)
            .setMessage(activity.getString(R.string.external_control_always_body, name))
            .setNegativeButton(R.string.external_control_cancel, null)
            .setPositiveButton(R.string.external_control_always) { _, _ -> allow() }
            .showBoundTo(activity)
            ?.getButton(DialogInterface.BUTTON_NEGATIVE)
            ?.requestFocus()
    }

    private suspend fun renderPrompt(prompt: RemoteCommandConsentPrompt?) {
        sections.externalControlWaiting.isVisible = prompt != null
        if (prompt == null) return
        sections.headerExternalControlWaiting.setExpanded(true)
        sections.textExternalControlPrompt.text = prompt.title
        sections.textExternalControlDescription.text = prompt.description
        sections.textExternalControlKind.text = prompt.kind
        if (!binding.root.isInTouchMode) sections.btnExternalControlDecline.requestFocus()
        var remaining = prompt.remainingMillis()
        while (remaining > 0) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining + SECOND_MS - 1)
            sections.textExternalControlCountdown.text =
                activity.getString(R.string.external_control_prompt_countdown, seconds)
            delay((remaining - (seconds - 1) * SECOND_MS).coerceAtLeast(1L))
            remaining = prompt.remainingMillis()
        }
    }

    private fun answer(accepted: Boolean) {
        viewModel.request.value?.let { viewModel.answer(it.key, accepted) }
    }

    private fun renderCommands() {
        val target = sections.externalControlCommandRows
        viewModel.commands.forEach { spec ->
            val summary = RemoteCommandLogText.summary(activity, spec.name, spec.summary)
            val className = activity.getString(spec.riskClass.externalControlLabel())
            val line = activity.getString(R.string.external_control_command_class, summary, className)
            val description = if (spec.confirm == RemoteConfirm.DEVICE) {
                "$line\n${activity.getString(R.string.external_control_asks_here)}"
            } else {
                line
            }
            target.addLine(description)
        }
    }

    private fun setFullLog(full: Boolean) {
        Timber.d("S4817: external control full log open=%b", full)
        fullLog = full
        logBack.isEnabled = full
        sections.btnExternalControlFullLog.isVisible = !full
        renderLog()
    }

    private fun renderLog() {
        val container = sections.externalControlLogRows
        container.removeAllViews()
        val visible = if (fullLog) entries else entries.takeLast(RECENT_LIMIT)
        if (visible.isEmpty()) {
            container.addLine(activity.getString(R.string.external_control_t5_empty))
        } else {
            visible.asReversed().forEach { container.addLine(RemoteCommandLogText.line(activity, it)) }
        }
    }

    private fun LinearLayout.addLine(label: String) {
        addView(
            TextView(context).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
        )
    }

    private companion object {
        const val SURFACE = "ExternalControlActivity"
        const val SECOND_MS = 1_000L
        const val RECENT_LIMIT = 20
    }
}
