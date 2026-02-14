package com.lestere.opensource.logger

import com.lestere.opensource.context.MDC
import com.lestere.opensource.filter.FilterChain
import com.lestere.opensource.filter.FilterConfig
import com.lestere.opensource.logger.events.LogEvent
import com.lestere.opensource.logger.events.LogEventBus
import com.lestere.opensource.performance.BackpressureController
import com.lestere.opensource.performance.BackpressureStrategy
import com.lestere.opensource.performance.LogBuffer
import com.lestere.opensource.rotation.RotationManager
import com.lestere.opensource.utils.toMultiStackString
import com.lestere.opensource.utils.toReadableString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.system.exitProcess

/**
 * Logs all the logs in the queue, main function is saved the logs to file system.
 * Now with Logback integration, structured logging support, and masking capabilities.
 * Includes enterprise features: rotation, filtering, sampling, and performance optimization.
 *
 * @author Lester E
 * @since 0.1.7
 */
object SoulLoggerProvider {
    private var _active: Boolean = false

    val active: Boolean get() = _active

    private var PLUGIN_CONFIG: SoulLoggerPluginConfiguration = SoulLoggerPluginConfiguration()

    private const val LOGGER_RELEASE_POOL_TIME: Long = 1000 * 60 // 1 minute

    private const val LOGGER_OBSERVER_PRE_PROCESSING_TIME: Long = 100 // 0.1 second

    private val MAX_LOG_FILE_SIZE: Long get() = PLUGIN_CONFIG.maxFileSize

    private val rootDictionary: String
        get() = PLUGIN_CONFIG.logDictionary
            .let { if (it.endsWith("/")) it else "$it/" }

    private var timestamp: Long = Clock.System.now().toEpochMilliseconds()

    private val path get() = "${rootDictionary}${timestamp}.log"

    // ==================== Enterprise Features ====================
    
    /** Log rotation manager for enterprise rotation policies */
    private var rotationManager: RotationManager? = null
    
    /** Filter chain for sampling and filtering */
    private var filterChain: FilterChain? = null
    
    /** Buffered writer for performance optimization */
    private var logBuffer: LogBuffer? = null
    
    /** Backpressure controller for queue management */
    private var backpressureController: BackpressureController? = null
    
    /** Batch write buffer */
    private val batchBuffer = mutableListOf<String>()
    
    /** Whether to use new rotation system */
    private val useRotation: Boolean
        get() = PLUGIN_CONFIG.rotation.enabled && rotationManager != null

    /**
     * Structured scope for all plugin coroutines.
     * Uses SupervisorJob to prevent one failing coroutine from cancelling others.
     */
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Channel for thread-safe log queue.
     * Uses capacity based on mode configuration.
     */
    private val logChannel: Channel<Logger> by lazy {
        Channel<Logger>(PLUGIN_CONFIG.queueCapacity)
    }

    /**
     * Logback appender channel for structured output.
     */
    internal val logbackChannel: Channel<SoulLoggerAppender.SoulLogEntry> by lazy {
        Channel<SoulLoggerAppender.SoulLogEntry>(PLUGIN_CONFIG.queueCapacity)
    }

    /**
     * The list to record the logs that have been processed.
     */
    private val processedLogs: MutableList<Logger> = mutableListOf()

    /**
     * Internal mutable stream for log broadcasting.
     */
    private val _logStream: MutableSharedFlow<Logger> by lazy {
        MutableSharedFlow<Logger>(
            replay = PLUGIN_CONFIG.stream.replay,
            extraBufferCapacity = PLUGIN_CONFIG.stream.replay
        )
    }

    /**
     * Public log stream for external consumers.
     * Uses shareIn to create a hot shared flow that replays the specified number of items.
     */
    val logStream: Flow<Logger> by lazy {
        _logStream
            .filter { it.logLevel >= PLUGIN_CONFIG.stream.minLevel }
            .flowOn(Dispatchers.Default)
            .shareIn(
                scope = pluginScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = PLUGIN_CONFIG.stream.replay
            )
    }

    /**
     * Adds a new log to the channel.
     * @param input [Logger] The logger instance to log.
     */
    internal fun log(input: Logger) {
        logChannel.trySend(input)
    }

    /**
     * Description: Logs all the logs in the queue to file system.
     */
    private fun launchLogoutSystem() {
        pluginScope.launch {
            for (log in logChannel) {
                processLog(log)
            }
        }
    }

    /**
     * Automatically recycle the system every specified time.
     */
    private fun launchAutoRecycleSystem() {
        pluginScope.launch {
            while (isActive) {
                synchronized(processedLogs) {
                    processedLogs.clear()
                }
                delay(LOGGER_RELEASE_POOL_TIME)
            }
        }
    }

    /**
     * Launch Logback integration if enabled.
     */
    private fun launchLogbackSystem() {
        if (!PLUGIN_CONFIG.enableLogback) return

        // Configure Logback
        LogbackConfigurator.configure(PLUGIN_CONFIG)

        // Launch Logback appender processing
        pluginScope.launch {
            for (entry in logbackChannel) {
                writeLogbackEntry(entry)
            }
        }
    }

