package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.core.compat.ChromeOsCompat
import com.sza.fastmediasorter.ui.common.showSoftInputImplicitly

/**
 * S2601: the default user and password fields - their tap-to-focus bridge and the commit-on-leave
 * writes.
 *
 * Its own class rather than another `setupXxx` in [GeneralSettingsViewSetupHelper], which stood over
 * detekt's LargeClass threshold. The two `lastCommitted*` fields hold the value last written and are
 * read by nothing else, so the state moves with its only readers instead of being left behind.
 */
class GeneralSettingsDefaultCredentialsSetupHelper(
    private val hostContext: GeneralSettingsHostContext,
) {
    private val binding get() = hostContext.binding
    private val viewModel get() = hostContext.viewModel
    private val fragment get() = hostContext.fragment

    private var lastCommittedDefaultUser: String = ""
    private var lastCommittedDefaultPassword: String = ""

    fun setup() {
        val currentSettings = viewModel.settings.value
        lastCommittedDefaultUser = currentSettings.defaultUser
        lastCommittedDefaultPassword = currentSettings.defaultPassword

        // Clear any programmatic filters - these fields accept any character including Cyrillic.
        binding.etDefaultUser.filters = arrayOf()
        binding.etDefaultPassword.filters = arrayOf()
        binding.etDefaultUser.isFocusableInTouchMode = true
        binding.etDefaultPassword.isFocusableInTouchMode = true

        binding.etDefaultUser.setText(lastCommittedDefaultUser)
        binding.etDefaultUser.imeOptions = EditorInfo.IME_ACTION_NEXT
        // No setOnClickListener on til/et - overriding performClick() breaks Chrome OS IME
        // connection: ARC establishes keyboard routing inside the system click handler, and a
        // custom listener replaces it.  TextInputLayout already forwards container clicks to the
        // inner EditText automatically, so no click listeners are needed here.
        installTapFocusBridge(binding.tilDefaultUser, binding.etDefaultUser)
        binding.etDefaultUser.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == binding.etDefaultUser.imeOptions) {
                commitDefaultUserIfChanged()
                binding.etDefaultPassword.requestFocus()
                true
            } else {
                false
            }
        }
        binding.etDefaultUser.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitDefaultUserIfChanged()
        }

        binding.etDefaultPassword.setText(lastCommittedDefaultPassword)
        binding.etDefaultPassword.imeOptions = EditorInfo.IME_ACTION_DONE
        installTapFocusBridge(binding.tilDefaultPassword, binding.etDefaultPassword)
        binding.etDefaultPassword.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == binding.etDefaultPassword.imeOptions) {
                commitDefaultPasswordIfChanged()
                view.clearFocus()
                val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                true
            } else {
                false
            }
        }
        binding.etDefaultPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitDefaultPasswordIfChanged()
        }
    }

    private fun installTapFocusBridge(container: View, editor: TextInputEditText) {
        val listener = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP && !editor.hasFocus()) {
                focusEditorFromTap(editor)
            }
            false
        }
        container.setOnTouchListener(listener)
        editor.setOnTouchListener(listener)
    }

    private fun focusEditorFromTap(editor: TextInputEditText) {
        editor.requestFocusFromTouch()
        editor.requestFocus()
        editor.setSelection(editor.text?.length ?: 0)

        // Non-Chrome OS devices in this screen can miss the editor-focus hand-off after a box tap.
        // Keep ARC on the native click path and only add explicit IME assist for other devices.
        if (ChromeOsCompat.isChromeOs(fragment.requireContext())) return

        editor.post {
            if (!editor.isAttachedToWindow) return@post
            val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInputImplicitly(editor)
        }
    }

    private fun commitDefaultUserIfChanged() {
        val newUser = binding.etDefaultUser.text.toString()
        if (lastCommittedDefaultUser == newUser) return

        lastCommittedDefaultUser = newUser
        val current = viewModel.settings.value
        // S1666: the secret trigger that imported the bundled credential file is gone with the file. It
        // guarded the action while the data shipped in every APK regardless - resources now come in only
        // through the user's own file, which needs no hidden entry point.
        if (current.defaultUser != newUser) {
            viewModel.updateSettings(current.copy(defaultUser = newUser))
        }
    }

    private fun commitDefaultPasswordIfChanged() {
        val newPassword = binding.etDefaultPassword.text.toString()
        if (lastCommittedDefaultPassword == newPassword) return

        lastCommittedDefaultPassword = newPassword
        val current = viewModel.settings.value
        if (current.defaultPassword != newPassword) {
            viewModel.updateSettings(current.copy(defaultPassword = newPassword))
        }
    }
}
