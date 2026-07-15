package com.sza.fastmediasorter.ui.companionimport.qr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityCompanionQrShareBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min

/**
 * S1039: renders the compact `FMSCFG1:` payload of an SFTP resource as a QR the recipient scans in
 * AddResource -> Scan QR. The QR may embed the plaintext password, so the window is secured against
 * screenshots / recents thumbnails. S1045: this protection is now gated on the user setting
 * `secureSensitiveScreens` (default ON) - applied secure-first, then relaxed only if the user opted
 * out, so there is never an unprotected first frame. Pure rendering otherwise.
 */
@AndroidEntryPoint
class CompanionQrShareActivity : AppCompatActivity() {

    // S1045: this screen does not extend BaseActivity (pure rendering, no ViewBinding generics), so it
    // reads the setting directly instead of via BaseActivity.isSensitiveScreen().
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var binding: ActivityCompanionQrShareBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // S1045: secure-first - protect the credential QR before any frame renders; the settings
        // collector below clears the flag only when the user has disabled the protection.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityCompanionQrShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            if (settings.secureSensitiveScreens) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        val payload = intent.getStringExtra(EXTRA_PAYLOAD).orEmpty()
        val resourceName = intent.getStringExtra(EXTRA_RESOURCE_NAME).orEmpty()
        val passwordIncluded = intent.getBooleanExtra(EXTRA_PASSWORD_INCLUDED, true)
        Timber.d("S1039: QR display for '$resourceName' passwordIncluded=$passwordIncluded")

        binding.tvQrResourceName.text = resourceName
        binding.tvQrNoPasswordNote.visibility = if (passwordIncluded) View.GONE else View.VISIBLE
        binding.btnQrClose.setOnClickListener { finish() }

        renderQr(payload)
    }

    private fun renderQr(payload: String) {
        val metrics = resources.displayMetrics
        val size = (min(metrics.widthPixels, metrics.heightPixels) * QR_SIZE_FRACTION)
            .toInt()
            .coerceIn(QR_SIZE_MIN_PX, QR_SIZE_MAX_PX)
        try {
            binding.imgQr.setImageBitmap(QrCodeEncoder.encode(payload, size))
        } catch (e: WriterException) {
            failEncode(e)
        } catch (e: IllegalArgumentException) {
            failEncode(e)
        }
    }

    // Expected when a payload is too dense to fit a scannable QR - user-facing recovery, not a crash.
    private fun failEncode(e: Exception) {
        Timber.w(e, "QR encode failed")
        Toast.makeText(this, R.string.companion_qr_share_encode_failed, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_PAYLOAD = "extra_qr_payload"
        const val EXTRA_RESOURCE_NAME = "extra_qr_resource_name"
        const val EXTRA_PASSWORD_INCLUDED = "extra_qr_password_included"

        private const val QR_SIZE_FRACTION = 0.7
        private const val QR_SIZE_MIN_PX = 320
        private const val QR_SIZE_MAX_PX = 720

        fun createIntent(
            context: Context,
            payload: String,
            resourceName: String,
            passwordIncluded: Boolean
        ): Intent = Intent(context, CompanionQrShareActivity::class.java)
            .putExtra(EXTRA_PAYLOAD, payload)
            .putExtra(EXTRA_RESOURCE_NAME, resourceName)
            .putExtra(EXTRA_PASSWORD_INCLUDED, passwordIncluded)
    }
}
