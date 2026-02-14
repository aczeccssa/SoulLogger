package com.lestere.opensource.logger.routes

import com.lestere.opensource.config.RuntimeLevelManager
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
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
                call.respond(mapOf("level" to levelManager.getGlobalLevelValue().name))
            }
            
            put {
                val request = call.receive<SetLevelRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    levelManager.setLevel(request.packagePattern, level)
                    call.respond(mapOf(
                        "success" to true,
                        "packagePattern" to (request.packagePattern.ifBlank { "global" }),
                        "level" to level.name
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }
            
            put("/global") {
                val request = call.receive<SetGlobalLevelRequest>()
                try {
                    val level = Level.valueOf(request.level.uppercase())
                    levelManager.setLevel("", level)
                    call.respond(mapOf("success" to true, "globalLevel" to level.name))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }
            
            delete("/{pattern}") {
                val pattern = call.parameters["pattern"]
                if (pattern != null) {
                    levelManager.reset(pattern)
                    call.respond(mapOf("success" to true, "removed" to pattern))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Pattern required"))
                }
            }
            
            delete {
                levelManager.reset()
                call.respond(mapOf("success" to true, "message" to "All custom levels reset"))
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
