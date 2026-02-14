package com.lestere.opensource.logger

import com.lestere.opensource.utils.ISO_ZERO
import com.lestere.opensource.utils.VERSION
import com.lestere.opensource.utils.toMultiStackString
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * The builder for the logger.
 */
class SoulLogger private constructor(logLevel: Level, thread: ThreadInfo, entry: String, command: Any) {
    companion object {

        /**
         * The running status of [SoulLoggerProvider].
         */
        val active: Boolean
            get() = SoulLoggerProvider.active

        /**
         * Public log stream for external consumers.
         *
         * This flow emits all logs that pass the stream configuration filter.
         * Features:
         * - Supports multiple subscribers
         * - Configurable replay buffer (default: 100 logs)
         * - Automatic level filtering
         * - Runs on Dispatchers.Default
         *
         * Usage:
         * ```kotlin
         * // Basic subscription
         * SoulLogger.logStream.collect { log ->
         *     println("[${log.logLevel}] ${log.command}")
         * }
         *
         * // Filtered subscription
         * SoulLogger.logStream
         *     .filter { it.logLevel == SoulLogger.Level.ERROR }
         *     .onEach { sendAlert(it) }
         *     .launchIn(scope)
         *
         * // SSE implementation
         * get("/logs/stream") {
         *     call.respondTextWriter(ContentType.Text.EventStream) {
         *         SoulLogger.logStream.collect { log ->
         *             write("data: ${Json.encodeToString(log)}\n\n")
         *             flush()
         *         }
         *     }
         * }
         * ```
         */
        val logStream
            get() = SoulLoggerProvider.logStream

        /**
         * Logger builder for debug logs.
         * @param command [Any] The command that was executed.
         * @param thread [Thread] Call thread, default is current thread.
         */
        fun debug(command: Any, thread: Thread = Thread.currentThread()): Unit = create(Level.DEBUG, command, thread)

        /**
         * Logger builder for information logs.
         * @param command [Any] The command that was executed.
         * @param thread [Thread] Call thread, default is current thread.
         */
        fun info(command: Any, thread: Thread = Thread.currentThread()): Unit = create(Level.INFO, command, thread)

        /**
         * Logger builder for warning logs.
         *
         * - Call `toMultiStackString()` method when command is an `Exception`.
         * @param thread [Thread] Call thread, default is current thread.
         * @param command [Any] The command that was executed.
         */
        fun warn(command: Any, thread: Thread = Thread.currentThread()): Unit = create(Level.WARN, command, thread)

        /**
         * Logger builder for error logs.
         * @param command [Any] The command that was executed.
         * @param thread [Thread] Call thread, default is current thread.
         */
        fun error(command: Throwable, thread: Thread = Thread.currentThread()): Throwable {
            create(Level.ERROR, command.toMultiStackString(), thread)
            return command
        }

        /**
         * Logger builder for fatal error logs.
         * @param command [Exception] The exception to log.
         * @param thread [Thread] Call thread, default is current thread.
         */
        fun fatal(command: Exception, thread: Thread = Thread.currentThread()): Exception {
            create(Level.FATAL, command, thread)
            return command
        }

        /**
         * Create a log.
         * @param level [Level] The log level of this log.
         * @param command [Any] The command that was executed.
         * @param thread [Thread] Call thread, default is current thread.
         */
        fun create(
            level: Level,
            command: Any,
            thread: Thread = Thread.currentThread()
        ) = SoulLogger(
            logLevel = level,
            thread = ThreadInfo(thread),
            entry = thread.stackTrace[4].className,
            command = command
        ).build()
    }

    private val data = Logger(VERSION, Clock.System.now(), logLevel, thread, entry, command.toString())

    /**
     * Log the data.
     */
    private fun build() {
        SoulLoggerProvider.log(data)
    }

    /**
     * The log level for soul logger.
     */
    enum class Level : Comparable<Level> {
        /**
         * Debug log level, use to express the debug information.
         */
        DEBUG,

        /**
         * Info log level, use to express the information.
         */
        INFO,

        /**
         * Warn log level, use to express the warning information, might have some exception.
         */
        WARN,

        /**
         * Error log level, use to express the error information, always with exception or error.
         */
        ERROR,

        /**
         * Fatal log level, use to express the fatal information, and the program will exit.
         */
        FATAL;

        companion object {
            fun of(status: HttpStatusCode): Level = when (status.value) {
                in 100..199 -> INFO
                in 200..299 -> INFO
                in 300..399 -> WARN
                in 400..499 -> ERROR
                in 500..599 -> ERROR
                else -> WARN
            }
        }
    }
}

/**
 * Thread basic information of soul logger.
 */
