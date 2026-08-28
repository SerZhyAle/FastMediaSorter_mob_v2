package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.NetworkFileNotFoundException
import javax.inject.Inject

/** S2151: what a failed media load turned out to be, in terms the player can act on. */
enum class MediaLoadFailureOutcome {
    /** The resource did not answer. Temporary - worth retrying, and nothing may be deleted over it. */
    UNAVAILABLE,

    /** The server answered that the file does not exist. Final - the entry can be dropped. */
    GONE,

    /** Not a network failure the classifier knows. The caller keeps its existing error surface. */
    UNRECOGNISED,
}

/**
 * S2151: names the cause of a media load failure so the player can speak about it, instead of
 * collapsing an unreachable server and a corrupt local file into one sentence.
 *
 * Takes a list because the loader wraps the real failure: Glide reports a `GlideException` whose root
 * causes carry the socket error, and classifying only the wrapper would report every outage as
 * unrecognised. The first candidate the classifier recognises wins, so callers pass the most specific
 * throwables first.
 *
 * Exists as a use case rather than a call from the ViewModel because the exception taxonomy lives in
 * the data layer, which UI code does not reach directly (S2103).
 */
class ClassifyMediaLoadFailureUseCase @Inject constructor() {

    operator fun invoke(candidates: List<Throwable>): MediaLoadFailureOutcome {
        val recognised = candidates.firstNotNullOfOrNull { candidate ->
            // classifyDetailedSilently, not classifySilently: the latter returns a NetworkException
            // unconditionally and so cannot say "this was not a network failure" at all.
            val result = NetworkErrorClassifier.classifyDetailedSilently(candidate)
            result.exception.takeIf { !result.usedFallback }
        }
        return when {
            recognised == null -> MediaLoadFailureOutcome.UNRECOGNISED
            recognised is NetworkFileNotFoundException -> MediaLoadFailureOutcome.GONE
            else -> MediaLoadFailureOutcome.UNAVAILABLE
        }
    }
}
