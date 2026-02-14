package com.lestere.opensource.logger

import com.lestere.opensource.ApplicationMode
import com.lestere.opensource.filter.FilterConfig
import com.lestere.opensource.masking.CompositeMaskingStrategy
import com.lestere.opensource.masking.MaskingStrategies
import com.lestere.opensource.performance.BackpressureStrategy
import com.lestere.opensource.performance.CompressionType
import com.lestere.opensource.rotation.RotationConfig
import com.lestere.opensource.rotation.RotationPolicy
import com.lestere.opensource.rotation.TimeRotationPattern
import com.lestere.opensource.utils.CACHE_ROOT_DICTIONARY
import io.ktor.server.application.*
import io.ktor.server.config.*

/**
 * Configuration for SoulLogger plugin with environment variable support and Mode-based defaults.
 *
 * Configuration resolution order (later overrides earlier):
 * 1. Mode defaults (DEVELOPMENT/PRODUCTION)
 * 2. application.conf (soul.logger.*)
 * 3. Environment variables (SOUL_LOGGER_*)
 * 4. DSL configuration in code
 *
 * @author LesterE
 * @since 1.0.0
 */
class SoulLoggerPluginConfiguration {

    /**
     * Application runtime mode. Determines default behaviors.
     * Config: soul.logger.mode
     * Env: SOUL_LOGGER_MODE
     */
    var mode: ApplicationMode = detectModeFromEnv()

    /**
     * Maximum file size per log file.
     * Default: 2MB
     * Config: soul.logger.max-file-size
     * Env: SOUL_LOGGER_MAX_FILE_SIZE
     */
    var maxFileSize: Long = 2 * 1024 * 1024L

    /**
     * Filter calls that should be logged (HTTP filter).
     */
    var httpFilter: (ApplicationCall) -> Boolean = { true }

    /**
     * Minimum log level to record.
     * Config: soul.logger.level
     * Env: SOUL_LOGGER_LEVEL
     */
    var level: SoulLogger.Level = SoulLogger.Level.INFO

    /**
     * Whether to enable console output.
     * Config: soul.logger.console
     * Env: SOUL_LOGGER_ENABLE_CONSOLE
     */
    var enableConsole: Boolean = true

    /**
     * Whether to enable file output.
     * Config: soul.logger.file
     * Env: SOUL_LOGGER_ENABLE_FILE
     */
    var enableFile: Boolean = true

    /**
     * Whether to enable introspection routes (analysis, reflex).
     * Config: soul.logger.introspection
     * Env: SOUL_LOGGER_ENABLE_INTROSPECTION
     */
    var enableIntrospection: Boolean = true

    /**
     * Whether to enable sensitive data masking.
     * Config: soul.logger.masking
     * Env: SOUL_LOGGER_ENABLE_MASKING
     */
    var enableMasking: Boolean = false

    /**
     * Whether to use Logback for log output.
     * Config: soul.logger.logback
     * Env: SOUL_LOGGER_ENABLE_LOGBACK
     */
    var enableLogback: Boolean = true

    /**
     * Log output format.
     * Config: soul.logger.format
     * Env: SOUL_LOGGER_FORMAT
     */
    var outputFormat: com.lestere.opensource.LogFormat = com.lestere.opensource.LogFormat.COLORFUL_TEXT

    /**
     * Async queue capacity.
     * Config: soul.logger.queue-capacity
     * Env: SOUL_LOGGER_QUEUE_CAPACITY
     */
    var queueCapacity: Int = 10_000

    /**
     * Custom Logback configuration file path.
     * Config: soul.logger.logback-config
     * Env: SOUL_LOGGER_LOGBACK_CONFIG_PATH
     */
    var logbackConfigPath: String? = null

    /**
     * Cache root directory for logs and reports.
     * Config: soul.logger.path
     * Env: SOUL_LOGGER_PATH
     */
    var cacheRootDictionary: String = CACHE_ROOT_DICTIONARY

