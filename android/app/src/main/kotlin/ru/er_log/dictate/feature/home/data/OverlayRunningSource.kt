package ru.er_log.dictate.feature.home.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.er_log.dictate.core.overlay.FloatingButtonService

/**
 * Provides a reactive stream of the floating overlay's running state.
 *
 * Implementations are responsible for efficiently polling or observing the
 * [FloatingButtonService] running flag without requiring a service binding.
 */
public interface OverlayRunningSource {
    /** Emits `true` when [FloatingButtonService] is running, `false` otherwise. */
    public val isRunning: Flow<Boolean>
}

/**
 * Tick-based implementation that polls [FloatingButtonService.isRunning] every 500 ms.
 *
 * This is the simplest approach and avoids the complexity of a bound service or broadcast
 * receiver while keeping latency to at most half a second.
 */
public class OverlayRunningSourceImpl : OverlayRunningSource {
    override val isRunning: Flow<Boolean> = flow {
        while (true) {
            emit(FloatingButtonService.isRunning.get())
            delay(500L)
        }
    }
}
