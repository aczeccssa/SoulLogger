package com.lestere.opensource.logger

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import org.slf4j.LoggerFactory

/**
 * Factory for configuring Logback based on SoulLogger configuration.
 *
 * @author LesterE
 * @since 1.0.0
 */
object LogbackConfigurator {

    /**
     * Configure Logback based on SoulLogger configuration.
     */
    fun configure(config: SoulLoggerPluginConfiguration) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()

        val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        rootLogger.level = convertLevel(config.level)

        // Configure console appender for development
        if (config.mode.enableConsole) {
            val consoleAppender = createConsoleAppender(context, config)
            rootLogger.addAppender(consoleAppender)
        }

        // Configure file appender if enabled
        if (config.mode.enableFile) {
            val fileAppender = createFileAppender(context, config)
            rootLogger.addAppender(fileAppender)
        }
    }

    /**
     * Create console appender with mode-specific pattern.
     */
    private fun createConsoleAppender(
        context: LoggerContext,
        config: SoulLoggerPluginConfiguration
    ): ConsoleAppender<ILoggingEvent> {
        val appender = ConsoleAppender<ILoggingEvent>()
        appender.context = context
        appender.name = "SOUL_CONSOLE"

        val encoder = PatternLayoutEncoder()
        encoder.context = context
        encoder.pattern = config.mode.logbackConsolePattern
        encoder.start()

        appender.encoder = encoder
        appender.start()

        return appender
    }

    /**
     * Create file appender for log output.
     */
    private fun createFileAppender(
        context: LoggerContext,
        config: SoulLoggerPluginConfiguration
    ): FileAppender<ILoggingEvent> {
        val appender = FileAppender<ILoggingEvent>()
        appender.context = context
        appender.name = "SOUL_FILE"

        val logDir = config.cacheRootDictionary.let {
            if (it.endsWith("/")) "${it}log/" else "$it/log/"
        }
        appender.file = "$logDir/application.log"

        val encoder = PatternLayoutEncoder()
        encoder.context = context
        encoder.pattern = if (config.mode.format.name.contains("JSON")) {
            // JSON format pattern (simplified)
            "{\"timestamp\":\"%d{yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}\",\"level\":\"%-5level\",\"logger\":\"%logger{36}\",\"thread\":\"%thread\",\"message\":\"%msg\"}%n"
        } else {
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"
        }
        encoder.start()

        appender.encoder = encoder
        appender.start()

        return appender
    }

    /**
     * Convert SoulLogger.Level to Logback Level.
     */
    private fun convertLevel(level: SoulLogger.Level): Level = when (level) {
        SoulLogger.Level.DEBUG -> Level.DEBUG
        SoulLogger.Level.INFO -> Level.INFO
        SoulLogger.Level.WARN -> Level.WARN
        SoulLogger.Level.ERROR -> Level.ERROR
        SoulLogger.Level.FATAL -> Level.ERROR
    }

    /**
     * Get or create a logger for the specified class.
     */
    fun getLogger(clazz: Class<*>): org.slf4j.Logger {
        return LoggerFactory.getLogger(clazz)
    }

    /**
     * Get logger by name.
     */
    fun getLogger(name: String): org.slf4j.Logger {
        return LoggerFactory.getLogger(name)
    }
}
