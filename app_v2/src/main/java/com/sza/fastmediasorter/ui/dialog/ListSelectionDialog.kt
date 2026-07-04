package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.databinding.DialogListSelectionBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Generic, reusable list-selection dialog (S0567, ADR-3). Renders a themed list of items
 * through [ListSelectionAdapter] (XML item views + button taxonomy) instead of runtime-built
 * buttons with hardcoded colors.
 *
 * Subclasses or callers supply a [ListSelectionConfig] describing how to load the items, format
 * them, detect the current selection, and receive the result.
 */
open class ListSelectionDialog<T>(
    context: Context,
    private val config: ListSelectionConfig<T>,
) : Dialog(context) {

    private lateinit var binding: DialogListSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogListSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        binding.tvTitle.text = config.title
        binding.btnCancel.setOnClickListener { dismiss() }
        if (config.allowClear && config.hasSelection) {
            binding.btnClear.visibility = View.VISIBLE
            binding.btnClear.setOnClickListener {
                config.onSelected(null)
                dismiss()
            }
        } else {
            binding.btnClear.visibility = View.GONE
        }

        binding.listSelectionRecycler.layoutManager = LinearLayoutManager(context)
        loadItems()
    }

    private fun loadItems() {
        config.lifecycleOwner.lifecycleScope.launch {
            val items = try {
                config.loader()
            } catch (e: Exception) {
                Timber.e(e, "ListSelectionDialog: error loading items")
                Toast.makeText(context, config.errorMessageRes, Toast.LENGTH_SHORT).show()
                dismiss()
                return@launch
            }
            if (items.isEmpty()) {
                Toast.makeText(context, config.emptyMessageRes, Toast.LENGTH_SHORT).show()
                dismiss()
                return@launch
            }
            binding.listSelectionRecycler.adapter = ListSelectionAdapter(
                items = items,
                formatter = config.formatter,
                isSelected = config.isSelected,
                onClick = { item ->
                    config.onSelected(item)
                    dismiss()
                },
            )
        }
    }
}

/**
 * Configuration for a [ListSelectionDialog].
 *
 * @param hasSelection whether a current selection exists (drives Clear button visibility).
 * @param isSelected marks the currently-selected item for the leading check icon.
 */
class ListSelectionConfig<T>(
    val title: CharSequence,
    val lifecycleOwner: LifecycleOwner,
    val loader: suspend () -> List<T>,
    val formatter: ListSelectionAdapter.ItemFormatter<T>,
    val hasSelection: Boolean,
    val isSelected: (T) -> Boolean,
    val allowClear: Boolean,
    val emptyMessageRes: Int,
    val errorMessageRes: Int,
    val onSelected: (T?) -> Unit,
)
