package com.sza.fastmediasorter.ui.broadcast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBroadcastShareBinding
import com.sza.fastmediasorter.ui.broadcast.helpers.BroadcastShareManager
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class BroadcastShareActivity : AppCompatActivity() {

    @Inject
    lateinit var shareManager: BroadcastShareManager

    private lateinit var binding: ActivityBroadcastShareBinding
    private var activeUrl: String? = null
    private var activeTitle: String? = null
    private var activeMode: String = "AUDIO_ONLY"

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            saveDescriptorToUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityBroadcastShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        activeUrl = url
        activeTitle = intent.getStringExtra(EXTRA_TITLE)
        activeMode = intent.getStringExtra(EXTRA_MODE) ?: "AUDIO_ONLY"

        // Set before renderBarcode so the address survives a QR encoding failure, which leaves the
        // image empty and a toast as the only feedback.
        binding.tvUrlValue.text = url
        Timber.d("S2707: broadcast share url row bound, length=${url.length}")

        binding.btnClose.setOnClickListener { finish() }
        binding.btnExportFile.setOnClickListener {
            val fileName = "broadcast_${url.hashCode()}.fmsbcast"
            createDocumentLauncher.launch(fileName)
        }

        renderBarcode(url, activeTitle, activeMode)
    }

    private fun renderBarcode(url: String, title: String?, mode: String) {
        val payload = shareManager.generateQrPayload(url, title, mode)
        val metrics = resources.displayMetrics
        val size = (min(metrics.widthPixels, metrics.heightPixels) * QR_SIZE_FRACTION)
            .toInt()
            .coerceIn(QR_SIZE_MIN_PX, QR_SIZE_MAX_PX)

        try {
            binding.imgQrCode.setImageBitmap(QrCodeEncoder.encode(payload, size))
        } catch (e: WriterException) {
            Timber.w(e, "BroadcastShareActivity: failed to encode QR")
            Toast.makeText(this, R.string.broadcast_share_save_failed, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "BroadcastShareActivity: illegal argument when encoding QR")
            Toast.makeText(this, R.string.broadcast_share_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDescriptorToUri(uri: Uri) {
        val url = activeUrl ?: return
        val jsonPayload = shareManager.generateJsonPayload(url, activeTitle, activeMode)
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, R.string.broadcast_share_saved, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Timber.w(e, "BroadcastShareActivity: failed to export descriptor file")
            Toast.makeText(this, R.string.broadcast_share_save_failed, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Timber.w(e, "BroadcastShareActivity: security exception exporting descriptor file")
            Toast.makeText(this, R.string.broadcast_share_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MODE = "extra_mode"
        private const val QR_SIZE_FRACTION = 0.65
        private const val QR_SIZE_MIN_PX = 300
        private const val QR_SIZE_MAX_PX = 600

        private var lastLaunchedUrl: String? = null

        fun launchIfNew(context: Context, url: String, title: String? = null, mode: String = "AUDIO_ONLY") {
            if (lastLaunchedUrl == url) return
            lastLaunchedUrl = url

            val intent = Intent(context, BroadcastShareActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MODE, mode)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }

        fun resetLaunchTracking() {
            lastLaunchedUrl = null
        }
    }
}
