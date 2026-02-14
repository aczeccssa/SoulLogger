package com.lestere.opensource.logger.routes

import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.utils.fileNotFoundException
import com.lestere.opensource.utils.pathParametersNotFound
import com.lestere.opensource.utils.respondRefiled
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Paths

internal fun Application.configureSoulLoggerLogFileReflex(config: SoulLoggerPluginConfiguration) {
    routing {
        route(config.reflex.path) {
            handleTimestampFileRequest(config.logDictionary)
        }
    }
}

private fun Route.handleTimestampFileRequest(dictionary: String) {
    get("/{timestamp}") {
        val id = call.parameters["timestamp"] ?: return@get call.respondRefiled(
            HttpStatusCode.BadRequest,
            pathParametersNotFound("timestamp"),
            Unit
        )
        val file = Paths.get("${dictionary}/${id}.log").toFile()
        if (file.exists() and !file.name.startsWith(".")) {
            call.response.header("Content-Type", "application/${file.extension}")
            call.response.header("Content-Disposition", "attachment; filename=\"${file.name}\"")
            call.respondFile(file)
        } else {
            call.respondRefiled(HttpStatusCode.NotFound, fileNotFoundException, Unit)
        }
    }
}
