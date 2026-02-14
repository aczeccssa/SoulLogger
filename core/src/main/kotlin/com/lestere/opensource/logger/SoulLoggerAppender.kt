package com.lestere.opensource.logger

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.lestere.opensource.LogFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Custom Logback Appender that integrates with SoulLogger's Channel-based architecture.
 *
 * Features:
 * - Maintains existing Channel queue for backpressure handling
 * - Supports both text and JSON output formats
 * - Handles file rotation based on size
 * - Async processing via coroutines
 *
 * @author LesterE
 * @since 1.0.0
 */
class SoulLoggerAppender : AppenderBase<ILoggingEvent>() {

    private var logChannel: Channel<SoulLogEntry>? = null
    private var pluginScope: CoroutineScope? = null
    private var config: SoulLoggerPluginConfiguration? = null
    private var logFormat: LogFormat = LogFormat.PLAIN_TEXT

    /**
     * Internal log entry representation.
     */
    data class SoulLogEntry(
        val timestamp: Long,
        val level: String,
        val loggerName: String,
        val message: String,
        val threadName: String,
        val mdc: Map<String, String>?,
        val throwable: String?
    )

    /**
     * Initialize the appender with SoulLogger configuration.
     */
    fun initialize(
        configuration: SoulLoggerPluginConfiguration,
        channel: Channel<SoulLogEntry>,
        scope: CoroutineScope
    ) {
        this.config = configuration
        this.logChannel = channel
        this.pluginScope = scope
        this.logFormat = configuration.mode.format
    }

    override fun start() {
        super.start()
        // Start processing coroutine
        pluginScope?.launch {
            processLogs()
        }
    }

    override fun stop() {
        super.stop()
        logChannel?.close()
    }

    override fun append(event: ILoggingEvent) {
        if (!isStarted) return

        val entry = SoulLogEntry(
            timestamp = event.timeStamp,
            level = event.level.levelStr,
            loggerName = event.loggerName,
            message = event.formattedMessage,
            threadName = event.threadName,
            mdc = if (event.mdcPropertyMap?.isNotEmpty() == true) event.mdcPropertyMap else null,
            throwable = event.throwableProxy?.stackTraceElementProxyArray?.take(5)
                ?.joinToString("\n") { it.toString() }
        )

        logChannel?.trySend(entry)
    }

    /**
     * Coroutine that processes logs from the channel and writes to file.
     */
    private suspend fun processLogs() {
        val cfg = config ?: return
        val channel = logChannel ?: return

        for (entry in channel) {
            try {
                writeLogEntry(entry, cfg)
            } catch (_: Exception) {
                // Silently ignore write errors in production
            }
        }
    }

    /**
     * Write a log entry to the appropriate output.
     */
    private fun writeLogEntry(entry: SoulLogEntry, cfg: SoulLoggerPluginConfiguration) {
        val line = when (logFormat) {
            LogFormat.JSON -> formatAsJson(entry)
            else -> formatAsText(entry)
        }

        // Write to file if enabled
        if (cfg.mode.enableFile) {
            writeToFile(line, cfg)
        }

        // Write to console if enabled (in dev mode)
        if (cfg.mode.enableConsole) {
            println(line)
        }
    }

    /**
     * Format entry as JSON.
     */
    private fun formatAsJson(entry: SoulLogEntry): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"timestamp\":\"${formatTimestamp(entry.timestamp)}\"")
        sb.append(",\"level\":\"${entry.level}\"")
        sb.append(",\"logger\":\"${escapeJson(entry.loggerName)}\"")
        sb.append(",\"thread\":\"${escapeJson(entry.threadName)}\"")
        sb.append(",\"message\":\"${escapeJson(entry.message)}\"")

        if (!entry.mdc.isNullOrEmpty()) {
            sb.append(",\"context\":{")
            entry.mdc.entries.joinToString(",") { (k, v) ->
                "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
            }.let { sb.append(it) }
            sb.append("}")
        }

        entry.throwable?.let {
            sb.append(",\"exception\":\"${escapeJson(it)}\"")
        }

        sb.append("}")
        return sb.toString()
    }

    /**
     * Format entry as plain text.
     */
    private fun formatAsText(entry: SoulLogEntry): String {
        val sb = StringBuilder()
        sb.append(formatTimestamp(entry.timestamp))
        sb.append(" ${entry.level.padEnd(5)}")
        sb.append(" [${entry.threadName}]")
        sb.append(" ${entry.loggerName}")
        sb.append(" - ${entry.message}")

        entry.throwable?.let {
            sb.append("\n$it")
        }

        return sb.toString()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
        return instant.toString()
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Write log line to file with rotation support.
     */
    private fun writeToFile(line: String, cfg: SoulLoggerPluginConfiguration) {
        val filePath = getCurrentLogFile(cfg)
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)

        Files.write(
            filePath,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    private var currentTimestamp: Long = Clock.System.now().toEpochMilliseconds()

    private fun getCurrentLogFile(cfg: SoulLoggerPluginConfiguration): Path {
        val logDir = cfg.cacheRootDictionary.let {
            if (it.endsWith("/")) "${it}log/" else "$it/log/"
        }

        val fileName = "$currentTimestamp.log"
        val path = Paths.get(logDir, fileName)

        // Check if file exists and exceeds max size
        if (Files.exists(path)) {
            val size = Files.size(path)
            if (size > cfg.maxFileSize) {
                // Rotate to new file
                currentTimestamp = Clock.System.now().toEpochMilliseconds()
                val newPath = Paths.get(logDir, "$currentTimestamp.log")
                Files.createDirectories(newPath.parent)
                if (!Files.exists(newPath)) {
                    Files.createFile(newPath)
                }
                return newPath
            }
        } else {
            Files.createDirectories(path.parent)
            Files.createFile(path)
        }

        return path
    }
}
