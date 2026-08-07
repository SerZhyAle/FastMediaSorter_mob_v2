package com.sza.fastmediasorter.ui.launcher.signal

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges every [LauncherSignalSource] into the single list the launcher status strip renders.
 *
 * This exists so the strip's owner asks one question - are there signals - instead of collecting each
 * producer and re-deciding precedence itself, which is what S1421 ADR-2 forbids.
 */
@Singleton
class LauncherSignalRegistry @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards LauncherSignalSource>,
) {

    /**
     * Sorted by [LauncherSignalKind] declaration order, not by the injected set's iteration order: a Dagger
     * multibinding is a `Set` and guarantees no order at all, so relying on binding order would let the row
     * reshuffle between builds.
     */
    fun observe(): Flow<List<LauncherSignal>> {
        if (sources.isEmpty()) {
            return flowOf(emptyList())
        }
        return combine(sources.map(::guarded)) { parts ->
            parts.toList()
                .flatten()
                .sortedWith(compareBy({ it.kind.ordinal }, { it.id }))
        }.distinctUntilChanged()
    }

    /**
     * @return where a tap on [signal] goes, asking each source until one claims it. A source that throws is
     * skipped rather than taking the whole strip down with it.
     */
    fun open(signal: LauncherSignal): Intent? = sources.firstNotNullOfOrNull { source ->
        runCatching { source.open(signal) }
            .onFailure { Timber.w(it, "Launcher signal source %s failed to open", source.javaClass.simpleName) }
            .getOrNull()
    }

    /**
     * `combine` waits for every input to emit before producing anything, so one silent source would hold the
     * whole strip empty. Seeding each with an empty list keeps a slow or broken producer from stalling the
     * others, and a failed one drops out instead of cancelling the merge.
     */
    private fun guarded(source: LauncherSignalSource): Flow<List<LauncherSignal>> =
        source.observe()
            .onStart { emit(emptyList()) }
            .catch { error ->
                Timber.w(error, "Launcher signal source %s failed", source.javaClass.simpleName)
                emit(emptyList())
            }
}
