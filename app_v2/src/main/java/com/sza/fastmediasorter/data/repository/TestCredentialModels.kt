package com.sza.fastmediasorter.data.repository

import com.google.gson.annotations.SerializedName

data class TestCredentialsConfig(
    @SerializedName("credentials")
    val credentials: List<TestCredential> = emptyList()
)

data class TestCredential(
    @SerializedName("type")
    val type: String, // "SMB", "FTP", "SFTP"
    
    @SerializedName("server")
    val server: String,
    
    @SerializedName("port")
    val port: Int? = null,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("shareName")
    val shareName: String? = null, // For SMB
    
    @SerializedName("folder")
    val folder: String? = null, // For SFTP/FTP path
    
    @SerializedName("domain")
    val domain: String? = null // For SMB
)
