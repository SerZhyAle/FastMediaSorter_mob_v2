package com.sza.fastmediasorter.domain.scanner

import com.sza.fastmediasorter.domain.usecase.MediaScanner

/**
 * S1861: the [MediaScanner] for [com.sza.fastmediasorter.domain.model.ResourceType.WEAR_WATCH].
 *
 * `MediaScannerFactory` lives in `src/main`, where rule 14 forbids a build-variant check, so the
 * factory injects this interface and never the GMS-backed class. The real implementation is mounted
 * from `src/wearGms` into the Wear-capable flavors; every other flavor mounts the inert twin from
 * `src/wearStub`, which reports an unreachable watch instead of pretending an empty one.
 *
 * It sits beside `domain/hash/FileHasher` and `domain/verifier/QuickVerifier` rather than in
 * `domain/usecase/` next to the factory: rule 6 reserves that package for `*UseCase` names, and the
 * `class-architecture-naming` gate refuses a plain capability interface there.
 */
interface WearWatchMediaScanner : MediaScanner {

    /**
     * Whether this build carries the Wear companion at all.
     *
     * Distinct from [isWatchReachable] on purpose: reachability answers "is the watch on right now",
     * which changes minute to minute, while this answers "can this build ever talk to a watch", which
     * is fixed at compile time by the mounted source set. UI that offers to add a watch resource keys
     * off this one - hiding the entry point because the watch happens to be off the wrist would make
     * a permanent capability look like a transient fault. Rule 14 forbids the `BuildConfig` check that
     * would otherwise answer it in `src/main`.
     */
    val isCompanionAvailable: Boolean

    /**
     * Whether a paired watch is currently connected.
     *
     * Separate from [MediaScanner.isWritable], which answers per path: this one answers for the
     * resource as a whole, so the resource can be marked "watch not connected" rather than drawn as
     * an empty folder.
     */
    suspend fun isWatchReachable(): Boolean
}
