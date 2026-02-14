package com.lestere.opensource.aggregation

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LocalLogAggregator(
    private val logDirectory: String = "logs",
    private val maxMemoryEntries: Int = 100_000
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val indexer = LogIndexer(maxMemoryEntries)
    private val aggregationEngine = AggregationEngine()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _stats = MutableStateFlow(AggregatorStats())
    val stats: StateFlow<AggregatorStats> = _stats.asStateFlow()
    
    private val _newLogs = MutableSharedFlow<Logger>(extraBufferCapacity = 100)
    val newLogs: SharedFlow<Logger> = _newLogs.asSharedFlow()
    
    private var processedCount = 0L
    
    data class AggregatorStats(
        val totalProcessed: Long = 0,
        val indexSize: Int = 0,
        val errorCount: Long = 0,
        val lastUpdate: Instant = Clock.System.now()
    )
    
    fun start() {
        if (_isRunning.value) return
        
        _isRunning.value = true
        
        // Subscribe to log stream if available
        scope.launch {
            try {
                SoulLoggerProvider.logStream.collect { log ->
                    index(log)
                }
            } catch (e: Exception) {
                // Log stream might not be enabled
            }
        }
        
        // Index existing log files
        scope.launch {
            indexExistingLogs()
        }
    }
    
    fun stop() {
        _isRunning.value = false
    }
    
    fun index(logger: Logger) {
        indexer.index(logger)
        processedCount++
        
        if (logger.logLevel >= SoulLogger.Level.ERROR) {
            _stats.value = _stats.value.copy(
                totalProcessed = processedCount,
                indexSize = indexer.size.value,
                errorCount = _stats.value.errorCount + 1,
                lastUpdate = Clock.System.now()
            )
        } else {
            _stats.value = _stats.value.copy(
                totalProcessed = processedCount,
                indexSize = indexer.size.value,
                lastUpdate = Clock.System.now()
            )
        }
        
        // Emit new log event
        _newLogs.tryEmit(logger)
    }
    
    private suspend fun indexExistingLogs() {
        try {
            val dir = Paths.get(logDirectory)
            if (!Files.exists(dir)) return
            
            val logFiles = Files.list(dir)
                .filter { it.toString().endsWith(".log") }
                .toList()
                .sortedBy { Files.getLastModifiedTime(it).toMillis() }
            
            logFiles.forEach { file ->
                parseLogFile(file)
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    private fun parseLogFile(file: Path) {
        try {
            Files.readAllLines(file).forEach { line ->
                val logger = Logger.parse(line)
                if (logger != null) {
                    index(logger)
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    fun search(query: String): List<Logger> {
        return indexer.search(query)
    }
    
    fun searchLogs(
        level: SoulLogger.Level? = null,
        entry: String? = null,
        startTime: Instant? = null,
        endTime: Instant? = null,
        limit: Int = 100
    ): List<Logger> {
        var results = if (level != null) {
            indexer.getByLevel(level)
        } else if (entry != null) {
            indexer.search("entry:$entry")
        } else {
            indexer.getRecent(limit)
        }
        
        // Apply time filter
        if (startTime != null || endTime != null) {
            results = results.filter { log ->
                val afterStart = startTime == null || log.timestamp >= startTime
                val beforeEnd = endTime == null || log.timestamp <= endTime
                afterStart && beforeEnd
            }
        }
        
        return results.take(limit)
    }
    
    fun getErrors(): List<Logger> {
        return indexer.getErrors()
    }
    
    fun aggregate(request: AggregationRequest): AggregationResult {
        val logs = indexer.getRecent(Int.MAX_VALUE)
        return aggregationEngine.aggregate(logs, request)
    }
    
    fun getStatistics(): Map<String, Any?> {
        val logs = indexer.getRecent(Int.MAX_VALUE)
        return aggregationEngine.getStatistics(logs)
    }
    
    fun getTopEntries(limit: Int = 10): List<Pair<String, Int>> {
        val logs = indexer.getRecent(Int.MAX_VALUE)
        return logs.groupBy { it.entry }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
    
    fun getErrorRateByHour(hours: Int = 24): Map<String, Double> {
        val logs = indexer.getRecent(Int.MAX_VALUE)
        
        return logs
            .groupBy { it.timestamp.toString().substring(0, 13).replace("T", " ") }
            .mapValues { (_, hourLogs) ->
                aggregationEngine.calculateErrorRatePublic(hourLogs)
            }
            .filterKeys { it >= hours.toString() }
    }
    
    fun clear() {
        indexer.clear()
        processedCount = 0
        _stats.value = AggregatorStats()
    }
    
    private val endTime: Instant get() = Clock.System.now()
}
