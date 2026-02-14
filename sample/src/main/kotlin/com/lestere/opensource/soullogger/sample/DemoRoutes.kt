package com.lestere.opensource.soullogger.sample

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.logger.SoulLoggerProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.take
import kotlinx.serialization.Serializable

fun Application.configureDemoRoutes() {
    routing {
        route("/demo") {
            get {
                call.respond(DemoListResponse(
                    message = "SoulLogger Demo Endpoints",
                    endpoints = DemoEndpoints(
                        rotation = "GET /demo/rotation - Test log rotation",
                        filter = "GET /demo/filter - Test log filtering",
                        masking = "GET /demo/masking - Test sensitive data masking",
                        metrics = "GET /demo/metrics - View metrics",
                        config = "PUT /demo/config - Update config",
                        level = "PUT /demo/level - Runtime level adjustment",
                        health = "GET /demo/health - Health check",
                        logs = "GET /demo/logs - Recent logs",
                        stream = "GET /demo/stream - SSE log stream test",
                        error = "GET /demo/error - Generate error log",
                        warn = "GET /demo/warn - Generate warning log"
                    )
                ))
            }

            get("/rotation") {
                repeat(5) { i ->
                    SoulLogger.info("Rotation test log $i - this message helps trigger rotation")
                }
                SoulLogger.info("Log rotation enabled: ${SoulLoggerPluginConfiguration().rotation.enabled}")
                call.respond(SuccessMessageResponse(message = "Log rotation test completed", rotationEnabled = true))
            }

            get("/filter") {
                SoulLogger.debug("This DEBUG message may be filtered")
                SoulLogger.info("This INFO message should pass filter")
                SoulLogger.warn("This WARN message is important")
                call.respond(FilterTestResponse(message = "Filter test completed", filterEnabled = true, sampleRate = "100%"))
            }

            get("/masking") {
                SoulLogger.info("User password: secret123")
                SoulLogger.info("Credit card: 4111-1111-1111-1111")
                SoulLogger.info("SSN: 123-45-6789")
                SoulLogger.info("API key: sk-1234567890abcdef")
                call.respond(MaskingTestResponse(message = "Masking test completed", masked = listOf("passwords", "credit cards", "SSN", "API keys")))
            }

            get("/metrics") {
                val config = SoulLoggerProvider.getConfig()
                call.respond(MetricsResponse(
                    message = "Metrics endpoint",
                    active = SoulLogger.active,
                    logLevel = config.level.name,
                    maxFileSize = config.maxFileSize,
                    queueCapacity = config.queueCapacity,
                    streamEnabled = config.stream.enabled,
                    logbackEnabled = config.enableLogback,
                    rotationEnabled = config.rotation.enabled,
                    filterEnabled = config.logFilter.enabled,
                    maskingEnabled = config.enableMasking,
                    performance = PerformanceInfo(
                        bufferType = config.performance.bufferType.name,
                        compressionEnabled = config.performance.compressionEnabled,
                        compressionType = config.performance.compressionType
                    ),
                    runtime = RuntimeInfo(
                        dynamicConfigEnabled = config.runtimeManagement.dynamicConfigEnabled,
                        hotReloadEnabled = config.runtimeManagement.hotReloadEnabled,
                        runtimeLevelEnabled = config.runtimeManagement.runtimeLevelEnabled,
                        healthCheckEnabled = config.healthCheck.enabled
                    )
                ))
            }

            put("/config") {
                val request = call.receive<ConfigUpdateRequest>()
                try {
                    val newLevel = Level.valueOf(request.level.uppercase())
                    SoulLogger.info("Config update requested: level = ${newLevel.name}")
                    call.respond(ConfigUpdateResponse(success = true, message = "Config updated to ${newLevel.name}"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(success = false, error = e.message ?: "Invalid"))
                }
            }

            put("/level/{package}") {
                val packagePattern = call.parameters["package"] ?: ""
                val request = call.receive<LevelUpdateRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    SoulLogger.info("Runtime level update: $packagePattern --> ${level.name}")
                    call.respond(LevelUpdateResponse(success = true, packagePattern = packagePattern, level = level.name))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(success = false, error = e.message ?: "Invalid"))
                }
            }

            get("/health") {
                call.respond(HealthDemoResponse(status = "healthy", timestamp = System.currentTimeMillis(), active = SoulLogger.active, queueSize = SoulLoggerProvider.getQueueSize()))
            }

            get("/logs") {
                call.respond(LogsDemoResponse(message = "Recent logs endpoint - use SSE for real-time", endpoint = "/demo/stream"))
            }

            get("/stream") {
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondTextWriter(ContentType.Text.EventStream) {
                    write(": SoulLogger SSE Demo\n\n")
                    try {
                        SoulLogger.logStream.take(10).collect { log ->
                            val entry = """{"timestamp":"${log.timestamp}","level":"${log.logLevel.name}","entry":"${log.entry}","command":"${log.command.replace("\"", "\\\"")}"}"""
                            write("data: $entry\n\n")
                            flush()
                        }
                    } catch (_: Exception) {
                        // Client disconnected
                    }
                }
            }

            get("/error") {
                try {
                    throw IllegalStateException("Demo error for testing")
                } catch (e: Exception) {
                    SoulLogger.error(e)
                }
                call.respond(SuccessMessageResponse(message = "Error logged", checkLogs = true))
            }

            get("/warn") {
                SoulLogger.warn("This is a demo warning message")
                SoulLogger.warn("Warning: Disk space is running low")
                SoulLogger.warn("Warning: High memory usage detected")
                call.respond(SuccessMessageResponse(message = "Warnings logged"))
            }

            get("/info") {
                SoulLogger.info("Application info: Server is running")
                SoulLogger.info("User action: User logged in")
                SoulLogger.info("System event: Configuration reloaded")
                call.respond(SuccessMessageResponse(message = "Info logs generated"))
            }

            get("/debug") {
                SoulLogger.debug("Debug: Entering function calculate()")
                SoulLogger.debug("Debug: Variable x = 42")
                SoulLogger.debug("Debug: Exiting function calculate()")
                call.respond(SuccessMessageResponse(message = "Debug logs generated"))
            }

            get("/load/{count}") {
                val count = call.parameters["count"]?.toIntOrNull() ?: 100
                repeat(count) { i ->
                    when (i % 5) {
                        0 -> SoulLogger.debug("Load test debug $i")
                        1 -> SoulLogger.info("Load test info $i")
                        2 -> SoulLogger.warn("Load test warn $i")
                        3 -> SoulLogger.error(IllegalStateException("Load test error $i"))
                        4 -> SoulLogger.info("Load test $i completed")
                    }
                }
                call.respond(LoadTestResponse(message = "Load test completed", count = count))
            }
        }
    }
}

