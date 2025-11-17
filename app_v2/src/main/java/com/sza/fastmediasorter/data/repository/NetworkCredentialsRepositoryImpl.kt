package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkCredentialsRepositoryImpl @Inject constructor(
    private val dao: NetworkCredentialsDao
) : NetworkCredentialsRepository {

    override suspend fun insert(credentials: NetworkCredentialsEntity): Long {
        return dao.insert(credentials)
    }

    override suspend fun getById(id: Long): NetworkCredentialsEntity? {
        // DAO doesn't have getById, use getByTypeServerAndPort as workaround
        return null // TODO: Add getById to DAO if needed
    }

    override suspend fun getByCredentialId(credentialId: String): NetworkCredentialsEntity? {
        return dao.getCredentialsById(credentialId)
    }

    override suspend fun getByTypeServerAndPort(
        type: String,
        server: String,
        port: Int
    ): NetworkCredentialsEntity? {
        return dao.getByTypeServerAndPort(type, server, port)
    }

    override suspend fun update(credentials: NetworkCredentialsEntity) {
        dao.update(credentials)
    }

    override suspend fun delete(credentials: NetworkCredentialsEntity) {
        // DAO doesn't have delete by entity, use deleteByCredentialId
        dao.deleteByCredentialId(credentials.credentialId)
    }
}
