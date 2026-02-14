package com.lestere.opensource.logger.routes

import com.lestere.opensource.config.DynamicConfigManager
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.configureConfigManagement(config: SoulLoggerPluginConfiguration, configManager: DynamicConfigManager) {
    routing {
        route("/soul/logger/config") {
            get {
                val currentConfig = configManager.getCurrentConfig()
                call.respond(currentConfig)
            }
            
            put("/level") {
                val request = call.receive<LevelUpdateRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    configManager.updateLevel(level)
                    call.respond(mapOf("success" to true, "level" to level.name))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }
            
            put("/queue-capacity") {
                val request = call.receive<QueueCapacityRequest>()
                if (request.capacity > 0) {
                    configManager.updateQueueCapacity(request.capacity)
                    call.respond(mapOf("success" to true, "capacity" to request.capacity))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid capacity"))
                }
            }
            
            put("/max-file-size") {
                val request = call.receive<MaxFileSizeRequest>()
                if (request.size > 0) {
                    configManager.updateMaxFileSize(request.size)
                    call.respond(mapOf("success" to true, "size" to request.size))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid size"))
                }
            }
            
            put("/console") {
                val request = call.receive<EnableRequest>()
                configManager.updateEnableConsole(request.enabled)
                call.respond(mapOf("success" to true, "enabled" to request.enabled))
            }
            
            put("/file") {
                val request = call.receive<EnableRequest>()
                configManager.updateEnableFile(request.enabled)
                call.respond(mapOf("success" to true, "enabled" to request.enabled))
            }
            
            put("/masking") {
                val request = call.receive<EnableRequest>()
                configManager.updateEnableMasking(request.enabled)
                call.respond(mapOf("success" to true, "enabled" to request.enabled))
            }
        }
    }
}

@Serializable
data class LevelUpdateRequest(val level: String)

@Serializable
data class QueueCapacityRequest(val capacity: Int)

@Serializable
data class MaxFileSizeRequest(val size: Long)

@Serializable
data class EnableRequest(val enabled: Boolean)
