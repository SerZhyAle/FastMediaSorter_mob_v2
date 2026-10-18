package com.sza.fastmediasorter.ui.settings.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangeOutwardAccessBinding
import com.sza.fastmediasorter.domain.model.ExchangeGrant
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageExchangeGrantsUseCase
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * "Access from outside" for one shared folder (contract DEVICE-EXCHANGE items BD and BE, owner decision В12): the
 * grants of that folder, each withdrawn alone, plus a link with a password and a guest invitation. No address or
 * port is shown (owner decision В5): the link code carries them, and the person only passes it on.
 */
class ExchangeOutwardAccessDialogManager(
    private val fragment: Fragment,
    private val grants: ManageExchangeGrantsUseCase,
) {

    private val context: Context get() = fragment.requireContext()

    fun show(rootUri: String, rootName: String) {
        val view = DialogExchangeOutwardAccessBinding.inflate(LayoutInflater.from(context))
        var watch: Job? = null
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.exchange_outward_title, rootName))
            .setView(view.root)
            .setNegativeButton(R.string.close, null)
            .setOnDismissListener {
                watch?.cancel()
                // The password must not outlive the dialog that showed it.
                view.textOutwardLinkResult.text = null
            }
            .showBoundTo(fragment) ?: return
        // S4348: a per-dialog guard - the container rebuilds only when the pair really changed.
        var rendered: Pair<List<ExchangeGrant>, Set<String>>? = null
        watch = fragment.viewLifecycleOwner.collectOnLifecycle(
            combine(grants.grants(rootUri), grants.confirmed, ::Pair)
        ) { (list, confirmed) ->
            val seen = list to confirmed
            if (seen == rendered) return@collectOnLifecycle
            rendered = seen
            renderGrants(view, list, confirmed)
        }
        view.btnOutwardCreateLink.setOnClickListener { createLink(view, rootUri, rootName) }
        view.btnOutwardInviteGuest.setOnClickListener { inviteGuest(view, rootUri, rootName) }
        view.btnExchangePortalOutward.setOnClickListener {
            MediaExchangePortalLinks.open(fragment.requireActivity(), MediaExchangePortalLinks.Page.EXCHANGE_SERVER)
        }
        dialog.setCanceledOnTouchOutside(false)
        Timber.d("S4348: outward access dialog shown")
    }

    private fun renderGrants(
        view: DialogExchangeOutwardAccessBinding,
        list: List<ExchangeGrant>,
        confirmed: Set<String>,
    ) {
        Timber.d("S4348: outward grants render")
        val container = view.containerOutwardGrants
        container.removeAllViews()
        view.textOutwardEmpty.isVisible = list.isEmpty()
        list.forEach { grant -> container.addView(grantRow(grant, grant.grantId in confirmed)) }
    }

    private fun grantRow(grant: ExchangeGrant, confirmed: Boolean): LinearLayout {
        val title = when (grant.kind) {
            ExchangeGrant.Kind.LINK -> context.getString(R.string.exchange_outward_item_link)
            ExchangeGrant.Kind.GUEST -> context.getString(R.string.exchange_outward_item_guest, grant.guest.orEmpty())
        }
        val label = TextView(context).apply {
            text = if (confirmed) title else context.getString(R.string.exchange_outward_unconfirmed, title)
        }
        val withdraw = MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setText(R.string.exchange_outward_withdraw)
            contentDescription = context.getString(R.string.exchange_outward_withdraw_description, title)
            minHeight = context.resources.getDimensionPixelSize(R.dimen.touch_target_min_height)
            setOnClickListener { fragment.viewLifecycleOwner.lifecycleScope.launch { grants.revoke(grant.grantId) } }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(withdraw)
        }
    }

    private fun createLink(view: DialogExchangeOutwardAccessBinding, rootUri: String, rootName: String) {
        val typed = view.etOutwardLinkPassword.text?.toString()?.takeIf(String::isNotBlank)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val created = grants.createLink(rootUri, rootName, typed)
            if (created == null) {
                Toast.makeText(context, R.string.exchange_outward_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            view.etOutwardLinkPassword.text = null
            view.textOutwardLinkResult.text =
                created.code + "\n\n" + context.getString(R.string.exchange_outward_link_created, created.password)
            view.textOutwardLinkResult.isVisible = true
            view.btnOutwardCopyLink.isVisible = true
            view.btnOutwardCopyLink.setOnClickListener { copy(created.code) }
        }
    }

    private fun inviteGuest(view: DialogExchangeOutwardAccessBinding, rootUri: String, rootName: String) {
        val login = view.etOutwardGuestLogin.text?.toString()?.trim().orEmpty()
        if (login.isEmpty()) {
            view.etOutwardGuestLogin.requestFocus()
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            if (grants.grantGuest(rootUri, rootName, login) == null) {
                Toast.makeText(context, R.string.exchange_outward_failed, Toast.LENGTH_LONG).show()
            } else {
                view.etOutwardGuestLogin.text = null
            }
        }
    }

    private fun copy(code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.exchange_outward_create_link), code))
        Toast.makeText(context, R.string.exchange_outward_link_copied, Toast.LENGTH_SHORT).show()
    }
}
