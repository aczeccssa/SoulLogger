package com.lestere.opensource.logger.routes

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerAnalyzer
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.utils.fileNotFoundException
import com.lestere.opensource.utils.invalidPathParameterException
import com.lestere.opensource.utils.isValidPathParameter
import com.lestere.opensource.utils.pathParametersNotFound
import com.lestere.opensource.utils.respondRefiled
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

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
        if (!isValidPathParameter(id)) return@get call.respondRefiled(
            HttpStatusCode.BadRequest,
            invalidPathParameterException,
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