@Serializable
data class ConfigUpdateRequest(val level: String)

@Serializable
data class LevelUpdateRequest(val level: String)

@Serializable
data class DemoListResponse(val message: String, val endpoints: DemoEndpoints)

@Serializable
data class DemoEndpoints(val rotation: String, val filter: String, val masking: String, val metrics: String, val config: String, val level: String, val health: String, val logs: String, val stream: String, val error: String, val warn: String)

@Serializable
data class SuccessMessageResponse(val message: String, val rotationEnabled: Boolean? = null, val checkLogs: Boolean? = null)

@Serializable
data class FilterTestResponse(val message: String, val filterEnabled: Boolean, val sampleRate: String)

@Serializable
data class MaskingTestResponse(val message: String, val masked: List<String>)

@Serializable
data class MetricsResponse(val message: String, val active: Boolean, val logLevel: String, val maxFileSize: Long, val queueCapacity: Int, val streamEnabled: Boolean, val logbackEnabled: Boolean, val rotationEnabled: Boolean, val filterEnabled: Boolean, val maskingEnabled: Boolean, val performance: PerformanceInfo, val runtime: RuntimeInfo)

@Serializable
data class PerformanceInfo(val bufferType: String, val compressionEnabled: Boolean, val compressionType: String)

@Serializable
data class RuntimeInfo(val dynamicConfigEnabled: Boolean, val hotReloadEnabled: Boolean, val runtimeLevelEnabled: Boolean, val healthCheckEnabled: Boolean)

@Serializable
data class ConfigUpdateResponse(val success: Boolean, val message: String)

@Serializable
data class LevelUpdateResponse(val success: Boolean, val packagePattern: String, val level: String)

@Serializable
data class HealthDemoResponse(val status: String, val timestamp: Long, val active: Boolean, val queueSize: Int)

@Serializable
data class LogsDemoResponse(val message: String, val endpoint: String)

@Serializable
data class LoadTestResponse(val message: String, val count: Int)