    /**
     * Process logger output method and reduce queue.
     * @param input [Logger] The logger instance to process.
     */
    private fun processLog(input: Logger) {
        // Apply filter chain (level, sampling, regex)
        val filter = filterChain
        if (filter != null) {
            if (!filter.shouldLog(input)) return
            if (!filter.shouldSample(input)) return
        }
        
        // Apply legacy level filter
        if (!validatePass(input)) return

        // Apply masking if enabled
        val maskedInput = if (PLUGIN_CONFIG.enableMasking) {
            applyMasking(input)
        } else {
            input
        }

        // Write using new rotation system
        if (useRotation) {
            writeWithRotation(maskedInput)
        } else {
            // Legacy write mode
            writeLegacy(maskedInput)
        }

        // Console output
        if (PLUGIN_CONFIG.enableConsole && !PLUGIN_CONFIG.enableLogback) {
            println(maskedInput.toString())
        }

        // Add to processed logs
        synchronized(processedLogs) {
            processedLogs.add(maskedInput)
        }

        // Broadcast to stream if enabled
        if (PLUGIN_CONFIG.stream.enabled) {
            _logStream.tryEmit(maskedInput)
        }

        // Send to Logback channel if enabled
        if (PLUGIN_CONFIG.enableLogback) {
            val entry = SoulLoggerAppender.SoulLogEntry(
                timestamp = input.timestamp.toEpochMilliseconds(),
                level = input.logLevel.name,
                loggerName = input.entry,
                message = input.command,
                threadName = input.thread.name,
                mdc = MDC.getContextMap(),
                throwable = null
            )
            logbackChannel.trySend(entry)
        }
    }
    
    /**
     * Write log using enterprise rotation system with buffering.
     */
    private fun writeWithRotation(maskedInput: Logger) {
        val manager = rotationManager ?: return
        val filePath = manager.getCurrentFile()
        
        val logLine = maskedInput.toString() + "\n"
        
        // Use buffered write
        val buffer = logBuffer
        if (buffer != null && buffer.isOpen()) {
            pluginScope.launch {
                try {
                    buffer.write(logLine)
                    manager.incrementSize(logLine.toByteArray().size.toLong())
                } catch (e: Exception) {
                    handleWriteError(e)
                }
            }
        } else {
            // Fallback to legacy write
            pluginScope.launch {
                try {
                    val data = logLine.toByteArray()
                    Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                } catch (e: Exception) {
                    handleWriteError(e)
                }
            }
        }
    }
    
    /**
     * Handle write errors based on mode.
     */
    private fun handleWriteError(e: Exception) {
        if (PLUGIN_CONFIG.mode == com.lestere.opensource.ApplicationMode.DEVELOPMENT) {
            println(e.toMultiStackString())
        }
    }
    
    /**
     * Legacy write method without rotation.
     */
    private fun writeLegacy(maskedInput: Logger) {
        val filePath = getFilePath() ?: return
        
        if (!PLUGIN_CONFIG.enableLogback || !PLUGIN_CONFIG.enableFile) {
            pluginScope.launch {
                try {
                    val data = (maskedInput.toString() + "\n").toByteArray()
                    Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                } catch (e: Exception) {
                    handleWriteError(e)
                }
            }
        }
    }

    /**
     * Initialize enterprise features: rotation, filtering, and performance optimization.
     */
    private fun initializeEnterpriseFeatures() {
        // Initialize rotation manager if enabled
        if (PLUGIN_CONFIG.rotation.enabled) {
            val rotationConfig = PLUGIN_CONFIG.rotation.toRotationConfig()
            val logDir = Paths.get(rootDictionary)
            rotationManager = RotationManager(
                policy = rotationConfig.policy,
                retentionConfig = rotationConfig.retention,
                compressionConfig = rotationConfig.compression,
                logDirectory = logDir
            )
        }
        
        // Initialize filter chain
        if (PLUGIN_CONFIG.logFilter.enabled) {
            filterChain = PLUGIN_CONFIG.logFilter.toFilterConfig().toFilterChain()
        }
        
        // Initialize buffer for performance
        if (PLUGIN_CONFIG.performance.bufferSize > 0 && rotationManager != null) {
            val initialPath = rotationManager!!.getCurrentFile()
            logBuffer = LogBuffer(
                filePath = initialPath,
                bufferSize = PLUGIN_CONFIG.performance.bufferSize,
                flushInterval = PLUGIN_CONFIG.performance.flushIntervalMs,
                autoFlush = PLUGIN_CONFIG.performance.immediateFlush
            )
        }
        
        // Initialize backpressure controller
        backpressureController = BackpressureController(
            strategy = PLUGIN_CONFIG.performance.toBackpressureStrategy(),
            highWatermark = PLUGIN_CONFIG.performance.highWatermark,
            lowWatermark = PLUGIN_CONFIG.performance.lowWatermark
        )
    }

