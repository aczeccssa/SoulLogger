package com.lestere.opensource.aggregation

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger

data class AggregationRequest(
    val groupBy: List<String> = listOf("level"),
    val metrics: List<Metric> = listOf(Metric.COUNT),
    val timeRange: TimeRange? = null,
    val filter: String? = null,
    val limit: Int = 100
)

data class TimeRange(
    val start: kotlinx.datetime.Instant,
    val end: kotlinx.datetime.Instant
)

enum class Metric {
    COUNT,
    ERROR_RATE,
    PER_SECOND,
    PERCENT_LEVEL
}

data class AggregationResult(
    val groups: List<GroupResult>,
    val totalCount: Int,
    val timeRange: TimeRange?
)

data class GroupResult(
    val key: String,
    val count: Int,
    val errorCount: Int,
    val errorRate: Double,
    val perSecond: Double
)

class AggregationEngine {

    fun aggregate(logs: List<Logger>, request: AggregationRequest): AggregationResult {
        val filtered = applyFilter(logs, request.filter)
        val timeFiltered = applyTimeRange(filtered, request.timeRange)
        
        val groups = when {
            request.groupBy.contains("level") -> aggregateByLevel(timeFiltered)
            request.groupBy.contains("entry") -> aggregateByEntry(timeFiltered)
            request.groupBy.contains("date") -> aggregateByDate(timeFiltered)
            request.groupBy.contains("hour") -> aggregateByHour(timeFiltered)
            else -> listOf(GroupResult("all", timeFiltered.size, 
                timeFiltered.count { it.logLevel >= SoulLogger.Level.ERROR },
                calculateErrorRate(timeFiltered),
                calculatePerSecond(timeFiltered)))
        }
        
        return AggregationResult(
            groups = groups.take(request.limit),
            totalCount = timeFiltered.size,
            timeRange = request.timeRange
        )
    }

    private fun applyFilter(logs: List<Logger>, filter: String?): List<Logger> {
        if (filter.isNullOrBlank()) return logs
        
        // Simple filter implementation
        return logs.filter { log ->
            filter.split(Regex("\\s+")).all { term ->
                when {
                    term.startsWith("level:") -> {
                        val level = term.substringAfter(":").uppercase()
                        log.logLevel.name == level
                    }
                    term.startsWith("entry:") -> {
                        val entry = term.substringAfter(":")
                        log.entry.contains(entry)
                    }
                    else -> log.command.contains(term, ignoreCase = true)
                }
            }
        }
    }

    private fun applyTimeRange(logs: List<Logger>, range: TimeRange?): List<Logger> {
        if (range == null) return logs
        return logs.filter { log ->
            log.timestamp in range.start..range.end
        }
    }

    private fun aggregateByLevel(logs: List<Logger>): List<GroupResult> {
        return SoulLogger.Level.entries.map { level ->
            val levelLogs = logs.filter { it.logLevel == level }
            GroupResult(
                key = level.name,
                count = levelLogs.size,
                errorCount = 0,
                errorRate = if (logs.isEmpty()) 0.0 else levelLogs.size.toDouble() / logs.size,
                perSecond = calculatePerSecond(levelLogs)
            )
        }
    }

    private fun aggregateByEntry(logs: List<Logger>): List<GroupResult> {
        return logs.groupBy { it.entry.substringAfterLast(".") }
            .map { (entry, entryLogs) ->
                GroupResult(
                    key = entry,
                    count = entryLogs.size,
                    errorCount = entryLogs.count { it.logLevel >= SoulLogger.Level.ERROR },
                    errorRate = calculateErrorRate(entryLogs),
                    perSecond = calculatePerSecond(entryLogs)
                )
            }
            .sortedByDescending { it.count }
    }

    private fun aggregateByDate(logs: List<Logger>): List<GroupResult> {
        return logs.groupBy { it.timestamp.toString().substring(0, 10) }
            .map { (date, dateLogs) ->
                GroupResult(
                    key = date,
                    count = dateLogs.size,
                    errorCount = dateLogs.count { it.logLevel >= SoulLogger.Level.ERROR },
                    errorRate = calculateErrorRate(dateLogs),
                    perSecond = calculatePerSecond(dateLogs)
                )
            }
            .sortedByDescending { it.key }
    }

    private fun aggregateByHour(logs: List<Logger>): List<GroupResult> {
        return logs.groupBy { it.timestamp.toString().substring(0, 13).replace("T", " ") }
            .map { (hour, hourLogs) ->
                GroupResult(
                    key = hour,
                    count = hourLogs.size,
                    errorCount = hourLogs.count { it.logLevel >= SoulLogger.Level.ERROR },
                    errorRate = calculateErrorRate(hourLogs),
                    perSecond = calculatePerSecond(hourLogs)
                )
            }
            .sortedByDescending { it.key }
    }

    private fun calculateErrorRate(logs: List<Logger>): Double {
        if (logs.isEmpty()) return 0.0
        val errors = logs.count { it.logLevel >= SoulLogger.Level.ERROR }
        return errors.toDouble() / logs.size
    }

    fun calculateErrorRatePublic(logs: List<Logger>): Double {
        return calculateErrorRate(logs)
    }

    private fun calculatePerSecond(logs: List<Logger>): Double {
        if (logs.size < 2) return 0.0
        
        val sorted = logs.sortedBy { it.timestamp }
        val first = sorted.first().timestamp
        val last = sorted.last().timestamp
        
        val durationSeconds = (last.toEpochMilliseconds() - first.toEpochMilliseconds()) / 1000.0
        if (durationSeconds <= 0) return 0.0
        
        return logs.size / durationSeconds
    }

    fun getStatistics(logs: List<Logger>): Map<String, Any?> {
        return mapOf(
            "total" to logs.size,
            "byLevel" to SoulLogger.Level.entries.associate { level ->
                level.name to logs.count { it.logLevel == level }
            },
            "errorCount" to logs.count { it.logLevel >= SoulLogger.Level.ERROR },
            "errorRate" to calculateErrorRate(logs),
            "uniqueEntries" to logs.map { it.entry }.distinct().size,
            "timeRange" to if (logs.isNotEmpty()) {
                val sorted = logs.sortedBy { it.timestamp }
                mapOf(
                    "start" to sorted.first().timestamp.toString(),
                    "end" to sorted.last().timestamp.toString()
                )
            } else null
        )
    }
}
