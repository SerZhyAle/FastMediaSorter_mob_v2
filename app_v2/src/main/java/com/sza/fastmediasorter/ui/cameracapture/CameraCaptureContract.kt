package com.sza.fastmediasorter.ui.cameracapture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode

/**
 * Single owner of every intent extra and result extra exchanged with [CameraCaptureActivity].
 *
 * Keeps the drop-in system-camera contract ([EXTRA_OUTPUT]) intact while adding an explicit capture
 * mode and result media kind so unified photo/video callers stay type-safe. The capture mode is
 * fixed per entry point (S0545 §3.4) for fixed callers; the dedicated "Camera" entry (S0563) may also
 * pass [EXTRA_ALLOW_MODE_SWITCH] to let the user toggle photo/video in-screen, in which case the host
 * owns the output file (dir + basename) and returns the actual path via [EXTRA_RESULT_OUTPUT_PATH].
 */
object CameraCaptureContract {

    /** Public, drop-in compatible output Uri extra (mirrors the system camera MediaStore contract). */
    const val EXTRA_OUTPUT = "output"

    /** Private real file path - lets CameraX write a File target without reverse-parsing a FileProvider Uri. */
    const val EXTRA_OUTPUT_PATH = "output_path"

    /** [CameraCaptureMode] name; absent extra means [CameraCaptureMode.PHOTO] for legacy callers. */
    const val EXTRA_CAPTURE_MODE = "capture_mode"

    /** Initial microphone-enabled state for video mode (default on); ignored in photo mode. */
    const val EXTRA_MICROPHONE_DEFAULT = "microphone_default"

    /** True only for the "Camera" entry (S0563): allow in-screen photo/video switching. Default false. */
    const val EXTRA_ALLOW_MODE_SWITCH = "allow_mode_switch"

    /**
     * S0566: true for the general "Camera" entry / home widget - the host stays open after each capture
     * for a multi-capture session and saves every file itself. Default false keeps fixed callers
     * one-shot-then-finish. Independent of [EXTRA_ALLOW_MODE_SWITCH]: a photo-only general entry still
     * stays open even though no in-screen mode switch is offered.
     */
    const val EXTRA_MULTI_CAPTURE = "multi_capture"

    /** Scratch dir the host writes into when [EXTRA_ALLOW_MODE_SWITCH] is set (mode picks the extension). */
    const val EXTRA_OUTPUT_DIR = "output_dir"

    /** Base file name (no extension) used with [EXTRA_OUTPUT_DIR]; host appends .jpg / .mp4 per mode. */
    const val EXTRA_OUTPUT_BASENAME = "output_basename"

    /** Result extra: [CameraCaptureMode] name of the media that was actually captured. */
    const val EXTRA_RESULT_MEDIA_KIND = "result_media_kind"

    /** Result extra: absolute path of the file the host actually wrote (switchable callers read this). */
    const val EXTRA_RESULT_OUTPUT_PATH = "result_output_path"

    /** Result extra: whether the recorded video carried audio (always false for photo results). */
    const val EXTRA_MICROPHONE_ENABLED = "microphone_enabled"

    fun createIntent(
        context: Context,
        outputUri: Uri,
        outputPath: String? = null,
        mode: CameraCaptureMode = CameraCaptureMode.PHOTO,
        microphoneDefault: Boolean = true,
    ): Intent =
        Intent(context, CameraCaptureActivity::class.java)
            .putExtra(EXTRA_OUTPUT, outputUri)
            .putExtra(EXTRA_CAPTURE_MODE, mode.name)
            .putExtra(EXTRA_MICROPHONE_DEFAULT, microphoneDefault)
            .apply { outputPath?.let { putExtra(EXTRA_OUTPUT_PATH, it) } }

    /**
     * Switchable "Camera" entry (S0563): the host owns the output file. The caller provides a scratch
     * [outputDir] and an extension-less [outputBaseName]; the host appends the extension for the mode
     * actually captured and returns the final path via [EXTRA_RESULT_OUTPUT_PATH]. No [EXTRA_OUTPUT]
     * uri is passed because nothing outside the host needs it before capture.
     */
    fun createSwitchableIntent(
        context: Context,
        outputDir: String,
        outputBaseName: String,
        initialMode: CameraCaptureMode = CameraCaptureMode.PHOTO,
        allowModeSwitch: Boolean = true,
        microphoneDefault: Boolean = true,
        // S0566: the general entry stays open and saves each capture itself (default true here).
        multiCapture: Boolean = true,
    ): Intent =
        Intent(context, CameraCaptureActivity::class.java)
            .putExtra(EXTRA_OUTPUT_DIR, outputDir)
            .putExtra(EXTRA_OUTPUT_BASENAME, outputBaseName)
            .putExtra(EXTRA_CAPTURE_MODE, initialMode.name)
            .putExtra(EXTRA_ALLOW_MODE_SWITCH, allowModeSwitch)
            .putExtra(EXTRA_MICROPHONE_DEFAULT, microphoneDefault)
            .putExtra(EXTRA_MULTI_CAPTURE, multiCapture)

    fun readMode(intent: Intent): CameraCaptureMode =
        CameraCaptureMode.fromName(intent.getStringExtra(EXTRA_CAPTURE_MODE))

    fun readAllowModeSwitch(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_ALLOW_MODE_SWITCH, false)

    fun readMultiCapture(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_MULTI_CAPTURE, false)

    fun readOutputDir(intent: Intent): String? = intent.getStringExtra(EXTRA_OUTPUT_DIR)

    fun readOutputBaseName(intent: Intent): String? = intent.getStringExtra(EXTRA_OUTPUT_BASENAME)

    fun readMicrophoneDefault(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_MICROPHONE_DEFAULT, true)

    fun readOutputPath(intent: Intent): String? = intent.getStringExtra(EXTRA_OUTPUT_PATH)

    @Suppress("DEPRECATION")
    fun readOutputUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_OUTPUT, Uri::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_OUTPUT) as? Uri
        }

    fun packResult(
        mode: CameraCaptureMode,
        microphoneEnabled: Boolean = false,
        outputPath: String? = null,
    ): Intent =
        Intent()
            .putExtra(EXTRA_RESULT_MEDIA_KIND, mode.name)
            .putExtra(EXTRA_MICROPHONE_ENABLED, microphoneEnabled)
            .apply { outputPath?.let { putExtra(EXTRA_RESULT_OUTPUT_PATH, it) } }

    fun readResultMediaKind(intent: Intent?): CameraCaptureMode =
        CameraCaptureMode.fromName(intent?.getStringExtra(EXTRA_RESULT_MEDIA_KIND))

    fun readResultOutputPath(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_RESULT_OUTPUT_PATH)

    fun readResultMicrophoneEnabled(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_MICROPHONE_ENABLED, false) ?: false
}