    /**
     * Apply masking to log command if it contains sensitive data.
     */
    private fun applyMasking(input: Logger): Logger {
        val maskedCommand = PLUGIN_CONFIG.maskingStrategy.maskMessage(input.command)
        return if (maskedCommand != input.command) {
            input.copy(command = maskedCommand)
        } else {
            input
        }
    }

    /**
     * Write entry via Logback.
     */
    private suspend fun writeLogbackEntry(entry: SoulLoggerAppender.SoulLogEntry) {
        // This is handled by Logback appenders
        // The channel is used for coordination with SoulLogger's file rotation
    }

    /**
     * Get the current log file path.
     */
    private fun getFilePath(): Path? {
        val logPath = Paths.get(this.path)

        if (!Files.exists(logPath)) {
            return createLogFileIfNotExists()
        }

        val size = Files.size(logPath)
        if (size > MAX_LOG_FILE_SIZE) {
            return createLogFileIfNotExists()
        }

        return logPath
    }

    /**
     * Create log file if the file not exists.
     */
    private fun createLogFileIfNotExists(): Path? {
        try {
            val prePath = this.path
            this.timestamp = Clock.System.now().toEpochMilliseconds()
            val newPath = Paths.get(this.path)
            if (!Files.exists(newPath)) Files.createDirectories(newPath.parent)
            if (!Files.exists(newPath)) Files.createFile(newPath)

            if (Files.exists(newPath) && this._active && PLUGIN_CONFIG.analysis.analysisLog) {
                pluginScope.launch {
                    LogEventBus.post(LogEvent.LogFileCreated(prePath, newPath.toString()))
                }
            }
            return newPath
        } catch (e: Exception) {
            if (PLUGIN_CONFIG.mode == com.lestere.opensource.ApplicationMode.DEVELOPMENT) {
                println("Failed to create log file: $path.")
                println(e)
            }
            return null
        }
    }

    /**
     * Log system init and start method.
     */
    fun launch(config: SoulLoggerPluginConfiguration) {
        this.PLUGIN_CONFIG = config
        try {
            // Initialize enterprise features (rotation, filtering, performance)
            initializeEnterpriseFeatures()

            // Initialize log stream if enabled
            if (config.stream.enabled) {
                @Suppress("UNUSED_EXPRESSION")
                logStream
            }

            // Configure Logback if enabled
            if (config.enableLogback) {
                launchLogbackSystem()
            }

            // Prepare legacy file system (if not using rotation)
            if (!useRotation) {
                createLogFileIfNotExists()
            }

            // Launch systems
            launchLogoutSystem()
            launchAutoRecycleSystem()

            // Register thread shutdown job
            registerShutdownHook()

            // Provider launch
            _active = true

            // Log startup message with mode info
            val modeMsg = "SoulLogger initialized in ${config.mode.name} mode (Level: ${config.level}, Logback: ${config.enableLogback})"
            SoulLogger.info(modeMsg)
        } catch (e: Exception) {
            println("Failed to launch logger system.")
            SoulLogger.fatal(e)
            exitProcess(-1)
        }
    }

    /**
     * Stop the logger system and cleanup resources.
     */
    fun stop() {
        _active = false
        
        // Flush and close buffer
        runCatching {
            runBlocking {
                logBuffer?.flush()
                logBuffer?.close()
            }
        }
        
        logChannel.close()
        logbackChannel.close()
        pluginScope.cancel()
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                // Stop accepting new logs first
                _active = false
                
                // Flush and close buffer
                runCatching {
                    runBlocking {
                        logBuffer?.flush()
                        logBuffer?.close()
                    }
                }
                
                // Close channels gracefully
                runCatching { logChannel.close() }
                runCatching { logbackChannel.close() }
                
                // Cancel coroutine scope
                runCatching { 
                    runBlocking { 
                        pluginScope.cancel() 
                    } 
                }
                
                // Generate analysis report on shutdown
                val logPath = if (useRotation) {
                    rotationManager?.getCurrentFile()?.toString() ?: this.path
                } else {
                    this.path
                }
                runCatching {
                    SoulLoggerAnalyzer.generateReport(logPath, PLUGIN_CONFIG.analysisOutputDictionary)
                }
                
                // Post shutdown event
                runCatching {
                    runBlocking {
                        LogEventBus.post(LogEvent.ApplicationShutdown(logPath))
                    }
                }
            } catch (_: Throwable) {
                // Suppress all exceptions during shutdown
            }
        })
    }

    private fun validatePass(input: Logger): Boolean {
        return input.logLevel >= PLUGIN_CONFIG.level
    }
    
    // ==================== Runtime Management APIs ====================
    
    fun getBuffer(): Any? = logBuffer
    
    fun getQueueSize(): Int {
        return try {
            logChannel.isEmpty.takeIf { !it }?.let { 1 } ?: 0
        } catch (_: Exception) {
            0
        }
    }
    
    fun getRotationManager(): RotationManager? = rotationManager
    
    fun getRemoteSink(): Any? = null  // Remote sink integration point
    
    fun getConfig(): SoulLoggerPluginConfiguration = PLUGIN_CONFIG
}
