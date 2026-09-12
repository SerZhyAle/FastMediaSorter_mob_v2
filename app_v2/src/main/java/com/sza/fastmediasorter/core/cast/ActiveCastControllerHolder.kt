package com.sza.fastmediasorter.core.cast

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2531: the one place outside the player where the live [CastController] can be reached.
 *
 * [CastControllerFactory] builds a controller per player screen from Activity-scoped arguments, so
 * the Hilt graph holds no binding a background listener could inject. The player screen deposits its
 * controller here for as long as it lives; anything answering a request from outside the process
 * asks for it, and `null` means no player is up rather than "Cast is unsupported".
 */
@Singleton
class ActiveCastControllerHolder @Inject constructor() {

    private val active = AtomicReference<CastController?>(null)

    fun attach(controller: CastController) {
        active.set(controller)
    }

    /**
     * Identity-checked: two player screens overlap during a rotation or a task switch, and a plain
     * clear would let the outgoing one erase the controller the incoming one just deposited.
     */
    fun detach(controller: CastController) {
        active.compareAndSet(controller, null)
    }

    fun current(): CastController? = active.get()
}
