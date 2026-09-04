package com.sza.fastmediasorter.wear.data.network

import org.junit.Assert.assertNotNull
import org.junit.Test

// S2502: the AndroidJUnit4 runner this carried lives in androidTestImplementation only, so its import
// did not resolve here and the whole wear JVM test source set failed to compile - every wear unit test
// with it, not just this one. The subject needs no instrumentation: createHttpDataSourceFactory builds
// a Media3 factory and a lambda, touching no Context and no Android framework call.
class WearStreamDataSourceFactoryProviderTest {

    @Test
    fun createHttpDataSourceFactory_returnsNonNullFactory() {
        val factory = WearStreamDataSourceFactoryProvider.createHttpDataSourceFactory()
        assertNotNull(factory)
    }
}