    /**
     * Reflex (log file access) configuration.
     */
    var reflex: ReflexConfiguration = ReflexConfiguration()

    /**
     * Analysis configuration.
     */
    var analysis: AnalysisConfiguration = AnalysisConfiguration()

    /**
     * Report generation configuration.
     */
    var report: ReportGenerationConfiguration = ReportGenerationConfiguration()

    /**
     * Stream configuration.
     */
    var stream: StreamConfiguration = StreamConfiguration()

    /**
     * Rotation configuration for log files.
     * Config: soul.logger.rotation.*
     * Env: SOUL_LOGGER_ROTATION_*
     */
    var rotation: RotationConfiguration = RotationConfiguration()

    /**
     * Filter configuration for log sampling.
     * Config: soul.logger.filter.*
     * Env: SOUL_LOGGER_FILTER_*
     */
    var logFilter: FilterConfiguration = FilterConfiguration()

    /**
     * Performance configuration.
     * Config: soul.logger.performance.*
     * Env: SOUL_LOGGER_PERFORMANCE_*
     */
    var performance: PerformanceConfiguration = PerformanceConfiguration()
    
    /**
     * Runtime management configuration.
     * Config: soul.logger.runtime.*
     * Env: SOUL_LOGGER_RUNTIME_*
     */
    var runtimeManagement: RuntimeManagementConfiguration = RuntimeManagementConfiguration()
    
    /**
     * Health check configuration.
     * Config: soul.logger.health.*
     * Env: SOUL_LOGGER_HEALTH_*
     */
    var healthCheck: HealthCheckConfiguration = HealthCheckConfiguration()

    /**
     * Masking strategy for sensitive data.
     */
    var maskingStrategy: CompositeMaskingStrategy = MaskingStrategies.default()

    /**
     * Log dictionary path.
     */
    val logDictionary get() = "${cacheRootDictionary}/log/"

    /**
     * Analysis output dictionary.
     */
    val analysisOutputDictionary get() = "${cacheRootDictionary}/report/"

    /**
     * Report generation dictionary.
     */
    val reportGenerationDict get() = "${cacheRootDictionary}/dist/"

