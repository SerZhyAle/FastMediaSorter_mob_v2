package com.sza.fastmediasorter.ui.launcher.menu

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.util.showBoundToHost

/**
 * S4530: "Set category.." - a multi-choice list of every category, empty ones included, plus
 * "New category..", which names a category and returns to the list with it ticked.
 *
 * Not injected: the host owns an instance and closes it on its view teardown, so no dialog outlives
 * the screen that opened it. Ticks made before "New category.." survive the round trip.
 */
class LauncherAppCategoryDialogManager(
    private val context: Context,
    private val labels: AppCategoryLabelResolver,
    private val onSave: (packageName: String, categoryIds: Set<String>) -> Unit,
    private val onCreate: (name: String, onCreated: (String) -> Unit) -> Unit,
) {

    private var dialog: AlertDialog? = null

    fun show(packageName: String, appLabel: String, catalog: AppCategoryCatalog) {
        showChooser(packageName, appLabel, catalog, catalog.categoryIdsOf(packageName))
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun showChooser(
        packageName: String,
        appLabel: String,
        catalog: AppCategoryCatalog,
        checkedIds: Set<String>,
    ) {
        dismiss()
        val categories = catalog.orderedCategories(labels::labelOf)
        val checked = categories.map { it.id in checkedIds }.toBooleanArray()
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.launcher_app_category_dialog_title, appLabel))
            .setMultiChoiceItems(categories.map(labels::labelOf).toTypedArray(), checked) { _, index, isChecked ->
                checked[index] = isChecked
            }
            .setPositiveButton(R.string.save) { _, _ ->
                onSave(packageName, categories.filterIndexed { index, _ -> checked[index] }.mapTo(HashSet()) { it.id })
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.launcher_app_category_new) { _, _ ->
                val kept = categories.filterIndexed { index, _ -> checked[index] }.mapTo(HashSet()) { it.id }
                showNameInput(packageName, appLabel, catalog, kept)
            }
            .showBoundToHost(context)
    }

    private fun showNameInput(
        packageName: String,
        appLabel: String,
        catalog: AppCategoryCatalog,
        checkedIds: Set<String>,
    ) {
        dismiss()
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = true
        }
        val field = TextInputLayout(context).apply {
            hint = context.getString(R.string.launcher_app_category_new_hint)
            addView(input)
        }
        val padding = context.resources.getDimensionPixelSize(R.dimen.margin_normal)
        val container = FrameLayout(context).apply {
            setPadding(padding, padding, padding, 0)
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(field, params)
        }
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.launcher_app_category_new)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text?.toString().orEmpty()
                onCreate(name) { createdId ->
                    showChooser(packageName, appLabel, catalog.including(createdId, name), checkedIds + createdId)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> showChooser(packageName, appLabel, catalog, checkedIds) }
            .showBoundToHost(context)
        input.requestFocus()
    }

    /**
     * The snapshot this dialog holds predates the new category, and the store's next emission may not
     * have reached the screen yet, so the created one is added here to be listed and ticked at once.
     */
    private fun AppCategoryCatalog.including(createdId: String, name: String): AppCategoryCatalog =
        if (categories.any { it.id == createdId }) {
            this
        } else {
            copy(categories = categories + AppCategory(id = createdId, name = name.trim(), systemCategory = null))
        }
}
