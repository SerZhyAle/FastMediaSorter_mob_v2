package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * S1649: in-memory [NetworkCredentialsRepository] for tests.
 *
 * Lives beside the interface rather than inside a test class because a fake declared in a
 * `domain/usecase` test file is charged by the architecture-naming gate: that package expects
 * `*UseCase`, and renaming a repository fake to satisfy it would make the name lie about what the
 * class is. Here the name is honest and the fake is reusable by the next test that needs one.
 *
 * @param initial rows the repository starts with.
 * @param orphanedIds credential ids [getOrphanedCredentials] reports, i.e. the ones no resource
 *   references. Kept explicit rather than derived, because these tests are about what the audit does
 *   with that answer, not about how it is computed.
 */
class FakeNetworkCredentialsRepository(
    initial: List<NetworkCredentialsEntity>,
    private val orphanedIds: Set<String>
) : NetworkCredentialsRepository {

    val stored = initial.toMutableList()

    /** Credential ids handed to [delete], in call order. */
    val deleted = mutableListOf<String>()

    override suspend fun insert(credentials: NetworkCredentialsEntity): Long = 0

    override suspend fun getById(id: Long): NetworkCredentialsEntity? = stored.firstOrNull { it.id == id }

    override suspend fun getByCredentialId(credentialId: String): NetworkCredentialsEntity? =
        stored.firstOrNull { it.credentialId == credentialId }

    override suspend fun getByTypeServerAndPort(
        type: String,
        server: String,
        port: Int
    ): NetworkCredentialsEntity? = null

    override suspend fun getByServerAndShare(server: String, shareName: String): NetworkCredentialsEntity? = null

    override suspend fun getCredentialsByHost(host: String): NetworkCredentialsEntity? = null

    override suspend fun getByTypeAndAccountId(type: String, accountId: String): NetworkCredentialsEntity? = null

    override suspend fun update(credentials: NetworkCredentialsEntity) = Unit

    override suspend fun delete(credentials: NetworkCredentialsEntity) {
        deleted += credentials.credentialId
        stored.removeAll { it.credentialId == credentials.credentialId }
    }

    override fun getAllCredentials(): Flow<List<NetworkCredentialsEntity>> = flowOf(stored.toList())

    override suspend fun getOrphanedCredentials(): List<NetworkCredentialsEntity> =
        stored.filter { it.credentialId in orphanedIds }

    override suspend fun getManualShareNamesForServer(server: String, port: Int): List<String> = emptyList()

    override suspend fun addManualShareName(server: String, port: Int, shareName: String) = Unit
}