    /**
     * Load configuration from Ktor ApplicationConfig.
     * This method is called automatically during plugin setup.
     *
     * Resolution order: default < application.conf < environment variables < DSL
     */
    fun loadFromConfig(config: ApplicationConfig) {
        val soulConfig = try {
            config.config("soul.logger")
        } catch (_: Exception) {
            return // No soul.logger config section, use defaults
        }

        // Mode (determines defaults) - use optional access
        val configMode = soulConfig.propertyOrNull("mode")?.getString() ?: ""
        if (configMode.isNotEmpty()) {
            mode = ApplicationMode.fromString(configMode)
        }

        // Apply mode defaults first
        applyModeDefaults()

        // Override with application.conf values (optional access)
        soulConfig.propertyOrNull("max-file-size")?.getString()?.let { maxFileSizeStr ->
            if (maxFileSizeStr.isNotEmpty()) {
                maxFileSizeStr.toLongOrNull()?.let { maxFileSize = it }
            }
        }

        soulConfig.propertyOrNull("level")?.getString()?.let { levelStr ->
            if (levelStr.isNotEmpty()) {
                level = try { SoulLogger.Level.valueOf(levelStr.uppercase()) } catch (_: Exception) { level }
            }
        }

        soulConfig.propertyOrNull("console")?.getString()?.let { consoleStr ->
            if (consoleStr.isNotEmpty()) {
                consoleStr.toBooleanStrictOrNull()?.let { enableConsole = it }
            }
        }

        soulConfig.propertyOrNull("file")?.getString()?.let { fileStr ->
            if (fileStr.isNotEmpty()) {
                fileStr.toBooleanStrictOrNull()?.let { enableFile = it }
            }
        }

        soulConfig.propertyOrNull("introspection")?.getString()?.let { introspectionStr ->
            if (introspectionStr.isNotEmpty()) {
                introspectionStr.toBooleanStrictOrNull()?.let { enableIntrospection = it }
            }
        }

        soulConfig.propertyOrNull("masking")?.getString()?.let { maskingStr ->
            if (maskingStr.isNotEmpty()) {
                maskingStr.toBooleanStrictOrNull()?.let { enableMasking = it }
            }
        }

        soulConfig.propertyOrNull("logback")?.getString()?.let { logbackStr ->
            if (logbackStr.isNotEmpty()) {
                logbackStr.toBooleanStrictOrNull()?.let { enableLogback = it }
            }
        }

        soulConfig.propertyOrNull("format")?.getString()?.let { formatStr ->
            if (formatStr.isNotEmpty()) {
                outputFormat = try {
                    com.lestere.opensource.LogFormat.valueOf(formatStr.uppercase())
                } catch (_: Exception) {
                    outputFormat
                }
            }
        }

        soulConfig.propertyOrNull("queue-capacity")?.getString()?.let { queueCapacityStr ->
            if (queueCapacityStr.isNotEmpty()) {
                queueCapacityStr.toIntOrNull()?.let { queueCapacity = it }
            }
        }

        soulConfig.propertyOrNull("logback-config")?.getString()?.let { logbackConfigStr ->
            if (logbackConfigStr.isNotEmpty()) {
                logbackConfigPath = logbackConfigStr
            }
        }

        soulConfig.propertyOrNull("path")?.getString()?.let { pathStr ->
            if (pathStr.isNotEmpty()) {
                cacheRootDictionary = pathStr
            }
        }

        // Apply environment variable overrides (highest priority)
        applyEnvironmentOverrides()

        // Update dependent configs
        reflex.route = enableIntrospection
        analysis.analysisRoute = enableIntrospection
    }

    /**
     * Apply mode-based defaults.
     */
    private fun applyModeDefaults() {
        enableConsole = mode.enableConsole
        enableFile = mode.enableFile
        enableIntrospection = mode.enableIntrospection
        enableMasking = mode.enableMasking
        enableLogback = mode.enableLogback
        outputFormat = mode.format
        queueCapacity = mode.defaultQueueCapacity
        level = mode.defaultLevel
    }

    /**
     * Apply environment variable overrides.
     */
    private fun applyEnvironmentOverrides() {
        System.getenv("SOUL_LOGGER_MODE")?.let {
            mode = ApplicationMode.fromString(it)
        }

        System.getenv("SOUL_LOGGER_MAX_FILE_SIZE")?.toLongOrNull()?.let {
            maxFileSize = it
        }

        System.getenv("SOUL_LOGGER_LEVEL")?.let {
            level = try { SoulLogger.Level.valueOf(it.uppercase()) } catch (_: Exception) { level }
        }

        System.getenv("SOUL_LOGGER_ENABLE_CONSOLE")?.toBooleanStrictOrNull()?.let {
            enableConsole = it
        }

        System.getenv("SOUL_LOGGER_ENABLE_FILE")?.toBooleanStrictOrNull()?.let {
            enableFile = it
        }

        System.getenv("SOUL_LOGGER_ENABLE_INTROSPECTION")?.toBooleanStrictOrNull()?.let {
            enableIntrospection = it
        }

        System.getenv("SOUL_LOGGER_ENABLE_MASKING")?.toBooleanStrictOrNull()?.let {
            enableMasking = it
        }

        System.getenv("SOUL_LOGGER_ENABLE_LOGBACK")?.toBooleanStrictOrNull()?.let {
            enableLogback = it
        }

        System.getenv("SOUL_LOGGER_FORMAT")?.let {
            outputFormat = try {
                com.lestere.opensource.LogFormat.valueOf(it.uppercase())
            } catch (_: Exception) {
                outputFormat
            }
        }

        System.getenv("SOUL_LOGGER_QUEUE_CAPACITY")?.toIntOrNull()?.let {
            queueCapacity = it
        }

        System.getenv("SOUL_LOGGER_PATH")?.let {
            cacheRootDictionary = it
        }

        System.getenv("SOUL_LOGGER_LOGBACK_CONFIG_PATH")?.let {
            logbackConfigPath = it
        }
    }

