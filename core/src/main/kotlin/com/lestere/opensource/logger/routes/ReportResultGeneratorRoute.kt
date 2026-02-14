package com.lestere.opensource.logger.routes

import com.lestere.opensource.arc.AutoReleaseCyber
import com.lestere.opensource.arc.AutoReleaseCyberTag
import com.lestere.opensource.arc.AutoReleaseCyberTask
import com.lestere.opensource.csv.CSVAnalyser
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerAnalyzer
import com.lestere.opensource.logger.SoulLoggerAnalyzer.ANALYSIS_CSV_EXT
import com.lestere.opensource.logger.SoulLoggerAnalyzer.ANALYSIS_CSV_FOUNT
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.pathString

/**
 * 1. Request: timestamp -> report.
 * 2. Response: result + analysed html url.
 * 3. Background: expire in some times(or when server down), delete when one of the conditions is met.
 * n. @Mark: Background remove task append to `AutoReleaseCyber`.
 */
internal fun Application.configureSoulLoggerReportResultGenerator(config: SoulLoggerPluginConfiguration) {
    routing {
        route("/${config.reflex.path}/report/generation/") {
            handleRequestGenerateLogFileAnalysisHtmlResult(
                config.analysisOutputDictionary,
                config.logDictionary,
                config.reportGenerationDict,
                config.report.tempFileExpiredIn
            )

            handleRequestGetGeneratedLogFileAnalysisHtmlResult(config.reportGenerationDict)
        }
    }
}

/**
 * Auto generate ARC(Auto Release Cyber) task and push to system using key information.
 */
private fun arcReportTaskCleaner(id: String, output: Path, aliveDurationMs: Long): AutoReleaseCyberTask {
    // Generate ARC(Auto Release Cyber) task
    val task = AutoReleaseCyberTask.build(
        "Task for generated temp html file(${id}).",
        AutoReleaseCyberTag.FORCE,
        aliveDurationMs
    ) {
        output.toFile().deleteRecursively()
        SoulLogger.info("Generated csv analysed html $id already deleted.")
    }
    // Push task to ARC(Auto Release Cyber) queue.
    AutoReleaseCyber.push(task)
    return task
}

private fun generateNotExistsLogReport(path: Path, tempDict: String, aliveDurationMs: Long) {
    SoulLoggerAnalyzer.generateReport(path.pathString, tempDict)
    if (!path.toFile().exists()) throw fileCreateException
    val task = AutoReleaseCyberTask.build(
        "Task for delete temp log report ${path}.",
        AutoReleaseCyberTag.FORCE,
        aliveDurationMs
    ) {
        path.toFile().deleteRecursively()
        SoulLogger.info("Generated temp log report $path already deleted.")
    }
    AutoReleaseCyber.push(task)
}

private fun Route.handleRequestGenerateLogFileAnalysisHtmlResult(
    reportDict: String,
    logDict: String,
    tempDict: String,
    aliveDurationMs: Long
) {
    get("/html/{timestamp}") {
        try {
            val ts = call.parameters["timestamp"] ?: return@get call.respondRefiled(
                HttpStatusCode.BadRequest,
                pathParametersNotFound("timestamp"),
                Unit
            )
            var path = Paths.get("${reportDict}/${ts}")
            if (!Files.exists(path)) {
                val logPath = Paths.get("${logDict}/${call.parameters["timestamp"]}.log")
                if (!Files.exists(logPath)) return@get call.respondRefiled(
                    HttpStatusCode.BadRequest,
                    fileNotFoundException,
                    Unit
                )
                path = Paths.get("${tempDict}/${call.parameters["timestamp"]}")
                if (!Files.exists(path)) generateNotExistsLogReport(logPath, tempDict, aliveDurationMs)
            }
            // Using csv report to generate
            val csvPath = path.resolve("${ANALYSIS_CSV_FOUNT}.${ANALYSIS_CSV_EXT}")
            val df = DataFrame.readCSV(csvPath.pathString)
            val tempId = UUID.randomUUID().toLcsSecString()
            val outputDict = Paths.get("${tempDict}/${tempId}/")
            CSVAnalyser.analysisCSVToHtmlWithAnalyzedFooter(df, outputDict)
            arcReportTaskCleaner(tempId, outputDict, aliveDurationMs)
            call.respondRefiled(HttpStatusCode.OK, tempId)
        } catch (e: Exception) {
            SoulLogger.error(e)
            call.respondRefiled(HttpStatusCode.BadRequest, fileCreateException, e)
        }
    }
}

private fun Route.handleRequestGetGeneratedLogFileAnalysisHtmlResult(tempDict: String) {
    get("/result/{id}") {
        val id = call.parameters["id"] ?: return@get call.respondRefiled(
            HttpStatusCode.BadRequest,
            pathParametersNotFound("id"),
            Unit
        )
        val path = Paths.get("${tempDict}/${id}/${CSVAnalyser.FULLY_RESULT_HTML_NAME}")
        if (!Files.exists(path)) return@get call.respondRefiled(HttpStatusCode.NotFound, fileNotFoundException, Unit)
        val file = path.toFile()
        call.response.header("Content-Type", "application/${file.extension}")
        call.response.header("Content-Disposition", "attachment; filename=\"${file.name}\"")
        call.respondFile(file)
    }
}
