package com.lestere.opensource.context

import kotlinx.serialization.Serializable

/**
 * Structured logging context containing trace and request information.
 *
 * This context is automatically included in log messages when available,
 * enabling distributed tracing and request correlation.
 *
 * @property traceId Unique trace identifier for distributed tracing
 * @property spanId Span identifier within the trace
 * @property parentSpanId Parent span identifier (for nested spans)
 * @property requestId Unique request identifier
 * @property userId Current user identifier
 * @property sessionId Session identifier
 * @property serviceName Service name identifier
 * @property environment Environment name (e.g., production, staging)
 * @property version Application version
 *
 * @author LesterE
 * @since 1.0.0
 */
@Serializable
data class LogContext(
    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null,
    val requestId: String? = null,
    val userId: String? = null,
    val sessionId: String? = null,
    val serviceName: String? = null,
    val environment: String? = null,
    val version: String? = null
) {
    /**
     * Convert to MDC-compatible map.
     */
    fun toMdcMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        traceId?.let { map["traceId"] = it }
        spanId?.let { map["spanId"] = it }
        parentSpanId?.let { map["parentSpanId"] = it }
        requestId?.let { map["requestId"] = it }
        userId?.let { map["userId"] = it }
        sessionId?.let { map["sessionId"] = it }
        serviceName?.let { map["serviceName"] = it }
        environment?.let { map["environment"] = it }
        version?.let { map["version"] = it }
        return map
    }

    /**
     * Apply this context to MDC.
     */
    fun applyToMdc() {
        toMdcMap().forEach { (k, v) -> MDC.put(k, v) }
    }

    companion object {
        /**
         * Create LogContext from current MDC values.
         */
        fun fromMdc(): LogContext {
            return LogContext(
                traceId = MDC.get("traceId"),
                spanId = MDC.get("spanId"),
                parentSpanId = MDC.get("parentSpanId"),
                requestId = MDC.get("requestId"),
                userId = MDC.get("userId"),
                sessionId = MDC.get("sessionId"),
                serviceName = MDC.get("serviceName"),
                environment = MDC.get("environment"),
                version = MDC.get("version")
            )
        }

        /**
         * Create a new trace context with generated IDs.
         */
        fun createTrace(
            serviceName: String? = null,
            environment: String? = null,
            version: String? = null
        ): LogContext {
            return LogContext(
                traceId = generateId(),
                spanId = generateId(),
                serviceName = serviceName,
                environment = environment,
                version = version
            )
        }

        /**
         * Create a child span from current context.
         */
        fun createChildSpan(parent: LogContext): LogContext {
            return parent.copy(
                parentSpanId = parent.spanId,
                spanId = generateId()
            )
        }

        private fun generateId(): String {
            return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        }
    }
}