@Serializable
data class ThreadInfo(val id: Long, val name: String) {
    constructor(thread: Thread) : this(thread.threadId(), thread.name)

    override fun toString(): String = "$name($id)"
}

/**
 * The logger model.
 */
@Serializable
data class Logger(
    val version: String,
    val timestamp: Instant,
    val logLevel: SoulLogger.Level,
    val thread: ThreadInfo,
    val entry: String,
    val command: String
) {
    /**
     * To output string.
     * @Mark format: ^(?P<version>[a-zA-Z0-9.-]+)\\s+(?P<timestamp>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z)\\s+(?P<logLevel>DEBUG|INFO|WARN|ERROR|FATAL)\\s+(?P<thread>.+\\(\\d+\\))\\s+\\[(?P<entry>.+)\\]\\s+(?P<command>.+)\$
     */
    override fun toString(): String = "$version $timestamp $logLevel $thread [$entry] $command"

    companion object {
        /**
         * Regex pattern for parsing log lines.
         * Format: version timestamp level thread [entry] command
         * Example: 0.0.1-dev 2024-01-15T10:30:45.123456Z INFO Thread-1(123) [com.example.Main] Operation completed
         */
        private val LOG_PATTERN = Regex(
            """^(?<version>[a-zA-Z0-9.-]+)\s+(?<timestamp>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z)\s+(?<level>DEBUG|INFO|WARN|ERROR|FATAL)\s+(?<thread>.+\(\d+\))\s+\[(?<entry>.+)\]\s+(?<command>.+)$"""
        )

        /**
         * Parse a log string into a Logger object.
         * @param input The log string to parse.
         * @return Logger object if parsing succeeds, null otherwise.
         */
        fun parse(input: String): Logger? {
            val match = LOG_PATTERN.matchEntire(input.trim()) ?: return null

            val timestampValue = match.groups["timestamp"]?.value?.let { tsStr ->
                try {
                    kotlinx.datetime.Instant.parse(tsStr)
                } catch (_: Exception) {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(0)
                }
            } ?: kotlinx.datetime.Instant.fromEpochMilliseconds(0)

            return Logger(
                version = match.groups["version"]?.value ?: VERSION,
                timestamp = timestampValue,
                logLevel = match.groups["level"]?.value?.let {
                    try {
                        SoulLogger.Level.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        SoulLogger.Level.WARN
                    }
                } ?: SoulLogger.Level.WARN,
                thread = parseThreadInfo(match.groups["thread"]?.value ?: "unknown(-1)"),
                entry = match.groups["entry"]?.value ?: "unknown",
                command = match.groups["command"]?.value ?: ""
            )
        }

        /**
         * Parse thread information from string format "name(id)".
         */
        private fun parseThreadInfo(threadStr: String): ThreadInfo {
            val match = Regex("""^(.+)\((\d+)\)$""").matchEntire(threadStr.trim())
            return if (match != null) {
                ThreadInfo(
                    id = match.groups[2]?.value?.toLongOrNull() ?: -1,
                    name = match.groups[1]?.value ?: threadStr
                )
            } else {
                ThreadInfo(-1, threadStr)
            }
        }

        /**
         * Parse a log string using split method (ktor-plugin compatible).
         * This method exists for backward compatibility with ktor-plugin log format.
         * @param input The log string to parse.
         * @return Logger object. Uses defaults for missing/invalid parts.
         */
        fun parseUsingSplit(input: String): Logger {
            val parts = input.split(" ", "(", ")", "[", "]", ": ")

            return Logger(
                version = parts.getOrNull(0) ?: VERSION,
                timestamp = parts
                    .getOrNull(1)
                    ?.let { 
                        try {
                            Instant.parse(it)
                        } catch (_: Exception) {
                            Clock.ISO_ZERO
                        }
                    }
                    ?: Clock.ISO_ZERO,
                logLevel = parts
                    .getOrNull(2)
                    ?.let { 
                        try {
                            SoulLogger.Level.valueOf(it.uppercase())
                        } catch (_: Exception) {
                            SoulLogger.Level.WARN
                        }
                    }
                    ?: SoulLogger.Level.WARN,
                thread = ThreadInfo(
                    id = parts.getOrNull(4)?.toLongOrNull() ?: -1,
                    name = parts.getOrNull(3) ?: "unknown thread"
                ),
                entry = parts.getOrNull(7) ?: "unknown entry",
                command = parts.subList(8, parts.size).joinToString(" ")
            )
        }

        val CSV_HEADER = arrayOf("Version", "Timestamp", "Level", "Thread", "Entry", "Command")
    }

    val paramsArray get() = arrayOf(version, timestamp.toString(), logLevel.name, thread.toString(), entry, command)
}