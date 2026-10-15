package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ExchangeCheckStep
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverReasons

/**
 * The words of the shared table of contract DEVICE-EXCHANGE section 25: one string per row of item CZ and one per
 * step of item CY, in the language shown. Every surface of this program that shows a failed exchange connection
 * takes its words from here, so two surfaces showing one failure show the same text.
 */
object ExchangeMessageText {

    private val WHAT: Map<ExchangeMessage, Int> = mapOf(
        ExchangeMessage.BAD_CREDENTIALS to R.string.exchange_failure_bad_credentials,
        ExchangeMessage.BAD_CREDENTIALS_ONE_TIME_CODE to R.string.exchange_failure_bad_credentials_one_time_code,
        ExchangeMessage.DEVICE_REVOKED to R.string.exchange_failure_device_revoked,
        ExchangeMessage.SIGNED_OUT to R.string.exchange_failure_signed_out,
        ExchangeMessage.VERSION_UNSUPPORTED to R.string.exchange_failure_version_unsupported,
        ExchangeMessage.TLS_REQUIRED to R.string.exchange_failure_tls_required,
        ExchangeMessage.ID_COLLISION to R.string.exchange_failure_id_collision,
        ExchangeMessage.CAPACITY to R.string.exchange_failure_capacity,
        ExchangeMessage.PORT_UNAVAILABLE to R.string.exchange_failure_port_unavailable,
        ExchangeMessage.UNAVAILABLE to R.string.exchange_failure_unavailable,
        ExchangeMessage.RATE_LIMITED to R.string.exchange_failure_rate_limited,
        ExchangeMessage.PROTECTION_UNSUPPORTED to R.string.exchange_failure_protection_unsupported,
        ExchangeMessage.INVITATION_INVALID to R.string.exchange_failure_invitation_invalid,
        ExchangeMessage.LOGIN_TAKEN to R.string.exchange_failure_login_taken,
        ExchangeMessage.PASSWORD_POLICY to R.string.exchange_failure_password_policy,
        ExchangeMessage.LIMIT_REACHED to R.string.exchange_failure_limit_reached,
        ExchangeMessage.LIMIT_REACHED_CLIPBOARD to R.string.exchange_failure_limit_reached_clipboard,
        ExchangeMessage.NOT_PERMITTED to R.string.exchange_failure_not_permitted,
        ExchangeMessage.DECLINED to R.string.exchange_failure_declined,
        ExchangeMessage.TIMEOUT to R.string.exchange_failure_timeout,
        ExchangeMessage.UNSUPPORTED to R.string.exchange_failure_unsupported,
        ExchangeMessage.UNREACHABLE_SERVER to R.string.exchange_failure_unreachable_server,
        ExchangeMessage.UNREACHABLE_DEVICE to R.string.exchange_failure_unreachable_device,
        ExchangeMessage.UNANSWERED to R.string.exchange_failure_unanswered,
        ExchangeMessage.TLS_FAILED to R.string.exchange_failure_tls_failed,
        ExchangeMessage.CERTIFICATE_CHANGED to R.string.exchange_failure_certificate_changed,
        ExchangeMessage.EMPTY_CREDENTIAL to R.string.exchange_failure_empty_credential,
        ExchangeMessage.SERVER_OFFLINE to R.string.exchange_failure_server_offline,
        ExchangeMessage.RESOURCE_LOCKED to R.string.exchange_failure_resource_locked,
        ExchangeMessage.CLIPBOARD_EMPTY to R.string.exchange_failure_clipboard_empty,
        ExchangeMessage.E2E_MISSING to R.string.exchange_failure_e2e_missing,
        ExchangeMessage.KEY_CHANGED to R.string.exchange_failure_key_changed,
        ExchangeMessage.SERVER_NO_E2E to R.string.exchange_failure_server_no_e2e,
        ExchangeMessage.SEALED_FAILED to R.string.exchange_failure_sealed_failed,
        ExchangeMessage.REFUSED to R.string.exchange_failure_refused,
        ExchangeMessage.TOKEN_NOT_STORED to R.string.exchange_failure_token_not_stored,
    )

    private val ADVICE: Map<ExchangeCheckStep, Int> = mapOf(
        ExchangeCheckStep.PC_ASLEEP to R.string.exchange_step_pc_asleep,
        ExchangeCheckStep.ADDRESS_LAN to R.string.exchange_step_address_lan,
        ExchangeCheckStep.GUEST_NETWORK to R.string.exchange_step_guest_network,
        ExchangeCheckStep.VPN to R.string.exchange_step_vpn,
        ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK to R.string.exchange_step_windows_public_network,
        ExchangeCheckStep.IP_FAMILY to R.string.exchange_step_ip_family,
        ExchangeCheckStep.SERVER_INTERNET to R.string.exchange_step_server_internet,
        ExchangeCheckStep.PORT_FORWARD to R.string.exchange_step_port_forward,
        ExchangeCheckStep.DEVICE_CLOCK to R.string.exchange_step_device_clock,
        ExchangeCheckStep.CERTIFICATE to R.string.exchange_step_certificate,
        ExchangeCheckStep.CREDENTIALS to R.string.exchange_step_credentials,
        ExchangeCheckStep.SIGN_IN_RATE to R.string.exchange_step_sign_in_rate,
        ExchangeCheckStep.INVITATION to R.string.exchange_step_invitation,
        ExchangeCheckStep.DEVICE_REMOVED to R.string.exchange_step_device_removed,
        ExchangeCheckStep.OPERATOR to R.string.exchange_step_operator,
    )

    /** What happened, in the table's words. */
    @StringRes
    fun what(message: ExchangeMessage): Int = checkNotNull(WHAT[message]) { "no words for $message" }

    /** What to try for one step of the ladder. */
    @StringRes
    fun advice(step: ExchangeCheckStep): Int = checkNotNull(ADVICE[step]) { "no words for $step" }

    /** The row's words and, on the next line, the advice of its own step when it has one. */
    fun twoParts(context: Context, message: ExchangeMessage): String {
        val what = context.getString(what(message))
        val step = message.step ?: return what
        return what + "\n" + context.getString(advice(step))
    }

    /**
     * The row a watch's answer to the hand-over of an entry stands for: its own reasons first, then whatever the
     * server refused with.
     */
    fun ofHandoverReason(reason: String): ExchangeMessage = when (reason) {
        WearExchangeHandoverReasons.UNREACHABLE -> ExchangeMessage.UNREACHABLE_SERVER
        WearExchangeHandoverReasons.UNANSWERED -> ExchangeMessage.UNANSWERED
        WearExchangeHandoverReasons.TLS_FAILED -> ExchangeMessage.TLS_FAILED
        WearExchangeHandoverReasons.CERTIFICATE_CHANGED,
        WearExchangeHandoverReasons.CERTIFICATE_UNTRUSTED,
        -> ExchangeMessage.CERTIFICATE_CHANGED
        else -> ExchangeMessage.ofWire(reason)
    }
}
