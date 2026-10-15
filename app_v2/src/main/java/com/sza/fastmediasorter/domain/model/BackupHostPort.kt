package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/** S4238: one extra access path (a LAN address, a forwarded port, an exchange tunnel) of a backed-up resource. */
data class BackupHostPort(
    @SerializedName("host")
    val host: String = "",
    @SerializedName("port")
    val port: Int = 0,
)
