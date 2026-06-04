package com.sza.fastmediasorter.diagnostics

import android.content.Context
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsContributor
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsSection
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * noLegal-only [ExtendedDiagnosticsContributor]. Lives in `src/noLegal/java`, so it is never
 * compiled into a public flavor. Produces the seven strategic S0336 §5.1 diagnostic sections via
 * [NoLegalDiagnosticsCollectors]. The whole [sections] body is defensive: a single failing
 * collector yields its section with degraded fields instead of throwing.
 */
@Singleton
class NoLegalExtendedDiagnosticsContributor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xrEnvironmentDetector: XrEnvironmentDetector,
) : ExtendedDiagnosticsContributor {

    private val collectors = NoLegalDiagnosticsCollectors(context, xrEnvironmentDetector)

    override fun sections(): List<ExtendedDiagnosticsSection> {
        Timber.d("S0336: noLegal extended diagnostics collected")
        return listOf(
            collectors.osSecurity(),
            collectors.permissions(),
            collectors.installerSignature(),
            collectors.runtimes(),
            collectors.mounts(),
            collectors.network(),
            collectors.processResources(),
        )
    }
}
