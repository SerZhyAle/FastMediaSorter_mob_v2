package com.sza.fastmediasorter.identity

import com.sza.fastmediasorter.domain.identity.GoogleScope
import java.time.Duration
import java.time.Instant

/**
 * Remembers for a while that Play services asked for a consent screen for a scope set.
 *
 * Background callers (the Drive directory poll, the rendezvous publisher) cannot show that screen, and
 * asking again returns the same answer until the user acts - one Play services round trip and one log line
 * per poll for nothing. The memory expires by itself so a grant made outside the app is still picked up,
 * and the issuer's owner forgets it when the user signs in, signs out or grants a scope. S4487.
 *
 * Not thread-safe: [GoogleTokenIssuer] reads and writes it only under its own mutex.
 */
internal class ConsentRefusalMemory(
    private val clock: () -> Instant = Instant::now,
    private val memory: Duration = DEFAULT_MEMORY
) {
    private val rememberedUntil = mutableMapOf<Set<GoogleScope>, Instant>()

    /** True while a refusal for exactly [scopes] is remembered; an expired entry is dropped on the way. */
    fun isRemembered(scopes: Set<GoogleScope>): Boolean {
        val until = rememberedUntil[scopes]
        val live = until != null && clock().isBefore(until)
        if (until != null && !live) rememberedUntil.remove(scopes)
        return live
    }

    fun remember(scopes: Set<GoogleScope>) {
        rememberedUntil[scopes] = clock().plus(memory)
    }

    fun forget() {
        rememberedUntil.clear()
    }

    companion object {
        /** Long enough to quiet a two-minute poll, short enough that an out-of-app grant shows up unaided. */
        val DEFAULT_MEMORY: Duration = Duration.ofMinutes(15)
    }
}
