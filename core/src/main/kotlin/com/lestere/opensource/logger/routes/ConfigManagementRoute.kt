package com.lestere.opensource.logger.routes

import com.lestere.opensource.config.DynamicConfigManager
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.models.CapacityUpdateResponse
import com.lestere.opensource.models.EnableUpdateResponse
import com.lestere.opensource.models.ErrorResponse
import com.lestere.opensource.models.LevelUpdateResponse
import com.lestere.opensource.models.SizeUpdateResponse
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
                    call.respond(LevelUpdateResponse(success = true, level = level.name))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Unknown error"))
                }
            }
            
            put("/queue-capacity") {
                val request = call.receive<QueueCapacityRequest>()
                if (request.capacity > 0) {
                    configManager.updateQueueCapacity(request.capacity)
                    call.respond(CapacityUpdateResponse(success = true, capacity = request.capacity))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid capacity"))
                }
            }
            
            put("/max-file-size") {
                val request = call.receive<MaxFileSizeRequest>()
                if (request.size > 0) {
                    configManager.updateMaxFileSize(request.size)
                    call.respond(SizeUpdateResponse(success = true, size = request.size))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid size"))
                }
            }
            
            put("/console") {
                val request = call.receive<EnableRequest>()
                configManager.updateEnableConsole(request.enabled)
                call.respond(EnableUpdateResponse(success = true, enabled = request.enabled))
            }

            put("/file") {
                val request = call.receive<EnableRequest>()
                configManager.updateEnableFile(request.enabled)
                call.respond(EnableUpdateResponse(success = true, enabled = request.enabled))
            }

            put("/masking") {
                val request = call.receive<EnableRequest>()
                configManager.updateEnableMasking(request.enabled)
                call.respond(EnableUpdateResponse(success = true, enabled = request.enabled))
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
