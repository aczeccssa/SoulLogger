package com.lestere.opensource.config

import com.lestere.opensource.ApplicationMode
import com.lestere.opensource.logger.SoulLogger

/**
 * Loads configuration from environment variables with SOUL_LOGGER_ prefix.
 *
 * Supported environment variables:
 * - SOUL_LOGGER_MODE: Application mode (development/production)
 * - SOUL_LOGGER_LEVEL: Default log level (DEBUG/INFO/WARN/ERROR/FATAL)
 * - SOUL_LOGGER_PATH: Log file output directory
 * - SOUL_LOGGER_MAX_FILE_SIZE: Maximum log file size in bytes (e.g., 10485760 for 10MB)
 * - SOUL_LOGGER_QUEUE_CAPACITY: Async queue capacity (default: mode-specific)
 * - SOUL_LOGGER_ENABLE_CONSOLE: Enable console output (true/false)
 * - SOUL_LOGGER_ENABLE_FILE: Enable file output (true/false)
 * - SOUL_LOGGER_FORMAT: Log format (colorful_text/plain_text/json)
 * - SOUL_LOGGER_ENABLE_MASKING: Enable sensitive data masking (true/false)
 * - SOUL_LOGGER_ENABLE_INTROSPECTION: Enable introspection routes (true/false)
 * - SOUL_LOGGER_ENABLE_LOGBACK: Use Logback for output (true/false)
 * - SOUL_LOGGER_LOGBACK_CONFIG_PATH: Custom Logback configuration file path
 *
 * @author LesterE
 * @since 1.0.0
 */
object EnvConfigLoader {

    private const val PREFIX = "SOUL_LOGGER_"

    /**
     * Environment-based configuration that can override mode defaults.
     */
    data class EnvConfig(
        val mode: ApplicationMode? = null,
        val level: SoulLogger.Level? = null,
        val path: String? = null,
        val maxFileSize: Long? = null,
        val queueCapacity: Int? = null,
        val enableConsole: Boolean? = null,
        val enableFile: Boolean? = null,
        val format: String? = null,
        val enableMasking: Boolean? = null,
        val enableIntrospection: Boolean? = null,
        val enableLogback: Boolean? = null,
        val logbackConfigPath: String? = null
    )

    /**
     * Load configuration from environment variables.
     */
    fun load(): EnvConfig = EnvConfig(
        mode = getString("MODE")?.let { ApplicationMode.fromString(it) },
        level = getString("LEVEL")?.let { parseLevel(it) },
        path = getString("PATH"),
        maxFileSize = getLong("MAX_FILE_SIZE"),
        queueCapacity = getInt("QUEUE_CAPACITY"),
        enableConsole = getBoolean("ENABLE_CONSOLE"),
        enableFile = getBoolean("ENABLE_FILE"),
        format = getString("FORMAT"),
        enableMasking = getBoolean("ENABLE_MASKING"),
        enableIntrospection = getBoolean("ENABLE_INTROSPECTION"),
        enableLogback = getBoolean("ENABLE_LOGBACK"),
        logbackConfigPath = getString("LOGBACK_CONFIG_PATH")
    )

    /**
     * Merge environment config with mode defaults.
     * Environment variables take precedence over mode defaults.
     */
    fun mergeWithModeDefaults(mode: ApplicationMode, envConfig: EnvConfig): MergedConfig {
        return MergedConfig(
            mode = mode,
            level = envConfig.level ?: mode.defaultLevel,
            path = envConfig.path ?: "./logs",
            maxFileSize = envConfig.maxFileSize ?: (2L * 1024 * 1024), // 2MB default
            queueCapacity = envConfig.queueCapacity ?: mode.defaultQueueCapacity,
            enableConsole = envConfig.enableConsole ?: mode.enableConsole,
            enableFile = envConfig.enableFile ?: mode.enableFile,
            format = envConfig.format ?: mode.format.name.lowercase(),
            enableMasking = envConfig.enableMasking ?: mode.enableMasking,
            enableIntrospection = envConfig.enableIntrospection ?: mode.enableIntrospection,
            enableLogback = envConfig.enableLogback ?: mode.enableLogback,
            logbackConfigPath = envConfig.logbackConfigPath
        )
    }

    /**
     * Merged configuration combining mode defaults and environment overrides.
     */
    data class MergedConfig(
        val mode: ApplicationMode,
        val level: SoulLogger.Level,
        val path: String,
        val maxFileSize: Long,
        val queueCapacity: Int,
        val enableConsole: Boolean,
        val enableFile: Boolean,
        val format: String,
        val enableMasking: Boolean,
        val enableIntrospection: Boolean,
        val enableLogback: Boolean,
        val logbackConfigPath: String?
    )

    private fun getString(name: String): String? =
        System.getenv("${PREFIX}${name}")?.takeIf { it.isNotBlank() }

    private fun getBoolean(name: String): Boolean? =
        getString(name)?.lowercase()?.toBooleanStrictOrNull()

    private fun getInt(name: String): Int? =
        getString(name)?.toIntOrNull()

    private fun getLong(name: String): Long? =
        getString(name)?.toLongOrNull()

    private fun parseLevel(value: String): SoulLogger.Level? = try {
        SoulLogger.Level.valueOf(value.uppercase())
    } catch (_: IllegalArgumentException) {
        null
    }
}
