package com.sza.fastmediasorter.ui.companionimport.qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityCompanionQrScanBinding
import timber.log.Timber

/**
 * S0988: camera QR scan for the companion `.fmscfg` import. Decodes a QR into the raw payload string
 * and returns it via [EXTRA_PAYLOAD]; the caller (AddResource) feeds it to the existing
 * [com.sza.fastmediasorter.data.companion.CompanionConfigParser] and import use-case, so this screen
 * is transport-neutral (it never inspects the string). CAMERA is already declared app-wide (S0359);
 * this only requests the runtime grant. Injects nothing - it purely returns a string.
 */
class CompanionQrScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompanionQrScanBinding
    private val session by lazy { QrScanSessionManager(this) }
    private var torchOn = false
    private var finishedWithResult = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startScan() else denyAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompanionQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnQrCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        binding.btnQrTorch.setOnClickListener { toggleTorch() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScan() {
        session.bind(binding.qrPreview) { payload -> onPayload(payload) }
    }

    /** Runs on the analyzer thread; hop to main before touching the result/finish. */
    private fun onPayload(payload: String) {
        runOnUiThread {
            if (finishedWithResult) return@runOnUiThread
            finishedWithResult = true
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PAYLOAD, payload))
            finish()
        }
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        session.setTorch(torchOn)
        binding.btnQrTorch.setIconResource(
            if (torchOn) R.drawable.ic_camera_flash_on else R.drawable.ic_camera_flash_off
        )
    }

    private fun denyAndFinish() {
        Toast.makeText(this, R.string.companion_qr_camera_denied, Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        session.unbind()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PAYLOAD = "extra_qr_payload"

        fun createIntent(context: Context): Intent =
            Intent(context, CompanionQrScanActivity::class.java)
    }
}
