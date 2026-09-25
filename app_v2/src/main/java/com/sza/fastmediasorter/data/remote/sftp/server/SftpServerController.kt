package com.sza.fastmediasorter.data.remote.sftp.server

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.network.LanAddressResolver
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerFailure
import com.sza.fastmediasorter.domain.model.SftpServerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import timber.log.Timber
import java.io.IOException
import java.net.BindException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the embedded SFTP server: builds a MINA [SshServer] on start, tears it down on stop, and
 * publishes what it is doing as [state].
 *
 * Nothing heavy is built at injection time - the server object, its thread pools and the listening
 * socket exist only between [start] and [stop], per the rule that protocol clients are never built
 * eagerly in a global scope (`docs/ARCHITECTURE.md`).
 *
 * The listener binds `0.0.0.0`: the same socket answers on Wi-Fi, Ethernet and any VPN or tunnel the
 * user set up, which is how external access works - the server builds no transport of its own
 * (strategic spec section 3). Only the SFTP subsystem is installed, so a client gets no shell.
 */
@Singleton
class SftpServerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SftpServerSettingsStore,
    private val identityStore: SftpServerIdentityStore,
    private val capabilityAvailability: CapabilityAvailability,
    private val mediaCapabilities: MediaCapabilities,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutableState = MutableStateFlow<SftpServerState>(SftpServerState.Stopped)
    val state: StateFlow<SftpServerState> = mutableState.asStateFlow()

    private val mutex = Mutex()
    private var server: SshServer? = null
    private var rootsJob: Job? = null

    @Volatile
    private var roots: List<SftpServerNode> = emptyList()

    fun isAvailable(): Boolean = capabilityAvailability.isSftpServerAvailable(mediaCapabilities)

    /** Starts the server unless it runs already; returns the resulting state. */
    suspend fun start(): SftpServerState = mutex.withLock {
        Timber.d("S3041: server start requested")
        val current = mutableState.value
        if (current is SftpServerState.Running) return@withLock current
        val result = if (isAvailable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mutableState.value = SftpServerState.Starting
            startLocked()
        } else {
            SftpServerState.Failed(SftpServerFailure.UNAVAILABLE)
        }
        mutableState.value = result
        result
    }

    /** Stops the server; safe to call when nothing runs. Completes on the application scope. */
    fun stop() {
        appScope.launch {
            mutex.withLock { stopLocked() }
        }
    }

    /** The LAN addresses a client can use right now; never a loopback address. */
    fun advertisedAddresses(): List<String> = listOfNotNull(LanAddressResolver(context).resolve())

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun startLocked(): SftpServerState {
        val config = settingsStore.snapshot()
        roots = withContext(ioDispatcher) { resolveRoots(config.rootUris) }
        val credentialsReady = when (config.authMode) {
            SftpServerAuthMode.PASSWORD -> identityStore.clientCredentials().password != null
            SftpServerAuthMode.PUBLIC_KEY -> parseAuthorizedKeys(config.authorizedKeys).isNotEmpty()
        }
        return when {
            roots.isEmpty() -> SftpServerState.Failed(SftpServerFailure.NO_SHARED_FOLDERS)
            !credentialsReady -> SftpServerState.Failed(SftpServerFailure.NO_CREDENTIAL)
            else -> launchServer(settingsStore.snapshot(), identityStore.hostKeyPair())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun launchServer(config: SftpServerConfig, hostKey: KeyPair): SftpServerState =
        withContext(ioDispatcher) {
            try {
                val instance = buildServer(config, hostKey)
                instance.start()
                server = instance
                watchRoots()
                Timber.i("SftpServerController: listening on port %d", instance.port)
                SftpServerState.Running(instance.port, advertisedAddresses())
            } catch (e: BindException) {
                Timber.w(e, "SftpServerController: port %d is taken", config.port)
                SftpServerState.Failed(SftpServerFailure.PORT_IN_USE)
            } catch (e: IOException) {
                Timber.e(e, "SftpServerController: server failed to start")
                SftpServerState.Failed(SftpServerFailure.START_FAILED)
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildServer(config: SftpServerConfig, hostKey: KeyPair): SshServer {
        OsUtils.setAndroid(true)
        // Android has no "user.home": without a resolver ServerBuilder's static init throws while locating the
        // default authorized_keys file, and the class stays unusable for the rest of the process.
        PathUtils.setUserHomeFolderResolver { context.filesDir.toPath() }
        return SshServer.setUpDefaultServer().apply {
            host = BIND_ALL_INTERFACES
            port = config.port
            keyPairProvider = KeyPairProvider.wrap(hostKey)
            fileSystemFactory = SftpSafFileSystemFactory { roots }
            subsystemFactories = listOf(
                SftpSubsystemFactory.Builder().withFileSystemAccessor(SftpSafFileSystemAccessor).build(),
            )
            when (config.authMode) {
                SftpServerAuthMode.PASSWORD -> {
                    passwordAuthenticator = passwordAuthenticator(config)
                    publickeyAuthenticator = null
                }
                SftpServerAuthMode.PUBLIC_KEY -> {
                    passwordAuthenticator = null
                    keyboardInteractiveAuthenticator = null
                    publickeyAuthenticator = publicKeyAuthenticator(config)
                }
            }
        }
    }

    private fun passwordAuthenticator(config: SftpServerConfig): PasswordAuthenticator {
        val expected = config.password.orEmpty().toByteArray(Charsets.UTF_8)
        return PasswordAuthenticator { user, password, _ ->
            // Constant-time: the comparison must not tell a LAN attacker how many characters matched.
            user == config.username && expected.isNotEmpty() &&
                MessageDigest.isEqual(expected, password.orEmpty().toByteArray(Charsets.UTF_8))
        }
    }

    private fun publicKeyAuthenticator(config: SftpServerConfig): PublickeyAuthenticator {
        val delegate = PublickeyAuthenticator.fromAuthorizedEntries(
            AUTHORIZED_KEYS_SOURCE,
            null,
            parseAuthorizedKeys(config.authorizedKeys),
            PublicKeyEntryResolver.IGNORING,
        )
        return PublickeyAuthenticator { user, key, session ->
            user == config.username && delegate.authenticate(user, key, session)
        }
    }

    /** A line MINA cannot parse, or whose key type it cannot decode here, is dropped rather than fatal. */
    private fun parseAuthorizedKeys(lines: List<String>): List<AuthorizedKeyEntry> = lines.mapNotNull { line ->
        try {
            AuthorizedKeyEntry.parseAuthorizedKeyEntry(line)
                ?.takeIf { it.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING) != null }
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "SftpServerController: unparsable authorized key skipped")
            null
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "SftpServerController: unsupported authorized key skipped")
            null
        }
    }

    /** Keeps the served roots in step with the settings, so a folder picked now serves the next login. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun watchRoots() {
        rootsJob?.cancel()
        rootsJob = appScope.launch(ioDispatcher) {
            settingsStore.values
                .map { config: SftpServerConfig -> config.rootUris }
                .distinctUntilChanged()
                .collect { roots = resolveRoots(it) }
        }
    }

    private suspend fun stopLocked() {
        rootsJob?.cancel()
        rootsJob = null
        val running = server
        server = null
        roots = emptyList()
        if (running != null) {
            withContext(ioDispatcher) {
                try {
                    running.stop(true)
                } catch (e: IOException) {
                    Timber.w(e, "SftpServerController: server did not stop cleanly")
                }
            }
        }
        // The service stops itself after a failed start; that stop must not wipe the reason the
        // settings surface is about to show.
        if (running != null || mutableState.value !is SftpServerState.Failed) {
            mutableState.value = SftpServerState.Stopped
        }
    }

    /** A picked folder whose access grant is gone no longer resolves to a directory and is skipped. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveRoots(uris: List<String>): List<SftpServerNode> = uris
        .mapNotNull { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
        .filter(DocumentFile::isDirectory)
        .map { DocumentFileServerNode(context, it) }

    private companion object {
        const val BIND_ALL_INTERFACES = "0.0.0.0"
        const val AUTHORIZED_KEYS_SOURCE = "fms-sftp-server-settings"
    }
}
