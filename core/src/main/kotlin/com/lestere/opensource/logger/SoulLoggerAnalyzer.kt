package com.lestere.opensource.logger

import com.lestere.opensource.csv.CSVGenerator
import com.lestere.opensource.models.CodableException
import com.lestere.opensource.utils.fileNotFoundException
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

internal object SoulLoggerAnalyzer {
    enum class SortEdge {
        RELEASE, OLDEST;
    }

    private const val ANALYSIS_SOURCES_EXT = "anares" // @Mark: `anares` means `Analyze Result`.

    private const val ANALYSIS_SOURCES_FOUNT = "telemetry-meters"

    internal const val ANALYSIS_CSV_EXT = "csv"

    internal const val ANALYSIS_CSV_FOUNT = "telemetry-metrics"

    internal fun generateReport(source: String, output: String) {
        val sourceFile = Paths.get(source).toFile()
        if (sourceFile.exists() and sourceFile.isFile) {
            val list = readFileToLoggerList(sourceFile.path, 0..Int.MAX_VALUE)
            val outputPath = Paths.get(output).resolve(sourceFile.nameWithoutExtension)
            if (!outputPath.toFile().exists()) {
                Files.createDirectories(outputPath)
            }
            val json = Json { prettyPrint = true }
            val anaPath = outputPath.resolve("${ANALYSIS_SOURCES_FOUNT}.${ANALYSIS_SOURCES_EXT}")
            val csvPath = outputPath.resolve("${ANALYSIS_CSV_FOUNT}.${ANALYSIS_CSV_EXT}")
            anaPath.writeText(json.encodeToString(list))
            SoulLogger.info("Analysis result created in \"${anaPath.absolutePathString()}\"")

            // @Fixed(2024.9.10): Type define replace empty first line, csv may divide block by first line.
            val dl = mutableListOf<Array<String>>()
            dl.add(Logger.CSV_HEADER)
            list.forEach { dl.add(it.paramsArray) }
            CSVGenerator.writeList(dl, csvPath)
            SoulLogger.info("Analysis csv created in \"${csvPath.absolutePathString()}\"")
        } else {
            throw fileNotFoundException
        }
    }

    @Throws(CodableException::class)
    suspend fun getEdgeLogFileInDictionary(dict: String, edge: SortEdge = SortEdge.RELEASE): String =
        withContext(Dispatchers.IO) {
            val logFileList = mutableListOf<Instant>()
            val dictionary = Paths.get(dict).toFile()
            dictionary.listFiles()
                ?.forEach { file ->
                    file.nameWithoutExtension
                        .toLongOrNull()
                        ?.let { timestamp ->
                            logFileList.add(Instant.fromEpochMilliseconds(timestamp))
                        }
                }
                ?: throw fileNotFoundException
            logFileList.sortByDescending { it }
            val single = when (edge) {
                SortEdge.OLDEST -> logFileList.lastOrNull()
                SortEdge.RELEASE -> logFileList.firstOrNull()
            }

            if (single == null) {
                throw fileNotFoundException
            } else {
                try {
                    return@withContext "${dictionary}/${single.toEpochMilliseconds()}.log"
                } catch (e: Exception) {
                    throw e
                }
            }
        }

    @Throws(CodableException::class)
    suspend fun readFileToLoggerListWithContext(dict: String, range: IntRange) = withContext(Dispatchers.IO) {
        return@withContext readFileToLoggerList(dict, range)
    }

    @Throws(CodableException::class)
    private fun readFileToLoggerList(dict: String, range: IntRange, indents: Int = 4): List<Logger> {
        val file = Paths.get(dict).toFile()
        if (file.exists() and file.isFile) {
            return file.readLines(Charsets.UTF_8)
                .asSequence()
                .filter { line -> !line.startsWith("\t".repeat(indents)) }
                .filterIndexed { index, _ -> index in range }
                .toList()
                .mapNotNull(Logger::parse)
        } else {
            throw fileNotFoundException
        }
    }
}