    /**
     * Configuration for log route reflex.
     */
    class ReflexConfiguration {
        var route: Boolean = true
        var path: String = "/soul/logger"
    }

    /**
     * Configuration for log file analysis.
     */
    class AnalysisConfiguration {
        var analysisLog: Boolean = true
        var analysisRoute: Boolean = true
        val maxItems: Int = 50
    }

    /**
     * Configuration for report generation.
     */
    class ReportGenerationConfiguration {
        var tempFileExpiredIn: Long = 600_000L
    }

    /**
     * Configuration for log streaming.
     */
    class StreamConfiguration {
        var enabled: Boolean = false
        var replay: Int = 100
        var minLevel: SoulLogger.Level = SoulLogger.Level.DEBUG
    }

    /**
     * Configuration for log file rotation.
     */
    class RotationConfiguration {
        var enabled: Boolean = true
        
        /** Rotation policy type: TIME, SIZE, COMPOSITE */
        var policyType: String = "TIME"
        
        /** Time pattern for time-based rotation: HOURLY, DAILY, WEEKLY, MONTHLY */
        var timePattern: String = "DAILY"
        
        /** Maximum file size in bytes for size-based rotation (default: 100MB) */
        var maxFileSize: Long = 100 * 1024 * 1024L
        
        /** Maximum number of days to keep history */
        var maxHistoryDays: Int = 30
        
        /** Maximum number of files to keep */
        var maxFiles: Int = 100
        
        /** Total size cap in bytes (default: 10GB) */
        var totalSizeCap: Long = 10L * 1024 * 1024 * 1024
        
        /** Enable compression of rotated files */
        var compress: Boolean = true
        
        /** Compression level (0-9) */
        var compressionLevel: Int = 6
        
        fun toRotationConfig(): RotationConfig {
            val policy = when (policyType.uppercase()) {
                "SIZE" -> RotationPolicy.SizeBased(
                    maxSizeBytes = maxFileSize
                )
                "COMPOSITE" -> RotationPolicy.Composite(
                    timePattern = TimeRotationPattern.entries.find { it.name == timePattern.uppercase() }
                        ?.let { com.lestere.opensource.rotation.TimeBasedTrigger.TimeRotationPattern.valueOf(it.name) }
                        ?: com.lestere.opensource.rotation.TimeBasedTrigger.TimeRotationPattern.DAILY,
                    maxSizeBytes = maxFileSize
                )
                else -> RotationPolicy.TimeBased(
                    pattern = TimeRotationPattern.entries.find { it.name == timePattern.uppercase() }
                        ?.let { com.lestere.opensource.rotation.TimeBasedTrigger.TimeRotationPattern.valueOf(it.name) }
                        ?: com.lestere.opensource.rotation.TimeBasedTrigger.TimeRotationPattern.DAILY
                )
            }
            
            return RotationConfig(
                enabled = enabled,
                policy = policy,
                retention = com.lestere.opensource.rotation.RetentionConfig(
                    maxHistoryDays = maxHistoryDays,
                    maxFiles = maxFiles,
                    totalSizeCapBytes = totalSizeCap
                ),
                compression = com.lestere.opensource.rotation.CompressionConfig(
                    enabled = compress,
                    compressionLevel = compressionLevel
                )
            )
        }
    }

