package com.lestere.opensource.soullogger.sample

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

fun Application.configureWebUI() {
    
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    val logCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    
    scope.launch {
        SoulLogger.logStream.collect { log ->
            val current = logCounts.value.toMutableMap()
            current[log.logLevel.name] = (current[log.logLevel.name] ?: 0) + 1
            logCounts.value = current
            
            val logs = recentLogs.value.toMutableList()
            logs.add(0, LogEntry(
                timestamp = log.timestamp.toString(),
                level = log.logLevel.name,
                thread = log.thread.toString(),
                entry = log.entry,
                command = log.command
            ))
            if (logs.size > 100) logs.removeAt(logs.size - 1)
            recentLogs.value = logs
        }
    }
    
    routing {
        get("/") {
            call.respondRedirect("/webui/", permanent = false)
        }
        
        get("/index.html") {
            call.respondRedirect("/webui/", permanent = false)
        }
        
        staticResources("/webui", "webui")
        
        route("/api/v1") {
            get("/health") {
                SoulLogger.debug("API: /api/v1/health called")
                val response = try {
                    val queueSize = SoulLoggerProvider.getQueueSize()
                    HealthResponse(
                        status = "healthy",
                        active = SoulLogger.active,
                        queueSize = queueSize,
                        timestamp = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    HealthResponse(status = "unhealthy", error = e.message ?: "Unknown")
                }
                call.respond(response)
            }
            
            get("/stats") {
                SoulLogger.debug("API: /api/v1/stats called")
                call.respond(StatsResponse(
                    active = SoulLogger.active,
                    logCounts = logCounts.value,
                    totalLogs = logCounts.value.values.sum(),
                    timestamp = System.currentTimeMillis()
                ))
            }
            
            get("/stats/level-distribution") {
                SoulLogger.debug("API: /api/v1/stats/level-distribution called")
                val counts = logCounts.value
                call.respond(LevelDistributionResponse(
                    debug = counts["DEBUG"] ?: 0,
                    info = counts["INFO"] ?: 0,
                    warn = counts["WARN"] ?: 0,
                    error = counts["ERROR"] ?: 0,
                    fatal = counts["FATAL"] ?: 0
                ))
            }
            
            get("/logs/recent") {
                SoulLogger.debug("API: /api/v1/logs/recent called")
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
                call.respond(RecentLogsResponse(
                    logs = recentLogs.value.take(limit),
                    total = recentLogs.value.size
                ))
            }
            
            post("/logs/clear") {
                SoulLogger.info("Dashboard logs cleared via API")
                logCounts.value = emptyMap()
                recentLogs.value = emptyList()
                call.respond(SuccessResponse(success = true))
            }
            
            get("/config") {
                SoulLogger.debug("API: /api/v1/config called")
                val config = SoulLoggerProvider.getConfig()
                call.respond(ConfigResponse(
                    level = config.level.name,
                    maxFileSize = config.maxFileSize,
                    queueCapacity = config.queueCapacity,
                    enableConsole = config.enableConsole,
                    enableFile = config.enableFile,
                    enableMasking = config.enableMasking,
                    enableLogback = config.enableLogback,
                    rotationEnabled = config.rotation.enabled,
                    filterEnabled = config.logFilter.enabled,
                    streamEnabled = config.stream.enabled
                ))
            }
            
            put("/config/level") {
                val request = call.receive<LevelChangeRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    SoulLogger.info("API: Log level changed to ${level.name}")
                    call.respond(LevelChangeResponse(success = true, level = level.name))
                } catch (e: Exception) {
                    SoulLogger.warn("API: Failed to change log level - ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(success = false, error = e.message ?: "Invalid"))
                }
            }
            
            post("/logs/generate") {
                val request = call.receive<GenerateLogsRequest>()
                SoulLogger.info("API: Generating ${request.count} test logs at ${request.level} level")
                repeat(request.count) { i ->
                    when (request.level.uppercase()) {
                        "DEBUG" -> SoulLogger.debug("Test $i")
                        "INFO" -> SoulLogger.info("Test $i")
                        "WARN" -> SoulLogger.warn("Test $i")
                        "ERROR" -> SoulLogger.error(IllegalStateException("Test $i"))
                        else -> SoulLogger.info("Test $i")
                    }
                }
                call.respond(GenerateLogsResponse(success = true, generated = request.count))
            }
            
            get("/logs/stream") {
                SoulLogger.debug("API: /api/v1/logs/stream - SSE connection opened")
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondTextWriter(ContentType.Text.EventStream) {
                    val writer = this
                    try {
                        writer.write(": SoulLogger Stream\n\n")
                        writer.flush()
                        SoulLogger.logStream.collect { log ->
                            try {
                                val entry = """{"timestamp":"${log.timestamp}","level":"${log.logLevel.name}","entry":"${log.entry}","command":"${log.command.replace("\"", "\\\"")}"}"""
                                writer.write("data: $entry\n\n")
                                writer.flush()
                            } catch (_: java.io.IOException) {
                                throw CancellationException("Client disconnected")
                            }
                        }
                    } catch (_: CancellationException) {
                        // Client disconnected normally
                    } catch (_: Exception) {
                        // Stream closed
                    }
                }
            }
        }
    }
}

@Serializable
data class LevelChangeRequest(val level: String)

@Serializable
data class GenerateLogsRequest(val count: Int, val level: String)

@Serializable
data class LogEntry(val timestamp: String, val level: String, val thread: String, val entry: String, val command: String)

@Serializable
data class HealthResponse(
    val status: String,
    val active: Boolean = true,
    val queueSize: Int = 0,
    val timestamp: Long = 0,
    val error: String? = null
)

@Serializable
data class StatsResponse(
    val active: Boolean,
    val logCounts: Map<String, Int>,
    val totalLogs: Int,
    val timestamp: Long
)

@Serializable
data class LevelDistributionResponse(
    val debug: Int,
    val info: Int,
    val warn: Int,
    val error: Int,
    val fatal: Int
)

@Serializable
data class RecentLogsResponse(
    val logs: List<LogEntry>,
    val total: Int
)

@Serializable
data class SuccessResponse(val success: Boolean)

@Serializable
data class ConfigResponse(
    val level: String,
    val maxFileSize: Long,
    val queueCapacity: Int,
    val enableConsole: Boolean,
    val enableFile: Boolean,
    val enableMasking: Boolean,
    val enableLogback: Boolean,
    val rotationEnabled: Boolean,
    val filterEnabled: Boolean,
    val streamEnabled: Boolean
)

@Serializable
data class LevelChangeResponse(val success: Boolean, val level: String)

@Serializable
data class GenerateLogsResponse(val success: Boolean, val generated: Int)

@Serializable
data class ErrorResponse(val success: Boolean, val error: String)
