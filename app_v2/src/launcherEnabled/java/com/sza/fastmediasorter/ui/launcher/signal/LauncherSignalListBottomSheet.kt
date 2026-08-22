package com.sza.fastmediasorter.ui.launcher.signal

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.databinding.LauncherSignalListItemBinding
import com.sza.fastmediasorter.databinding.LauncherSignalListSheetBinding
import timber.log.Timber

/**
 * S1421 §5.1: every active signal, including the ones the strip could not fit.
 *
 * The owner picked the one overflow rule that loses nothing, and this list is what makes that true - it is
 * the only way to reach a signal the row truncated. It holds no registry and performs no navigation: the
 * strip's owner supplies the list and the tap handler, so there is exactly one navigation path (ADR-2).
 * S1908 adds dismissal the same way - as callbacks supplied by that owner, never as a registry held here.
 *
 * A picker, so it carries no confirm/cancel pair - CLAUDE.md §11 exempts this shape.
 */
class LauncherSignalListBottomSheet : BottomSheetDialogFragment() {

    // Set by the caller before show(); not persisted across process death, as this is a modal dialog that is
    // never restored from the back stack.
    internal var signals: List<LauncherSignal> = emptyList()
    internal var onTap: (LauncherSignal) -> Unit = {}

    /**
     * S1908: whether a row offers dismissal at all. Defaults to "nothing can be dismissed", so a caller that
     * supplies no answer gets the panel exactly as it behaved before this ticket rather than a row of
     * buttons that do nothing.
     */
    internal var canDismiss: (LauncherSignal) -> Boolean = { false }
    internal var onDismiss: (LauncherSignal) -> Unit = {}
    internal var onDismissAll: (List<LauncherSignal>) -> Unit = {}

    private var binding: LauncherSignalListSheetBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LauncherSignalListSheetBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sheet = binding ?: return
        val list = sheet.launcherSignalList
        list.layoutManager = GridLayoutManager(requireContext(), columnCount())
        list.adapter = SignalAdapter()
        Timber.d("S1908: signal panel opened, ${signals.size} signal(s), ${columnCount()} column(s)")
        sheet.launcherSignalDismissAll.setOnClickListener {
            // Exactly the rows that show their own button, so the header's promise is the sum of theirs.
            onDismissAll(signals.filter(canDismiss))
        }
        renderDismissAll()
        // Focus the first row once it exists, so the sheet is usable from a D-pad without a first tap.
        // doOnPreDraw fires after layout, which is when the position-0 holder is there to focus.
        list.doOnPreDraw {
            list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    /**
     * S1908: pushed in by the strip's owner after a dismissal, because the panel stays open while the system
     * removes the notifications. Without it the list keeps showing rows for notifications that are gone and
     * the header keeps offering to clear them.
     */
    @Suppress("NotifyDataSetChanged") // Precedent: PermissionRowAdapter - see the reason below.
    internal fun submit(updated: List<LauncherSignal>) {
        // A whole-list refresh rather than a diff: the panel holds a handful of rows, and the registry
        // already emits through distinctUntilChanged, so this runs when something genuinely changed, which
        // is what strategic §3.2 asks for by "no unnecessary redraws of the notification list".
        signals = updated
        binding?.launcherSignalList?.adapter?.notifyDataSetChanged()
        renderDismissAll()
    }

    /**
     * Strategic §6.3: the header action exists only while there is something it would actually clear - the
     * app's own signals reflect work happening right now and would return the instant they were cleared.
     */
    private fun renderDismissAll() {
        binding?.launcherSignalDismissAll?.isVisible = signals.any(canDismiss)
    }

    /**
     * S1908: 2 columns in portrait, 4 in landscape (strategic goal 2). Read from the configuration in one
     * place rather than from a `layout-land` twin, so the number stays a single expression that §5.3 can
     * later make a function of width without touching the row.
     */
    private fun columnCount(): Int =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LANDSCAPE_COLUMNS
        } else {
            PORTRAIT_COLUMNS
        }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class SignalAdapter : RecyclerView.Adapter<SignalAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: LauncherSignalListItemBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LauncherSignalListItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

        override fun getItemCount(): Int = signals.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val signal = signals[position]
            LauncherSignalIconBinder.bind(holder.itemBinding.launcherSignalItemIcon, signal.icon)
            holder.itemBinding.launcherSignalItemLabel.text = signal.label
            holder.itemBinding.launcherSignalItemDetail.text = signal.detail.orEmpty()
            holder.itemBinding.launcherSignalItemDetail.isVisible = !signal.detail.isNullOrBlank()
            holder.itemBinding.root.contentDescription = signal.label
            holder.itemBinding.root.setOnClickListener {
                onTap(signal)
                dismiss()
            }
            bindDismiss(holder, signal)
        }

        /**
         * The listener is cleared on the hidden branch, not merely left behind an invisible view: a recycled
         * holder carries the previous row's listener, and an invisible button that a D-pad or an
         * accessibility service can still reach would fire it against the wrong application.
         *
         * The tap does not close the sheet. Strategic §5.2 ends the flow at the list redrawing itself, and
         * dismissing the panel would hide the very update that shows the action worked.
         */
        private fun bindDismiss(holder: ViewHolder, signal: LauncherSignal) {
            val button = holder.itemBinding.launcherSignalItemDismiss
            val dismissible = canDismiss(signal)
            button.isVisible = dismissible
            if (dismissible) {
                button.setOnClickListener { onDismiss(signal) }
            } else {
                button.setOnClickListener(null)
                button.isClickable = false
            }
        }
    }

    private companion object {
        const val PORTRAIT_COLUMNS = 2
        const val LANDSCAPE_COLUMNS = 4
    }
}
