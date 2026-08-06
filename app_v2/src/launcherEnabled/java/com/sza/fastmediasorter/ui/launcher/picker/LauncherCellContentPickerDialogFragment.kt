package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.ContactActionAvailabilityProvider
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerWindow
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: the first step of "put something on the desktop". Level one lists the kinds of thing a cell can
 * hold; choosing "Gadget" re-opens this same dialog on its gadget list - one class, two modes, so the
 * whole flow is FragmentResult-based and survives a config change (a lambda callback would not). Every
 * other kind hands off to the shared panel picker for that kind: the host reads the category out of the
 * result and opens the matching picker.
 *
 * The dialog carries only [row]/[col] and never touches a repository. Terminal placement - and the
 * ADR-10 rememberFileList write for a resource gadget - is the host + ViewModel's job.
 */
@AndroidEntryPoint
class LauncherCellContentPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var gadgetRegistry: LauncherGadgetRegistry

    @Inject
    lateinit var contactActionAvailability: ContactActionAvailabilityProvider

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var row: Int = 0
    private var col: Int = 0
    private var gadgetMode: Boolean = false
    private var actionMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        row = requireArguments().getInt(ARG_ROW)
        col = requireArguments().getInt(ARG_COL)
        gadgetMode = requireArguments().getBoolean(ARG_GADGET_MODE, false)
        actionMode = requireArguments().getBoolean(ARG_ACTION_MODE, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogSearchableOptionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleRes = when {
            gadgetMode -> R.string.launcher_edit_pick_gadget_title
            actionMode -> R.string.launcher_edit_kind_action
            else -> R.string.launcher_edit_add_cell_title
        }
        binding.tvOptionPickerTitle.text = getString(titleRes)
        binding.tvOptionPickerTitle.isVisible = true
        val options = when {
            gadgetMode -> gadgetOptions()
            actionMode -> actionOptions()
            else -> categoryOptions()
        }
        Timber.d("S1413: cell content picker gadgetMode=%s columns=%d", gadgetMode, currentColumnCount())
        SearchableOptionPickerController.attach(
            binding = binding,
            options = options,
            selectedId = null,
            resetRow = null,
            columns = currentColumnCount(),
        ) { picked ->
            picked?.let { onPicked(it.id) }
        }
    }

    /**
     * S1413: the gadget list is a catalogue and reads better as a grid; the category list above it is a
     * short menu of eight kinds, and gridding a menu only makes it harder to scan (strategic §2).
     */
    private fun currentColumnCount(): Int =
        if (gadgetMode || actionMode) SearchableOptionPickerWindow.columnsFor(resources.displayMetrics) else 1

    /**
     * S1413: the launcher host absorbs rotation through android:configChanges, so this dialog is never
     * recreated and the column count derived in [onViewCreated] would survive into a screen of a
     * different width. Only the span count changes - re-attaching would discard the search text.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (_binding == null) return
        SearchableOptionPickerWindow.apply(dialog, binding)
        SearchableOptionPickerController.reflowColumns(binding, currentColumnCount())
    }

    private fun categoryOptions(): List<Option> = listOfNotNull(
        category(CATEGORY_APP, R.string.launcher_edit_kind_app, R.drawable.ic_apps),
        category(CATEGORY_FEATURE, R.string.launcher_edit_kind_feature, R.drawable.ic_launcher_mode),
        category(CATEGORY_RESOURCE, R.string.launcher_edit_kind_resource, R.drawable.ic_folder),
        category(CATEGORY_STREAM, R.string.launcher_edit_kind_stream, R.drawable.ic_cast),
        // S1405: the robot marks the Android side; the gear stays with this app's own settings.
        category(CATEGORY_OS, R.string.launcher_edit_kind_os, R.drawable.ic_android),
        category(CATEGORY_SCHEDULED_OP, R.string.launcher_edit_kind_scheduled_op, R.drawable.ic_schedule),
        contactCategory(
            CATEGORY_CONTACT_PROFILE,
            LauncherContactAction.PROFILE,
            R.string.launcher_contact_action_profile,
            R.drawable.ic_contact,
        ),
        contactCategory(
            CATEGORY_CONTACT_DIAL,
            LauncherContactAction.DIAL,
            R.string.launcher_contact_action_dial,
            R.drawable.ic_call,
        ),
        contactCategory(
            CATEGORY_CONTACT_SMS,
            LauncherContactAction.SMS,
            R.string.launcher_contact_action_sms,
            R.drawable.ic_send_phone_chat,
        ),
        contactCategory(
            CATEGORY_CONTACT_MESSAGE,
            LauncherContactAction.MESSAGE,
            R.string.launcher_contact_action_message,
            R.drawable.ic_send_chat,
        ),
        category(CATEGORY_GADGET, R.string.launcher_edit_kind_gadget, R.drawable.ic_view_grid),
        category(CATEGORY_ACTION, R.string.launcher_edit_kind_action, R.drawable.ic_launcher_mode),
    )

    /**
     * S0428: a contact row is absent, not disabled, when the device cannot perform its action - a photo
     * frame with no radio offers no call and no SMS row at all (strategic §2 goal 4).
     */
    private fun contactCategory(
        id: String,
        action: LauncherContactAction,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int,
    ): Option? = category(id, labelRes, iconRes)
        .takeIf { contactActionAvailability.isAvailable(action) }
        .also { Timber.d("S0428: category %s available=%b", action.name, it != null) }

    private fun gadgetOptions(): List<Option> = gadgetRegistry.available().map { gadget ->
        Option(
            id = GADGET_PREFIX + gadget.key,
            label = getString(gadget.labelRes),
            leading = LeadingVisual.IconRes(gadget.iconRes),
        )
    }

    // S1402: the same four actions the Start menu lists, so a removed one can be put back.
    private fun actionOptions(): List<Option> = LauncherActionCatalog.all.map { action ->
        Option(
            id = ACTION_PREFIX + action.key,
            label = getString(action.labelRes),
            leading = LeadingVisual.IconRes(action.iconRes),
        )
    }

    private fun category(
        id: String,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int,
    ): Option = Option(id = id, label = getString(labelRes), leading = LeadingVisual.IconRes(iconRes))

    private fun onPicked(id: String) {
        val gadgetKey = id.takeIf { it.startsWith(GADGET_PREFIX) }?.removePrefix(GADGET_PREFIX)
        val actionKey = id.takeIf { it.startsWith(ACTION_PREFIX) }?.removePrefix(ACTION_PREFIX)
        val category = when {
            gadgetKey != null -> CATEGORY_GADGET
            actionKey != null -> CATEGORY_ACTION
            else -> id
        }
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_ROW to row,
                RESULT_COL to col,
                RESULT_CATEGORY to category,
                RESULT_GADGET_KEY to gadgetKey,
                RESULT_ACTION_KEY to actionKey,
            ),
        )
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        SearchableOptionPickerWindow.apply(dialog, binding)
        dialog?.let { DialogAccessibilityHelper.applyInitialFocus(it) }
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    companion object {
        const val TAG = "LauncherCellContentPicker"

        // A distinct tag for the gadget-list re-open, so findFragmentByTag does not see the just-dismissed
        // category dialog (its transaction commits asynchronously) and skip showing the second level.
        const val TAG_GADGET = "LauncherCellContentPickerGadget"

        const val RESULT_KEY = "launcher_content_picker_result"
        const val RESULT_ROW = "result_row"
        const val RESULT_COL = "result_col"
        const val RESULT_CATEGORY = "result_category"
        const val RESULT_GADGET_KEY = "result_gadget_key"

        const val CATEGORY_APP = "app"
        const val CATEGORY_FEATURE = "feature"
        const val CATEGORY_RESOURCE = "resource"
        const val CATEGORY_STREAM = "stream"
        const val CATEGORY_OS = "os"
        const val CATEGORY_SCHEDULED_OP = "scheduled_op"

        // S0428: one id per contact action, so the editor's first list needs no action sub-picker.
        const val CATEGORY_CONTACT_PROFILE = "contact_profile"
        const val CATEGORY_CONTACT_DIAL = "contact_dial"
        const val CATEGORY_CONTACT_SMS = "contact_sms"
        const val CATEGORY_CONTACT_MESSAGE = "contact_message"
        const val CATEGORY_GADGET = "gadget"
        const val CATEGORY_ACTION = "action"

        const val RESULT_ACTION_KEY = "result_action_key"

        // S1402: a distinct tag for the action-list re-open, for the same reason TAG_GADGET exists.
        const val TAG_ACTION = "LauncherCellContentPickerAction"

        private const val GADGET_PREFIX = "gadget:"
        private const val ACTION_PREFIX = "action:"
        private const val ARG_ROW = "arg_row"
        private const val ARG_COL = "arg_col"
        private const val ARG_GADGET_MODE = "arg_gadget_mode"
        private const val ARG_ACTION_MODE = "arg_action_mode"

        fun newInstance(row: Int, col: Int): LauncherCellContentPickerDialogFragment =
            LauncherCellContentPickerDialogFragment().apply {
                arguments = bundleOf(ARG_ROW to row, ARG_COL to col)
            }

        fun newGadgetInstance(row: Int, col: Int): LauncherCellContentPickerDialogFragment =
            LauncherCellContentPickerDialogFragment().apply {
                arguments = bundleOf(ARG_ROW to row, ARG_COL to col, ARG_GADGET_MODE to true)
            }

        fun newActionInstance(row: Int, col: Int): LauncherCellContentPickerDialogFragment =
            LauncherCellContentPickerDialogFragment().apply {
                arguments = bundleOf(ARG_ROW to row, ARG_COL to col, ARG_ACTION_MODE to true)
            }
    }
}
