package com.lestere.opensource.logger

import com.lestere.opensource.arc.AutoReleaseCyberPlugin
import com.lestere.opensource.config.DynamicConfigManager
import com.lestere.opensource.config.HotReloader
import com.lestere.opensource.config.RuntimeLevelManager
import com.lestere.opensource.health.HealthCheckManager
import com.lestere.opensource.logger.routes.*
import com.lestere.opensource.utils.toReadableString
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import kotlinx.datetime.Clock
import java.nio.file.Paths

/**
 * Ktor plugin for SoulLogger with Mode-aware configuration.
 *
 * Features:
 * - Automatically configures based on DEVELOPMENT or PRODUCTION mode
 * - Respects environment variables (SOUL_LOGGER_*)
 * - Enables/disables introspection routes based on mode
 * - Integrates with Logback when enabled
 * - Supports structured logging with MDC
 * - Dynamic configuration with hot reload
 * - Runtime log level adjustment
 * - Health check endpoints
 *
 * Installation:
 * ```kotlin
 * // Development mode (default)
 * install(SoulLoggerPlugin) {
 *     mode = ApplicationMode.DEVELOPMENT
 * }
 *
 * // Production mode
 * install(SoulLoggerPlugin) {
 *     mode = ApplicationMode.PRODUCTION
 * }
 *
 * // Or via environment variable: SOUL_LOGGER_MODE=production
 * ```
 *
 * @author LesterE
 * @since 0.1.0
 */
val SoulLoggerPlugin = createApplicationPlugin("SoulLoggerPlugin", ::SoulLoggerPluginConfiguration) {

    // Load configuration from application.conf automatically
    pluginConfig.loadFromConfig(environment.config)

    // Initialize runtime management components
    val dynamicConfigManager = DynamicConfigManager(pluginConfig)
    val runtimeLevelManager = RuntimeLevelManager()
    val healthCheckManager = HealthCheckManager(pluginConfig)
    var hotReloader: HotReloader? = null

    on(MonitoringEvent(ApplicationStarted)) { app ->
        // Launch soul logger system with configuration
        SoulLoggerProvider.launch(pluginConfig)

        SoulLogger.info("Application launched in ${Clock.System.now().toReadableString()}")
        SoulLogger.info("Running in ${pluginConfig.mode.name} mode with introspection ${if (pluginConfig.enableIntrospection) "enabled" else "disabled"}")

        // Register log reflect route (only in development mode or when explicitly enabled)
        if (pluginConfig.reflex.route && pluginConfig.enableIntrospection) {
            app.configureSoulLoggerLogFileReflex(pluginConfig)
            SoulLogger.debug("Log reflex route registered at ${pluginConfig.reflex.path}")
        }

        // Register analysis routes (only in development mode or when explicitly enabled)
        if (pluginConfig.analysis.analysisRoute && pluginConfig.enableIntrospection) {
            app.apply {
                configureSoulLoggerLogFileAnalysis(this@createApplicationPlugin.pluginConfig)
                configureSoulLoggerReportResultGenerator(this@createApplicationPlugin.pluginConfig)
            }
            SoulLogger.debug("Analysis routes registered")
        }

        // Register runtime management routes (if enabled)
        if (pluginConfig.runtimeManagement.dynamicConfigEnabled) {
            app.configureConfigManagement(pluginConfig, dynamicConfigManager)
            SoulLogger.debug("Config management route registered at /soul/logger/config")
        }

        if (pluginConfig.runtimeManagement.runtimeLevelEnabled) {
            app.configureRuntimeLevel(pluginConfig, runtimeLevelManager)
            SoulLogger.debug("Runtime level route registered at /soul/logger/level")
        }

        // Register health check routes (if enabled)
        if (pluginConfig.healthCheck.enabled) {
            app.configureHealthCheck(pluginConfig, healthCheckManager)
            SoulLogger.debug("Health check route registered at /soul/logger/health")
        }

        // Start hot reloader (if enabled)
        if (pluginConfig.runtimeManagement.hotReloadEnabled) {
            try {
                val configPath = Paths.get(System.getProperty("soul.logger.config.path", "application.conf"))
                hotReloader = HotReloader(
                    configPath,
                    dynamicConfigManager,
                    HotReloader.ReloadStrategy.valueOf(pluginConfig.runtimeManagement.hotReloadStrategy.uppercase())
                )
                hotReloader?.start()
                SoulLogger.debug("Hot reloader started")
            } catch (e: Exception) {
                SoulLogger.warn("Failed to start hot reloader: ${e.message}")
            }
        }

        // Warn if introspection is disabled
        if (!pluginConfig.enableIntrospection) {
            SoulLogger.info("Introspection routes are disabled in ${pluginConfig.mode.name} mode")
        }
    }

    onCall { call ->
        if (pluginConfig.httpFilter(call)) {
            SoulLogger.info("Call ${call.request.local.remoteHost} --> ${call.request.path()}")
        }
    }

    onCallRespond { call, body ->
        if (pluginConfig.httpFilter(call)) {
            val level = call.response.status()?.let { SoulLogger.Level.of(it) } ?: SoulLogger.Level.WARN
            val fromTo = "${call.request.local.remoteHost} --> ${call.request.path()}"
            val bodySize = body.toString().toByteArray(Charsets.UTF_8).size
            val statusType = "${call.response.status() ?: "Unknown status"} && $bodySize bytes"
            val command = "Response $fromTo ($statusType)"
            SoulLogger.create(level, command)
        }
    }

    on(MonitoringEvent(ApplicationStopped)) {
        hotReloader?.stop()
        dynamicConfigManager.shutdown()
        SoulLogger.info("Application stopped in ${Clock.System.now().toReadableString()}")
    }
}
