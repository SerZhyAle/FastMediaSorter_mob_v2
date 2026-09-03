package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection

/**
 * A contributor that answers whatever the test tells it to, including by failing.
 *
 * The collector's own responsibilities - ordering and surviving a contributor that throws - are about
 * the seam and not about any real area of the report, so they are asserted against a double rather than
 * against whichever real contributor happens to be convenient to break.
 */
class FakeWearSystemInfoContributor(
    override val order: Int,
    private val answer: List<WearSystemInfoSection> = emptyList(),
    private val failure: Throwable? = null
) : WearSystemInfoContributor {

    override suspend fun sections(): List<WearSystemInfoSection> {
        failure?.let { thrown -> throw thrown }
        return answer
    }
}
