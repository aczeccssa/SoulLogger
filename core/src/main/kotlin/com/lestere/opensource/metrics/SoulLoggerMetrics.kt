package com.lestere.opensource.metrics

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SoulLoggerMetrics(
    private val registry: MeterRegistry,
    private val metricsPrefix: String = "soullogger"
) {
    
    private val _logsWritten = AtomicLong(0)
    private val _logsDropped = AtomicLong(0)
    private val _logsErrors = AtomicLong(0)
    private val _queueSize = AtomicLong(0)
    private val _rotationCount = AtomicLong(0)
    
    // Counters
    val logsWritten: Counter = Counter.builder("$metricsPrefix.logs.written")
        .description("Total number of logs written")
        .register(registry)
    
    val logsDropped: Counter = Counter.builder("$metricsPrefix.logs.dropped")
        .description("Total number of logs dropped due to backpressure")
        .register(registry)
    
    val logsErrors: Counter = Counter.builder("$metricsPrefix.logs.errors")
        .description("Total number of log write errors")
        .register(registry)
    
    // Gauges
    init {
        Gauge.builder("$metricsPrefix.queue.size") { _queueSize.get() }
            .description("Current queue size")
            .register(registry)
        
        Gauge.builder("$metricsPrefix.rotation.count") { _rotationCount.get() }
            .description("Total number of log file rotations")
            .register(registry)
    }
    
    // Distribution summary for log sizes
    val logSizeSummary: DistributionSummary = DistributionSummary.builder("$metricsPrefix.log.size")
        .description("Distribution of log message sizes")
        .register(registry)
    
    // Timer for write latency
    val writeTimer: Timer = Timer.builder("$metricsPrefix.write.latency")
        .description("Time taken to write logs")
        .register(registry)
    
    // Timer for flush latency
    val flushTimer: Timer = Timer.builder("$metricsPrefix.flush.latency")
        .description("Time taken to flush buffer")
        .register(registry)
    
    // State flows for monitoring
    private val _metricsState = MutableStateFlow(MetricsState())
    val metricsState: StateFlow<MetricsState> = _metricsState.asStateFlow()
    
    data class MetricsState(
        val totalWritten: Long = 0,
        val totalDropped: Long = 0,
        val totalErrors: Long = 0,
        val currentQueueSize: Long = 0,
        val rotationCount: Long = 0
    )
    
    fun recordLogWritten(logger: Logger) {
        _logsWritten.incrementAndGet()
        logsWritten.increment()
        logSizeSummary.record(logger.command.toByteArray().size.toDouble())
        updateState()
    }
    
    fun recordLogDropped() {
        _logsDropped.incrementAndGet()
        logsDropped.increment()
        updateState()
    }
    
    fun recordLogError() {
        _logsErrors.incrementAndGet()
        logsErrors.increment()
        updateState()
    }
    
    fun recordQueueSize(size: Int) {
        _queueSize.set(size.toLong())
        updateState()
    }
    
    fun recordRotation() {
        _rotationCount.incrementAndGet()
        updateState()
    }
    
    fun recordWriteTime(timeNanos: Long) {
        writeTimer.record(timeNanos, TimeUnit.NANOSECONDS)
    }
    
    fun recordFlushTime(timeNanos: Long) {
        flushTimer.record(timeNanos, TimeUnit.NANOSECONDS)
    }
    
    private fun updateState() {
        _metricsState.value = MetricsState(
            totalWritten = _logsWritten.get(),
            totalDropped = _logsDropped.get(),
            totalErrors = _logsErrors.get(),
            currentQueueSize = _queueSize.get(),
            rotationCount = _rotationCount.get()
        )
    }
    
    fun getSummary(): Map<String, Any> {
        return mapOf(
            "logs.written" to _logsWritten.get(),
            "logs.dropped" to _logsDropped.get(),
            "logs.errors" to _logsErrors.get(),
            "queue.size" to _queueSize.get(),
            "rotation.count" to _rotationCount.get(),
            "write.latency.p50" to writeTimer.percentile(0.5, TimeUnit.MILLISECONDS),
            "write.latency.p95" to writeTimer.percentile(0.95, TimeUnit.MILLISECONDS),
            "write.latency.p99" to writeTimer.percentile(0.99, TimeUnit.MILLISECONDS),
            "flush.latency.p50" to flushTimer.percentile(0.5, TimeUnit.MILLISECONDS),
            "flush.latency.p95" to flushTimer.percentile(0.95, TimeUnit.MILLISECONDS),
            "flush.latency.p99" to flushTimer.percentile(0.99, TimeUnit.MILLISECONDS)
        )
    }
    
    companion object {
        fun create(registry: MeterRegistry, prefix: String = "soullogger"): SoulLoggerMetrics {
            return SoulLoggerMetrics(registry, prefix)
        }
    }
}

class MetricsConfig(
    var enabled: Boolean = true,
    var prefix: String = "soullogger",
    var includeWriteLatency: Boolean = true,
    var includeFlushLatency: Boolean = true,
    var includeQueueSize: Boolean = true
)

object SoulLoggerMetricsRegistry {
    private var instance: SoulLoggerMetrics? = null
    private var config: MetricsConfig = MetricsConfig()
    
    fun initialize(registry: MeterRegistry, config: MetricsConfig = MetricsConfig()) {
        this.config = config
        if (config.enabled) {
            instance = SoulLoggerMetrics(registry, config.prefix)
        }
    }
    
    fun get(): SoulLoggerMetrics? = instance
    
    fun isEnabled(): Boolean = config.enabled
}
