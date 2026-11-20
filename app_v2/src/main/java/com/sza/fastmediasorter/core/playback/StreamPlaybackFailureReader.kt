package com.sza.fastmediasorter.core.playback

import android.os.Bundle
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.SessionCommand
import com.sza.fastmediasorter.core.playback.resilience.StreamAudioFailure
import com.sza.fastmediasorter.core.playback.resilience.StreamFailureClass
import com.sza.fastmediasorter.core.playback.resilience.StreamFailureReason
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Reads the evidence the inline audio classifier needs off a player's own [PlaybackException]
 * (STREAM-BANK 2.10, `06` section 2.5): the HTTP status the server answered with and whether the TLS
 * handshake refused the server's certificate.
 *
 * Only the process that owns the player sees that evidence. A `MediaController` receives a copy whose
 * `cause` was rebuilt from a class name and a message, so on the controller both answers are absent and
 * the service, which read the original, forwards its verdict instead (S4641).
 */
internal object StreamPlaybackFailureReader {

    fun read(error: PlaybackException): StreamAudioFailure {
        val status = httpStatusOf(error)
        val untrusted = isUntrustedCertificate(error)
        return StreamAudioFailure(
            failureClass = StreamFailureClass.classify(error.errorCode, status, untrusted),
            errorCode = error.errorCode,
            httpStatus = status,
        )
    }

    private fun httpStatusOf(error: PlaybackException): Int? {
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return null
        return causes(error).filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
    }

    /**
     * A handshake that merely broke off (a reset mid-handshake) carries no certificate verdict and stays
     * transient; only a rejected certificate or a host name it does not cover is hard. Takes any link of
     * the chain, not only a [PlaybackException], so the loader retry policy (S4759) reads the same
     * evidence before the player error exists.
     */
    internal fun isUntrustedCertificate(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            cause is CertificateException ||
                cause is CertPathValidatorException ||
                cause is SSLPeerUnverifiedException
        }

    private fun causes(error: PlaybackException): Sequence<Throwable> = generateSequence(error.cause) { it.cause }
}

/**
 * The custom session command `AudioPlaybackService` broadcasts when a stream ended in failure, so the
 * screen surfaces the failure at once instead of waiting for its tolerance timer (S4641). The url lets
 * a controller tell its own station from another item the service may be playing.
 */
object StreamFailureSignal {

    const val ACTION = "com.sza.fastmediasorter.action.STREAM_FAILURE"
    private const val KEY_REASON = "reason"
    private const val KEY_URL = "url"

    val command: SessionCommand get() = SessionCommand(ACTION, Bundle.EMPTY)

    fun args(reason: StreamFailureReason, url: String): Bundle = Bundle().apply {
        putString(KEY_REASON, reason.name)
        putString(KEY_URL, url)
    }

    fun reasonOf(args: Bundle): StreamFailureReason? = StreamFailureReason.fromName(args.getString(KEY_REASON))

    fun urlOf(args: Bundle): String? = args.getString(KEY_URL)
}
