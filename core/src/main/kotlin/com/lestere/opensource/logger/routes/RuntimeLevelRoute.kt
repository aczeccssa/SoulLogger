package com.lestere.opensource.logger.routes

import com.lestere.opensource.config.RuntimeLevelManager
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.models.ErrorResponse
import com.lestere.opensource.models.GlobalLevelResponse
import com.lestere.opensource.models.RemoveLevelResponse
import com.lestere.opensource.models.ResetLevelsResponse
import com.lestere.opensource.models.SetGlobalLevelResponse
import com.lestere.opensource.models.SetLevelResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.configureRuntimeLevel(config: SoulLoggerPluginConfiguration, levelManager: RuntimeLevelManager) {
    routing {
        route("/soul/logger/level") {
            get {
                val levels = levelManager.getAllLevels()
                call.respond(levels)
            }
            
            get("/global") {
                call.respond(GlobalLevelResponse(level = levelManager.getGlobalLevelValue().name))
            }
            
            put {
                val request = call.receive<SetLevelRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    levelManager.setLevel(request.packagePattern, level)
                    call.respond(SetLevelResponse(
                        success = true,
                        packagePattern = request.packagePattern.ifBlank { "global" },
                        level = level.name
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Unknown error"))
                }
            }
            
            put("/global") {
                val request = call.receive<SetGlobalLevelRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    levelManager.setLevel("", level)
                    call.respond(SetGlobalLevelResponse(success = true, globalLevel = level.name))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Unknown error"))
                }
            }
            
            delete("/{pattern}") {
                val pattern = call.parameters["pattern"]
                if (pattern != null) {
                    levelManager.reset(pattern)
                    call.respond(RemoveLevelResponse(success = true, removed = pattern))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Pattern required"))
                }
            }
            
            delete {
                levelManager.reset()
                call.respond(ResetLevelsResponse(success = true, message = "All custom levels reset"))
            }
        }
    }
}

@Serializable
data class SetLevelRequest(
    val packagePattern: String,
    val level: String
)

@Serializable
data class SetGlobalLevelRequest(
    val level: String
)
