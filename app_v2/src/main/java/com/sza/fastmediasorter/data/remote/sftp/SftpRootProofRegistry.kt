package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.data.network.exceptions.NetworkPinRefusedException
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.utils.SftpPathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the `pin-v1` proof (SHARE-SESSION 1.2, DEVICE-EXCHANGE 17 item AH) for every SFTP operation whose path
 * lies under an exchange resource or a local resource whose PIN the server has accepted.
 *
 * About 40 places build [SftpClient.SftpConnectionInfo], so the proof is not threaded through them: [SftpClient]
 * asks here at its entry points, the way the host-key pin is filled in. A path decides the root, a stored PIN
 * decides whether that root is protected. A local PIN becomes eligible only after a root-specific proof succeeds.
 */
@Singleton
class SftpRootProofRegistry @Inject constructor(
    private val resourceDao: ResourceDao,
    private val pins: ExchangeResourcePinStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    /** One attached share's root as its resource row names it, and the binding that owns its PIN. */
    internal data class Root(val name: String, val binding: ExchangeBinding)

    private val roots = MutableStateFlow<Map<String, List<Root>>?>(null)
    private val localRoots = MutableStateFlow<Map<String, List<SftpRootProof>>>(emptyMap())
    private val verifiedLocal = ConcurrentHashMap<String, SftpRootProof>()
    private val observing = AtomicBoolean(false)

    // The binding a proof was handed out for, by session key, so the outcome of that session names its owner.
    private val handedOut = ConcurrentHashMap<String, ExchangeBinding>()
    private val refused = MutableStateFlow<Set<ExchangeBinding>>(emptySet())

    /** Bindings whose last proof was refused: the PIN stored for them is wrong, or the owner changed it. */
    val refusals: StateFlow<Set<ExchangeBinding>> = refused.asStateFlow()

    /** Returns [info] with the proof filled in when [remotePath] lies under a protected root; a given proof stays. */
    suspend fun withProof(info: SftpClient.SftpConnectionInfo, remotePath: String?): SftpClient.SftpConnectionInfo {
        if (info.rootProof != null || remotePath == null) return info
        return proofFor(info, remotePath, awaitRoots())
    }

    /** Blocking twin of [withProof] for the ExoPlayer data-source path, which already blocks on network I/O. */
    fun withProofBlocking(info: SftpClient.SftpConnectionInfo, remotePath: String?): SftpClient.SftpConnectionInfo {
        if (info.rootProof != null || remotePath == null) return info
        return runBlocking { proofFor(info, remotePath, roots.value ?: awaitRoots()) }
    }

    /** Records what a session that carried a handed-out proof answered; every other session is ignored. */
    fun noteOutcome(info: SftpClient.SftpConnectionInfo, outcome: Result<*>) {
        val root = info.rootProof?.root ?: return
        val binding = handedOut[sessionKey(info, root)] ?: return
        val failure = outcome.exceptionOrNull()
        when {
            failure is NetworkPinRefusedException -> refused.update { it + binding }
            failure == null -> refused.update { it - binding }
        }
    }

    /** The same, for a connect that threw instead of returning a result. */
    fun noteFailure(info: SftpClient.SftpConnectionInfo, failure: Throwable) =
        noteOutcome(info, Result.failure<Unit>(failure))

    /** A PIN just accepted for [binding] clears an earlier refusal. */
    fun clearRefusal(binding: ExchangeBinding) = refused.update { it - binding }

    /** A local PIN is a candidate only for the resource root that just failed to list. */
    suspend fun localCandidate(info: SftpClient.SftpConnectionInfo, path: String): SftpRootProof? {
        if (info.rootProof != null) return null
        val exchange = awaitRoots()[key(info.host, info.port)]
        return if (exchange?.any { covers(it.name, path) } == true) {
            null
        } else {
            localRoots.value[key(info.host, info.port)]?.firstOrNull { it.root == path.trim('/') }
        }
    }

    /** Remember only a proof whose own session authenticated and could stat its root. */
    fun rememberLocal(info: SftpClient.SftpConnectionInfo, proof: SftpRootProof) {
        verifiedLocal[localKey(info, proof.root)] = proof
    }

    private suspend fun proofFor(
        info: SftpClient.SftpConnectionInfo,
        remotePath: String,
        snapshot: Map<String, List<Root>>,
    ): SftpClient.SftpConnectionInfo {
        val local = localRoots.value[key(info.host, info.port)]?.firstOrNull { covers(it.root, remotePath) }
        if (local != null && verifiedLocal[localKey(info, local.root)] == local) {
            return info.copy(rootProof = local)
        }
        val root = snapshot[key(info.host, info.port)]?.firstOrNull { covers(it.name, remotePath) }
        val pin = root?.let { pins.pin(it.binding) }
        return if (root == null || pin == null) {
            info
        } else {
            handedOut[sessionKey(info, root.name)] = root.binding
            info.copy(rootProof = SftpRootProof(root.name, pin))
        }
    }

    private suspend fun awaitRoots(): Map<String, List<Root>> {
        ensureObserving()
        return roots.filterNotNull().first()
    }

    private fun ensureObserving() {
        if (!observing.compareAndSet(false, true)) return
        applicationScope.launch {
            resourceDao.getAllResources()
                .catch { e ->
                    // Degrade to "no proofs": an operation then fails as a refusal, never as a hang on a snapshot.
                    Timber.e(e, "SFTP root proofs unavailable; protected roots will be refused")
                    roots.value = roots.value ?: emptyMap()
                }
                .collect { resources ->
                    val local = buildLocalRoots(resources)
                    val active = local.values.flatten().toSet()
                    verifiedLocal.forEach { (key, proof) ->
                        if (proof !in active) verifiedLocal.remove(key, proof)
                    }
                    localRoots.value = local
                    roots.value = buildRoots(resources)
                }
        }
    }

    internal companion object {
        fun buildLocalRoots(resources: List<ResourceEntity>): Map<String, List<SftpRootProof>> {
            val result = HashMap<String, MutableList<SftpRootProof>>()
            resources.filter { it.type == ResourceType.SFTP && it.exchangeAccount == null }.forEach { entity ->
                val pin = entity.accessPin?.takeIf { it.isNotBlank() } ?: return@forEach
                val name = SftpPathUtils.parseSftpPath(entity.path)?.remotePath?.trim('/')
                if (!name.isNullOrEmpty()) {
                    val proof = SftpRootProof(name, pin)
                    SftpHostKeyPinRegistry.addressesOf(entity).forEach { address ->
                        result.getOrPut(address) { mutableListOf() } += proof
                    }
                }
            }
            return result
        }

        /** Every address of every exchange-bound SFTP resource, mapped to the roots reachable through it. */
        fun buildRoots(resources: List<ResourceEntity>): Map<String, List<Root>> {
            val result = HashMap<String, MutableList<Root>>()
            resources.filter { it.type == ResourceType.SFTP }.forEach { entity ->
                val account = entity.exchangeAccount
                val resourceId = entity.exchangeResourceId
                val name = SftpPathUtils.parseSftpPath(entity.path)?.remotePath?.trim('/')
                if (account != null && resourceId != null && !name.isNullOrEmpty()) {
                    val root = Root(name, ExchangeBinding(account, resourceId))
                    SftpHostKeyPinRegistry.addressesOf(entity).forEach { address ->
                        result.getOrPut(address) { mutableListOf() } += root
                    }
                }
            }
            return result
        }

        /** [remotePath] is the root itself or lies under it; `/MusicBox` is not under `Music`. */
        fun covers(root: String, remotePath: String): Boolean {
            val path = remotePath.trimStart('/')
            return path == root || path.startsWith("$root/")
        }

        fun key(host: String, port: Int): String = SftpHostKeyPinRegistry.key(host, port)

        private fun sessionKey(info: SftpClient.SftpConnectionInfo, root: String) =
            key(info.host, info.port) + "|" + root

        private fun localKey(info: SftpClient.SftpConnectionInfo, root: String) =
            key(info.host, info.port) + "|" + info.username + "|" + root
    }
}
