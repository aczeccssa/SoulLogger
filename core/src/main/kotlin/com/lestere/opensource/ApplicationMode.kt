package com.lestere.opensource

import com.lestere.opensource.logger.SoulLogger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Log output format options.
 */
enum class LogFormat {
    /** Plain text format with color codes for console */
    COLORFUL_TEXT,
    /** Plain text format without colors */
    PLAIN_TEXT,
    /** Structured JSON format */
    JSON
}

/**
 * Stack trace display mode.
 */
enum class StackTraceMode {
    /** Full stack trace with all frames */
    FULL,
    /** Compact stack trace (first 5 frames only) */
    COMPACT,
    /** No stack trace */
    NONE
}

/**
 * Represents the application runtime mode with distinct behaviors for development and production.
 *
 * @property defaultLevel Default minimum log level for this mode
 * @property enableConsole Whether to output logs to console
 * @property enableFile Whether to output logs to file
 * @property stackTraceMode How to display stack traces
 * @property enableIntrospection Whether to enable introspection routes (analysis, reflex)
 * @property enableMasking Whether to mask sensitive data
 * @property format Log output format
 * @property defaultQueueCapacity Default async queue capacity
 * @property enableLogback Whether to use Logback for output
 *
 * @author LesterE
 * @since 1.0.0
 */
@Serializable
enum class ApplicationMode {

    @SerialName("development")
    DEVELOPMENT {
        override val defaultLevel = SoulLogger.Level.DEBUG
        override val enableConsole = true
        override val enableFile = true
        override val stackTraceMode = StackTraceMode.FULL
        override val enableIntrospection = true
        override val enableMasking = false
        override val format = LogFormat.COLORFUL_TEXT
        override val defaultQueueCapacity = 10_000
        override val enableLogback = true
        override val logbackConsolePattern =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n"
    },

    @SerialName("production")
    PRODUCTION {
        override val defaultLevel = SoulLogger.Level.INFO
        override val enableConsole = false
        override val enableFile = true
        override val stackTraceMode = StackTraceMode.COMPACT
        override val enableIntrospection = false
        override val enableMasking = true
        override val format = LogFormat.JSON
        override val defaultQueueCapacity = 100_000
        override val enableLogback = true
        override val logbackConsolePattern =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"
    };

    abstract val defaultLevel: SoulLogger.Level
    abstract val enableConsole: Boolean
    abstract val enableFile: Boolean
    abstract val stackTraceMode: StackTraceMode
    abstract val enableIntrospection: Boolean
    abstract val enableMasking: Boolean
    abstract val format: LogFormat
    abstract val defaultQueueCapacity: Int
    abstract val enableLogback: Boolean
    abstract val logbackConsolePattern: String

    companion object {
        /**
         * Parse mode from string (case-insensitive).
         */
        fun fromString(value: String): ApplicationMode = when (value.lowercase()) {
            "development", "dev" -> DEVELOPMENT
            "production", "prod" -> PRODUCTION
            else -> DEVELOPMENT
        }
    }
}