    /**
     * Configuration for log filtering and sampling.
     */
    class FilterConfiguration {
        var enabled: Boolean = true
        var minLevel: SoulLogger.Level = SoulLogger.Level.INFO
        var samplingEnabled: Boolean = false
        var sampleRate: Double = 1.0
        var samplingStrategy: String = "RANDOM"
        var includePatterns: String = "" // comma-separated
        var excludePatterns: String = "" // comma-separated
        var errorBoostEnabled: Boolean = true
        var errorBoostDurationMs: Long = 60_000
        var errorBoostRate: Double = 10.0
        
        fun toFilterConfig(): FilterConfig {
            return FilterConfig(
                enabled = enabled,
                minLevel = minLevel,
                samplingEnabled = samplingEnabled,
                sampleRate = sampleRate,
                samplingStrategy = com.lestere.opensource.filter.RateFilter.SamplingStrategy.valueOf(
                    samplingStrategy.uppercase()
                ),
                includePatterns = includePatterns.split(",").filter { it.isNotBlank() },
                excludePatterns = excludePatterns.split(",").filter { it.isNotBlank() },
                errorBoostEnabled = errorBoostEnabled,
                errorBoostDurationMs = errorBoostDurationMs,
                errorBoostRate = errorBoostRate
            )
        }
    }

    /**
     * Configuration for performance optimization.
     */
    class PerformanceConfiguration {
        // Legacy buffer settings
        var bufferSize: Int = 8 * 1024 // 8KB
        var flushIntervalMs: Long = 1000 // 1 second
        var batchSize: Int = 100
        var immediateFlush: Boolean = false
        var backpressureStrategy: String = "SUSPEND"
        var highWatermark: Int = 8000
        var lowWatermark: Int = 2000
        
        // New performance features
        var bufferType: BufferType = BufferType.MMAP
        
        enum class BufferType {
            LEGACY,
            RING,
            MMAP
        }
        
        // Mmap configuration
        var mmapFileSize: Long = 1024L * 1024L * 1024L  // 1GB
        var mmapRotationEnabled: Boolean = true
        
        // Compression configuration
        var compressionEnabled: Boolean = true
        var compressionType: String = "SNAPPY"  // NONE, LZ4, SNAPPY, ZSTD
        var compressionBatchSize: Int = 1000
        
        // Object pool configuration
        var objectPoolEnabled: Boolean = true
        var objectPoolCapacity: Int = 1000
        
        // GC optimization
        var threadInfoCacheEnabled: Boolean = true
        var longTimestampEnabled: Boolean = false
        
        fun toBackpressureStrategy(): BackpressureStrategy {
            return BackpressureStrategy.valueOf(backpressureStrategy.uppercase())
        }
        
        fun toCompressionType(): CompressionType {
            return CompressionType.fromString(compressionType)
        }
        
        fun toBufferType(): BufferType {
            return try {
                BufferType.valueOf(bufferType.name)
            } catch (_: Exception) {
                BufferType.MMAP
            }
        }
    }
    
    // Runtime management configuration
    class RuntimeManagementConfiguration {
        var dynamicConfigEnabled: Boolean = true
        var hotReloadEnabled: Boolean = true
        var hotReloadStrategy: String = "WATCH"  // POLLING, WATCH
        var hotReloadIntervalMs: Long = 5000
        var runtimeLevelEnabled: Boolean = true
        var allowRemoteLevelUpdate: Boolean = true
    }
    
    // Health check configuration
    class HealthCheckConfiguration {
        var enabled: Boolean = true
        var diskCheckEnabled: Boolean = true
        var queueCheckEnabled: Boolean = true
        var diskSpaceThresholdMb: Long = 1024
        var queueUsageThreshold: Double = 0.8
    }

    companion object {
        private fun detectModeFromEnv(): ApplicationMode {
            return System.getenv("SOUL_LOGGER_MODE")?.let {
                ApplicationMode.fromString(it)
            } ?: ApplicationMode.DEVELOPMENT
        }
    }
}
