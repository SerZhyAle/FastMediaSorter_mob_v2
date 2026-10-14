package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.NetworkTimeoutException
import com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException
import com.sza.fastmediasorter.domain.model.HostPort
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "online, but not reachable from this network" verdict of contract DEVICE-EXCHANGE section 8.3:
 * the producer's Drive records say it is online and inside their TTL, yet none of its endpoints answered from
 * here - typically a carrier NAT on one side. Drive carries no relay, so the honest outcome is to say so
 * instead of reporting an ordinary dropped connection.
 *
 * Held per endpoint for one network epoch: the resolver clears it on a network change and when a
 * candidate of the group answers again.
 */
@Singleton
class SftpRendezvousVerdicts @Inject constructor() {

    // newSetFromMap, not newKeySet(): KeySetView is API 24 and the legacy flavor ships to API 23.
    private val unreachable: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun markUnreachableFromHere(endpoints: Collection<HostPort>) {
        endpoints.forEach { unreachable.add(key(it.host, it.port)) }
    }

    fun clear(endpoints: Collection<HostPort>) {
        endpoints.forEach { unreachable.remove(key(it.host, it.port)) }
    }

    fun clearAll() = unreachable.clear()

    fun isUnreachableFromHere(host: String, port: Int): Boolean = key(host, port) in unreachable

    /**
     * [failure] wrapped in [SftpPeerUnreachableFromHereException] when the verdict holds for [host]:[port]
     * and the failure is a connectivity one; an authentication or host-key verdict is never masked.
     */
    fun annotate(host: String, port: Int, failure: Throwable): Throwable {
        if (!isUnreachableFromHere(host, port) || failure is SftpPeerUnreachableFromHereException) return failure
        val classified = NetworkErrorClassifier.classifySilently(failure)
        val connectivity = classified is NetworkTimeoutException ||
            (classified is NetworkConnectionLostException && classified !is WifiRequiredException)
        return if (connectivity) SftpPeerUnreachableFromHereException(failure) else failure
    }

    fun <T> annotate(host: String, port: Int, result: Result<T>): Result<T> {
        val failure = result.exceptionOrNull() ?: return result
        val annotated = annotate(host, port, failure)
        return if (annotated === failure) result else Result.failure(annotated)
    }

    private fun key(host: String, port: Int): String = "${host.lowercase()}:$port"
}

/** A connect to a producer that is online on Drive but answered on none of its endpoints from this network. */
class SftpPeerUnreachableFromHereException(cause: Throwable) :
    IOException("SFTP peer is online but unreachable from this network", cause)
