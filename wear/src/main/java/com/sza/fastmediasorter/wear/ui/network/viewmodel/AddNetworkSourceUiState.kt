package com.sza.fastmediasorter.wear.ui.network.viewmodel

import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType

data class AddNetworkSourceUiState(
    val protocol: NetworkSourceType = NetworkSourceType.SMB,
    val name: String = "",
    val server: String = "",
    val port: Int = 445,
    val username: String = "",
    val password: String = "",
    val shareName: String = "",
    val domain: String = "",
    val basePath: String = "/",
    val useSshKey: Boolean = false,
    val sshPrivateKey: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    /**
     * One-shot "saved" signal. The screen pops itself on it only while it is composed, so a save that
     * finishes after the user already swiped back pops nothing beneath the form.
     */
    val isSaved: Boolean = false
)
