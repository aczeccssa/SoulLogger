package com.lestere.opensource.context

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Mapped Diagnostic Context (MDC) for structured logging.
 *
 * Provides thread-local and coroutine-context storage for contextual information
 * that should be included with log messages.
 *
 * Usage:
 * ```
 * MDC.put("traceId", generateTraceId())
 * MDC.put("userId", user.id)
 *
 * SoulLogger.info("Processing request")
 * // Output: {..., "context":{"traceId":"abc123","userId":"user456"}, ...}
 *
 * MDC.clear()
 * ```
 *
 * @author LesterE
 * @since 1.0.0
 */
object MDC {

    private val contextMap = ThreadLocal<MutableMap<String, String>>()

    /**
     * Put a value into the MDC.
     */
    fun put(key: String, value: String) {
        val map = contextMap.get() ?: mutableMapOf()
        map[key] = value
        contextMap.set(map)
    }

    /**
     * Get a value from the MDC.
     */
    fun get(key: String): String? {
        return contextMap.get()?.get(key)
    }

    /**
     * Remove a value from the MDC.
     */
    fun remove(key: String) {
        contextMap.get()?.remove(key)
    }

    /**
     * Clear all values from the MDC.
     */
    fun clear() {
        contextMap.remove()
    }

    /**
     * Get a copy of the current MDC context map.
     */
    fun getContextMap(): Map<String, String> {
        return contextMap.get()?.toMap() ?: emptyMap()
    }

    /**
     * Set the MDC context map.
     */
    fun setContextMap(contextMap: Map<String, String>) {
        this.contextMap.set(contextMap.toMutableMap())
    }

    /**
     * Execute a block with temporary MDC values.
     */
    inline fun <T> withContext(vararg pairs: Pair<String, String>, block: () -> T): T {
        val previous = getContextMap()
        try {
            pairs.forEach { (k, v) -> put(k, v) }
            return block()
        } finally {
            clear()
            setContextMap(previous)
        }
    }

    /**
     * Create a CoroutineContext element for propagating MDC across coroutines.
     */
    fun asContextElement(): MDCContextElement {
        return MDCContextElement(getContextMap())
    }
}

/**
 * Coroutine context element for MDC propagation.
 */
class MDCContextElement(
    private val contextMap: Map<String, String>
) : ThreadContextElement<Map<String, String>>,
    AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<MDCContextElement>

    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>) {
        MDC.clear()
        if (oldState.isNotEmpty()) {
            MDC.setContextMap(oldState)
        }
    }

    override fun updateThreadContext(context: CoroutineContext): Map<String, String> {
        val previous = MDC.getContextMap()
        MDC.clear()
        if (contextMap.isNotEmpty()) {
            MDC.setContextMap(contextMap)
        }
        return previous
    }
}
