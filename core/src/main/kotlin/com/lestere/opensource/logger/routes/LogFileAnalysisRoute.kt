package com.lestere.opensource.logger.routes

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerAnalyzer
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.utils.fileNotFoundException
import com.lestere.opensource.utils.pathParametersNotFound
import com.lestere.opensource.utils.respondRefiled
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

internal fun Application.configureSoulLoggerLogFileAnalysis(config: SoulLoggerPluginConfiguration) {
    routing {
        route("${config.reflex.path}/analysis") {
            handleAnalysisTimestampRequest(config.logDictionary, config.analysis.maxItems)
        }
    }
}

private fun Route.handleAnalysisTimestampRequest(dictionary: String, maxItems: Int) {
    get("/release") {
        val page = call.queryParameters["page"]?.toIntOrNull() ?: 0
        try {
            val path = SoulLoggerAnalyzer.getEdgeLogFileInDictionary(dictionary)
            val pre = page * maxItems
            val list = SoulLoggerAnalyzer.readFileToLoggerListWithContext(path, pre..<(pre + maxItems))
            call.respondRefiled(HttpStatusCode.OK, list)
        } catch (e: Exception) {
            SoulLogger.error(e)
            call.respondRefiled(HttpStatusCode.BadRequest, fileNotFoundException, Unit)
        }
    }

    get("/{timestamp}") {
        val id = call.parameters["timestamp"] ?: return@get call.respondRefiled(
            HttpStatusCode.BadRequest,
            pathParametersNotFound("timestamp"),
            Unit
        )
        val page = call.queryParameters["page"]?.toIntOrNull() ?: 0

        try {
            val pre = page * maxItems
            val list =
                SoulLoggerAnalyzer.readFileToLoggerListWithContext("${dictionary}/${id}.log", pre..<(pre + maxItems))
            call.respondRefiled(HttpStatusCode.OK, list)
        } catch (e: Exception) {
            SoulLogger.error(e)
            call.respondRefiled(HttpStatusCode.BadRequest, fileNotFoundException, Unit)
        }
    }
}
