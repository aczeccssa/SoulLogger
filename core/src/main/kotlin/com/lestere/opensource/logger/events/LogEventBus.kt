package com.lestere.opensource.logger.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Internal event bus for publishing log events.
 * Uses Kotlin SharedFlow for thread-safe event broadcasting.
 */
public object LogEventBus {
    private val _events = MutableSharedFlow<LogEvent>(replay = 0)
    public val events: SharedFlow<LogEvent> = _events.asSharedFlow()

    /**
     * Post an event to the event bus.
     * @param event The event to publish.
     */
    public suspend fun post(event: LogEvent) {
        _events.emit(event)
    }
}
