package com.sza.fastmediasorter.data.companion

import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0984: write side of the companion config contract - mirror of [CompanionConfigParser].
 *
 * Emits plain-JSON transport (starts with `{`), which [CompanionConfigParser.parse] round-trips
 * without the `FMSCFG1:` gzip variant (that variant is import-only, used by the QR path).
 */
@Singleton
class CompanionConfigSerializer @Inject constructor() {

    private val gson = Gson()

    fun serialize(dto: CompanionConfigDto): String = gson.toJson(dto)
}
