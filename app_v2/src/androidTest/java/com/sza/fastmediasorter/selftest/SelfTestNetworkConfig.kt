package com.sza.fastmediasorter.selftest

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import java.io.StringReader
import java.util.Properties

/**
 * S3741: network endpoints for device tests, read from the file `selftest-provision.ps1` pushes to
 * `/data/local/tmp`. The file is read through the shell uid ([android.app.UiAutomation]) because the
 * app's own SELinux domain may not open shell-owned files, and it is never part of the repository.
 *
 * Keys per protocol, `smb` / `sftp` / `ftp`: `<p>.host`, `<p>.port`, `<p>.path`, `<p>.user`,
 * `<p>.password`. An absent `<p>.host` means the protocol is not provisioned and its tests are skipped
 * with that reason - never counted as passed.
 */
object SelfTestNetworkConfig {

    const val DEVICE_FILE = "/data/local/tmp/fms-selftest-network.properties"

    enum class Protocol(val key: String) { SMB("smb"), SFTP("sftp"), FTP("ftp") }

    data class Endpoint(
        val host: String,
        val port: Int?,
        val path: String,
        val user: String,
        val password: String,
    )

    private val properties: Properties by lazy { load() }

    fun endpoint(protocol: Protocol): Endpoint? {
        val p = protocol.key
        val host = properties.getProperty("$p.host")?.takeIf { it.isNotBlank() } ?: return null
        return Endpoint(
            host = host,
            port = properties.getProperty("$p.port")?.toIntOrNull(),
            path = properties.getProperty("$p.path").orEmpty(),
            user = properties.getProperty("$p.user").orEmpty(),
            password = properties.getProperty("$p.password").orEmpty(),
        )
    }

    /** Skips the calling test with a stated reason when [protocol] has no endpoint on this device. */
    fun assumeProtocol(protocol: Protocol): Endpoint {
        val endpoint = endpoint(protocol)
        assumeTrue("network not provisioned: ${protocol.key}", endpoint != null)
        return requireNotNull(endpoint)
    }

    private fun load(): Properties {
        timber.log.Timber.d("S3757: loaded network config from $DEVICE_FILE")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val text = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat $DEVICE_FILE"))
            .use { it.readBytes().decodeToString() }
        return Properties().apply { load(StringReader(text)) }
    }
}
